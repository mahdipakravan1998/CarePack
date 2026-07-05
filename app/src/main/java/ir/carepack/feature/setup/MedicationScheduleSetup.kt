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
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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

sealed interface MedicationScheduleEvent {

    data object Completed :
        MedicationScheduleEvent
}

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

    private val eventChannel =
        Channel<MedicationScheduleEvent>(
            capacity = Channel.BUFFERED,
        )

    val events =
        eventChannel.receiveAsFlow()

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


    fun save() {
        val effectiveFrom =
            currentEffectiveFrom()

        mutableState.update {
                current ->
            current.copy(
                schedule =
                    current
                        .schedule
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

                eventChannel.send(
                    MedicationScheduleEvent.Completed,
                )
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
                eventChannel.send(
                    MedicationScheduleEvent.Completed,
                )
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
        viewModelScope.launch {
            userExperiencePreferenceStore
                .setSeniorMode(
                    seniorMode,
                )

            mutableState.update {
                    state ->
                state.copy(
                    seniorMode = seniorMode,
                    showPostSetupSimpleModeSuggestion =
                        false,
                )
            }

            eventChannel.send(
                MedicationScheduleEvent.Completed,
            )
        }
    }

    private fun applyValidationErrors(
        errors: Map<CarePlanField, String>,
    ) {
        mutableState.update {
                state ->
            state.copy(
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

@Composable
fun MedicationScheduleRoute(
    viewModel:
    MedicationScheduleViewModel,
    onCompleted: () -> Unit,
    onOpenReminderSettings: () -> Unit = {},
    firstSetupReminderReadiness:
    FirstSetupReminderReadinessUiState? = null,
    onFirstSetupRequestNotificationPermission:
    (() -> Unit)? = null,
    onFirstSetupOpenNotificationSettings:
    (() -> Unit)? = null,
    onFirstSetupRequestExactAlarmAccess:
    (() -> Unit)? = null,
    onFirstSetupOpenBatterySettings:
    (() -> Unit)? = null,
) {
    val context =
        LocalContext.current

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission(),
        ) {
            Unit
        }

    val readiness =
        firstSetupReminderReadiness
            ?: platformFirstSetupReminderReadiness(
                context = context,
            )

    val requestNotificationPermission =
        onFirstSetupRequestNotificationPermission
            ?: {
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                }
            }

    val openNotificationSettings =
        onFirstSetupOpenNotificationSettings
            ?: {
                context.startFirstSetupSettingsActivity(
                    Intent(
                        Settings
                            .ACTION_APP_NOTIFICATION_SETTINGS,
                    ).putExtra(
                        Settings
                            .EXTRA_APP_PACKAGE,
                        context.packageName,
                    ),
                )
            }

    val requestExactAlarmAccess =
        onFirstSetupRequestExactAlarmAccess
            ?: {
                context.startFirstSetupSettingsActivity(
                    Intent(
                        Settings
                            .ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse(
                            "package:${context.packageName}",
                        ),
                    ),
                    fallback =
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse(
                                "package:${context.packageName}",
                            ),
                        ),
                )
            }

    val openBatterySettings =
        onFirstSetupOpenBatterySettings
            ?: {
                context.startFirstSetupSettingsActivity(
                    Intent(
                        Settings
                            .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                    ),
                    fallback =
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse(
                                "package:${context.packageName}",
                            ),
                        ),
                )
            }

    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        viewModel,
    ) {
        viewModel
            .events
            .collect { event ->
                when (event) {
                    MedicationScheduleEvent
                        .Completed -> {
                        onCompleted()
                    }
                }
            }
    }

    MedicationScheduleScreen(
        state = state,
        firstSetupReminderReadiness =
            readiness,
        onMedicationNameChanged =
            viewModel::onMedicationNameChanged,
        onInstructionChanged =
            viewModel::onInstructionChanged,
        onMedicationTypeChanged =
            viewModel::onMedicationTypeChanged,
        onDosageTextChanged =
            viewModel::onDosageTextChanged,
        onDoseUnitChanged =
            viewModel::onDoseUnitChanged,
        onWeekdayToggled =
            viewModel::onWeekdayToggled,
        onInputModeSelected =
            viewModel::onInputModeSelected,
        onTimeDraftChanged =
            viewModel::onTimeDraftChanged,
        onAddTime =
            viewModel::addTime,
        onRemoveTime =
            viewModel::removeTime,
        onIntervalHoursSelected =
            viewModel::onIntervalHoursSelected,
        onIntervalAnchorChanged =
            viewModel::onIntervalAnchorChanged,
        onStartDateChanged =
            viewModel::onStartDateChanged,
        onEndDateChanged =
            viewModel::onEndDateChanged,
        onInitialReminderGuidanceContinue =
            viewModel::dismissInitialReminderGuidance,
        onEnableSimpleModeAfterFirstSetup =
            viewModel::enableSimpleModeAfterFirstSetup,
        onDeferSimpleModeAfterFirstSetup =
            viewModel::deferSimpleModeAfterFirstSetup,
        onOpenReminderSettings =
            onOpenReminderSettings,
        onRequestNotificationPermission =
            requestNotificationPermission,
        onOpenNotificationSettings =
            openNotificationSettings,
        onRequestExactAlarmAccess =
            requestExactAlarmAccess,
        onOpenBatterySettings =
            openBatterySettings,
        onSave =
            viewModel::save,
    )
}

