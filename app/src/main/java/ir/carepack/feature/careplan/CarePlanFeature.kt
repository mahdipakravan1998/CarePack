package ir.carepack.feature.careplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CarePlanUiState(
    val isLoading: Boolean = true,
    val overview: CarePlanOverview? = null,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
)

class CarePlanViewModel(
    private val carePlanService: CarePlanService,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        CarePlanUiState(),
    )

    val state = mutableState

    init {
        carePlanService
            .observeCarePlan()
            .onEach { overview ->
                mutableState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        overview = overview,
                        errorMessage = null,
                    )
                }
            }
            .catch {
                mutableState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        errorMessage = "خواندن برنامه مراقبت انجام نشد.",
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun updateRecipientName(displayName: String) {
        val recipientId = mutableState.value.overview?.recipientId ?: return

        runOperation {
            when (
                val outcome = carePlanService.updateRecipientName(
                    UpdateRecipientNameCommand(
                        recipientId = recipientId,
                        displayName = displayName,
                    ),
                )
            ) {
                UpdateRecipientNameOutcome.Updated,
                UpdateRecipientNameOutcome.Unchanged,
                    -> Unit

                UpdateRecipientNameOutcome.NotFound -> {
                    showError("فرد تحت مراقبت پیدا نشد.")
                }

                is UpdateRecipientNameOutcome.Invalid -> {
                    showError(
                        outcome.errors.firstOrNull()?.message
                            ?: "نام واردشده معتبر نیست.",
                    )
                }
            }
        }
    }

    fun stopMedication(medicationId: String) {
        runOperation {
            when (carePlanService.stopMedication(medicationId)) {
                StopMedicationOutcome.Stopped,
                StopMedicationOutcome.AlreadyStopped,
                    -> Unit

                StopMedicationOutcome.NotFound -> {
                    showError("دارو پیدا نشد.")
                }
            }
        }
    }

    fun archiveMedication(medicationId: String) {
        runOperation {
            when (carePlanService.archiveMedication(medicationId)) {
                ArchiveMedicationOutcome.Archived,
                ArchiveMedicationOutcome.AlreadyArchived,
                    -> Unit

                ArchiveMedicationOutcome.MustStopFirst -> {
                    showError("پیش از بایگانی، دارو را متوقف کنید.")
                }

                ArchiveMedicationOutcome.NotFound -> {
                    showError("دارو پیدا نشد.")
                }
            }
        }
    }

    private fun runOperation(operation: suspend () -> Unit) {
        if (mutableState.value.isWorking) {
            return
        }

        viewModelScope.launch {
            mutableState.update { currentState ->
                currentState.copy(
                    isWorking = true,
                    errorMessage = null,
                )
            }

            try {
                operation()
            } catch (_: Exception) {
                showError("انجام عملیات ممکن نشد. دوباره تلاش کنید.")
            } finally {
                mutableState.update { currentState ->
                    currentState.copy(isWorking = false)
                }
            }
        }
    }

    private fun showError(message: String) {
        mutableState.update { currentState ->
            currentState.copy(errorMessage = message)
        }
    }

    companion object {
        fun factory(
            carePlanService: CarePlanService,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CarePlanViewModel(
                    carePlanService = carePlanService,
                )
            }
        }
    }
}

@Composable
fun CarePlanRoute(
    viewModel: CarePlanViewModel,
    onBack: () -> Unit,
    onAddMedication: (String) -> Unit,
    onEditMedicationText: (String) -> Unit,
    onEditSchedule: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CarePlanScreen(
        state = state,
        onBack = onBack,
        onAddMedication = onAddMedication,
        onEditMedicationText = onEditMedicationText,
        onEditSchedule = onEditSchedule,
        onUpdateRecipientName = viewModel::updateRecipientName,
        onStopMedication = viewModel::stopMedication,
        onArchiveMedication = viewModel::archiveMedication,
    )
}

