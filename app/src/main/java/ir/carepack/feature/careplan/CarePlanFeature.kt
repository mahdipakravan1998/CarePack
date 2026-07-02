package ir.carepack.feature.careplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.domain.careplan.ArchiveMedicationOutcome
import ir.carepack.domain.careplan.CarePlanOverview
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.MedicationPlanItem
import ir.carepack.domain.careplan.SchedulePlan
import ir.carepack.domain.careplan.StopMedicationOutcome
import ir.carepack.domain.model.MedicationStatus
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class CarePlanUiState(
    val isLoading: Boolean = true,
    val overview: CarePlanOverview? = null,
    val errorMessage: String? = null,
    val pendingMedicationAction:
    PendingMedicationAction? = null,
    val actionInProgress: Boolean = false,
)

sealed interface PendingMedicationAction {

    data class Stop(
        val medicationId: String,
        val medicationName: String,
    ) : PendingMedicationAction

    data class Archive(
        val medicationId: String,
        val medicationName: String,
    ) : PendingMedicationAction
}

sealed interface CarePlanEvent {

    data class ShowMessage(
        val message: String,
    ) : CarePlanEvent
}

class CarePlanViewModel(
    private val carePlanService: CarePlanService,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            CarePlanUiState(),
        )

    val state =
        mutableState.asStateFlow()

    private val eventChannel =
        Channel<CarePlanEvent>(
            capacity = Channel.BUFFERED,
        )

    val events =
        eventChannel.receiveAsFlow()

    init {
        observeCarePlan()
    }

    fun retry() {
        observeCarePlan()
    }

    fun requestStopMedication(
        medicationId: String,
        medicationName: String,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    pendingMedicationAction =
                        PendingMedicationAction
                            .Stop(
                                medicationId =
                                    medicationId,
                                medicationName =
                                    medicationName,
                            ),
                    errorMessage = null,
                )
    }

    fun requestArchiveMedication(
        medicationId: String,
        medicationName: String,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    pendingMedicationAction =
                        PendingMedicationAction
                            .Archive(
                                medicationId =
                                    medicationId,
                                medicationName =
                                    medicationName,
                            ),
                    errorMessage = null,
                )
    }

    fun dismissMedicationAction() {
        mutableState.value =
            mutableState
                .value
                .copy(
                    pendingMedicationAction = null,
                )
    }

    fun confirmMedicationAction() {
        val action =
            mutableState
                .value
                .pendingMedicationAction
                ?: return

        if (
            mutableState
                .value
                .actionInProgress
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.value =
                mutableState
                    .value
                    .copy(
                        actionInProgress = true,
                        errorMessage = null,
                    )

            try {
                when (action) {
                    is PendingMedicationAction.Stop -> {
                        handleStopMedication(
                            action,
                        )
                    }

                    is PendingMedicationAction.Archive -> {
                        handleArchiveMedication(
                            action,
                        )
                    }
                }
            } catch (
                cancellationException: CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            errorMessage =
                                "انجام عملیات ممکن نشد. دوباره تلاش کنید.",
                        )
            } finally {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            actionInProgress = false,
                            pendingMedicationAction = null,
                        )
            }
        }
    }

    private fun observeCarePlan() {
        viewModelScope.launch {
            mutableState.value =
                mutableState
                    .value
                    .copy(
                        isLoading = true,
                        errorMessage = null,
                    )

            try {
                carePlanService
                    .observeCarePlan()
                    .collect { overview ->
                        mutableState.value =
                            mutableState
                                .value
                                .copy(
                                    isLoading = false,
                                    overview = overview,
                                    errorMessage = null,
                                )
                    }
            } catch (
                cancellationException: CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            isLoading = false,
                            errorMessage =
                                "خواندن برنامه دارویی انجام نشد.",
                        )
            }
        }
    }

    private suspend fun handleStopMedication(
        action: PendingMedicationAction.Stop,
    ) {
        when (
            carePlanService.stopMedication(
                medicationId =
                    action.medicationId,
            )
        ) {
            StopMedicationOutcome.Stopped -> {
                eventChannel.send(
                    CarePlanEvent.ShowMessage(
                        "دارو متوقف شد.",
                    ),
                )
            }

            StopMedicationOutcome.NotFound -> {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            errorMessage =
                                "دارو پیدا نشد.",
                        )
            }

            StopMedicationOutcome.AlreadyStopped -> {
                eventChannel.send(
                    CarePlanEvent.ShowMessage(
                        "دارو قبلاً متوقف شده است.",
                    ),
                )
            }
        }
    }

    private suspend fun handleArchiveMedication(
        action: PendingMedicationAction.Archive,
    ) {
        when (
            carePlanService.archiveMedication(
                medicationId =
                    action.medicationId,
            )
        ) {
            ArchiveMedicationOutcome.Archived -> {
                eventChannel.send(
                    CarePlanEvent.ShowMessage(
                        "دارو بایگانی شد.",
                    ),
                )
            }

            ArchiveMedicationOutcome.NotFound -> {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            errorMessage =
                                "دارو پیدا نشد.",
                        )
            }

            ArchiveMedicationOutcome.MustStopFirst -> {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            errorMessage =
                                "قبل از بایگانی، دارو را متوقف کنید.",
                        )
            }

            ArchiveMedicationOutcome.AlreadyArchived -> {
                eventChannel.send(
                    CarePlanEvent.ShowMessage(
                        "دارو قبلاً بایگانی شده است.",
                    ),
                )
            }
        }
    }

    companion object {

        fun factory(
            carePlanService: CarePlanService,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CarePlanViewModel(
                        carePlanService =
                            carePlanService,
                    )
                }
            }
    }
}

