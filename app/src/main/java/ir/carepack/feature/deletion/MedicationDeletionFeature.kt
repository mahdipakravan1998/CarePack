package ir.carepack.feature.deletion

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.carepack.R
import ir.carepack.domain.calendar.toPersianDigits
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionPreview
import ir.carepack.settings.deletion.MedicationDeletionPreviewResult
import ir.carepack.settings.deletion.MedicationDeletionResult
import ir.carepack.settings.deletion.MedicationDeletionStage
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.experience.carePackExperience
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationDeletionUiState(
    val isLoading: Boolean = true,
    val preview: MedicationDeletionPreview? = null,
    val acknowledged: Boolean = false,
    val isDeleting: Boolean = false,
    val changedSincePreview: Boolean = false,
    val previewLoadFailed: Boolean = false,
    val deletionFailureStage: MedicationDeletionStage? = null,
    val databaseDeletedAfterFailure: Boolean = false,
    val medicationNotFound: Boolean = false,
    val deletionCompleted: Boolean = false,
) {
    val canDelete: Boolean
        get() = preview != null &&
                    acknowledged && !isLoading &&
                    !isDeleting && !medicationNotFound &&
                    !deletionCompleted
}

class MedicationDeletionViewModel(
    private val medicationId: String,
    private val coordinator: MedicationDeletionCoordinator,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
            MedicationDeletionUiState(),
        )

    val state = mutableState.asStateFlow()

    init {
        loadPreview()
    }

    fun loadPreview() {
        if (
            mutableState.value
                .isDeleting) {
            return
        }

        viewModelScope.launch {
            mutableState.update { current ->
                current.copy(
                    isLoading = true,
                    previewLoadFailed = false,
                    deletionFailureStage = null,
                    databaseDeletedAfterFailure = false,
                    medicationNotFound = false,
                )
            }

            val result = try {
                    coordinator.loadPreview(
                        medicationId = medicationId,
                    )
                } catch (
                    cancellationException: CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    MedicationDeletionPreviewResult.Failed()
                }

            when (result) {
                is MedicationDeletionPreviewResult.Available -> {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            preview = result.preview,
                            acknowledged = false,
                            changedSincePreview = false,
                            previewLoadFailed = false,
                            medicationNotFound = false,
                        )
                    }
                }

                MedicationDeletionPreviewResult.NotFound -> {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            preview = null,
                            acknowledged = false,
                            previewLoadFailed = false,
                            medicationNotFound = true,
                        )
                    }
                }

                is MedicationDeletionPreviewResult.Failed -> {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            previewLoadFailed = true,
                            medicationNotFound = false,
                        )
                    }
                }
            }
        }
    }

    fun setAcknowledged(
        acknowledged: Boolean,
    ) {
        if (
            mutableState.value
                .isDeleting) {
            return
        }

        mutableState.update { current ->
            current.copy(
                acknowledged = acknowledged,
                deletionFailureStage = null,
                databaseDeletedAfterFailure = false,
            )
        }
    }

    fun deleteMedication() {
        val expectedPreview = mutableState
                .value.preview
                ?: return

        if (
            !mutableState.value
                .canDelete) {
            return
        }

        mutableState.update { current ->
            current.copy(
                isDeleting = true,
                changedSincePreview = false,
                deletionFailureStage = null,
                databaseDeletedAfterFailure = false,
            )
        }

        viewModelScope.launch {
            val result = try {
                    coordinator.deleteMedication(
                        expectedPreview = expectedPreview,
                    )
                } catch (
                    cancellationException: CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    MedicationDeletionResult.Failed(
                            stage = MedicationDeletionStage
                                    .VALIDATING_PREVIEW,
                            databaseDeleted = false,
                        )
                }

            when (result) {
                is MedicationDeletionResult.Completed,
                MedicationDeletionResult.AlreadyDeleted -> {
                    mutableState.update { current ->
                        current.copy(
                            isDeleting = false,
                            acknowledged = false,
                            deletionCompleted = true,
                            deletionFailureStage = null,
                            databaseDeletedAfterFailure = false,
                        )
                    }
                }

                is MedicationDeletionResult.ChangedSincePreview -> {
                    mutableState.update { current ->
                        current.copy(
                            isDeleting = false,
                            preview = result.latestPreview,
                            acknowledged = false,
                            changedSincePreview = true,
                            deletionFailureStage = null,
                            databaseDeletedAfterFailure = false,
                        )
                    }
                }

                is MedicationDeletionResult.Failed -> {
                    mutableState.update { current ->
                        current.copy(
                            isDeleting = false,
                            acknowledged = true,
                            deletionFailureStage = result.stage,
                            databaseDeletedAfterFailure = result.databaseDeleted,
                        )
                    }
                }
            }
        }
    }

    companion object {

        fun factory(
            medicationId: String,
            coordinator: MedicationDeletionCoordinator,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    MedicationDeletionViewModel(
                        medicationId = medicationId,
                        coordinator = coordinator,
                    )
            }
    }
}