private fun platformFirstSetupReminderReadiness(
    context: Context,
): FirstSetupReminderReadinessUiState {
    val notificationGateway =
        AndroidNotificationPermissionGateway(
            context = context,
        )

    val exactAlarmGateway =
        AndroidExactAlarmCapabilityGateway(
            context = context,
        )

    val batteryGateway =
        AndroidBatteryOptimizationGateway(
            context = context,
        )

    return FirstSetupReminderReadinessUiState(
        notificationRuntimePermissionRequired =
            notificationGateway
                .requiresRuntimePermission(),
        notificationPermissionGranted =
            notificationGateway
                .isPermissionGranted(),
        notificationPermissionCanBeRequested =
            notificationGateway
                .requiresRuntimePermission() &&
                    !notificationGateway
                        .isPermissionGranted(),
        exactAlarmRelevant = true,
        exactAlarmAvailable =
            exactAlarmGateway
                .canScheduleExactAlarms(),
        batteryOptimizationState =
            batteryGateway
                .currentState(),
        manufacturer =
            Build.MANUFACTURER,
    )
}

private fun Context.startFirstSetupSettingsActivity(
    intent: Intent,
    fallback: Intent? = null,
) {
    val launchIntent =
        Intent(
            intent,
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK,
        )

    try {
        startActivity(
            launchIntent,
        )
    } catch (_: ActivityNotFoundException) {
        fallback
            ?.let { fallbackIntent ->
                startActivity(
                    Intent(
                        fallbackIntent,
                    ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                    ),
                )
            }
    }
}

@Composable
private fun InitialReminderGuidanceCard(
    readiness:
    FirstSetupReminderReadinessUiState,
    onContinue: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val manufacturerGuidance =
        firstSetupManufacturerGuidance(
            manufacturer =
                readiness.manufacturer,
        )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "first_setup_reminder_guidance",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .first_setup_reminder_guidance_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "first_setup_reminder_guidance_title",
                        ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .first_setup_reminder_guidance_body,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                modifier =
                    Modifier.testTag(
                        "first_setup_reminder_guidance_body",
                    ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .first_setup_reminder_guidance_settings_path,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                modifier =
                    Modifier.testTag(
                        "first_setup_reminder_guidance_settings_path",
                    ),
            )

            FirstSetupReadinessActions(
                readiness =
                    readiness,
                manufacturerGuidance =
                    manufacturerGuidance,
                onRequestNotificationPermission =
                    onRequestNotificationPermission,
                onOpenNotificationSettings =
                    onOpenNotificationSettings,
                onRequestExactAlarmAccess =
                    onRequestExactAlarmAccess,
                onOpenBatterySettings =
                    onOpenBatterySettings,
            )

            Button(
                onClick =
                    onOpenReminderSettings,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "first_setup_reminder_guidance_open_reminder_settings",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .open_reminder_settings,
                        ),
                )
            }

            TextButton(
                onClick =
                    onContinue,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "first_setup_reminder_guidance_continue",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .first_setup_reminder_guidance_continue,
                        ),
                )
            }
        }
    }
}

