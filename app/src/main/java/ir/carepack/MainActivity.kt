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
    }

    override fun onNewIntent(
        intent: Intent,
    ) {
        super.onNewIntent(intent)

        setIntent(intent)

        if (
            startupDeletionState.value ==
            StartupDeletionState.READY
        ) {
            validateNotificationIntent(
                sourceIntent = intent,
            )
        }
    }

    override fun onStart() {
        super.onStart()

        when (
            startupDeletionState.value
        ) {
            StartupDeletionState.CHECKING -> {
                recoverIncompleteDeletion()
            }

            StartupDeletionState.READY -> {
                reconcileForegroundState()
            }

            StartupDeletionState.FAILED -> {
                Unit
            }
        }
    }

    private fun recoverIncompleteDeletion() {
        if (
            deletionRecoveryJob
                ?.isActive == true
        ) {
            return
        }

        deletionRecoveryJob =
            lifecycleScope.launch {
                startupDeletionState.value =
                    StartupDeletionState.CHECKING

                val result =
                    try {
                        container
                            .dataDeletionCoordinator
                            .resumeIncompleteDeletionIfNeeded()
                    } catch (
                        cancellation:
                        CancellationException,
                    ) {
                        throw cancellation
                    } catch (_: Exception) {
                        startupDeletionState.value =
                            StartupDeletionState.FAILED

                        return@launch
                    }

                when (result) {
                    DataDeletionResult.Completed -> {
                        notificationOccurrenceId.value =
                            null

                        completeStartupGate()
                    }

                    DataDeletionResult
                        .NoDeletionPending -> {
                        completeStartupGate()
                    }

                    is DataDeletionResult.Failed -> {
                        startupDeletionState.value =
                            StartupDeletionState.FAILED
                    }
                }
            }
    }

    private fun completeStartupGate() {
        startupDeletionState.value =
            StartupDeletionState.READY

        validateNotificationIntent(
            sourceIntent = intent,
        )

        reconcileForegroundState()
    }

    private fun reconcileForegroundState() {
        if (
            startupDeletionState.value !=
            StartupDeletionState.READY
        ) {
            return
        }

        if (
            foregroundReconciliationJob
                ?.isActive == true
        ) {
            return
        }

        foregroundReconciliationJob =
            lifecycleScope.launch {
                foregroundGenerationError.value =
                    null

                try {
                    container
                        .appReconciler
                        .reconcile(
                            reason =
                                ReconciliationReason
                                    .APPLICATION_FOREGROUND,
                        )
                } catch (
                    cancellation:
                    CancellationException,
                ) {
                    throw cancellation
                } catch (_: Exception) {
                    foregroundGenerationError.value =
                        "به‌روزرسانی نوبت‌ها و یادآورها انجام نشد."
                }
            }
    }

    private fun validateNotificationIntent(
        sourceIntent: Intent?,
    ) {
        if (
            startupDeletionState.value !=
            StartupDeletionState.READY
        ) {
            return
        }

        notificationValidationJob
            ?.cancel()

        notificationValidationJob =
            lifecycleScope.launch {
                notificationOccurrenceId.value =
                    container
                        .notificationNavigationValidator
                        .validatedOccurrenceId(
                            intent = sourceIntent,
                        )
            }
    }
}

private enum class StartupDeletionState {
    CHECKING,
    READY,
    FAILED,
}

@androidx.compose.runtime.Composable
private fun StartupDeletionRecoveryScreen(
    isRetryAvailable: Boolean,
    onRetry: () -> Unit,
) {
    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    "startup_deletion_gate",
                ),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        contentPadding,
                    )
                    .padding(
                        24.dp,
                    )
                    .carePackPoliteLiveRegion(),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center,
        ) {
            if (!isRetryAvailable) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.testTag(
                            "startup_deletion_progress",
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
                                top = 20.dp,
                            )
                            .testTag(
                                "startup_deletion_progress_text",
                            ),
                )
            } else {
                Text(
                    text =
                        stringResource(
                            R.string
                                .carepack_delete_all_failed,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                    modifier =
                        Modifier
                            .carePackHeading()
                            .testTag(
                                "startup_deletion_error",
                            ),
                )

                Button(
                    onClick = onRetry,
                    modifier =
                        Modifier
                            .padding(
                                top = 20.dp,
                            )
                            .testTag(
                                "startup_deletion_retry",
                            ),
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
}
