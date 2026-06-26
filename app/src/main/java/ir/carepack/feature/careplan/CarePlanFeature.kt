package ir.carepack.feature.careplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
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
import ir.carepack.domain.careplan.ArchiveMedicationOutcome
import ir.carepack.domain.careplan.CarePlanOverview
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.MedicationPlanItem
import ir.carepack.domain.careplan.StopMedicationOutcome
import ir.carepack.domain.careplan.UpdateRecipientNameCommand
import ir.carepack.domain.careplan.UpdateRecipientNameOutcome
import ir.carepack.domain.model.MedicationStatus
import java.time.DayOfWeek
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

    private val mutableState =
        MutableStateFlow(
            CarePlanUiState(),
        )

    val state = mutableState

    init {
        carePlanService
            .observeCarePlan()
            .onEach { overview ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        overview = overview,
                        errorMessage = null,
                    )
                }
            }
            .catch {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage =
                            "خواندن برنامه مراقبت انجام نشد.",
                    )
                }
            }
            .launchIn(viewModelScope)
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
                                recipientId = recipientId,
                                displayName = displayName,
                            ),
                        )
            ) {
                UpdateRecipientNameOutcome.Updated,
                UpdateRecipientNameOutcome.Unchanged,
                    -> Unit

                UpdateRecipientNameOutcome.NotFound -> {
                    showError(
                        "فرد تحت مراقبت پیدا نشد.",
                    )
                }

                is UpdateRecipientNameOutcome.Invalid -> {
                    showError(
                        outcome.errors
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
                carePlanService.stopMedication(
                    medicationId,
                )
            ) {
                StopMedicationOutcome.Stopped,
                StopMedicationOutcome.AlreadyStopped,
                    -> Unit

                StopMedicationOutcome.NotFound -> {
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
                carePlanService.archiveMedication(
                    medicationId,
                )
            ) {
                ArchiveMedicationOutcome.Archived,
                ArchiveMedicationOutcome.AlreadyArchived,
                    -> Unit

                ArchiveMedicationOutcome.MustStopFirst -> {
                    showError(
                        "پیش از بایگانی، دارو را متوقف کنید.",
                    )
                }

                ArchiveMedicationOutcome.NotFound -> {
                    showError(
                        "دارو پیدا نشد.",
                    )
                }
            }
        }
    }

    private fun runOperation(
        operation: suspend () -> Unit,
    ) {
        if (mutableState.value.isWorking) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
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
                    it.copy(
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
            it.copy(
                errorMessage = message,
            )
        }
    }

    companion object {
        fun factory(
            carePlanService: CarePlanService,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    CarePlanViewModel(
                        carePlanService =
                            carePlanService,
                    )
                }
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
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    CarePlanScreen(
        state = state,
        onBack = onBack,
        onAddMedication = onAddMedication,
        onEditMedicationText =
            onEditMedicationText,
        onEditSchedule = onEditSchedule,
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
    onAddMedication: (String) -> Unit,
    onEditMedicationText: (String) -> Unit,
    onEditSchedule: (String) -> Unit,
    onUpdateRecipientName: (String) -> Unit,
    onStopMedication: (String) -> Unit,
    onArchiveMedication: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameDialogVisible by
    remember {
        mutableStateOf(false)
    }

    var recipientNameDraft by
    remember {
        mutableStateOf("")
    }

    var medicationToStop by
    remember {
        mutableStateOf<MedicationPlanItem?>(
            null,
        )
    }

    var medicationToArchive by
    remember {
        mutableStateOf<MedicationPlanItem?>(
            null,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
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
                    R.string.care_plan_title,
                ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp),
            )

            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                }

                state.overview == null -> {
                    Text(
                        text =
                            "برنامه مراقبت پیدا نشد.",
                    )
                }

                else -> {
                    val overview =
                        state.overview

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(
                                    20.dp,
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
                            )

                            TextButton(
                                onClick = {
                                    recipientNameDraft =
                                        overview
                                            .recipientDisplayName

                                    renameDialogVisible =
                                        true
                                },
                                enabled =
                                    !state.isWorking,
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

                    Spacer(
                        modifier =
                            Modifier.height(16.dp),
                    )

                    Button(
                        onClick = {
                            onAddMedication(
                                overview.recipientId,
                            )
                        },
                        enabled =
                            !state.isWorking,
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .add_medication,
                                ),
                        )
                    }

                    state.errorMessage?.let { error ->
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
                            Modifier.height(16.dp),
                    )

                    if (
                        overview.medications.isEmpty()
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .no_medications,
                                ),
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    12.dp,
                                ),
                            modifier =
                                Modifier.fillMaxSize(),
                        ) {
                            items(
                                items =
                                    overview.medications,
                                key = {
                                    it.medicationId
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
                                        medicationToStop =
                                            medication
                                    },
                                    onArchive = {
                                        medicationToArchive =
                                            medication
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (renameDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                renameDialogVisible = false
            },
            title = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .edit_recipient,
                        ),
                )
            },
            text = {
                OutlinedTextField(
                    value = recipientNameDraft,
                    onValueChange = {
                        recipientNameDraft = it
                    },
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
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameDialogVisible =
                            false

                        onUpdateRecipientName(
                            recipientNameDraft,
                        )
                    },
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
                        renameDialogVisible =
                            false
                    },
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

    medicationToStop?.let { medication ->
        AlertDialog(
            onDismissRequest = {
                medicationToStop = null
            },
            title = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .confirm_stop_title,
                        ),
                )
            },
            text = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .confirm_stop_body,
                            medication.name,
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        medicationToStop = null

                        onStopMedication(
                            medication.medicationId,
                        )
                    },
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .stop_medication,
                            ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        medicationToStop = null
                    },
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

    medicationToArchive?.let { medication ->
        AlertDialog(
            onDismissRequest = {
                medicationToArchive = null
            },
            title = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .confirm_archive_title,
                        ),
                )
            },
            text = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .confirm_archive_body,
                            medication.name,
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        medicationToArchive = null

                        onArchiveMedication(
                            medication.medicationId,
                        )
                    },
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .archive_medication,
                            ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        medicationToArchive = null
                    },
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
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier.padding(20.dp),
        ) {
            Text(
                text = medication.name,
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp),
            )

            Text(
                text = medication.instruction,
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp),
            )

            Text(
                text =
                    when (medication.status) {
                        MedicationStatus.ACTIVE -> {
                            stringResource(
                                R.string.status_active,
                            )
                        }

                        MedicationStatus.STOPPED -> {
                            stringResource(
                                R.string.status_stopped,
                            )
                        }
                    },
                color =
                    when (medication.status) {
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
            )

            medication.schedule?.let { schedule ->
                val weekdayNames =
                    schedule
                        .weekdays
                        .sortedBy {
                            it.value
                        }
                        .joinToString(
                            separator = "، ",
                            transform =
                                ::weekdayPersianLabel,
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
                    schedule.startDate?.toString()
                        ?: stringResource(
                            R.string.open_ended,
                        )

                val endDateText =
                    schedule.endDate?.toString()
                        ?: stringResource(
                            R.string.open_ended,
                        )

                Spacer(
                    modifier =
                        Modifier.height(12.dp),
                )

                Text(
                    text =
                        stringResource(
                            R.string.schedule_weekdays,
                        ) +
                                " " +
                                weekdayNames,
                )

                Text(
                    text =
                        stringResource(
                            R.string.schedule_times,
                        ) +
                                " " +
                                timeNames,
                )

                Text(
                    text =
                        stringResource(
                            R.string.schedule_zone,
                        ) +
                                " " +
                                schedule.zoneId,
                )

                Text(
                    text =
                        stringResource(
                            R.string.schedule_dates,
                        ) +
                                " " +
                                startDateText +
                                " — " +
                                endDateText,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp),
            )

            if (
                medication.status ==
                MedicationStatus.ACTIVE
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp,
                        ),
                ) {
                    OutlinedButton(
                        onClick = onEditText,
                        enabled = enabled,
                        modifier =
                            Modifier.weight(1f),
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
                        onClick = onEditSchedule,
                        enabled = enabled,
                        modifier =
                            Modifier.weight(1f),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.edit_schedule,
                                ),
                        )
                    }
                }

                TextButton(
                    onClick = onStop,
                    enabled = enabled,
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.stop_medication,
                            ),
                    )
                }
            } else {
                TextButton(
                    onClick = onArchive,
                    enabled = enabled,
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

private fun weekdayPersianLabel(
    dayOfWeek: DayOfWeek,
): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "دوشنبه"
        DayOfWeek.TUESDAY -> "سه‌شنبه"
        DayOfWeek.WEDNESDAY -> "چهارشنبه"
        DayOfWeek.THURSDAY -> "پنجشنبه"
        DayOfWeek.FRIDAY -> "جمعه"
        DayOfWeek.SATURDAY -> "شنبه"
        DayOfWeek.SUNDAY -> "یکشنبه"
    }
}

private val TIME_FORMATTER =
    DateTimeFormatter.ofPattern(
        "HH:mm",
    )
