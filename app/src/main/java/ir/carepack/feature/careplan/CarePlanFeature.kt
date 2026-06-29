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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import ir.carepack.domain.careplan.CarePlanValidation
import ir.carepack.domain.careplan.MedicationPlanItem
import ir.carepack.domain.careplan.StopMedicationOutcome
import ir.carepack.domain.careplan.UpdateRecipientNameCommand
import ir.carepack.domain.careplan.UpdateRecipientNameOutcome
import ir.carepack.domain.careplan.errorsOrEmpty
import ir.carepack.domain.careplan.valueOrNull
import ir.carepack.domain.model.MedicationStatus
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CarePlanUiState(
    val isLoading: Boolean = true,
    val overview:
    CarePlanOverview? = null,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
)

class CarePlanViewModel(
    private val carePlanService:
    CarePlanService,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            CarePlanUiState(),
        )

    val state =
        mutableState

    init {
        carePlanService
            .observeCarePlan()
            .onEach { overview ->
                mutableState.update {
                        currentState ->
                    currentState.copy(
                        isLoading = false,
                        overview = overview,
                        errorMessage = null,
                    )
                }
            }
            .catch {
                mutableState.update {
                        currentState ->
                    currentState.copy(
                        isLoading = false,
                        errorMessage =
                            "خواندن برنامه مراقبت انجام نشد.",
                    )
                }
            }
            .launchIn(
                viewModelScope,
            )
    }

    fun updateRecipientName(
        displayName: String,
    ) {
        val recipientId =
            mutableState
                .value
                .overview
                ?.recipientId
                ?: return

        runOperation {
            when (
                val outcome =
                    carePlanService
                        .updateRecipientName(
                            UpdateRecipientNameCommand(
                                recipientId =
                                    recipientId,
                                displayName =
                                    displayName,
                            ),
                        )
            ) {
                UpdateRecipientNameOutcome
                    .Updated,
                UpdateRecipientNameOutcome
                    .Unchanged,
                    -> Unit

                UpdateRecipientNameOutcome
                    .NotFound -> {
                    showError(
                        "فرد تحت مراقبت پیدا نشد.",
                    )
                }

                is UpdateRecipientNameOutcome
                .Invalid -> {
                    showError(
                        outcome
                            .errors
                            .firstOrNull()
                            ?.message
                            ?: "نام واردشده معتبر نیست.",
                    )
                }
            }
        }
    }

    fun stopMedication(
        medicationId: String,
    ) {
        runOperation {
            when (
                carePlanService
                    .stopMedication(
                        medicationId,
                    )
            ) {
                StopMedicationOutcome
                    .Stopped,
                StopMedicationOutcome
                    .AlreadyStopped,
                    -> Unit

                StopMedicationOutcome
                    .NotFound -> {
                    showError(
                        "دارو پیدا نشد.",
                    )
                }
            }
        }
    }

    fun archiveMedication(
        medicationId: String,
    ) {
        runOperation {
            when (
                carePlanService
                    .archiveMedication(
                        medicationId,
                    )
            ) {
                ArchiveMedicationOutcome
                    .Archived,
                ArchiveMedicationOutcome
                    .AlreadyArchived,
                    -> Unit

                ArchiveMedicationOutcome
                    .MustStopFirst -> {
                    showError(
                        "پیش از بایگانی، دارو را متوقف کنید.",
                    )
                }

                ArchiveMedicationOutcome
                    .NotFound -> {
                    showError(
                        "دارو پیدا نشد.",
                    )
                }
            }
        }
    }

    private fun runOperation(
        operation:
        suspend () -> Unit,
    ) {
        if (
            mutableState
                .value
                .isWorking
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                    currentState ->
                currentState.copy(
                    isWorking = true,
                    errorMessage = null,
                )
            }

            try {
                operation()
            } catch (_: Exception) {
                showError(
                    "انجام عملیات ممکن نشد. دوباره تلاش کنید.",
                )
            } finally {
                mutableState.update {
                        currentState ->
                    currentState.copy(
                        isWorking = false,
                    )
                }
            }
        }
    }

    private fun showError(
        message: String,
    ) {
        mutableState.update {
                currentState ->
            currentState.copy(
                errorMessage = message,
            )
        }
    }

    companion object {

        fun factory(
            carePlanService:
            CarePlanService,
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
    viewModel:
    CarePlanViewModel,
    onBack: () -> Unit,
    onAddMedication:
        (String) -> Unit,
    onEditMedicationText:
        (String) -> Unit,
    onEditSchedule:
        (String) -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    CarePlanScreen(
        state = state,
        onBack = onBack,
        onAddMedication =
            onAddMedication,
        onEditMedicationText =
            onEditMedicationText,
        onEditSchedule =
            onEditSchedule,
        onUpdateRecipientName =
            viewModel::updateRecipientName,
        onStopMedication =
            viewModel::stopMedication,
        onArchiveMedication =
            viewModel::archiveMedication,
    )
}

@Composable
fun CarePlanScreen(
    state: CarePlanUiState,
    onBack: () -> Unit,
    onAddMedication:
        (String) -> Unit,
    onEditMedicationText:
        (String) -> Unit,
    onEditSchedule:
        (String) -> Unit,
    onUpdateRecipientName:
        (String) -> Unit,
    onStopMedication:
        (String) -> Unit,
    onArchiveMedication:
        (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogState =
        remember {
            mutableStateOf<
                    CarePlanDialogState?
                    >(null)
        }

    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "care_plan_screen",
                ),
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        contentPadding,
                    )
                    .navigationBarsPadding(),
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
            item(
                key = "care-plan-back",
            ) {
                TextButton(
                    onClick = onBack,
                    enabled =
                        !state.isWorking,
                    modifier =
                        Modifier.testTag(
                            "care_plan_back",
                        ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.back,
                            ),
                    )
                }
            }

            item(
                key = "care-plan-title",
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .care_plan_title,
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
                    item(
                        key = "care-plan-loading",
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .carePackPoliteLiveRegion()
                                    .testTag(
                                        "care_plan_loading",
                                    ),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                state.overview == null -> {
                    item(
                        key = "care-plan-not-found",
                    ) {
                        Text(
                            text =
                                "برنامه مراقبت پیدا نشد.",
                            modifier =
                                Modifier
                                    .carePackPoliteLiveRegion()
                                    .testTag(
                                        "care_plan_not_found",
                                    ),
                        )
                    }
                }

                else -> {
                    val overview =
                        checkNotNull(
                            state.overview,
                        )

                    item(
                        key = "care-recipient",
                    ) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        "care_recipient_card",
                                    ),
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(
                                        20.dp,
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
                                                .recipient_title,
                                        ),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelLarge,
                                )

                                Text(
                                    text =
                                        overview
                                            .recipientDisplayName,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge,
                                    modifier =
                                        Modifier
                                            .carePackHeading(),
                                )

                                TextButton(
                                    onClick = {
                                        dialogState.value =
                                            CarePlanDialogState
                                                .RenameRecipient(
                                                    draft =
                                                        overview
                                                            .recipientDisplayName,
                                                    errorMessage =
                                                        null,
                                                )
                                    },
                                    enabled =
                                        !state.isWorking,
                                    modifier =
                                        Modifier.testTag(
                                            "edit_recipient_button",
                                        ),
                                ) {
                                    Text(
                                        text =
                                            stringResource(
                                                R.string
                                                    .edit_recipient,
                                            ),
                                    )
                                }
                            }
                        }
                    }

                    item(
                        key = "add-medication",
                    ) {
                        Button(
                            onClick = {
                                onAddMedication(
                                    overview
                                        .recipientId,
                                )
                            },
                            enabled =
                                !state.isWorking,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        "add_medication_button",
                                    ),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string
                                            .add_medication,
                                    ),
                            )
                        }
                    }

                    state.errorMessage
                        ?.let { error ->
                            item(
                                key = "care-plan-error",
                            ) {
                                Text(
                                    text =
                                        error,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .error,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .carePackPoliteLiveRegion()
                                            .testTag(
                                                "care_plan_error",
                                            ),
                                )
                            }
                        }

                    if (
                        overview
                            .medications
                            .isEmpty()
                    ) {
                        item(
                            key = "no-medications",
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string
                                            .no_medications,
                                    ),
                                modifier =
                                    Modifier.testTag(
                                        "care_plan_no_medications",
                                    ),
                            )
                        }
                    } else {
                        items(
                            items =
                                overview.medications,
                            key = {
                                    medication ->
                                medication.medicationId
                            },
                        ) { medication ->
                            MedicationPlanCard(
                                medication =
                                    medication,
                                enabled =
                                    !state.isWorking,
                                onEditText = {
                                    onEditMedicationText(
                                        medication
                                            .medicationId,
                                    )
                                },
                                onEditSchedule = {
                                    onEditSchedule(
                                        medication
                                            .medicationId,
                                    )
                                },
                                onStop = {
                                    dialogState.value =
                                        CarePlanDialogState
                                            .StopMedication(
                                                medication =
                                                    medication,
                                            )
                                },
                                onArchive = {
                                    dialogState.value =
                                        CarePlanDialogState
                                            .ArchiveMedication(
                                                medication =
                                                    medication,
                                            )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    when (
        val currentDialog =
            dialogState.value
    ) {
        is CarePlanDialogState
        .RenameRecipient -> {
            AlertDialog(
                onDismissRequest = {
                    if (!state.isWorking) {
                        dialogState.value =
                            null
                    }
                },
                title = {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .edit_recipient,
                            ),
                        modifier =
                            Modifier
                                .carePackHeading(),
                    )
                },
                text = {
                    OutlinedTextField(
                        value =
                            currentDialog.draft,
                        onValueChange = {
                                value ->
                            val updatedError =
                                if (
                                    currentDialog
                                        .errorMessage !=
                                    null
                                ) {
                                    recipientNameError(
                                        value,
                                    )
                                } else {
                                    null
                                }

                            dialogState.value =
                                currentDialog.copy(
                                    draft = value,
                                    errorMessage =
                                        updatedError,
                                )
                        },
                        enabled =
                            !state.isWorking,
                        label = {
                            Text(
                                text =
                                    stringResource(
                                        R.string
                                            .recipient_name_label,
                                    ),
                            )
                        },
                        singleLine = true,
                        isError =
                            currentDialog
                                .errorMessage !=
                                    null,
                        supportingText = {
                            currentDialog
                                .errorMessage
                                ?.let { error ->
                                    Text(
                                        text =
                                            error,
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .error,
                                        modifier =
                                            Modifier
                                                .carePackPoliteLiveRegion()
                                                .testTag(
                                                    "recipient_rename_error",
                                                ),
                                    )
                                }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "recipient_rename_field",
                                ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val validation =
                                CarePlanValidation
                                    .validateRecipientName(
                                        currentDialog
                                            .draft,
                                    )

                            val validationError =
                                validation
                                    .errorsOrEmpty()
                                    .firstOrNull()
                                    ?.message

                            if (
                                validationError !=
                                null
                            ) {
                                dialogState.value =
                                    currentDialog.copy(
                                        errorMessage =
                                            validationError,
                                    )
                            } else {
                                onUpdateRecipientName(
                                    checkNotNull(
                                        validation
                                            .valueOrNull(),
                                    ),
                                )

                                dialogState.value =
                                    null
                            }
                        },
                        enabled =
                            !state.isWorking,
                        modifier =
                            Modifier.testTag(
                                "recipient_rename_save",
                            ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .save_changes,
                                ),
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            dialogState.value =
                                null
                        },
                        enabled =
                            !state.isWorking,
                        modifier =
                            Modifier.testTag(
                                "recipient_rename_cancel",
                            ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.cancel,
                                ),
                        )
                    }
                },
                modifier =
                    Modifier.testTag(
                        "recipient_rename_dialog",
                    ),
            )
        }

        is CarePlanDialogState
        .StopMedication -> {
            val medication =
                currentDialog.medication

            MedicationConfirmationDialog(
                title =
                    stringResource(
                        R.string
                            .confirm_stop_title,
                    ),
                body =
                    stringResource(
                        R.string
                            .confirm_stop_body,
                        medication.name,
                    ),
                confirmLabel =
                    stringResource(
                        R.string
                            .stop_medication,
                    ),
                confirmTestTag =
                    "confirm_stop_${medication.medicationId}",
                enabled =
                    !state.isWorking,
                onConfirm = {
                    onStopMedication(
                        medication
                            .medicationId,
                    )

                    dialogState.value =
                        null
                },
                onDismiss = {
                    dialogState.value =
                        null
                },
            )
        }

        is CarePlanDialogState
        .ArchiveMedication -> {
            val medication =
                currentDialog.medication

            MedicationConfirmationDialog(
                title =
                    stringResource(
                        R.string
                            .confirm_archive_title,
                    ),
                body =
                    stringResource(
                        R.string
                            .confirm_archive_body,
                        medication.name,
                    ),
                confirmLabel =
                    stringResource(
                        R.string
                            .archive_medication,
                    ),
                confirmTestTag =
                    "confirm_archive_${medication.medicationId}",
                enabled =
                    !state.isWorking,
                onConfirm = {
                    onArchiveMedication(
                        medication
                            .medicationId,
                    )

                    dialogState.value =
                        null
                },
                onDismiss = {
                    dialogState.value =
                        null
                },
            )
        }

        null -> Unit
    }
}

