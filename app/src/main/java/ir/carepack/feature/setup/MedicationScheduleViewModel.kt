package ir.carepack.feature.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.calendar.FirstDayOfWeekPolicy
import ir.carepack.domain.careplan.AddScheduleCommand
import ir.carepack.domain.careplan.AddScheduleOutcome
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.reminder.ManufacturerGuidance
import ir.carepack.domain.reminder.ManufacturerGuidanceClassifier
import ir.carepack.reminder.permission.AndroidBatteryOptimizationGateway
import ir.carepack.reminder.permission.AndroidExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.AndroidNotificationPermissionGateway
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.feature.careplan.MedicationTextFields
import ir.carepack.feature.careplan.ScheduleFormCallbacks
import ir.carepack.feature.careplan.ScheduleFormFields
import ir.carepack.feature.careplan.ScheduleFormUiState
import ir.carepack.feature.careplan.ScheduleInputMode
import ir.carepack.feature.careplan.addDraftTime
import ir.carepack.feature.careplan.clearErrors
import ir.carepack.feature.careplan.effectiveMinutesOfDay
import ir.carepack.feature.careplan.parseDates
import ir.carepack.feature.careplan.removeTime
import ir.carepack.feature.careplan.toFieldErrors
import ir.carepack.feature.careplan.toHourMinuteText
import ir.carepack.feature.careplan.toMinuteOfDay
import ir.carepack.feature.careplan.toSchedulePattern
import ir.carepack.feature.careplan.toggleWeekday
import ir.carepack.feature.careplan.withDateErrors
import ir.carepack.feature.careplan.withEndDate
import ir.carepack.feature.careplan.withInputMode
import ir.carepack.feature.careplan.withIntervalAnchorDraft
import ir.carepack.feature.careplan.withIntervalHours
import ir.carepack.feature.careplan.withIntervalHoursDefault
import ir.carepack.feature.careplan.withPreviewEffectiveFrom
import ir.carepack.feature.careplan.withStartDate
import ir.carepack.feature.careplan.withTimeDraft
import ir.carepack.feature.careplan.withValidationErrors
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import ir.carepack.ui.experience.carePackExperience
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class MedicationScheduleUiState(
    val medicationName: String = "",
    val instruction: String = "",
    val medicationType: String = "",
    val dosageText: String = "",
    val doseUnit: String = "",
    val schedule: ScheduleFormUiState,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val previewAnchorDate: LocalDate = LocalDate.now(),
    val medicationErrors: Map<CarePlanField, String> = emptyMap(),
    val generalError: String? = null,
    val isSaving: Boolean = false,
    val isAddScheduleOnly: Boolean = false,
    val showInitialReminderGuidance: Boolean = true,
    val seniorMode: SeniorMode = SeniorMode.STANDARD,
    val showPostSetupSimpleModeSuggestion: Boolean = false,
    val completionRequested: Boolean = false,
    val completionSeniorMode: SeniorMode? = null,
)

data class FirstSetupReminderReadinessUiState(
    val notificationRuntimePermissionRequired: Boolean = true,
    val notificationPermissionGranted: Boolean = false,
    val notificationPermissionCanBeRequested: Boolean = true,
    val exactAlarmRelevant: Boolean = true,
    val exactAlarmAvailable: Boolean = false,
    val batteryOptimizationState:
    BatteryOptimizationState =
        BatteryOptimizationState.UNKNOWN,
    val manufacturer: String? = null,
)


private sealed interface MedicationScheduleMode {

    data class CreateMedication(
        val recipientId: String,
        val completeInitialSetup: Boolean,
    ) : MedicationScheduleMode

    data class AddSchedule(
        val medicationId: String,
    ) : MedicationScheduleMode
}