@Composable
fun CarePlanRoute(
    viewModel: CarePlanViewModel,
    onAddMedication:
        (String) -> Unit,
    onAddSchedule:
        (String) -> Unit,
    onEditMedicationText:
        (String) -> Unit,
    onEditSchedule:
        (String) -> Unit,
    snackbarHost:
    suspend (String) -> Unit = {},
) {
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
                    is CarePlanEvent.ShowMessage -> {
                        snackbarHost(
                            event.message,
                        )
                    }
                }
            }
    }

    CarePlanScreen(
        state = state,
        onRetry =
            viewModel::retry,
        onAddMedication =
            onAddMedication,
        onAddSchedule =
            onAddSchedule,
        onEditMedicationText =
            onEditMedicationText,
        onEditSchedule =
            onEditSchedule,
        onStopMedication =
            viewModel::requestStopMedication,
        onArchiveMedication =
            viewModel::requestArchiveMedication,
        onConfirmMedicationAction =
            viewModel::confirmMedicationAction,
        onDismissMedicationAction =
            viewModel::dismissMedicationAction,
    )
}

@Composable
private fun CarePlanScreen(
    state: CarePlanUiState,
    onRetry: () -> Unit,
    onAddMedication:
        (String) -> Unit,
    onAddSchedule:
        (String) -> Unit,
    onEditMedicationText:
        (String) -> Unit,
    onEditSchedule:
        (String) -> Unit,
    onStopMedication:
        (String, String) -> Unit,
    onArchiveMedication:
        (String, String) -> Unit,
    onConfirmMedicationAction: () -> Unit,
    onDismissMedicationAction: () -> Unit,
) {
    val overview =
        state.overview

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .testTag(
                    "care_plan_screen",
                ),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = 24.dp,
                    vertical = 16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            item {
                Text(
                    text =
                        stringResource(
                            R.string.care_plan_title,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,
                    modifier =
                        Modifier
                            .carePackHeading()
                            .testTag(
                                "care_plan_title",
                            ),
                )
            }

            when {
                state.isLoading -> {
                    item {
                        Text(
                            text =
                                stringResource(
                                    R.string.loading,
                                ),
                            modifier =
                                Modifier
                                    .carePackPoliteLiveRegion()
                                    .testTag(
                                        "care_plan_loading",
                                    ),
                        )
                    }
                }

                state.errorMessage != null -> {
                    item {
                        ErrorCard(
                            message =
                                state.errorMessage,
                            onRetry =
                                onRetry,
                        )
                    }
                }

                overview == null -> {
                    item {
                        EmptyCarePlanCard()
                    }
                }

                overview.medications.isEmpty() -> {
                    item {
                        EmptyCarePlanCard()
                    }
                }

                else -> {
                    item {
                        AddMedicationButton(
                            recipientId =
                                overview.recipientId,
                            onAddMedication =
                                onAddMedication,
                        )
                    }

                    items(
                        items =
                            overview.medications,
                        key = {
                            it.medicationId
                        },
                    ) { medication ->
                        MedicationCard(
                            medication =
                                medication,
                            onAddSchedule =
                                onAddSchedule,
                            onEditMedicationText =
                                onEditMedicationText,
                            onEditSchedule =
                                onEditSchedule,
                            onStopMedication =
                                onStopMedication,
                            onArchiveMedication =
                                onArchiveMedication,
                        )
                    }
                }
            }
        }
    }

    state
        .pendingMedicationAction
        ?.let { action ->
            ConfirmMedicationActionDialog(
                action =
                    action,
                inProgress =
                    state.actionInProgress,
                onConfirm =
                    onConfirmMedicationAction,
                onDismiss =
                    onDismissMedicationAction,
            )
        }
}

