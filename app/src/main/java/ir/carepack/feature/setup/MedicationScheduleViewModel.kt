package ir.carepack.feature.setup

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.feature.careplan.MedicationTextDraft
import ir.carepack.feature.careplan.ScheduleFormUiState
import ir.carepack.feature.careplan.ScheduleFormEditor
import ir.carepack.feature.careplan.ScheduleFormUpdate
import ir.carepack.feature.careplan.SchedulePreviewTimestampPolicy
import ir.carepack.feature.careplan.ScheduleInputMode
import ir.carepack.feature.careplan.clearErrors
import ir.carepack.feature.careplan.effectiveMinutesOfDay
import ir.carepack.feature.careplan.parseDates
import ir.carepack.feature.careplan.removeTime
import ir.carepack.feature.careplan.toFieldErrors
import ir.carepack.feature.careplan.toSchedulePattern
import ir.carepack.feature.careplan.toggleWeekday
import ir.carepack.feature.careplan.withDateErrors
import ir.carepack.feature.careplan.withIntervalHoursDefault
import ir.carepack.feature.careplan.withPreviewEffectiveFrom
import ir.carepack.feature.careplan.withValidationErrors
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
    val medication: MedicationTextDraft = MedicationTextDraft(),
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
    val batteryOptimizationState: BatteryOptimizationState =
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
    private val setupPreferenceStore: SetupPreferenceStore,
    private val userExperiencePreferenceStore: UserExperiencePreferenceStore,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : ViewModel() {

    private val scheduleEditor = ScheduleFormEditor(
            clock = clock,
            zoneProvider = zoneProvider,
            previewTimestampPolicy = SchedulePreviewTimestampPolicy.FRESH_CLOCK_READ,
        )

    private val currentZone = scheduleEditor.currentZone

    private val mutableState = MutableStateFlow(
            MedicationScheduleUiState(
                schedule = ScheduleFormUiState(
                        weekdays = DayOfWeek.entries.toSet(),
                        minutesOfDay = emptyList(),
                        timeDraft = "",
                        startDateText = "",
                        endDateText = "",
                        zoneId = currentZone.id,
                        previewEffectiveFrom = currentEffectiveFrom(),
                    ).withIntervalHoursDefault(),
                previewAnchorDate = currentPreviewDate(),
                isAddScheduleOnly = mode is MedicationScheduleMode
                    .AddSchedule,
                showInitialReminderGuidance = mode is MedicationScheduleMode
                    .CreateMedication,
            ),
        )

    val state = mutableState.asStateFlow()


    init {
        viewModelScope.launch {
            userExperiencePreferenceStore.state
                .collect { preferenceState ->
                    mutableState.update {
                            currentState ->
                        currentState.copy(
                            firstDayOfWeek = FirstDayOfWeekPolicy
                                    .resolve(
                                        preference = preferenceState
                                                .firstDayOfWeekPreference,
                                        zoneId = currentZone,
                                        locale = Locale.getDefault(),
                                    ),
                            seniorMode = preferenceState
                                    .seniorMode,
                        )
                    }
                }
        }
    }

    fun onMedicationNameChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.MEDICATION_NAME, value)
    }

    fun onInstructionChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.INSTRUCTION, value)
    }

    fun onMedicationTypeChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.MEDICATION_TYPE, value)
    }

    fun onDosageTextChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.DOSAGE_TEXT, value)
    }

    fun onDoseUnitChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.DOSE_UNIT, value)
    }

    fun onWeekdayToggled(
        dayOfWeek: DayOfWeek,
    ) {
        updateSchedule {
            scheduleEditor.toggleWeekday(it, dayOfWeek)
        }
    }

    fun onInputModeSelected(
        mode: ScheduleInputMode,
    ) {
        updateSchedule {
            scheduleEditor.selectInputMode(it, mode)
        }
    }

    fun onTimeDraftChanged(
        value: String,
    ) {
        updateSchedule {
            scheduleEditor.changeTimeDraft(it, value)
        }
    }

    fun addTime() {
        updateSchedule {
            scheduleEditor.addTime(it)
        }
    }

    fun removeTime(
        minuteOfDay: Int,
    ) {
        updateSchedule {
            scheduleEditor.removeTime(it, minuteOfDay)
        }
    }

    fun onIntervalHoursSelected(
        hours: Int,
    ) {
        updateSchedule {
            scheduleEditor.selectIntervalHours(it, hours)
        }
    }

    fun onIntervalAnchorChanged(
        value: String,
    ) {
        updateSchedule {
            scheduleEditor.changeIntervalAnchor(it, value)
        }
    }

    fun onStartDateChanged(
        value: String,
    ) {
        updateSchedule {
            scheduleEditor.changeStartDate(it, value)
        }
    }

    fun onEndDateChanged(
        value: String,
    ) {
        updateSchedule {
            scheduleEditor.changeEndDate(it, value)
        }
    }

    fun dismissInitialReminderGuidance() {
        mutableState.update {
                state ->
            state.copy(
                showInitialReminderGuidance = false,
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
        val effectiveFrom = currentEffectiveFrom()

        mutableState.update { current ->
            current.copy(
                schedule = current.schedule
                        .withPreviewEffectiveFrom(
                            effectiveFrom,
                        ),
                previewAnchorDate = effectiveFrom
                        .atZone(
                            currentZone,
                        ).toLocalDate(),
            )
        }
        val current = mutableState.value

        val pendingTimeError = current.schedule.errors[
                CarePlanField.TIMES]

        if (pendingTimeError != null) {
            mutableState.update { state ->
                state.copy(
                    generalError = pendingTimeError,
                )
            }

            return
        }

        if (current.isSaving) {
            return
        }

        val parsedDates = current
                .schedule.parseDates()

        if (
            parsedDates.errors
                .isNotEmpty()) {
            mutableState.update {
                    state ->
                state.copy(
                    schedule = state
                            .schedule.withDateErrors(
                                parsedDates.errors,
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
                    schedule = state
                            .schedule.clearErrors()
                            .withPreviewEffectiveFrom(
                                currentEffectiveFrom(),
                            ),
                    previewAnchorDate = currentPreviewDate(),
                )
            }

            try {
                val latest = mutableState.value

                when (
                    val currentMode = mode
                ) {
                    is MedicationScheduleMode.CreateMedication -> {
                        saveMedicationAndSchedule(
                            mode = currentMode,
                            latest = latest,
                            startDate = parsedDates.startDate,
                            endDate = parsedDates.endDate,
                        )
                    }

                    is MedicationScheduleMode.AddSchedule -> {
                        saveAdditionalSchedule(
                            mode = currentMode,
                            latest = latest,
                            startDate = parsedDates.startDate,
                            endDate = parsedDates.endDate,
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
        val outcome = carePlanService
                .createMedicationAndSchedule(
                    CreateMedicationScheduleCommand(
                        recipientId = mode.recipientId,
                        medicationName = latest.medication.medicationName,
                        instruction = latest.medication.instruction,
                        weekdays = latest
                                .schedule.weekdays,
                        minutesOfDay = latest
                                .schedule.effectiveMinutesOfDay(),
                        schedulePattern = latest
                                .schedule.toSchedulePattern(),
                        startDate = startDate,
                        endDate = endDate,
                        zoneId = latest
                                .schedule.zoneId,
                        medicationType = latest.medication.medicationType,
                        dosageText = latest.medication.dosageText,
                        doseUnit = latest.medication.doseUnit,
                    ),
                )

        when (outcome) {
            is CreateMedicationScheduleOutcome.Created -> {
                if (mode.completeInitialSetup) {
                    setupPreferenceStore.markSetupComplete()

                    val seniorMode = userExperiencePreferenceStore
                            .state.first()
                            .seniorMode

                    if (
                        seniorMode == SeniorMode.STANDARD
                    ) {
                        mutableState.update {
                                state ->
                            state.copy(
                                seniorMode = seniorMode,
                                showPostSetupSimpleModeSuggestion = true,
                            )
                        }

                        return
                    }
                }

                requestCompletion()
            }

            CreateMedicationScheduleOutcome.RecipientNotFound -> {
                showGeneralError(
                    "فرد تحت مراقبت پیدا نشد.",
                )
            }

            is CreateMedicationScheduleOutcome.Invalid -> {
                applyValidationErrors(
                    outcome.errors
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
        val outcome = carePlanService.addSchedule(
                AddScheduleCommand(
                    medicationId = mode.medicationId,
                    weekdays = latest
                            .schedule.weekdays,
                    minutesOfDay = latest
                            .schedule.effectiveMinutesOfDay(),
                    schedulePattern = latest
                            .schedule.toSchedulePattern(),
                    startDate = startDate,
                    endDate = endDate,
                    zoneId = latest
                            .schedule.zoneId,
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
                    outcome.errors
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
                userExperiencePreferenceStore.setSeniorMode(seniorMode)

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
                        generalError = "ذخیره حالت نمایش انجام نشد. دوباره تلاش کنید.",
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
                generalError = errors.values.firstOrNull(),
                medicationErrors = if (state.isAddScheduleOnly) {
                        emptyMap()
                    } else {
                        errors.filterKeys {
                                field ->
                            field == CarePlanField
                                        .MEDICATION_NAME || field ==
                                    CarePlanField.INSTRUCTION
                        }
                    },
                schedule = state
                        .schedule.withValidationErrors(
                            errors,
                        ).withPreviewEffectiveFrom(
                            currentEffectiveFrom(),
                        ),
                previewAnchorDate = currentPreviewDate(),
            )
        }
    }

    private fun updateSchedule(
        transform: (
            ScheduleFormUiState,
        ) -> ScheduleFormUpdate,
    ) {
        mutableState.update {
                state ->
            val update = transform(state.schedule)
            state.copy(
                schedule = update.schedule,
                previewAnchorDate = update.previewAnchorDate,
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

    private fun updateMedicationField(
        field: CarePlanField,
        value: String,
    ) {
        mutableState.update { current ->
            current.copy(
                medication = current.medication.withField(field, value),
                medicationErrors = current.medicationErrors - field,
                generalError = null,
            )
        }
    }

    private fun currentEffectiveFrom(): Instant =
        scheduleEditor.currentEffectiveFrom()

    private fun currentPreviewDate(): LocalDate =
        scheduleEditor.currentPreviewDate()

    companion object {

        fun factory(
            recipientId: String,
            carePlanService: CarePlanService,
            setupPreferenceStore: SetupPreferenceStore,
            userExperiencePreferenceStore: UserExperiencePreferenceStore,
            completeInitialSetup: Boolean,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    MedicationScheduleViewModel(
                        mode = MedicationScheduleMode
                                .CreateMedication(
                                    recipientId = recipientId,
                                    completeInitialSetup = completeInitialSetup,
                                ),
                        carePlanService = carePlanService,
                        setupPreferenceStore = setupPreferenceStore,
                        userExperiencePreferenceStore = userExperiencePreferenceStore,
                        clock = clock,
                        zoneProvider = zoneProvider,
                    )
            }

        fun addScheduleFactory(
            medicationId: String,
            carePlanService: CarePlanService,
            setupPreferenceStore: SetupPreferenceStore,
            userExperiencePreferenceStore: UserExperiencePreferenceStore,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    MedicationScheduleViewModel(
                        mode = MedicationScheduleMode
                                .AddSchedule(
                                    medicationId = medicationId,
                                ),
                        carePlanService = carePlanService,
                        setupPreferenceStore = setupPreferenceStore,
                        userExperiencePreferenceStore = userExperiencePreferenceStore,
                        clock = clock,
                        zoneProvider = zoneProvider,
                    )
            }
    }
}