class MedicationScheduleViewModel private constructor(
    private val mode: MedicationScheduleMode,
    private val carePlanService: CarePlanService,
    private val setupPreferenceStore:
    SetupPreferenceStore,
    private val userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : ViewModel() {

    private val currentZone =
        zoneProvider.currentZone()

    private val mutableState =
        MutableStateFlow(
            MedicationScheduleUiState(
                schedule =
                    ScheduleFormUiState(
                        weekdays =
                            DayOfWeek.entries.toSet(),
                        minutesOfDay =
                            emptyList(),
                        timeDraft = "",
                        startDateText = "",
                        endDateText = "",
                        zoneId =
                            currentZone.id,
                        previewEffectiveFrom =
                            currentEffectiveFrom(),
                    ).withIntervalHoursDefault(),
                previewAnchorDate =
                    currentPreviewDate(),
                isAddScheduleOnly =
                    mode is MedicationScheduleMode
                    .AddSchedule,
                showInitialReminderGuidance =
                    mode is MedicationScheduleMode
                    .CreateMedication,
            ),
        )

    val state =
        mutableState.asStateFlow()


    init {
        viewModelScope.launch {
            userExperiencePreferenceStore
                .state
                .collect { preferenceState ->
                    mutableState.update {
                            currentState ->
                        currentState.copy(
                            firstDayOfWeek =
                                FirstDayOfWeekPolicy
                                    .resolve(
                                        preference =
                                            preferenceState
                                                .firstDayOfWeekPreference,
                                        zoneId =
                                            currentZone,
                                        locale =
                                            Locale.getDefault(),
                                    ),
                            seniorMode =
                                preferenceState
                                    .seniorMode,
                        )
                    }
                }
        }
    }

    fun onMedicationNameChanged(
        value: String,
    ) {
        mutableState.update {
                currentState ->
            currentState.copy(
                medicationName = value,
                medicationErrors =
                    currentState
                        .medicationErrors -
                            CarePlanField
                                .MEDICATION_NAME,
                generalError = null,
            )
        }
    }

    fun onInstructionChanged(
        value: String,
    ) {
        mutableState.update {
                currentState ->
            currentState.copy(
                instruction = value,
                medicationErrors =
                    currentState
                        .medicationErrors -
                            CarePlanField
                                .INSTRUCTION,
                generalError = null,
            )
        }
    }

    fun onMedicationTypeChanged(
        value: String,
    ) {
        mutableState.update {
                currentState ->
            currentState.copy(
                medicationType = value,
                medicationErrors =
                    currentState
                        .medicationErrors -
                            CarePlanField
                                .MEDICATION_TYPE,
                generalError = null,
            )
        }
    }

    fun onDosageTextChanged(
        value: String,
    ) {
        mutableState.update {
                currentState ->
            currentState.copy(
                dosageText = value,
                medicationErrors =
                    currentState
                        .medicationErrors -
                            CarePlanField
                                .DOSAGE_TEXT,
                generalError = null,
            )
        }
    }

    fun onDoseUnitChanged(
        value: String,
    ) {
        mutableState.update {
                currentState ->
            currentState.copy(
                doseUnit = value,
                medicationErrors =
                    currentState
                        .medicationErrors -
                            CarePlanField
                                .DOSE_UNIT,
                generalError = null,
            )
        }
    }

    fun onWeekdayToggled(
        dayOfWeek: DayOfWeek,
    ) {
        updateSchedule {
            it.toggleWeekday(
                dayOfWeek,
            )
        }
    }

    fun onInputModeSelected(
        mode: ScheduleInputMode,
    ) {
        updateSchedule {
            it.withInputMode(
                mode,
            )
        }
    }

    fun onTimeDraftChanged(
        value: String,
    ) {
        updateSchedule {
            it.withTimeDraft(
                value,
            )
        }
    }

    fun addTime() {
        updateSchedule {
            it.addDraftTime()
        }
    }

    fun removeTime(
        minuteOfDay: Int,
    ) {
        updateSchedule {
            it.removeTime(
                minuteOfDay,
            )
        }
    }

    fun onIntervalHoursSelected(
        hours: Int,
    ) {
        updateSchedule {
            it.withIntervalHours(
                hours,
            )
        }
    }

    fun onIntervalAnchorChanged(
        value: String,
    ) {
        updateSchedule {
            it.withIntervalAnchorDraft(
                value,
            )
        }
    }

    fun onStartDateChanged(
        value: String,
    ) {
        updateSchedule {
            it.withStartDate(
                value,
            )
        }
    }

    fun onEndDateChanged(
        value: String,
    ) {
        updateSchedule {
            it.withEndDate(
                value,
            )
        }
    }

    fun dismissInitialReminderGuidance() {
        mutableState.update {
                state ->
            state.copy(
                showInitialReminderGuidance =
                    false,
            )
        }
    }

    fun enableSimpleModeAfterFirstSetup() {
        completeAfterPostSetupSimpleModeSuggestion(
            seniorMode = SeniorMode.SIMPLE,
        )
    }

    fun deferSimpleModeAfterFirstSetup() {
        completeAfterPostSetupSimpleModeSuggestion(
            seniorMode = SeniorMode.STANDARD,
        )
    }

    fun onCompletionHandled() {
        mutableState.update { state ->
            if (state.completionRequested) {
                state.copy(
                    completionRequested = false,
                    completionSeniorMode = null,
                )
            } else {
                state
            }
        }
    }
    fun save() {
        val effectiveFrom =
            currentEffectiveFrom()

        mutableState.update { current ->
            current.copy(
                schedule =
                    current.schedule
                        .withPreviewEffectiveFrom(
                            effectiveFrom,
                        ),
                previewAnchorDate =
                    effectiveFrom
                        .atZone(
                            currentZone,
                        )
                        .toLocalDate(),
            )
        }
        val current =
            mutableState.value

        val pendingTimeError =
            current.schedule.errors[
                CarePlanField.TIMES
            ]

        if (pendingTimeError != null) {
            mutableState.update { state ->
                state.copy(
                    generalError =
                        pendingTimeError,
                )
            }

            return
        }

        if (current.isSaving) {
            return
        }

        val parsedDates =
            current
                .schedule
                .parseDates()

        if (
            parsedDates
                .errors
                .isNotEmpty()
        ) {
            mutableState.update {
                    state ->
                state.copy(
                    schedule =
                        state
                            .schedule
                            .withDateErrors(
                                parsedDates
                                    .errors,
                            ),
                )
            }

            return
        }

        viewModelScope.launch {
            mutableState.update {
                    state ->
                state.copy(
                    isSaving = true,
                    generalError = null,
                    schedule =
                        state
                            .schedule
                            .clearErrors()
                            .withPreviewEffectiveFrom(
                                currentEffectiveFrom(),
                            ),
                    previewAnchorDate =
                        currentPreviewDate(),
                )
            }

            try {
                val latest =
                    mutableState.value

                when (
                    val currentMode =
                        mode
                ) {
                    is MedicationScheduleMode
                    .CreateMedication -> {
                        saveMedicationAndSchedule(
                            mode =
                                currentMode,
                            latest =
                                latest,
                            startDate =
                                parsedDates.startDate,
                            endDate =
                                parsedDates.endDate,
                        )
                    }

                    is MedicationScheduleMode
                    .AddSchedule -> {
                        saveAdditionalSchedule(
                            mode =
                                currentMode,
                            latest =
                                latest,
                            startDate =
                                parsedDates.startDate,
                            endDate =
                                parsedDates.endDate,
                        )
                    }
                }
            } catch (
                cancellationException: CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                showGeneralError(
                    "ذخیره‌سازی انجام نشد. دوباره تلاش کنید.",
                )
            } finally {
                mutableState.update {
                        state ->
                    state.copy(
                        isSaving = false,
                    )
                }
            }
        }
    }

    private suspend fun saveMedicationAndSchedule(
        mode: MedicationScheduleMode.CreateMedication,
        latest: MedicationScheduleUiState,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ) {
        val outcome =
            carePlanService
                .createMedicationAndSchedule(
                    CreateMedicationScheduleCommand(
                        recipientId =
                            mode.recipientId,
                        medicationName =
                            latest.medicationName,
                        instruction =
                            latest.instruction,
                        weekdays =
                            latest
                                .schedule
                                .weekdays,
                        minutesOfDay =
                            latest
                                .schedule
                                .effectiveMinutesOfDay(),
                        schedulePattern =
                            latest
                                .schedule
                                .toSchedulePattern(),
                        startDate =
                            startDate,
                        endDate =
                            endDate,
                        zoneId =
                            latest
                                .schedule
                                .zoneId,
                        medicationType =
                            latest.medicationType,
                        dosageText =
                            latest.dosageText,
                        doseUnit =
                            latest.doseUnit,
                    ),
                )

        when (outcome) {
            is CreateMedicationScheduleOutcome.Created -> {
                if (mode.completeInitialSetup) {
                    setupPreferenceStore
                        .markSetupComplete()

                    val seniorMode =
                        userExperiencePreferenceStore
                            .state
                            .first()
                            .seniorMode

                    if (
                        seniorMode ==
                        SeniorMode.STANDARD
                    ) {
                        mutableState.update {
                                state ->
                            state.copy(
                                seniorMode =
                                    seniorMode,
                                showPostSetupSimpleModeSuggestion =
                                    true,
                            )
                        }

                        return
                    }
                }

                requestCompletion()
            }

            CreateMedicationScheduleOutcome
                .RecipientNotFound -> {
                showGeneralError(
                    "فرد تحت مراقبت پیدا نشد.",
                )
            }

            is CreateMedicationScheduleOutcome
            .Invalid -> {
                applyValidationErrors(
                    outcome
                        .errors
                        .toFieldErrors(),
                )
            }
        }
    }

    private suspend fun saveAdditionalSchedule(
        mode: MedicationScheduleMode.AddSchedule,
        latest: MedicationScheduleUiState,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ) {
        val outcome =
            carePlanService.addSchedule(
                AddScheduleCommand(
                    medicationId =
                        mode.medicationId,
                    weekdays =
                        latest
                            .schedule
                            .weekdays,
                    minutesOfDay =
                        latest
                            .schedule
                            .effectiveMinutesOfDay(),
                    schedulePattern =
                        latest
                            .schedule
                            .toSchedulePattern(),
                    startDate =
                        startDate,
                    endDate =
                        endDate,
                    zoneId =
                        latest
                            .schedule
                            .zoneId,
                ),
            )

        when (outcome) {
            is AddScheduleOutcome.Created -> {
                requestCompletion()
            }

            AddScheduleOutcome.NotFound -> {
                showGeneralError(
                    "دارو پیدا نشد.",
                )
            }

            AddScheduleOutcome.NotEditable -> {
                showGeneralError(
                    "برای این دارو نمی‌توان برنامه تازه اضافه کرد.",
                )
            }

            is AddScheduleOutcome.Invalid -> {
                applyValidationErrors(
                    outcome
                        .errors
                        .toFieldErrors(),
                )
            }
        }
    }

    private fun completeAfterPostSetupSimpleModeSuggestion(
        seniorMode: SeniorMode,
    ) {
        if (mutableState.value.isSaving) {
            return
        }

        viewModelScope.launch {
            mutableState.update { state ->
                state.copy(
                    isSaving = true,
                    generalError = null,
                )
            }

            try {
                userExperiencePreferenceStore
                    .setSeniorMode(seniorMode)

                mutableState.update { state ->
                    state.copy(
                        seniorMode = seniorMode,
                        showPostSetupSimpleModeSuggestion = false,
                        completionRequested = true,
                        completionSeniorMode = seniorMode,
                    )
                }
            } catch (
                cancellationException: CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.update { state ->
                    state.copy(
                        generalError =
                            "ذخیره حالت نمایش انجام نشد. دوباره تلاش کنید.",
                    )
                }
            } finally {
                mutableState.update { state ->
                    if (state.completionRequested) {
                        state
                    } else {
                        state.copy(isSaving = false)
                    }
                }
            }
        }
    }
    private fun requestCompletion() {
        mutableState.update {
                state ->
            state.copy(
                completionRequested = true,
            )
        }
    }
    private fun applyValidationErrors(
        errors: Map<CarePlanField, String>,
    ) {
        mutableState.update {
                state ->
            state.copy(
                generalError =
                    errors.values.firstOrNull(),
                medicationErrors =
                    if (state.isAddScheduleOnly) {
                        emptyMap()
                    } else {
                        errors.filterKeys {
                                field ->
                            field ==
                                    CarePlanField
                                        .MEDICATION_NAME ||
                                    field ==
                                    CarePlanField
                                        .INSTRUCTION
                        }
                    },
                schedule =
                    state
                        .schedule
                        .withValidationErrors(
                            errors,
                        )
                        .withPreviewEffectiveFrom(
                            currentEffectiveFrom(),
                        ),
                previewAnchorDate =
                    currentPreviewDate(),
            )
        }
    }

    private fun updateSchedule(
        transform:
            (
            ScheduleFormUiState,
        ) -> ScheduleFormUiState,
    ) {
        mutableState.update {
                state ->
            state.copy(
                schedule =
                    transform(
                        state.schedule,
                    ).withPreviewEffectiveFrom(
                        currentEffectiveFrom(),
                    ),
                previewAnchorDate =
                    currentPreviewDate(),
                generalError = null,
            )
        }
    }

    private fun showGeneralError(
        message: String,
    ) {
        mutableState.update {
                state ->
            state.copy(
                generalError = message,
            )
        }
    }

    private fun currentEffectiveFrom():
            Instant =
        clock.instant()

    private fun currentPreviewDate():
            LocalDate =
        currentEffectiveFrom()
            .atZone(
                currentZone,
            )
            .toLocalDate()

    companion object {

        fun factory(
            recipientId: String,
            carePlanService:
            CarePlanService,
            setupPreferenceStore:
            SetupPreferenceStore,
            userExperiencePreferenceStore:
            UserExperiencePreferenceStore,
            completeInitialSetup: Boolean,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MedicationScheduleViewModel(
                        mode =
                            MedicationScheduleMode
                                .CreateMedication(
                                    recipientId =
                                        recipientId,
                                    completeInitialSetup =
                                        completeInitialSetup,
                                ),
                        carePlanService =
                            carePlanService,
                        setupPreferenceStore =
                            setupPreferenceStore,
                        userExperiencePreferenceStore =
                            userExperiencePreferenceStore,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }

        fun addScheduleFactory(
            medicationId: String,
            carePlanService:
            CarePlanService,
            setupPreferenceStore:
            SetupPreferenceStore,
            userExperiencePreferenceStore:
            UserExperiencePreferenceStore,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MedicationScheduleViewModel(
                        mode =
                            MedicationScheduleMode
                                .AddSchedule(
                                    medicationId =
                                        medicationId,
                                ),
                        carePlanService =
                            carePlanService,
                        setupPreferenceStore =
                            setupPreferenceStore,
                        userExperiencePreferenceStore =
                            userExperiencePreferenceStore,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }
    }
}