@Composable
private fun MedicationCard(
    medication: MedicationPlanItem,
    onAddSchedule:
        (String) -> Unit,
    onEditMedicationText:
        (String) -> Unit,
    onEditSchedule:
        (String) -> Unit,
    onStopMedication:
        (String, String) -> Unit,
    onArchiveMedication:
        (String, String) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "medication_card_${medication.medicationId}",
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
                    medication.name,
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                modifier =
                    Modifier.testTag(
                        "medication_name_${medication.medicationId}",
                    ),
            )

            Text(
                text =
                    medication.instruction,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                modifier =
                    Modifier.testTag(
                        "medication_instruction_${medication.medicationId}",
                    ),
            )

            Text(
                text =
                    medicationStatusText(
                        medication.status,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .labelLarge,
                modifier =
                    Modifier.testTag(
                        "medication_status_${medication.medicationId}",
                    ),
            )

            if (
                medication
                    .schedules
                    .isEmpty()
            ) {
                Text(
                    text =
                        "برنامه فعالی برای این دارو وجود ندارد.",
                    modifier =
                        Modifier.testTag(
                            "no_active_schedules_${medication.medicationId}",
                        ),
                )
            } else {
                medication
                    .schedules
                    .forEachIndexed {
                            index,
                            schedule ->
                        SchedulePlanCard(
                            schedule =
                                schedule,
                            scheduleNumber =
                                index + 1,
                            enabled =
                                medication.status ==
                                        MedicationStatus.ACTIVE,
                            onEditSchedule =
                                onEditSchedule,
                        )
                    }
            }

            if (
                medication.status ==
                MedicationStatus.ACTIVE
            ) {
                OutlinedButton(
                    onClick = {
                        onAddSchedule(
                            medication.medicationId,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                "add_schedule_${medication.medicationId}",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.add_schedule,
                            ),
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    onEditMedicationText(
                        medication.medicationId,
                    )
                },
                enabled =
                    medication.status ==
                            MedicationStatus.ACTIVE,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "edit_medication_${medication.medicationId}",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.edit_medication_text,
                        ),
                )
            }

            OutlinedButton(
                onClick = {
                    onStopMedication(
                        medication.medicationId,
                        medication.name,
                    )
                },
                enabled =
                    medication.status ==
                            MedicationStatus.ACTIVE,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "stop_medication_${medication.medicationId}",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.stop_medication,
                        ),
                )
            }

            OutlinedButton(
                onClick = {
                    onArchiveMedication(
                        medication.medicationId,
                        medication.name,
                    )
                },
                enabled =
                    medication.status ==
                            MedicationStatus.STOPPED,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "archive_medication_${medication.medicationId}",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.archive_medication,
                        ),
                )
            }
        }
    }
}

