package ir.carepack.feature.careplan

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.UpdateScheduleCommand
import ir.carepack.domain.careplan.UpdateScheduleOutcome
import ir.carepack.domain.model.MedicationStatus
import java.time.DayOfWeek
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleEditUiState(
    val isLoading: Boolean = true,
    val originalZoneId: String? = null,
    val schedule:
    ScheduleFormUiState? = null,
    val errors:
    Map<CarePlanField, String> =
        emptyMap(),
    val isSaving: Boolean = false,
    val generalError: String? = null,
)

sealed interface ScheduleEditEvent {

    data object Completed :
        ScheduleEditEvent
}

class ScheduleEditViewModel(
    private val medicationId: String,
    private val carePlanService:
    CarePlanService,
    private val zoneProvider:
    ZoneProvider,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            ScheduleEditUiState(),
        )

    val state =
        mutableState.asStateFlow()

    private val eventChannel =
        Channel<ScheduleEditEvent>(
            capacity = Channel.BUFFERED,
        )

    val events =
        eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun onWeekdayToggled(day: DayOfWeek) = updateSchedule(
        fieldToClear = CarePlanField.WEEKDAYS,
    ) { schedule ->
        schedule.toggleWeekday(day)
    }

    fun onTimeDraftChanged(value: String) = updateSchedule(
        fieldToClear = CarePlanField.TIMES,
    ) { schedule ->
        schedule.withTimeDraft(value)
    }

    fun addTime() {
        val schedule = mutableState.value.schedule ?: return
        when (val result = schedule.addDraftTime()) {
            is AddScheduleTimeResult.Invalid -> setError(
                field = CarePlanField.TIMES,
                message = result.message,
            )
            is AddScheduleTimeResult.Updated -> updateSchedule(
                fieldToClear = CarePlanField.TIMES,
            ) { result.state }
        }
    }

    fun removeTime(minuteOfDay: Int) = updateSchedule(
        fieldToClear = CarePlanField.TIMES,
    ) { schedule ->
        schedule.removeTime(minuteOfDay)
    }

    fun onStartDateChanged(value: String) = updateSchedule(
        fieldToClear = CarePlanField.START_DATE,
    ) { schedule ->
        schedule.withStartDate(value)
    }

    fun onEndDateChanged(value: String) = updateSchedule(
        fieldToClear = CarePlanField.END_DATE,
    ) { schedule ->
        schedule.withEndDate(value)
    }

    fun save() {
        val schedule = mutableState.value.schedule ?: return
        if (mutableState.value.isSaving) {
            return
        }

        val parsedDates = schedule.parseDates()
        if (parsedDates.errors.isNotEmpty()) {
            mutableState.update { current ->
                current.copy(errors = current.errors + parsedDates.errors)
            }
            return
        }

        viewModelScope.launch {
            mutableState.update { current ->
                current.copy(
                    isSaving = true,
                    errors = emptyMap(),
                    generalError = null,
                )
            }

            try {
                when (
                    val outcome = carePlanService.updateSchedule(
                        UpdateScheduleCommand(
                            medicationId = medicationId,
                            weekdays = schedule.weekdays,
                            minutesOfDay = schedule.minutesOfDay,
                            startDate = parsedDates.startDate,
                            endDate = parsedDates.endDate,
                            zoneId = schedule.zoneId,
                        ),
                    )
                ) {
                    UpdateScheduleOutcome.Updated,
                    UpdateScheduleOutcome.Unchanged,
                        -> eventChannel.send(ScheduleEditEvent.Completed)

                    UpdateScheduleOutcome.NotFound -> showGeneralError("دارو پیدا نشد.")
                    UpdateScheduleOutcome.NotEditable -> {
                        showGeneralError("این برنامه قابل ویرایش نیست.")
                    }
                    is UpdateScheduleOutcome.Invalid -> mutableState.update { current ->
                        current.copy(errors = outcome.errors.toFieldErrors())
                    }
                }
            } catch (_: Exception) {
                showGeneralError("ذخیره‌سازی انجام نشد. دوباره تلاش کنید.")
            } finally {
                mutableState.update { current -> current.copy(isSaving = false) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val snapshot = carePlanService.getMedicationEditor(medicationId)
                val existingSchedule = snapshot?.schedule
                if (
                    snapshot == null ||
                    snapshot.status != MedicationStatus.ACTIVE ||
                    existingSchedule == null
                ) {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            generalError = "برنامه قابل ویرایش پیدا نشد.",
                        )
                    }
                    return@launch
                }

                mutableState.update { current ->
                    current.copy(
                        isLoading = false,
                        originalZoneId = existingSchedule.zoneId,
                        schedule = ScheduleFormUiState(
                            weekdays = existingSchedule.weekdays,
                            minutesOfDay = existingSchedule.times.map { it.toMinuteOfDay() },
                            timeDraft = "",
                            startDateText = existingSchedule.startDate?.toString().orEmpty(),
                            endDateText = existingSchedule.endDate?.toString().orEmpty(),
                            zoneId = zoneProvider.currentZone().id,
                        ),
                    )
                }
            } catch (_: Exception) {
                mutableState.update { current ->
                    current.copy(
                        isLoading = false,
                        generalError = "خواندن برنامه انجام نشد.",
                    )
                }
            }
        }
    }

    private fun updateSchedule(
        fieldToClear: CarePlanField,
        transform: (ScheduleFormUiState) -> ScheduleFormUiState,
    ) {
        mutableState.update { current ->
            val schedule = current.schedule ?: return@update current
            current.copy(
                schedule = transform(schedule),
                errors = current.errors - fieldToClear,
                generalError = null,
            )
        }
    }

    private fun setError(field: CarePlanField, message: String) {
        mutableState.update { current ->
            current.copy(errors = current.errors + (field to message))
        }
    }

    private fun showGeneralError(message: String) {
        mutableState.update { current -> current.copy(generalError = message) }
    }

    companion object {
        fun factory(
            medicationId: String,
            carePlanService:
            CarePlanService,
            zoneProvider:
            ZoneProvider,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ScheduleEditViewModel(
                        medicationId =
                            medicationId,
                        carePlanService =
                            carePlanService,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }
        }

    }
}

@Composable
fun ScheduleEditRoute(
    viewModel: ScheduleEditViewModel,
    onBack: () -> Unit,
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
                ScheduleEditEvent.Completed -> {
                    onCompleted()
                }
            }
        }
    }

    ScheduleEditScreen(
        state = state,
        onBack = onBack,
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
private fun ScheduleEditScreen(
    state: ScheduleEditUiState,
    onBack: () -> Unit,
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
) {
    Scaffold(
        modifier =
            Modifier.fillMaxSize(),
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
            TextButton(
                onClick = onBack,
            ) {
                Text(
                    text = stringResource(
                        R.string.back,
                    ),
                )
            }

            Text(
                text = stringResource(
                    R.string
                        .schedule_edit_title,
                ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp),
            )

            if (state.isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            state.originalZoneId?.let {
                    oldZone ->
                Text(
                    text = stringResource(
                        R.string
                            .original_zone_value,
                        oldZone,
                    ),
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp),
                )
            }

            state.schedule?.let {
                    schedule ->
                ScheduleFormFields(
                    state = schedule,
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
            }

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
                )
            }

            Spacer(
                modifier =
                    Modifier.height(24.dp),
            )

            Button(
                onClick = onSave,
                enabled =
                    !state.isSaving &&
                            state.schedule != null,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = stringResource(
                            R.string
                                .save_changes,
                        ),
                    )
                }
            }
        }
    }
}
