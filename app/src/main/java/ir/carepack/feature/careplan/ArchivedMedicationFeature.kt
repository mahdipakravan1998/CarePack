package ir.carepack.feature.careplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.carepack.R
import ir.carepack.domain.careplan.ArchivedMedication
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience
import ir.carepack.ui.viewmodel.carePackViewModelFactory
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArchivedMedicationListUiState(
    val isLoading: Boolean = true,
    val medications: List<ArchivedMedication> = emptyList(),
    val errorMessage: String? = null,
)

class ArchivedMedicationListViewModel(
    private val carePlanService: CarePlanService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ArchivedMedicationListUiState())
    val state = mutableState.asStateFlow()

    init {
        observeArchive()
    }

    fun retry() = observeArchive()

    private fun observeArchive() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, errorMessage = null)
            try {
                carePlanService.observeArchivedMedications().collect { medications ->
                    mutableState.value = ArchivedMedicationListUiState(
                        isLoading = false,
                        medications = medications,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableState.value = ArchivedMedicationListUiState(
                    isLoading = false,
                    errorMessage = "خواندن بایگانی انجام نشد.",
                )
            }
        }
    }

    companion object {
        fun factory(carePlanService: CarePlanService): ViewModelProvider.Factory =
            carePackViewModelFactory {
                ArchivedMedicationListViewModel(carePlanService)
            }
    }
}

data class ArchivedMedicationDetailUiState(
    val isLoading: Boolean = true,
    val medication: ArchivedMedication? = null,
    val errorMessage: String? = null,
)

class ArchivedMedicationDetailViewModel(
    private val medicationId: String,
    private val carePlanService: CarePlanService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ArchivedMedicationDetailUiState())
    val state = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            mutableState.value = ArchivedMedicationDetailUiState(isLoading = true)
            try {
                val medication = carePlanService.getArchivedMedication(medicationId)
                mutableState.value = if (medication == null) {
                    ArchivedMedicationDetailUiState(
                        isLoading = false,
                        errorMessage = "داروی بایگانی‌شده پیدا نشد.",
                    )
                } else {
                    ArchivedMedicationDetailUiState(
                        isLoading = false,
                        medication = medication,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableState.value = ArchivedMedicationDetailUiState(
                    isLoading = false,
                    errorMessage = "خواندن جزئیات بایگانی انجام نشد.",
                )
            }
        }
    }

    companion object {
        fun factory(
            medicationId: String,
            carePlanService: CarePlanService,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
            ArchivedMedicationDetailViewModel(medicationId, carePlanService)
        }
    }
}

@Composable
fun ArchivedMedicationListRoute(
    viewModel: ArchivedMedicationListViewModel,
    onBack: () -> Unit,
    onOpenMedication: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArchivedMedicationListScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenMedication = onOpenMedication,
    )
}

@Composable
fun ArchivedMedicationListScreen(
    state: ArchivedMedicationListUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenMedication: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val experience = carePackExperience()
    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding()
            .testTag("archived_medications_screen"),
        contentPadding = PaddingValues(
            horizontal = experience.screenHorizontalPadding,
            vertical = experience.screenVerticalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(experience.itemSpacing),
    ) {
        item {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.carePackInteractiveControl().testTag("archive_back"),
            ) {
                Text(stringResource(R.string.back))
            }
        }
        item {
            Text(
                text = stringResource(R.string.archived_medications_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.carePackHeading().testTag("archive_title"),
            )
        }
        when {
            state.isLoading -> item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.loading))
                }
            }
            state.errorMessage != null -> item {
                ArchiveError(message = state.errorMessage, onRetry = onRetry)
            }
            state.medications.isEmpty() -> item {
                Card(modifier = Modifier.fillMaxWidth().testTag("archive_empty")) {
                    Text(
                        text = stringResource(R.string.archived_medications_empty),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            else -> items(state.medications, key = ArchivedMedication::medicationId) { medication ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) {
                            onOpenMedication(medication.medicationId)
                        }
                        .testTag("archived_medication_${medication.medicationId}"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(medication.name, style = MaterialTheme.typography.titleLarge)
                        Text(medication.instruction, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(
                                R.string.archived_medication_archived_at,
                                medication.archivedAt.toDisplayDateTime(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArchivedMedicationDetailRoute(
    viewModel: ArchivedMedicationDetailViewModel,
    onBack: () -> Unit,
    onDeleteMedication: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArchivedMedicationDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onDeleteMedication = onDeleteMedication,
    )
}

@Composable
fun ArchivedMedicationDetailScreen(
    state: ArchivedMedicationDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDeleteMedication: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val experience = carePackExperience()
    Column(
        modifier = modifier.fillMaxSize().navigationBarsPadding()
            .padding(
                horizontal = experience.screenHorizontalPadding,
                vertical = experience.screenVerticalPadding,
            )
            .testTag("archived_medication_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(experience.itemSpacing),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.carePackInteractiveControl().testTag("archive_detail_back"),
        ) {
            Text(stringResource(R.string.back))
        }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.errorMessage != null -> ArchiveError(state.errorMessage, onRetry)
            state.medication != null -> {
                val medication = state.medication
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.carePackHeading().testTag("archive_detail_name"),
                )
                Text(medication.instruction, style = MaterialTheme.typography.bodyLarge)
                listOf(
                    medication.medicationType,
                    medication.dosageText,
                    medication.doseUnit,
                ).filter(String::isNotBlank).joinToString(" · ")
                    .takeIf(String::isNotBlank)?.let { Text(it) }
                Text(
                    stringResource(
                        R.string.archived_medication_ended_at,
                        medication.endedAt.toDisplayDateTime(),
                    ),
                )
                Text(
                    stringResource(
                        R.string.archived_medication_archived_at,
                        medication.archivedAt.toDisplayDateTime(),
                    ),
                )
                Text(
                    text = stringResource(R.string.archived_medication_read_only),
                    modifier = Modifier.carePackPoliteLiveRegion(),
                )
                Button(
                    onClick = { onDeleteMedication(medication.medicationId) },
                    modifier = Modifier.fillMaxWidth().carePackPrimaryAction()
                        .testTag("archive_delete_permanently"),
                ) {
                    Text(
                        text = stringResource(R.string.medication_deletion_entry_action),
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().carePackPoliteLiveRegion()
            .testTag("archive_error"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

private val ARCHIVE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd HH:mm",
    Locale.ROOT,
).withZone(ZoneId.systemDefault())

private fun java.time.Instant.toDisplayDateTime(): String =
    ARCHIVE_DATE_TIME_FORMATTER.format(this)