@Composable
private fun SchedulePlanCard(
    schedule: SchedulePlan,
    scheduleNumber: Int,
    enabled: Boolean,
    onEditSchedule:
        (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "schedule_card_${schedule.scheduleSeriesId}",
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp,
            ),
    ) {
        Text(
            text =
                stringResource(
                    R.string.schedule_card_title,
                    scheduleNumber,
                ),
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            modifier =
                Modifier.testTag(
                    "schedule_card_title_${schedule.scheduleSeriesId}",
                ),
        )

        val weekdayNames =
            schedule
                .weekdays
                .sortedBy {
                    it.value
                }
                .map { dayOfWeek ->
                    stringResource(
                        weekdayPersianNameResource(
                            dayOfWeek,
                        ),
                    )
                }
                .joinToString(
                    separator = "، ",
                )

        val timeNames =
            schedule
                .times
                .joinToString(
                    separator = "، ",
                ) { time ->
                    time.format(
                        TIME_FORMATTER,
                    )
                }

        val startDateText =
            schedule
                .startDate
                ?.toJalaliDateText()
                ?: stringResource(
                    R.string.open_ended,
                )

        val endDateText =
            schedule
                .endDate
                ?.toJalaliDateText()
                ?: stringResource(
                    R.string.open_ended,
                )

        ScheduleSummaryLine(
            label =
                stringResource(
                    R.string.schedule_weekdays,
                ),
            value =
                weekdayNames,
        )

        ScheduleSummaryLine(
            label =
                stringResource(
                    R.string.schedule_times,
                ),
            value =
                timeNames,
        )

        when (
            val pattern =
                schedule.schedulePattern
        ) {
            is FixedTimeSchedule -> {
                Unit
            }

            is IntervalSchedule -> {
                ScheduleSummaryLine(
                    label =
                        stringResource(
                            R.string.schedule_pattern,
                        ),
                    value =
                        stringResource(
                            R.string.schedule_interval_hours,
                            pattern.intervalHours,
                        ),
                )
            }
        }

        ScheduleSummaryLine(
            label =
                stringResource(
                    R.string.schedule_dates,
                ),
            value =
                "شروع: $startDateText، پایان: $endDateText",
        )

        ScheduleSummaryLine(
            label =
                stringResource(
                    R.string.schedule_zone,
                ),
            value =
                schedule.zoneId,
            ltr =
                true,
        )

        OutlinedButton(
            onClick = {
                onEditSchedule(
                    schedule.scheduleSeriesId,
                )
            },
            enabled =
                enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "edit_schedule_${schedule.scheduleSeriesId}",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.edit_schedule,
                    ),
            )
        }

        HorizontalDivider()
    }
}

@Composable
private fun ScheduleSummaryLine(
    label: String,
    value: String,
    ltr: Boolean = false,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                2.dp,
            ),
    ) {
        Text(
            text =
                label,
            style =
                MaterialTheme
                    .typography
                    .labelMedium,
        )

        Text(
            text =
                value,
            style =
                MaterialTheme
                    .typography
                    .bodyMedium
                    .copy(
                        textDirection =
                            if (ltr) {
                                TextDirection.Ltr
                            } else {
                                TextDirection.Content
                            },
                    ),
        )
    }
}

@Composable
private fun AddMedicationButton(
    recipientId: String,
    onAddMedication:
        (String) -> Unit,
) {
    Button(
        onClick = {
            onAddMedication(
                recipientId,
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "add_medication",
                ),
    ) {
        Text(
            text =
                stringResource(
                    R.string.add_medication,
                ),
        )
    }
}

@Composable
private fun EmptyCarePlanCard() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "care_plan_empty",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.no_medications,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(
                    "care_plan_error",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    message,
                color =
                    MaterialTheme
                        .colorScheme
                        .error,
            )

            Button(
                onClick = onRetry,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.retry,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ConfirmMedicationActionDialog(
    action: PendingMedicationAction,
    inProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title =
        when (action) {
            is PendingMedicationAction.Stop ->
                stringResource(
                    R.string.confirm_stop_title,
                )

            is PendingMedicationAction.Archive ->
                stringResource(
                    R.string.confirm_archive_title,
                )
        }

    val text =
        when (action) {
            is PendingMedicationAction.Stop ->
                stringResource(
                    R.string.confirm_stop_body,
                    action.medicationName,
                )

            is PendingMedicationAction.Archive ->
                stringResource(
                    R.string.confirm_archive_body,
                    action.medicationName,
                )
        }

    AlertDialog(
        onDismissRequest =
            onDismiss,
        title = {
            Text(
                text = title,
            )
        },
        text = {
            Text(
                text = text,
            )
        },
        confirmButton = {
            TextButton(
                onClick =
                    onConfirm,
                enabled =
                    !inProgress,
            ) {
                Text(
                    text =
                        "تأیید",
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick =
                    onDismiss,
                enabled =
                    !inProgress,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.cancel,
                        ),
                )
            }
        },
    )
}

@Composable
private fun medicationStatusText(
    status: MedicationStatus,
): String =
    when (status) {
        MedicationStatus.ACTIVE ->
            stringResource(
                R.string.status_active,
            )

        MedicationStatus.STOPPED ->
            stringResource(
                R.string.status_stopped,
            )
    }

private val TIME_FORMATTER =
    DateTimeFormatter.ofPattern(
        "HH:mm",
        Locale.ROOT,
    )