@Composable
fun MedicationDeletionRoute(
    medicationId: String,
    coordinator: MedicationDeletionCoordinator,
    onDeletionCompleted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MedicationDeletionViewModel =
        viewModel(
            factory = MedicationDeletionViewModel
                    .factory(
                        medicationId = medicationId,
                        coordinator = coordinator,
                    ),
        )

    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        state.deletionCompleted,
    ) {
        if (state.deletionCompleted) {
            onDeletionCompleted()
        }
    }

    MedicationDeletionScreen(
        state = state,
        onAcknowledgedChange = viewModel::setAcknowledged,
        onDelete = viewModel::deleteMedication,
        onRetryPreview = viewModel::loadPreview,
        onRetryDeletion = viewModel::deleteMedication,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun MedicationDeletionScreen(
    state: MedicationDeletionUiState,
    onAcknowledgedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRetryPreview: () -> Unit,
    onRetryDeletion: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val experience = carePackExperience()

    Scaffold(
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                    .fillMaxSize().padding(
                        innerPadding,
                    ).verticalScroll(
                        rememberScrollState(),
                    ).padding(
                        horizontal = experience
                                .screenHorizontalPadding,
                        vertical = experience
                                .screenVerticalPadding,
                    ).testTag(
                        "medication_deletion_screen",
                    ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.sectionSpacing,
                ),
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !state.isDeleting,
                modifier = Modifier
                        .fillMaxWidth().heightIn(
                            min = experience
                                    .controlMinHeight,
                        ).testTag(
                            "medication_deletion_back",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.back,
                        ),
                )
            }

            Text(
                text = stringResource(
                        R.string.medication_deletion_title,
                    ),
                style = MaterialTheme
                        .typography.headlineMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "medication_deletion_title",
                        ),
            )

            when {
                state.isLoading -> {
                    LoadingDeletionPreview()
                }

                state.previewLoadFailed -> {
                    PreviewLoadFailure(
                        onRetry = onRetryPreview,
                    )
                }

                state.medicationNotFound -> {
                    MedicationNotFound()
                }

                state.preview != null -> {
                    MedicationDeletionContent(
                        state = state,
                        onAcknowledgedChange = onAcknowledgedChange,
                        onDelete = onDelete,
                        onRetryDeletion = onRetryDeletion,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingDeletionPreview() {
    Column(
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(
                    "medication_deletion_loading",
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        CircularProgressIndicator()

        Text(
            text = stringResource(
                    R.string.medication_deletion_loading,
                ),
        )
    }
}

@Composable
private fun PreviewLoadFailure(
    onRetry: () -> Unit,
) {
    val experience = carePackExperience()

    Card(
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(
                    "medication_deletion_preview_error",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    experience.itemSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.medication_deletion_preview_failed,
                    ),
                color = MaterialTheme
                        .colorScheme.error,
            )

            Button(
                onClick = onRetry,
                modifier = Modifier
                        .fillMaxWidth().heightIn(
                            min = experience
                                    .primaryActionMinHeight,
                        ).testTag(
                            "medication_deletion_preview_retry",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.retry,
                        ),
                )
            }
        }
    }
}

@Composable
private fun MedicationNotFound() {
    Card(
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(
                    "medication_deletion_not_found",
                ),
    ) {
        Text(
            text = stringResource(
                    R.string.medication_deletion_not_found,
                ),
            modifier = Modifier.padding(
                    16.dp,
                ),
        )
    }
}

@Composable
private fun MedicationDeletionContent(
    state: MedicationDeletionUiState,
    onAcknowledgedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRetryDeletion: () -> Unit,
) {
    val preview = checkNotNull(
            state.preview,
        )

    val experience = carePackExperience()

    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "medication_deletion_warning",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    experience.itemSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.medication_deletion_warning_title,
                    ),
                style = MaterialTheme
                        .typography.titleLarge,
                color = MaterialTheme
                        .colorScheme.error,
                modifier = Modifier.carePackHeading(),
            )

            Text(
                text = stringResource(
                        if (experience.isSimple) {
                            R.string.medication_deletion_warning_body_simple
                        } else {
                            R.string.medication_deletion_warning_body
                        },
                    ),
            )
        }
    }

    Text(
        text = stringResource(
                R.string.medication_deletion_impact_title,
            ),
        style = MaterialTheme
                .typography.titleLarge,
        modifier = Modifier.carePackHeading(),
    )

    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "medication_deletion_preview",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    experience.itemSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.compactSpacing,
                ),
        ) {
            Text(
                text = preview.medicationName,
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "medication_deletion_name",
                        ),
            )

            HorizontalDivider()

            ImpactCountRow(
                label = stringResource(
                        R.string.medication_deletion_schedule_series_count,
                    ),
                count = preview.scheduleSeriesCount,
                testTag = "medication_deletion_schedule_series_count",
            )

            ImpactCountRow(
                label = stringResource(
                        R.string.medication_deletion_schedule_version_count,
                    ),
                count = preview.scheduleVersionCount,
                testTag = "medication_deletion_schedule_version_count",
            )

            ImpactCountRow(
                label = stringResource(
                        R.string.medication_deletion_occurrence_count,
                    ),
                count = preview.occurrenceCount,
                testTag = "medication_deletion_occurrence_count",
            )

            ImpactCountRow(
                label = stringResource(
                        R.string.medication_deletion_report_count,
                    ),
                count = preview.caregiverReportCount,
                testTag = "medication_deletion_report_count",
            )
        }
    }

    Text(
        text = stringResource(
                R.string.medication_deletion_external_limit,
            ),
        style = MaterialTheme
                .typography.bodyMedium,
        modifier = Modifier.testTag(
                "medication_deletion_external_limit",
            ),
    )

    if (state.changedSincePreview) {
        Card(
            modifier = Modifier
                    .fillMaxWidth().carePackPoliteLiveRegion()
                    .testTag(
                        "medication_deletion_changed",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.medication_deletion_changed,
                    ),
                modifier = Modifier.padding(
                        experience.itemSpacing,
                    ),
            )
        }
    }

    if (state.deletionFailureStage != null) {
        Card(
            modifier = Modifier
                    .fillMaxWidth().carePackPoliteLiveRegion()
                    .testTag(
                        "medication_deletion_error",
                    ),
        ) {
            Column(
                modifier = Modifier.padding(
                        experience.itemSpacing,
                    ),
                verticalArrangement = Arrangement.spacedBy(
                        experience.itemSpacing,
                    ),
            ) {
                Text(
                    text = stringResource(
                            if (
                                state.databaseDeletedAfterFailure
                            ) {
                                R.string.medication_deletion_cleanup_failed
                            } else {
                                R.string.medication_deletion_failed
                            },
                        ),
                    color = MaterialTheme
                            .colorScheme.error,
                )

                Button(
                    onClick = onRetryDeletion,
                    modifier = Modifier
                            .fillMaxWidth().heightIn(
                                min = experience
                                        .primaryActionMinHeight,
                            ).testTag(
                                "medication_deletion_retry",
                            ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.retry,
                            ),
                    )
                }
            }
        }
    }

    if (state.isDeleting) {
        Column(
            modifier = Modifier
                    .fillMaxWidth().carePackPoliteLiveRegion()
                    .testTag(
                        "medication_deletion_progress",
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            CircularProgressIndicator()

            Text(
                text = stringResource(
                        R.string.medication_deletion_progress,
                    ),
            )
        }
    } else {
        Row(
            modifier = Modifier
                    .fillMaxWidth().toggleable(
                        value = state.acknowledged,
                        enabled = true,
                        role = Role.Checkbox,
                        onValueChange = onAcknowledgedChange,
                    ).padding(
                        vertical = experience.compactSpacing,
                    ).testTag(
                        "medication_deletion_acknowledgement",
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            Checkbox(
                checked = state.acknowledged,
                onCheckedChange = null,
            )

            Text(
                text = stringResource(
                        R.string.medication_deletion_acknowledgement,
                    ),
                style = if (experience.isSimple) {
                        MaterialTheme.typography
                            .titleMedium
                    } else {
                        MaterialTheme.typography
                            .bodyLarge
                    },
                modifier = Modifier.weight(
                        1f,
                    ),
            )
        }

        Button(
            onClick = onDelete,
            enabled = state.canDelete,
            colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme
                            .colorScheme.error,
                    contentColor = MaterialTheme
                            .colorScheme.onError,
                ),
            modifier = Modifier
                    .fillMaxWidth().heightIn(
                        min = experience
                                .primaryActionMinHeight,
                    ).testTag(
                        "medication_deletion_confirm",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.medication_deletion_action,
                    ),
            )
        }
    }
}

@Composable
private fun ImpactCountRow(
    label: String,
    count: Int,
    testTag: String,
) {
    Row(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    testTag,
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(
                    1f,
                ),
        )

        Text(
            text = count
                    .toString().toPersianDigits(),
            style = MaterialTheme
                    .typography.titleMedium,
        )
    }
}
