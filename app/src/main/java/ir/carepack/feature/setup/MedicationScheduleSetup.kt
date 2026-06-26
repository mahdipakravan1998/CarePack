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
import androidx.compose.material3.OutlinedTextField
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
import ir.carepack.feature.careplan.ScheduleFormCallbacks
import ir.carepack.feature.careplan.ScheduleFormFields
import ir.carepack.feature.careplan.ScheduleFormUiState
import ir.carepack.feature.careplan.parseHourMinute
import ir.carepack.feature.careplan.parseOptionalDate
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationScheduleUiState(
    val medicationName: String = "",
    val instruction: String = "",
    val schedule:
    ScheduleFormUiState,
    val errors:
    Map<CarePlanField, String> =
        emptyMap(),
    val isSaving: Boolean = false,
    val generalError: String? = null,
)

sealed interface MedicationScheduleEvent {

    data object Completed :
        MedicationScheduleEvent
}

class MedicationScheduleViewModel(
    private val recipientId: String,
    private val carePlanService:
    CarePlanService,
    private val setupPreferenceStore:
    SetupPreferenceStore,
    private val completeInitialSetup:
    Boolean,
    clock: Clock,
    zoneProvider: ZoneProvider,
) : ViewModel() {

    private val initialDateTime:
            LocalDateTime =
        LocalDateTime
            .ofInstant(
                clock.instant(),
                zoneProvider.currentZone(),
            )
            .plusMinutes(
                DEFAULT_FUTURE_MINUTES,
            )
            .withSecond(0)
            .withNano(0)

    private val mutableState =
        MutableStateFlow(
            MedicationScheduleUiState(
                schedule =
                    ScheduleFormUiState(
                        weekdays =
                            setOf(
                                initialDateTime
                                    .dayOfWeek,
                            ),
                        minutesOfDay =
                            listOf(
                                initialDateTime
                                    .toLocalTime()
                                    .toMinuteOfDay(),
                            ),
                        timeDraft = "",
                        startDateText = "",
                        endDateText = "",
                        zoneId =
                            zoneProvider
                                .currentZone()
                                .id,
                    ),
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

    fun onMedicationNameChanged(
        value: String,
    ) {
        mutableState.update {
            it.copy(
                medicationName = value,
                errors =
                    it.errors -
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
            it.copy(
                instruction = value,
                errors =
                    it.errors -
                            CarePlanField
                                .INSTRUCTION,
                generalError = null,
            )
        }
    }

    fun onWeekdayToggled(
        day: DayOfWeek,
    ) {
        mutableState.update { current ->
            val weekdays =
                current
                    .schedule
                    .weekdays
                    .toMutableSet()

            if (!weekdays.add(day)) {
                weekdays.remove(day)
            }

            current.copy(
                schedule =
                    current
                        .schedule
                        .copy(
                            weekdays =
                                weekdays,
                        ),
                errors =
                    current.errors -
                            CarePlanField
                                .WEEKDAYS,
                generalError = null,
            )
        }
    }

    fun onTimeDraftChanged(
        value: String,
    ) {
        mutableState.update {
            it.copy(
                schedule =
                    it.schedule.copy(
                        timeDraft = value,
                    ),
                errors =
                    it.errors -
                            CarePlanField.TIMES,
                generalError = null,
            )
        }
    }

    fun addTime() {
        val minuteOfDay =
            parseHourMinute(
                mutableState
                    .value
                    .schedule
                    .timeDraft,
            )

        if (minuteOfDay == null) {
            setFieldError(
                field = CarePlanField.TIMES,
                message =
                    "زمان باید به شکل معتبر ۲۴ ساعته مانند ۱۴:۳۰ باشد.",
            )
            return
        }

        if (
            minuteOfDay in
            mutableState
                .value
                .schedule
                .minutesOfDay
        ) {
            setFieldError(
                field = CarePlanField.TIMES,
                message =
                    "این زمان قبلاً اضافه شده است.",
            )
            return
        }

        mutableState.update {
            it.copy(
                schedule =
                    it.schedule.copy(
                        minutesOfDay =
                            (
                                    it.schedule
                                        .minutesOfDay +
                                            minuteOfDay
                                    ).sorted(),
                        timeDraft = "",
                    ),
                errors =
                    it.errors -
                            CarePlanField.TIMES,
            )
        }
    }

    fun removeTime(
        minuteOfDay: Int,
    ) {
        mutableState.update {
            it.copy(
                schedule =
                    it.schedule.copy(
                        minutesOfDay =
                            it.schedule
                                .minutesOfDay -
                                    minuteOfDay,
                    ),
                errors =
                    it.errors -
                            CarePlanField.TIMES,
            )
        }
    }

    fun onStartDateChanged(
        value: String,
    ) {
        mutableState.update {
            it.copy(
                schedule =
                    it.schedule.copy(
                        startDateText =
                            value,
                    ),
                errors =
                    it.errors -
                            CarePlanField.START_DATE,
                generalError = null,
            )
        }
    }

    fun onEndDateChanged(
        value: String,
    ) {
        mutableState.update {
            it.copy(
                schedule =
                    it.schedule.copy(
                        endDateText =
                            value,
                    ),
                errors =
                    it.errors -
                            CarePlanField.END_DATE,
                generalError = null,
            )
        }
    }

    fun save() {
        if (mutableState.value.isSaving) {
            return
        }

        val current =
            mutableState.value

        val startDate =
            parseOptionalDate(
                current
                    .schedule
                    .startDateText,
            )

        val endDate =
            parseOptionalDate(
                current
                    .schedule
                    .endDateText,
            )

        val localErrors =
            mutableMapOf<
                    CarePlanField,
                    String,
                    >()

        if (
            current
                .schedule
                .startDateText
                .isNotBlank() &&
            startDate == null
        ) {
            localErrors[
                CarePlanField.START_DATE
            ] =
                "تاریخ شروع باید به شکل YYYY-MM-DD باشد."
        }

        if (
            current
                .schedule
                .endDateText
                .isNotBlank() &&
            endDate == null
        ) {
            localErrors[
                CarePlanField.END_DATE
            ] =
                "تاریخ پایان باید به شکل YYYY-MM-DD باشد."
        }

        if (localErrors.isNotEmpty()) {
            mutableState.update {
                it.copy(
                    errors =
                        it.errors +
                                localErrors,
                )
            }
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isSaving = true,
                    errors = emptyMap(),
                    generalError = null,
                )
            }

            try {
                val state =
                    mutableState.value

                when (
                    val outcome =
                        carePlanService
                            .createMedicationAndSchedule(
                                CreateMedicationScheduleCommand(
                                    recipientId =
                                        recipientId,
                                    medicationName =
                                        state
                                            .medicationName,
                                    instruction =
                                        state
                                            .instruction,
                                    weekdays =
                                        state
                                            .schedule
                                            .weekdays,
                                    minutesOfDay =
                                        state
                                            .schedule
                                            .minutesOfDay,
                                    startDate =
                                        startDate,
                                    endDate =
                                        endDate,
                                    zoneId =
                                        state
                                            .schedule
                                            .zoneId,
                                ),
                            )
                ) {
                    is CreateMedicationScheduleOutcome
                    .Created -> {
                        if (
                            completeInitialSetup
                        ) {
                            setupPreferenceStore
                                .markSetupComplete()
                        }

                        eventChannel.send(
                            MedicationScheduleEvent
                                .Completed,
                        )
                    }

                    is CreateMedicationScheduleOutcome
                    .Invalid -> {
                        mutableState.update {
                            it.copy(
                                errors =
                                    outcome
                                        .errors
                                        .associate {
                                                error ->
                                            error.field to
                                                    error.message
                                        },
                            )
                        }
                    }

                    CreateMedicationScheduleOutcome
                        .RecipientNotFound -> {
                        mutableState.update {
                            it.copy(
                                generalError =
                                    "فرد تحت مراقبت پیدا نشد.",
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        generalError =
                            "ذخیره‌سازی انجام نشد. دوباره تلاش کنید.",
                    )
                }
            } finally {
                mutableState.update {
                    it.copy(
                        isSaving = false,
                    )
                }
            }
        }
    }

    private fun setFieldError(
        field: CarePlanField,
        message: String,
    ) {
        mutableState.update {
            it.copy(
                errors =
                    it.errors +
                            (field to message),
            )
        }
    }

    companion object {
        fun factory(
            recipientId: String,
            carePlanService:
            CarePlanService,
            setupPreferenceStore:
            SetupPreferenceStore,
            completeInitialSetup:
            Boolean,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    MedicationScheduleViewModel(
                        recipientId =
                            recipientId,
                        carePlanService =
                            carePlanService,
                        setupPreferenceStore =
                            setupPreferenceStore,
                        completeInitialSetup =
                            completeInitialSetup,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }
        }

        private const val
                DEFAULT_FUTURE_MINUTES =
            2L
    }
}

@Composable
fun MedicationScheduleRoute(
    viewModel:
    MedicationScheduleViewModel,
    onCompleted: () -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect {
                event ->
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
        onMedicationNameChanged =
            viewModel::
            onMedicationNameChanged,
        onInstructionChanged =
            viewModel::
            onInstructionChanged,
        onWeekdayToggled =
            viewModel::onWeekdayToggled,
        onTimeDraftChanged =
            viewModel::
            onTimeDraftChanged,
        onAddTime =
            viewModel::addTime,
        onRemoveTime =
            viewModel::removeTime,
        onStartDateChanged =
            viewModel::
            onStartDateChanged,
        onEndDateChanged =
            viewModel::
            onEndDateChanged,
        onSave =
            viewModel::save,
    )
}

@Composable
fun MedicationScheduleScreen(
    state: MedicationScheduleUiState,
    onMedicationNameChanged:
        (String) -> Unit,
    onInstructionChanged:
        (String) -> Unit,
    onWeekdayToggled:
        (DayOfWeek) -> Unit,
    onTimeDraftChanged:
        (String) -> Unit,
    onAddTime: () -> Unit,
    onRemoveTime: (Int) -> Unit,
    onStartDateChanged:
        (String) -> Unit,
    onEndDateChanged:
        (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(24.dp),
            verticalArrangement =
                Arrangement.Top,
        ) {
            Text(
                text = stringResource(
                    R.string
                        .medication_schedule_title,
                ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp),
            )

            OutlinedTextField(
                value =
                    state.medicationName,
                onValueChange =
                    onMedicationNameChanged,
                enabled =
                    !state.isSaving,
                label = {
                    Text(
                        text = stringResource(
                            R.string
                                .medication_name_label,
                        ),
                    )
                },
                singleLine = true,
                isError =
                    state.errors.containsKey(
                        CarePlanField
                            .MEDICATION_NAME,
                    ),
                supportingText = {
                    state.errors[
                        CarePlanField
                            .MEDICATION_NAME
                    ]?.let {
                        Text(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(
                        "medication_name",
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            OutlinedTextField(
                value =
                    state.instruction,
                onValueChange =
                    onInstructionChanged,
                enabled =
                    !state.isSaving,
                label = {
                    Text(
                        text = stringResource(
                            R.string
                                .instruction_label,
                        ),
                    )
                },
                minLines = 3,
                isError =
                    state.errors.containsKey(
                        CarePlanField
                            .INSTRUCTION,
                    ),
                supportingText = {
                    state.errors[
                        CarePlanField
                            .INSTRUCTION
                    ]?.let {
                        Text(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(
                        "medication_instruction",
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp),
            )

            ScheduleFormFields(
                state = state.schedule,
                errors = state.errors,
                callbacks =
                    ScheduleFormCallbacks(
                        onWeekdayToggled =
                            onWeekdayToggled,
                        onTimeDraftChanged =
                            onTimeDraftChanged,
                        onAddTime =
                            onAddTime,
                        onRemoveTime =
                            onRemoveTime,
                        onStartDateChanged =
                            onStartDateChanged,
                        onEndDateChanged =
                            onEndDateChanged,
                    ),
                enabled =
                    !state.isSaving,
            )

            state.generalError?.let {
                    error ->
                Spacer(
                    modifier =
                        Modifier.height(12.dp),
                )

                Text(
                    text = error,
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                    modifier =
                        Modifier.testTag(
                            "schedule_error",
                        ),
                )
            }

            Spacer(
                modifier =
                    Modifier.height(24.dp),
            )

            Button(
                onClick = onSave,
                enabled =
                    !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(
                        "medication_schedule_save",
                    ),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = stringResource(
                            R.string
                                .create_care_plan,
                        ),
                    )
                }
            }
        }
    }
}

private fun LocalTime.toMinuteOfDay():
        Int {
    return hour * MINUTES_PER_HOUR +
            minute
}

private const val MINUTES_PER_HOUR =
    60