@Composable
fun CarePlanScreen(
    state: CarePlanUiState,
    onBack: () -> Unit,
    onAddMedication: (String) -> Unit,
    onEditMedicationText: (String) -> Unit,
    onEditSchedule: (String) -> Unit,
    onUpdateRecipientName: (String) -> Unit,
    onStopMedication: (String) -> Unit,
    onArchiveMedication: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialogState = remember {
        mutableStateOf<CarePlanDialogState?>(null)
    }

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }

            Text(
                text = stringResource(R.string.care_plan_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(Modifier.height(20.dp))

            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                }

                state.overview == null -> {
                    Text("برنامه مراقبت پیدا نشد.")
                }

                else -> {
                    val overview = state.overview

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(R.string.recipient_title),
                                style = MaterialTheme.typography.labelLarge,
                            )

                            Text(
                                text = overview.recipientDisplayName,
                                style = MaterialTheme.typography.titleLarge,
                            )

                            TextButton(
                                onClick = {
                                    dialogState.value =
                                        CarePlanDialogState.RenameRecipient(
                                            draft = overview.recipientDisplayName,
                                            errorMessage = null,
                                        )
                                },
                                enabled = !state.isWorking,
                                modifier = Modifier.testTag("edit_recipient_button"),
                            ) {
                                Text(stringResource(R.string.edit_recipient))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            onAddMedication(overview.recipientId)
                        },
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.add_medication))
                    }

                    state.errorMessage?.let { error ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    if (overview.medications.isEmpty()) {
                        Text(stringResource(R.string.no_medications))
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = overview.medications,
                                key = { it.medicationId },
                            ) { medication ->
                                MedicationPlanCard(
                                    medication = medication,
                                    enabled = !state.isWorking,
                                    onEditText = {
                                        onEditMedicationText(medication.medicationId)
                                    },
                                    onEditSchedule = {
                                        onEditSchedule(medication.medicationId)
                                    },
                                    onStop = {
                                        dialogState.value =
                                            CarePlanDialogState.StopMedication(
                                                medication = medication,
                                            )
                                    },
                                    onArchive = {
                                        dialogState.value =
                                            CarePlanDialogState.ArchiveMedication(
                                                medication = medication,
                                            )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (val currentDialog = dialogState.value) {
        is CarePlanDialogState.RenameRecipient -> {
            AlertDialog(
                onDismissRequest = {
                    dialogState.value = null
                },
                title = {
                    Text(stringResource(R.string.edit_recipient))
                },
                text = {
                    OutlinedTextField(
                        value = currentDialog.draft,
                        onValueChange = { value ->
                            val updatedError =
                                if (currentDialog.errorMessage != null) {
                                    recipientNameError(value)
                                } else {
                                    null
                                }

                            dialogState.value = currentDialog.copy(
                                draft = value,
                                errorMessage = updatedError,
                            )
                        },
                        label = {
                            Text(stringResource(R.string.recipient_name_label))
                        },
                        singleLine = true,
                        isError = currentDialog.errorMessage != null,
                        supportingText = {
                            currentDialog.errorMessage?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("recipient_rename_error"),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recipient_rename_field"),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val validation = CarePlanValidation.validateRecipientName(
                                currentDialog.draft,
                            )
                            val validationError = validation.errorsOrEmpty()
                                .firstOrNull()
                                ?.message

                            if (validationError != null) {
                                dialogState.value = currentDialog.copy(
                                    errorMessage = validationError,
                                )
                            } else {
                                onUpdateRecipientName(
                                    checkNotNull(validation.valueOrNull()),
                                )
                                dialogState.value = null
                            }
                        },
                        enabled = !state.isWorking,
                        modifier = Modifier.testTag("recipient_rename_save"),
                    ) {
                        Text(stringResource(R.string.save_changes))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            dialogState.value = null
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        is CarePlanDialogState.StopMedication -> {
            val medication = currentDialog.medication
            MedicationConfirmationDialog(
                title = stringResource(R.string.confirm_stop_title),
                body = stringResource(
                    R.string.confirm_stop_body,
                    medication.name,
                ),
                confirmLabel = stringResource(R.string.stop_medication),
                confirmTestTag = "confirm_stop_${medication.medicationId}",
                onConfirm = {
                    onStopMedication(medication.medicationId)
                    dialogState.value = null
                },
                onDismiss = {
                    dialogState.value = null
                },
            )
        }

        is CarePlanDialogState.ArchiveMedication -> {
            val medication = currentDialog.medication
            MedicationConfirmationDialog(
                title = stringResource(R.string.confirm_archive_title),
                body = stringResource(
                    R.string.confirm_archive_body,
                    medication.name,
                ),
                confirmLabel = stringResource(R.string.archive_medication),
                confirmTestTag = "confirm_archive_${medication.medicationId}",
                onConfirm = {
                    onArchiveMedication(medication.medicationId)
                    dialogState.value = null
                },
                onDismiss = {
                    dialogState.value = null
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
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            Text(body)
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(confirmTestTag),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun MedicationPlanCard(
    medication: MedicationPlanItem,
    enabled: Boolean,
    onEditText: () -> Unit,
    onEditSchedule: () -> Unit,
    onStop: () -> Unit,
    onArchive: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("medication_card_${medication.medicationId}"),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = medication.name,
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(6.dp))

            Text(medication.instruction)

            Spacer(Modifier.height(10.dp))

            Text(
                text = when (medication.status) {
                    MedicationStatus.ACTIVE -> {
                        stringResource(R.string.status_active)
                    }

                    MedicationStatus.STOPPED -> {
                        stringResource(R.string.status_stopped)
                    }
                },
                color = when (medication.status) {
                    MedicationStatus.ACTIVE -> {
                        MaterialTheme.colorScheme.primary
                    }

                    MedicationStatus.STOPPED -> {
                        MaterialTheme.colorScheme.secondary
                    }
                },
            )

            medication.schedule?.let { schedule ->
                val weekdayNames = schedule.weekdays
                    .sortedBy { it.value }
                    .map { dayOfWeek ->
                        stringResource(
                            weekdayPersianNameResource(dayOfWeek),
                        )
                    }
                    .joinToString(separator = "، ")

                val timeNames = schedule.times.joinToString(
                    separator = "، ",
                ) { time ->
                    time.format(TIME_FORMATTER)
                }

                val startDateText = schedule.startDate?.toString()
                    ?: stringResource(R.string.open_ended)

                val endDateText = schedule.endDate?.toString()
                    ?: stringResource(R.string.open_ended)

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.schedule_weekdays) +
                            " " +
                            weekdayNames,
                )

                Text(
                    text = stringResource(R.string.schedule_times) +
                            " " +
                            timeNames,
                )

                Text(
                    text = stringResource(R.string.schedule_zone) +
                            " " +
                            schedule.zoneId,
                )

                Text(
                    text = stringResource(R.string.schedule_dates) +
                            " " +
                            startDateText +
                            " — " +
                            endDateText,
                )
            }

            Spacer(Modifier.height(14.dp))

            if (medication.status == MedicationStatus.ACTIVE) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = onEditText,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.edit_medication_text))
                    }

                    OutlinedButton(
                        onClick = onEditSchedule,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.edit_schedule))
                    }
                }

                TextButton(
                    onClick = onStop,
                    enabled = enabled,
                    modifier = Modifier.testTag(
                        "stop_medication_${medication.medicationId}",
                    ),
                ) {
                    Text(stringResource(R.string.stop_medication))
                }
            } else {
                TextButton(
                    onClick = onArchive,
                    enabled = enabled,
                    modifier = Modifier.testTag(
                        "archive_medication_${medication.medicationId}",
                    ),
                ) {
                    Text(stringResource(R.string.archive_medication))
                }
            }
        }
    }
}

private sealed interface CarePlanDialogState {
    data class RenameRecipient(
        val draft: String,
        val errorMessage: String?,
    ) : CarePlanDialogState

    data class StopMedication(
        val medication: MedicationPlanItem,
    ) : CarePlanDialogState

    data class ArchiveMedication(
        val medication: MedicationPlanItem,
    ) : CarePlanDialogState
}

private fun recipientNameError(value: String): String? =
    CarePlanValidation.validateRecipientName(value)
        .errorsOrEmpty()
        .firstOrNull()
        ?.message

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
