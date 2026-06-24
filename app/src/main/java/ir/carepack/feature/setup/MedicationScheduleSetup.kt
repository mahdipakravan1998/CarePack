package ir.carepack.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationScheduleUiState(
    val medicationName: String = "",
    val instruction: String = "",
    val weekday: DayOfWeek,
    val timeText: String,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface MedicationScheduleEvent {
    data object Completed : MedicationScheduleEvent
}

class MedicationScheduleViewModel(
    private val recipientId: String,
    private val carePlanService: CarePlanService,
    private val setupPreferenceStore:
    SetupPreferenceStore,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : ViewModel() {

    private val initialDateTime: LocalDateTime =
        LocalDateTime
            .ofInstant(
                clock.instant(),
                zoneProvider.currentZone(),
            )
            .plusMinutes(DEFAULT_FUTURE_MINUTES)
            .withSecond(0)
            .withNano(0)

    private val mutableState =
        MutableStateFlow(
            MedicationScheduleUiState(
                weekday = initialDateTime.dayOfWeek,
                timeText = initialDateTime
                    .toLocalTime()
                    .toHourMinuteText(),
            ),
        )

    val state = mutableState.asStateFlow()

    private val eventChannel =
        Channel<MedicationScheduleEvent>(
            capacity = Channel.BUFFERED,
        )

    val events = eventChannel.receiveAsFlow()

    fun onMedicationNameChanged(
        newValue: String,
    ) {
        mutableState.update {
            it.copy(
                medicationName = newValue,
                errorMessage = null,
            )
        }
    }

    fun onInstructionChanged(
        newValue: String,
    ) {
        mutableState.update {
            it.copy(
                instruction = newValue,
                errorMessage = null,
            )
        }
    }

    fun onWeekdayChanged(
        newValue: DayOfWeek,
    ) {
        mutableState.update {
            it.copy(
                weekday = newValue,
                errorMessage = null,
            )
        }
    }

    fun onTimeChanged(
        newValue: String,
    ) {
        mutableState.update {
            it.copy(
                timeText = newValue,
                errorMessage = null,
            )
        }
    }

    fun save() {
        if (mutableState.value.isSaving) {
            return
        }

        val localTime =
            parseHourMinute(mutableState.value.timeText)

        if (localTime == null) {
            mutableState.update {
                it.copy(
                    errorMessage =
                        "زمان باید به شکل معتبر ۲۴ ساعته مانند ۱۴:۳۰ باشد.",
                )
            }
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            try {
                val outcome =
                    carePlanService
                        .createMedicationAndSchedule(
                            CreateMedicationScheduleCommand(
                                recipientId = recipientId,
                                medicationName =
                                    mutableState.value
                                        .medicationName,
                                instruction =
                                    mutableState.value
                                        .instruction,
                                weekday =
                                    mutableState.value
                                        .weekday,
                                localTime = localTime,
                                zoneId =
                                    zoneProvider
                                        .currentZone()
                                        .id,
                            ),
                        )

                when (outcome) {
                    is CreateMedicationScheduleOutcome.Created -> {
                        runCatching {
                            setupPreferenceStore
                                .markSetupComplete()
                        }

                        eventChannel.send(
                            MedicationScheduleEvent.Completed,
                        )
                    }

                    is CreateMedicationScheduleOutcome.Invalid -> {
                        mutableState.update {
                            it.copy(
                                errorMessage =
                                    outcome.reason,
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        errorMessage =
                            "ذخیره‌سازی انجام نشد. دوباره تلاش کنید.",
                    )
                }
            } finally {
                mutableState.update {
                    it.copy(isSaving = false)
                }
            }
        }
    }

    companion object {
        fun factory(
            recipientId: String,
            carePlanService: CarePlanService,
            setupPreferenceStore:
            SetupPreferenceStore,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    MedicationScheduleViewModel(
                        recipientId = recipientId,
                        carePlanService =
                            carePlanService,
                        setupPreferenceStore =
                            setupPreferenceStore,
                        clock = clock,
                        zoneProvider = zoneProvider,
                    )
                }
            }
        }

        private const val DEFAULT_FUTURE_MINUTES =
            2L
    }
}

@Composable
fun MedicationScheduleRoute(
    viewModel: MedicationScheduleViewModel,
    onCompleted: () -> Unit,
) {
    val state by
    viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MedicationScheduleEvent.Completed -> {
                    onCompleted()
                }
            }
        }
    }

    MedicationScheduleScreen(
        state = state,
        onMedicationNameChanged =
            viewModel::onMedicationNameChanged,
        onInstructionChanged =
            viewModel::onInstructionChanged,
        onWeekdayChanged =
            viewModel::onWeekdayChanged,
        onTimeChanged =
            viewModel::onTimeChanged,
        onSave = viewModel::save,
    )
}

