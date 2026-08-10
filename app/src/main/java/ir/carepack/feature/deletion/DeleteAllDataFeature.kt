package ir.carepack.feature.deletion

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.carepack.R
import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.DataDeletionResult
import ir.carepack.settings.deletion.DataDeletionStage
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeleteAllDataUiState(
    val showConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val deletionCompleted: Boolean = false,
    val failedStage: DataDeletionStage? = null,
)

class DeleteAllDataViewModel(
    private val dataDeletionCoordinator: DataDeletionCoordinator,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
            DeleteAllDataUiState(),
        )

    val state = mutableState.asStateFlow()

    fun requestDeletion() {
        if (
            mutableState.value
                .isDeleting) {
            return
        }

        mutableState.update {
                current ->
            current.copy(
                showConfirmation = true,
                failedStage = null,
            )
        }
    }

    fun dismissConfirmation() {
        if (
            mutableState.value
                .isDeleting) {
            return
        }

        mutableState.update {
                current ->
            current.copy(
                showConfirmation = false,
            )
        }
    }

    fun confirmDeletion() {
        runDeletion(
            resumeOnly = false,
        )
    }

    fun retryDeletion() {
        runDeletion(
            resumeOnly = true,
        )
    }

    private fun runDeletion(
        resumeOnly: Boolean,
    ) {
        if (
            mutableState.value
                .isDeleting) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                    current ->
                current.copy(
                    showConfirmation = false,
                    isDeleting = true,
                    failedStage = null,
                )
            }

            val result = try {
                    if (resumeOnly) {
                        dataDeletionCoordinator.resumeIncompleteDeletionIfNeeded()
                    } else {
                        dataDeletionCoordinator.deleteEverything()
                    }
                } catch (
                    cancellationException: CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    DataDeletionResult.Failed(
                        stage = DataDeletionStage
                                .MARKING_DELETION_IN_PROGRESS,
                    )
                }

            when (result) {
                DataDeletionResult.Completed -> {
                    mutableState.update {
                            current ->
                        current.copy(
                            isDeleting = false,
                            deletionCompleted = true,
                            failedStage = null,
                        )
                    }
                }

                DataDeletionResult.NoDeletionPending -> {
                    mutableState.update {
                            current ->
                        current.copy(
                            isDeleting = false,
                            deletionCompleted = true,
                            failedStage = null,
                        )
                    }
                }

                is DataDeletionResult.Failed -> {
                    mutableState.update {
                            current ->
                        current.copy(
                            isDeleting = false,
                            deletionCompleted = false,
                            failedStage = result.stage,
                        )
                    }
                }
            }
        }
    }

    companion object {

        fun factory(
            dataDeletionCoordinator: DataDeletionCoordinator,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    DeleteAllDataViewModel(
                        dataDeletionCoordinator = dataDeletionCoordinator,
                    )
            }
    }
}

