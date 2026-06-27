package ir.carepack.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.feature.careplan.MedicationTextFields
import ir.carepack.feature.careplan.ScheduleFormCallbacks
import ir.carepack.feature.careplan.ScheduleFormFields
import ir.carepack.feature.careplan.ScheduleFormUiState
import ir.carepack.feature.careplan.addDraftTime
import ir.carepack.feature.careplan.clearErrors
import ir.carepack.feature.careplan.parseDates
import ir.carepack.feature.careplan.removeTime
import ir.carepack.feature.careplan.toFieldErrors
import ir.carepack.feature.careplan.toMinuteOfDay
import ir.carepack.feature.careplan.toggleWeekday
import ir.carepack.feature.careplan.withDateErrors
import ir.carepack.feature.careplan.withEndDate
import ir.carepack.feature.careplan.withStartDate
import ir.carepack.feature.careplan.withTimeDraft
import ir.carepack.feature.careplan.withValidationErrors
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationScheduleUiState(
    val medicationName: String = "",
    val instruction: String = "",
    val schedule: ScheduleFormUiState,
    val errors: Map<CarePlanField, String> = emptyMap(),
    val isSaving: Boolean = false,
    val generalError: String? = null,
)

sealed interface MedicationScheduleEvent {
    data object Completed : MedicationScheduleEvent
}

class MedicationScheduleViewModel(
    private val recipientId: String,
    private val carePlanService: CarePlanService,
    private val setupPreferenceStore: SetupPreferenceStore,
    private val completeInitialSetup: Boolean,
    clock: Clock,
    zoneProvider: ZoneProvider,
) : ViewModel() {

    private val initialDateTime = LocalDateTime.ofInstant(
        clock.instant(),
        zoneProvider.currentZone(),
    )
        .plusMinutes(DEFAULT_FUTURE_MINUTES)
        .withSecond(0)
        .withNano(0)

    private val mutableState = MutableStateFlow(
        MedicationScheduleUiState(
            schedule = ScheduleFormUiState(
                weekdays = setOf(initialDateTime.dayOfWeek),
                minutesOfDay = listOf(initialDateTime.toLocalTime().toMinuteOfDay()),
                timeDraft = "",
                startDateText = "",
                endDateText = "",
                zoneId = zoneProvider.currentZone().id,
            ),
        ),
    )

    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<MedicationScheduleEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun onMedicationNameChanged(value: String) {
        mutableState.update { current ->
            current.copy(
                medicationName = value,
                errors = current.errors - CarePlanField.MEDICATION_NAME,
                generalError = null,
            )
        }
    }

    fun onInstructionChanged(value: String) {
        mutableState.update { current ->
            current.copy(
                instruction = value,
                errors = current.errors - CarePlanField.INSTRUCTION,
                generalError = null,
            )
        }
    }

    fun onWeekdayToggled(day: DayOfWeek) =
        updateSchedule { it.toggleWeekday(day) }

    fun onTimeDraftChanged(value: String) =
        updateSchedule { it.withTimeDraft(value) }

    fun addTime() =
        updateSchedule(clearGeneralError = false) { it.addDraftTime() }

    fun removeTime(minuteOfDay: Int) =
        updateSchedule(clearGeneralError = false) {
            it.removeTime(minuteOfDay)
        }

    fun onStartDateChanged(value: String) =
        updateSchedule { it.withStartDate(value) }

    fun onEndDateChanged(value: String) =
        updateSchedule { it.withEndDate(value) }

    fun save() {
        val current = mutableState.value
        if (current.isSaving) {
            return
        }

        val parsedDates = current.schedule.parseDates()
        if (parsedDates.errors.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(
                    schedule = state.schedule.withDateErrors(parsedDates.errors),
                )
            }
            return
        }

        viewModelScope.launch {
            mutableState.update { state ->
                state.copy(
                    isSaving = true,
                    errors = emptyMap(),
                    schedule = state.schedule.clearErrors(),
                    generalError = null,
                )
            }

            try {
                val state = mutableState.value
                when (
                    val outcome = carePlanService.createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId = recipientId,
                            medicationName = state.medicationName,
                            instruction = state.instruction,
                            weekdays = state.schedule.weekdays,
                            minutesOfDay = state.schedule.minutesOfDay,
                            startDate = parsedDates.startDate,
                            endDate = parsedDates.endDate,
                            zoneId = state.schedule.zoneId,
                        ),
                    )
                ) {
                    is CreateMedicationScheduleOutcome.Created -> {
                        if (completeInitialSetup) {
                            setupPreferenceStore.markSetupComplete()
                        }
                        eventChannel.send(MedicationScheduleEvent.Completed)
                    }

                    is CreateMedicationScheduleOutcome.Invalid -> {
                        val fieldErrors = outcome.errors.toFieldErrors()
                        mutableState.update { currentState ->
                            currentState.copy(
                                errors = fieldErrors.filterKeys {
                                    it in MEDICATION_FIELDS
                                },
                                schedule = currentState.schedule
                                    .withValidationErrors(fieldErrors),
                            )
                        }
                    }

                    CreateMedicationScheduleOutcome.RecipientNotFound -> {
                        showGeneralError("فرد تحت مراقبت پیدا نشد.")
                    }
                }
            } catch (_: Exception) {
                showGeneralError("ذخیره‌سازی انجام نشد. دوباره تلاش کنید.")
            } finally {
                mutableState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun updateSchedule(
        clearGeneralError: Boolean = true,
        transform: (ScheduleFormUiState) -> ScheduleFormUiState,
    ) {
        mutableState.update { current ->
            current.copy(
                schedule = transform(current.schedule),
                generalError = if (clearGeneralError) {
                    null
                } else {
                    current.generalError
                },
            )
        }
    }

    private fun showGeneralError(message: String) {
        mutableState.update { it.copy(generalError = message) }
    }

    companion object {
        fun factory(
            recipientId: String,
            carePlanService: CarePlanService,
            setupPreferenceStore: SetupPreferenceStore,
            completeInitialSetup: Boolean,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MedicationScheduleViewModel(
                    recipientId = recipientId,
                    carePlanService = carePlanService,
                    setupPreferenceStore = setupPreferenceStore,
                    completeInitialSetup = completeInitialSetup,
                    clock = clock,
                    zoneProvider = zoneProvider,
                )
            }
        }

        private const val DEFAULT_FUTURE_MINUTES = 2L

        private val MEDICATION_FIELDS = setOf(
            CarePlanField.MEDICATION_NAME,
            CarePlanField.INSTRUCTION,
        )
    }
}