@Composable
fun MedicationScheduleScreen(
    state: MedicationScheduleUiState,
    onMedicationNameChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    onWeekdayChanged: (DayOfWeek) -> Unit,
    onTimeChanged: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var weekdayMenuExpanded by
    remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(
                    R.string.medication_schedule_title,
                ),
                style =
                    MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = state.medicationName,
                onValueChange =
                    onMedicationNameChanged,
                label = {
                    Text(
                        text = stringResource(
                            R.string.medication_name_label,
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("medication_name"),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.instruction,
                onValueChange = onInstructionChanged,
                label = {
                    Text(
                        text = stringResource(
                            R.string.instruction_label,
                        ),
                    )
                },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("medication_instruction"),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    R.string.weekday_label,
                ),
                style =
                    MaterialTheme.typography.labelLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        weekdayMenuExpanded = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weekday_selector"),
                ) {
                    Text(
                        text = weekdayPersianName(
                            state.weekday,
                        ),
                    )
                }

                DropdownMenu(
                    expanded = weekdayMenuExpanded,
                    onDismissRequest = {
                        weekdayMenuExpanded = false
                    },
                ) {
                    DayOfWeek.entries.forEach {
                            dayOfWeek ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text =
                                        weekdayPersianName(
                                            dayOfWeek,
                                        ),
                                )
                            },
                            onClick = {
                                weekdayMenuExpanded =
                                    false

                                onWeekdayChanged(
                                    dayOfWeek,
                                )
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.timeText,
                onValueChange = onTimeChanged,
                label = {
                    Text(
                        text = stringResource(
                            R.string.time_label,
                        ),
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType =
                        KeyboardType.Number,
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("schedule_time"),
            )

            state.errorMessage?.let { errorMessage ->
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = errorMessage,
                    color =
                        MaterialTheme.colorScheme.error,
                    style =
                        MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier.testTag("schedule_error"),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                    Text(
                        text = stringResource(
                            R.string.create_care_plan,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun weekdayPersianName(
    dayOfWeek: DayOfWeek,
): String {
    return when (dayOfWeek) {
        DayOfWeek.SATURDAY ->
            stringResource(R.string.saturday)

        DayOfWeek.SUNDAY ->
            stringResource(R.string.sunday)

        DayOfWeek.MONDAY ->
            stringResource(R.string.monday)

        DayOfWeek.TUESDAY ->
            stringResource(R.string.tuesday)

        DayOfWeek.WEDNESDAY ->
            stringResource(R.string.wednesday)

        DayOfWeek.THURSDAY ->
            stringResource(R.string.thursday)

        DayOfWeek.FRIDAY ->
            stringResource(R.string.friday)
    }
}

private fun parseHourMinute(
    value: String,
): LocalTime? {
    val match = HOUR_MINUTE_REGEX.matchEntire(
        value.trim(),
    ) ?: return null

    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()

    return runCatching {
        LocalTime.of(hour, minute)
    }.getOrNull()
}

private fun LocalTime.toHourMinuteText(): String {
    return String.format(
        Locale.ROOT,
        "%02d:%02d",
        hour,
        minute,
    )
}

private val HOUR_MINUTE_REGEX =
    Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")