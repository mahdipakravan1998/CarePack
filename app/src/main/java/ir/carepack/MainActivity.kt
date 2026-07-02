package ir.carepack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ir.carepack.app.CarePackApp
import ir.carepack.app.ForegroundGenerationErrorHost
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.settings.deletion.DataDeletionResult
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.theme.CarePackTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity :
    ComponentActivity() {

    private val container
        get() =
            (
                    application as
                            CarePackApplication
                    ).container

    private val foregroundGenerationError =
        MutableStateFlow<String?>(null)

    private val notificationOccurrenceId =
        MutableStateFlow<String?>(null)

    private val startupDeletionState =
        MutableStateFlow(
            StartupDeletionState.CHECKING,
        )

    private var foregroundReconciliationJob:
            Job? = null

    private var notificationValidationJob:
            Job? = null

    private var deletionRecoveryJob:
            Job? = null

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val deletionState by
            startupDeletionState
                .collectAsStateWithLifecycle()

            val generationError by
            foregroundGenerationError
                .collectAsStateWithLifecycle()

            val pendingNotificationOccurrenceId by
            notificationOccurrenceId
                .collectAsStateWithLifecycle()

            CarePackTheme {
                when (deletionState) {
                    StartupDeletionState.CHECKING -> {
                        StartupDeletionRecoveryScreen(
                            isRetryAvailable = false,
                            onRetry = {},
                        )
                    }

                    StartupDeletionState.FAILED -> {
                        StartupDeletionRecoveryScreen(
                            isRetryAvailable = true,
                            onRetry =
                                ::recoverIncompleteDeletion,
                        )
                    }

                    StartupDeletionState.READY -> {
                        ForegroundGenerationErrorHost(
                            errorMessage =
                                generationError,
                            onRetry =
                                ::reconcileForegroundState,
                        ) {
                            CarePackApp(
                                carePlanService =
                                    container
                                        .carePlanService,
                                todayQueryService =
                                    container
                                        .todayQueryService,
                                caregiverReportService =
                                    container
                                        .caregiverReportService,
                                setupPreferenceStore =
                                    container
                                        .setupPreferenceStore,
                                reminderPreferenceStore =
                                    container
                                        .reminderPreferenceStore,
                                reminderCoordinator =
                                    container
                                        .reminderCoordinator,
                                notificationPermissionGateway =
                                    container
                                        .notificationPermissionGateway,
                                todayReportFormatter =
                                    container
                                        .todayReportFormatter,
                                privacyPreferenceStore =
                                    container
                                        .privacyPreferenceStore,
                                userExperiencePreferenceStore =
                                    container
                                        .userExperiencePreferenceStore,
                                textShareGateway =
                                    container
                                        .textShareGateway,
                                dataDeletionCoordinator =
                                    container
                                        .dataDeletionCoordinator,
                                clock =
                                    container.clock,
                                zoneProvider =
                                    container
                                        .zoneProvider,
                                notificationOccurrenceId =
                                    pendingNotificationOccurrenceId,
                                onNotificationOccurrenceHandled = {
                                    notificationOccurrenceId.value =
                                        null
                                },
                            )
                        }
                    }
                }
            }
        }

        recoverIncompleteDeletion()
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(
        intent: Intent,
    ) {
        super.onNewIntent(intent)

        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()

        reconcileForegroundState()
    }

    override fun onStop() {
        foregroundReconciliationJob?.cancel()
        foregroundReconciliationJob = null
        super.onStop()
    }

    override fun onDestroy() {
        notificationValidationJob?.cancel()
        deletionRecoveryJob?.cancel()
        super.onDestroy()
    }

    private fun reconcileForegroundState() {
        foregroundReconciliationJob?.cancel()

        foregroundReconciliationJob =
            lifecycleScope.launch {
                try {
                    foregroundGenerationError.value =
                        null

                    container
                        .reminderCoordinator
                        .reconcile(
                            ReconciliationReason
                                .APPLICATION_FOREGROUND,
                        )
                } catch (
                    cancellationException:
                    CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    foregroundGenerationError.value =
                        getString(
                            R.string.storage_error,
                        )
                }
            }
    }

    private fun handleNotificationIntent(
        intent: Intent?,
    ) {
        if (intent == null) {
            return
        }

        notificationValidationJob?.cancel()

        notificationValidationJob =
            lifecycleScope.launch {
                val occurrenceId =
                    container
                        .notificationNavigationValidator
                        .validatedOccurrenceId(
                            intent,
                        )
                        ?: return@launch

                notificationOccurrenceId.value =
                    occurrenceId
            }
    }

    private fun recoverIncompleteDeletion() {
        deletionRecoveryJob?.cancel()

        deletionRecoveryJob =
            lifecycleScope.launch {
                startupDeletionState.value =
                    StartupDeletionState.CHECKING

                startupDeletionState.value =
                    when (
                        container
                            .dataDeletionCoordinator
                            .resumeIncompleteDeletionIfNeeded()
                    ) {
                        DataDeletionResult.Completed,
                        DataDeletionResult.NoDeletionPending,
                            -> {
                            StartupDeletionState.READY
                        }

                        is DataDeletionResult.Failed -> {
                            StartupDeletionState.FAILED
                        }
                    }
            }
    }
}

private enum class StartupDeletionState {
    CHECKING,
    READY,
    FAILED,
}

@Composable
private fun StartupDeletionRecoveryScreen(
    isRetryAvailable: Boolean,
    onRetry: () -> Unit,
) {
    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    "startup_deletion_recovery_screen",
                ),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues,
                    )
                    .padding(
                        horizontal = 24.dp,
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center,
        ) {
            if (isRetryAvailable) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .carepack_delete_all_failed,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,
                    modifier =
                        Modifier
                            .carePackHeading()
                            .carePackPoliteLiveRegion()
                            .testTag(
                                "startup_deletion_recovery_error",
                            ),
                )

                Button(
                    onClick = onRetry,
                    modifier =
                        Modifier
                            .padding(
                                top = 16.dp,
                            )
                            .testTag(
                                "startup_deletion_recovery_retry",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.retry_action,
                            ),
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier =
                        Modifier.testTag(
                            "startup_deletion_recovery_progress",
                        ),
                )

                Text(
                    text =
                        stringResource(
                            R.string
                                .carepack_delete_all_progress,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge,
                    modifier =
                        Modifier
                            .padding(
                                top = 16.dp,
                            )
                            .carePackPoliteLiveRegion(),
                )
            }
        }
    }
}