private fun firstSetupManufacturerGuidance(
    manufacturer: String?,
): ManufacturerGuidance {
    val normalized =
        manufacturer
            ?.trim()
            ?.lowercase(
                Locale.ROOT,
            )
            .orEmpty()

    val classifierInput =
        if (
            normalized.contains(
                other = "miui",
            ) ||
            normalized.contains(
                other = "hyperos",
            )
        ) {
            "Xiaomi"
        } else {
            manufacturer
        }

    return ManufacturerGuidanceClassifier
        .classify(
            manufacturer =
                classifierInput,
        )
}

@Composable
private fun FirstSetupReadinessActions(
    readiness:
    FirstSetupReminderReadinessUiState,
    manufacturerGuidance: ManufacturerGuidance,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "first_setup_readiness_actions",
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text =
                stringResource(
                    R.string
                        .first_setup_readiness_title,
                ),
            style =
                MaterialTheme
                    .typography
                    .titleSmall,
            modifier =
                Modifier
                    .carePackHeading()
                    .testTag(
                        "first_setup_readiness_title",
                    ),
        )

        FirstSetupNotificationPermissionSection(
            readiness =
                readiness,
            onRequestNotificationPermission =
                onRequestNotificationPermission,
            onOpenNotificationSettings =
                onOpenNotificationSettings,
        )

        FirstSetupExactAlarmSection(
            readiness =
                readiness,
            onRequestExactAlarmAccess =
                onRequestExactAlarmAccess,
        )

        FirstSetupBatterySection(
            readiness =
                readiness,
            onOpenBatterySettings =
                onOpenBatterySettings,
        )

        FirstSetupOemGuidanceSection(
            guidance =
                manufacturerGuidance,
        )
    }
}

@Composable
private fun FirstSetupNotificationPermissionSection(
    readiness:
    FirstSetupReminderReadinessUiState,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "first_setup_notification_permission_card",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    12.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .notification_permission_rationale_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleSmall,
            )

            Text(
                text =
                    stringResource(
                        when {
                            !readiness
                                .notificationRuntimePermissionRequired -> {
                                R.string
                                    .notification_permission_not_required
                            }

                            readiness
                                .notificationPermissionGranted -> {
                                R.string
                                    .notification_permission_granted
                            }

                            else -> {
                                R.string
                                    .notification_permission_denied
                            }
                        },
                    ),
                modifier =
                    Modifier.testTag(
                        "first_setup_notification_permission_status",
                    ),
            )

            if (
                readiness
                    .notificationRuntimePermissionRequired &&
                !readiness
                    .notificationPermissionGranted
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .notification_permission_rationale_body,
                        ),
                    modifier =
                        Modifier.testTag(
                            "first_setup_notification_permission_rationale",
                        ),
                )

                if (
                    readiness
                        .notificationPermissionCanBeRequested
                ) {
                    Button(
                        onClick =
                            onRequestNotificationPermission,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "first_setup_request_notification_permission",
                                ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .request_notification_permission,
                                ),
                        )
                    }
                }

                OutlinedButton(
                    onClick =
                        onOpenNotificationSettings,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                "first_setup_open_notification_settings",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .open_notification_settings,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstSetupExactAlarmSection(
    readiness:
    FirstSetupReminderReadinessUiState,
    onRequestExactAlarmAccess: () -> Unit,
) {
    if (
        !readiness.exactAlarmRelevant ||
        readiness.exactAlarmAvailable
    ) {
        return
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "first_setup_exact_alarm_card",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    12.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .exact_alarm_rationale_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleSmall,
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .exact_alarm_rationale_body,
                    ),
                modifier =
                    Modifier.testTag(
                        "first_setup_approximate_fallback",
                    ),
            )

            OutlinedButton(
                onClick =
                    onRequestExactAlarmAccess,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "first_setup_request_exact_alarm_access",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .request_exact_alarm_access,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FirstSetupBatterySection(
    readiness:
    FirstSetupReminderReadinessUiState,
    onOpenBatterySettings: () -> Unit,
) {
    if (
        readiness.batteryOptimizationState !=
        BatteryOptimizationState.NOT_IGNORED
    ) {
        return
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "first_setup_battery_guidance_card",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    12.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .battery_guidance_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleSmall,
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .battery_guidance_body,
                    ),
                modifier =
                    Modifier.testTag(
                        "first_setup_battery_guidance_body",
                    ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .battery_optimization_not_ignored,
                    ),
                modifier =
                    Modifier.testTag(
                        "first_setup_battery_optimization_status",
                    ),
            )

            OutlinedButton(
                onClick =
                    onOpenBatterySettings,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "first_setup_open_battery_settings",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .open_battery_settings,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FirstSetupOemGuidanceSection(
    guidance: ManufacturerGuidance,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "first_setup_oem_guidance_card",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    12.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    guidance.title,
                style =
                    MaterialTheme
                        .typography
                        .titleSmall,
                modifier =
                    Modifier.testTag(
                        "first_setup_oem_guidance_title",
                    ),
            )

            Text(
                text =
                    guidance.body,
                modifier =
                    Modifier.testTag(
                        "first_setup_oem_guidance_body",
                    ),
            )

            guidance
                .actionItems
                .forEachIndexed {
                        index,
                        actionItem ->
                    Text(
                        text =
                            "• $actionItem",
                        modifier =
                            Modifier.testTag(
                                "first_setup_oem_guidance_action_$index",
                            ),
                    )
                }
        }
    }
}