@Composable
fun MedicationScheduleRoute(
    viewModel: MedicationScheduleViewModel,
    onCompleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MedicationScheduleEvent.Completed -> onCompleted()
            }
        }
    }

    MedicationScheduleScreen(
        state = state,
        onMedicationNameChanged = viewModel::onMedicationNameChanged,
        onInstructionChanged = viewModel::onInstructionChanged,
        onWeekdayToggled = viewModel::onWeekdayToggled,
        onTimeDraftChanged = viewModel::onTimeDraftChanged,
        onAddTime = viewModel::addTime,
        onRemoveTime = viewModel::removeTime,
        onStartDateChanged = viewModel::onStartDateChanged,
        onEndDateChanged = viewModel::onEndDateChanged,
        onSave = viewModel::save,
    )
}

@Composable
fun MedicationScheduleScreen(
    state: MedicationScheduleUiState,
    onMedicationNameChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    onWeekdayToggled: (DayOfWeek) -> Unit,
    onTimeDraftChanged: (String) -> Unit,
    onAddTime: () -> Unit,
    onRemoveTime: (Int) -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.medication_schedule_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(Modifier.height(24.dp))

            MedicationTextFields(
                medicationName = state.medicationName,
                instruction = state.instruction,
                errors = state.errors,
                enabled = !state.isSaving,
                onMedicationNameChanged = onMedicationNameChanged,
                onInstructionChanged = onInstructionChanged,
                instructionMinLines = 3,
                medicationNameTestTag = "medication_name",
                instructionTestTag = "medication_instruction",
            )

            Spacer(Modifier.height(20.dp))

            ScheduleFormFields(
                state = state.schedule,
                callbacks = ScheduleFormCallbacks(
                    onWeekdayToggled = onWeekdayToggled,
                    onTimeDraftChanged = onTimeDraftChanged,
                    onAddTime = onAddTime,
                    onRemoveTime = onRemoveTime,
                    onStartDateChanged = onStartDateChanged,
                    onEndDateChanged = onEndDateChanged,
                ),
                enabled = !state.isSaving,
            )

            state.generalError?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("schedule_error"),
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("medication_schedule_save"),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.create_care_plan))
                }
            }
        }
    }
}