@Composable
private fun MedicationConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmTestTag: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (enabled) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = title,
                modifier =
                    Modifier.carePackHeading(),
            )
        },
        text = {
            Text(
                text = body,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = enabled,
                modifier =
                    Modifier.testTag(
                        confirmTestTag,
                    ),
            ) {
                Text(
                    text =
                        confirmLabel,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = enabled,
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
private fun MedicationPlanCard(
    medication:
    MedicationPlanItem,
    enabled: Boolean,
    onEditText: () -> Unit,
    onEditSchedule: () -> Unit,
    onStop: () -> Unit,
    onArchive: () -> Unit,
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
                    20.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp,
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
                    Modifier.carePackHeading(),
            )

            Text(
                text =
                    medication.instruction,
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            val medicationStatusText =
                when (
                    medication.status
                ) {
                    MedicationStatus.ACTIVE -> {
                        stringResource(
                            R.string
                                .status_active,
                        )
                    }

                    MedicationStatus.STOPPED -> {
                        stringResource(
                            R.string
                                .status_stopped,
                        )
                    }
                }

            Text(
                text =
                    medicationStatusText,
                color =
                    when (
                        medication.status
                    ) {
                        MedicationStatus.ACTIVE -> {
                            MaterialTheme
                                .colorScheme
                                .primary
                        }

                        MedicationStatus.STOPPED -> {
                            MaterialTheme
                                .colorScheme
                                .secondary
                        }
                    },
                style =
                    MaterialTheme
                        .typography
                        .labelLarge,
                modifier =
                    Modifier.testTag(
                        "medication_status_${medication.medicationId}",
                    ),
            )

            medication.schedule?.let {
                    schedule ->
                val weekdayNames =
                    schedule
                        .weekdays
                        .sortedBy {
                            it.value
                        }
                        .map {
                                dayOfWeek ->
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
                        ?.toString()
                        ?: stringResource(
                            R.string.open_ended,
                        )

                val endDateText =
                    schedule
                        .endDate
                        ?.toString()
                        ?: stringResource(
                            R.string.open_ended,
                        )

                ScheduleSummaryLine(
                    label =
                        stringResource(
                            R.string
                                .schedule_weekdays,
                        ),
                    value =
                        weekdayNames,
                )

                ScheduleSummaryLine(
                    label =
                        stringResource(
                            R.string
                                .schedule_times,
                        ),
                    value =
                        timeNames,
                    forceLeftToRight =
                        true,
                )

                ScheduleSummaryLine(
                    label =
                        stringResource(
                            R.string
                                .schedule_zone,
                        ),
                    value =
                        schedule.zoneId,
                    forceLeftToRight =
                        true,
                )

                ScheduleSummaryLine(
                    label =
                        stringResource(
                            R.string
                                .schedule_dates,
                        ),
                    value =
                        "$startDateText — $endDateText",
                    forceLeftToRight =
                        true,
                )
            }

            if (
                medication.status ==
                MedicationStatus.ACTIVE
            ) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp,
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick =
                            onEditText,
                        enabled = enabled,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "edit_medication_text_${medication.medicationId}",
                                ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .edit_medication_text,
                                ),
                        )
                    }

                    OutlinedButton(
                        onClick =
                            onEditSchedule,
                        enabled = enabled,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "edit_schedule_${medication.medicationId}",
                                ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .edit_schedule,
                                ),
                        )
                    }
                }

                TextButton(
                    onClick = onStop,
                    enabled = enabled,
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
                                R.string
                                    .stop_medication,
                            ),
                    )
                }
            } else {
                TextButton(
                    onClick = onArchive,
                    enabled = enabled,
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
                                R.string
                                    .archive_medication,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleSummaryLine(
    label: String,
    value: String,
    forceLeftToRight: Boolean = false,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                2.dp,
            ),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme
                    .typography
                    .labelMedium,
        )

        Text(
            text = value,
            style =
                if (forceLeftToRight) {
                    MaterialTheme
                        .typography
                        .bodyMedium
                        .copy(
                            textDirection =
                                TextDirection.Ltr,
                        )
                } else {
                    MaterialTheme
                        .typography
                        .bodyMedium
                },
        )
    }
}

private sealed interface CarePlanDialogState {

    data class RenameRecipient(
        val draft: String,
        val errorMessage: String?,
    ) : CarePlanDialogState

    data class StopMedication(
        val medication:
        MedicationPlanItem,
    ) : CarePlanDialogState

    data class ArchiveMedication(
        val medication:
        MedicationPlanItem,
    ) : CarePlanDialogState
}

private fun recipientNameError(
    value: String,
): String? =
    CarePlanValidation
        .validateRecipientName(
            value,
        )
        .errorsOrEmpty()
        .firstOrNull()
        ?.message

private val TIME_FORMATTER =
    DateTimeFormatter.ofPattern(
        "HH:mm",
    )