@Composable
private fun PostSetupSimpleModeSuggestionCard(
    onEnableSimpleMode: () -> Unit,
    onDeferSimpleMode: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "post_setup_simple_mode_suggestion",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .post_setup_simple_mode_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "post_setup_simple_mode_title",
                        ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .post_setup_simple_mode_summary,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                modifier =
                    Modifier.testTag(
                        "post_setup_simple_mode_summary",
                    ),
            )

            Button(
                onClick =
                    onEnableSimpleMode,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "post_setup_enable_simple_mode",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .post_setup_simple_mode_enable,
                        ),
                )
            }

            TextButton(
                onClick =
                    onDeferSimpleMode,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "post_setup_defer_simple_mode",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .post_setup_simple_mode_defer,
                        ),
                )
            }
        }
    }
}

@Composable
private fun MedicationScheduleScreen(
    state: MedicationScheduleUiState,
    firstSetupReminderReadiness:
    FirstSetupReminderReadinessUiState,
    onMedicationNameChanged:
        (String) -> Unit,
    onInstructionChanged:
        (String) -> Unit,
    onMedicationTypeChanged:
        (String) -> Unit,
    onDosageTextChanged:
        (String) -> Unit,
    onDoseUnitChanged:
        (String) -> Unit,
    onWeekdayToggled:
        (DayOfWeek) -> Unit,
    onInputModeSelected:
        (ScheduleInputMode) -> Unit,
    onTimeDraftChanged:
        (String) -> Unit,
    onAddTime: () -> Unit,
    onRemoveTime:
        (Int) -> Unit,
    onIntervalHoursSelected:
        (Int) -> Unit,
    onIntervalAnchorChanged:
        (String) -> Unit,
    onStartDateChanged:
        (String) -> Unit,
    onEndDateChanged:
        (String) -> Unit,
    onInitialReminderGuidanceContinue: () -> Unit,
    onEnableSimpleModeAfterFirstSetup: () -> Unit,
    onDeferSimpleModeAfterFirstSetup: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    if (state.isAddScheduleOnly) {
                        "add_schedule_screen"
                    } else {
                        "medication_schedule_screen"
                    },
                ),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        contentPadding,
                    )
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal = 24.dp,
                        vertical = 16.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            Text(
                text =
                    if (state.isAddScheduleOnly) {
                        stringResource(
                            R.string
                                .add_schedule_title,
                        )
                    } else {
                        stringResource(
                            R.string
                                .medication_schedule_title,
                        )
                    },
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "medication_schedule_title",
                        ),
            )

            if (
                !state.isAddScheduleOnly &&
                state.showInitialReminderGuidance
            ) {
                InitialReminderGuidanceCard(
                    readiness =
                        firstSetupReminderReadiness,
                    onContinue =
                        onInitialReminderGuidanceContinue,
                    onOpenReminderSettings =
                        onOpenReminderSettings,
                    onRequestNotificationPermission =
                        onRequestNotificationPermission,
                    onOpenNotificationSettings =
                        onOpenNotificationSettings,
                    onRequestExactAlarmAccess =
                        onRequestExactAlarmAccess,
                    onOpenBatterySettings =
                        onOpenBatterySettings,
                )
            }

            if (
                state.showPostSetupSimpleModeSuggestion
            ) {
                PostSetupSimpleModeSuggestionCard(
                    onEnableSimpleMode =
                        onEnableSimpleModeAfterFirstSetup,
                    onDeferSimpleMode =
                        onDeferSimpleModeAfterFirstSetup,
                )
            }

            if (!state.isAddScheduleOnly) {
                MedicationTextFields(
                    medicationName =
                        state.medicationName,
                    instruction =
                        state.instruction,
                    medicationType =
                        state.medicationType,
                    dosageText =
                        state.dosageText,
                    doseUnit =
                        state.doseUnit,
                    errors =
                        state.medicationErrors,
                    enabled =
                        !state.isSaving,
                    onMedicationNameChanged =
                        onMedicationNameChanged,
                    onInstructionChanged =
                        onInstructionChanged,
                    onMedicationTypeChanged =
                        onMedicationTypeChanged,
                    onDosageTextChanged =
                        onDosageTextChanged,
                    onDoseUnitChanged =
                        onDoseUnitChanged,
                    instructionMinLines = 3,
                    medicationNameTestTag =
                        "medication_name",
                    instructionTestTag =
                        "medication_instruction",
                    medicationTypeTestTag =
                        "medication_type",
                    dosageTextTestTag =
                        "dosage_text",
                    doseUnitTestTag =
                        "dose_unit",
                )
            }

            ScheduleFormFields(
                state =
                    state.schedule,
                callbacks =
                    ScheduleFormCallbacks(
                        onWeekdayToggled =
                            onWeekdayToggled,
                        onInputModeSelected =
                            onInputModeSelected,
                        onTimeDraftChanged =
                            onTimeDraftChanged,
                        onAddTime =
                            onAddTime,
                        onRemoveTime =
                            onRemoveTime,
                        onIntervalHoursSelected =
                            onIntervalHoursSelected,
                        onIntervalAnchorChanged =
                            onIntervalAnchorChanged,
                        onStartDateChanged =
                            onStartDateChanged,
                        onEndDateChanged =
                            onEndDateChanged,
                    ),
                enabled =
                    !state.isSaving,
                firstDayOfWeek =
                    state.firstDayOfWeek,
                previewAnchorDate =
                    state.previewAnchorDate,
                modifier =
                    Modifier.testTag(
                        "medication_schedule_form",
                    ),
            )

            state.generalError
                ?.let { message ->
                    Text(
                        text =
                            message,
                        color =
                            MaterialTheme
                                .colorScheme
                                .error,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .carePackPoliteLiveRegion()
                                .testTag(
                                    "medication_schedule_error",
                                ),
                    )
                }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            Button(
                onClick =
                    onSave,
                enabled =
                    !state.isSaving,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "save_medication_schedule",
                        ),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(
                                24.dp,
                            ),
                    )
                } else {
                    Text(
                        text =
                            if (state.isAddScheduleOnly) {
                                stringResource(
                                    R.string
                                        .add_schedule,
                                )
                            } else {
                                stringResource(
                                    R.string
                                        .create_care_plan,
                                )
                            },
                    )
                }
            }
        }
    }
}