@Composable
fun DeleteAllDataRoute(
    dataDeletionCoordinator: DataDeletionCoordinator,
    onDeletionCompleted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DeleteAllDataViewModel =
        viewModel(
            factory = DeleteAllDataViewModel.factory(
                    dataDeletionCoordinator = dataDeletionCoordinator,
                ),
        )

    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        state.deletionCompleted,
    ) {
        if (
            state.deletionCompleted) {
            onDeletionCompleted()
        }
    }

    DeleteAllDataScreen(
        state = state,
        onRequestDeletion = viewModel::requestDeletion,
        onDismissConfirmation = viewModel::dismissConfirmation,
        onConfirmDeletion = viewModel::confirmDeletion,
        onRetry = viewModel::retryDeletion,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun DeleteAllDataScreen(
    state: DeleteAllDataUiState,
    onRequestDeletion: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmDeletion: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val experience = carePackExperience()

    Scaffold(
        modifier = modifier
                .fillMaxSize().testTag(
                    "delete_all_data_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                    .fillMaxSize().padding(
                        contentPadding,
                    ).navigationBarsPadding()
                    .verticalScroll(
                        rememberScrollState(),
                    ).padding(
                        horizontal = experience
                                .screenHorizontalPadding,
                        vertical = experience
                                .screenVerticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.sectionSpacing,
                ),
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !state.isDeleting,
                modifier = Modifier
                        .carePackInteractiveControl().testTag(
                            "delete_all_data_back",
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
                        R.string.carepack_delete_all_title,
                    ),
                style = MaterialTheme
                        .typography.headlineMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "delete_all_data_title",
                        ),
            )

            Card(
                modifier = Modifier
                        .fillMaxWidth().testTag(
                            "delete_all_data_warning",
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
                                R.string.carepack_delete_all_warning_title,
                            ),
                        style = MaterialTheme
                                .typography.titleLarge,
                        color = MaterialTheme
                                .colorScheme.error,
                        modifier = Modifier
                                .carePackHeading(),
                    )

                    Text(
                        text = stringResource(
                                R.string.carepack_delete_all_warning_body,
                            ),
                        style = MaterialTheme
                                .typography.bodyLarge,
                    )
                }
            }

            Text(
                text = stringResource(
                        R.string.carepack_delete_all_scope_title,
                    ),
                style = MaterialTheme
                        .typography.titleLarge,
                modifier = Modifier.carePackHeading(),
            )

            Text(
                text = stringResource(
                        R.string.carepack_delete_all_scope_body,
                    ),
                style = MaterialTheme
                        .typography.bodyLarge,
                modifier = Modifier.testTag(
                        "delete_all_data_scope",
                    ),
            )

            Text(
                text = stringResource(
                        R.string.carepack_delete_all_external_limit,
                    ),
                style = MaterialTheme
                        .typography.bodyMedium,
                modifier = Modifier.testTag(
                        "delete_all_data_external_limit",
                    ),
            )

            when {
                state.isDeleting -> {
                    Column(
                        modifier = Modifier
                                .fillMaxWidth().carePackPoliteLiveRegion()
                                .testTag(
                                    "delete_all_data_progress",
                                ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                                experience.itemSpacing,
                            ),
                    ) {
                        CircularProgressIndicator()

                        Text(
                            text = stringResource(
                                    R.string.carepack_delete_all_progress,
                                ),
                            style = MaterialTheme
                                    .typography.bodyLarge,
                        )
                    }
                }

                state.failedStage != null -> {
                    Card(
                        modifier = Modifier
                                .fillMaxWidth().carePackPoliteLiveRegion()
                                .testTag(
                                    "delete_all_data_error",
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
                                        R.string.carepack_delete_all_failed,
                                    ),
                                style = MaterialTheme
                                        .typography.bodyLarge,
                                color = MaterialTheme
                                        .colorScheme.error,
                            )

                            Button(
                                onClick = onRetry,
                                modifier = Modifier
                                        .fillMaxWidth().carePackPrimaryAction()
                                        .testTag(
                                            "delete_all_data_retry",
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

                !state.deletionCompleted -> {
                    Button(
                        onClick = onRequestDeletion,
                        modifier = Modifier
                                .fillMaxWidth().carePackPrimaryAction()
                                .testTag(
                                    "delete_all_data_request",
                                ),
                    ) {
                        Text(
                            text = stringResource(
                                    R.string.carepack_delete_all_action,
                                ),
                        )
                    }
                }
            }
        }
    }

    if (state.showConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            title = {
                Text(
                    text = stringResource(
                            R.string.carepack_delete_confirmation_title,
                        ),
                    modifier = Modifier.carePackHeading(),
                )
            },
            text = {
                Text(
                    text = stringResource(
                            R.string.carepack_delete_confirmation_body,
                        ),
                    style = MaterialTheme
                            .typography.bodyLarge,
                    modifier = Modifier.testTag(
                            "delete_all_data_confirmation_body",
                        ),
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeletion,
                    modifier = Modifier
                            .carePackPrimaryAction().testTag(
                                "delete_all_data_confirm",
                            ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.carepack_delete_confirmation_action,
                            ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissConfirmation,
                    modifier = Modifier
                            .carePackInteractiveControl().testTag(
                                "delete_all_data_cancel",
                            ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.cancel,
                            ),
                    )
                }
            },
            modifier = Modifier.testTag(
                    "delete_all_data_confirmation",
                ),
        )
    }
}
