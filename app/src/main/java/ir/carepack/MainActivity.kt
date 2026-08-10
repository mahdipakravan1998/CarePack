package ir.carepack

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ir.carepack.app.AppReconciliationOutcome
import ir.carepack.app.CarePackApp
import ir.carepack.app.CarePackUiDependencies
import ir.carepack.app.ForegroundGenerationErrorHost
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.reminder.notification.ReminderNotificationContract
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.theme.CarePackTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container
        get() = (
                    application as CarePackApplication
                    ).container

    private val uiDependencies by lazy(LazyThreadSafetyMode.NONE) {
        CarePackUiDependencies(
            carePlanService = container.carePlanService,
            todayQueryService = container.todayQueryService,
            caregiverReportService = container.caregiverReportService,
            setupPreferenceStore = container.setupPreferenceStore,
            reminderPreferenceStore = container.reminderPreferenceStore,
            reminderCoordinator = container.reminderCoordinator,
            reminderTestCoordinator = container.reminderTestCoordinator,
            notificationPermissionGateway = container.notificationPermissionGateway,
            todayReportFormatter = container.todayReportFormatter,
            dateRangeSummaryService = container.dateRangeSummaryService,
            rangeReportFormatter = container.rangeReportFormatter,
            privacyPreferenceStore = container.privacyPreferenceStore,
            userExperiencePreferenceStore = container.userExperiencePreferenceStore,
            textShareGateway = container.textShareGateway,
            dataDeletionCoordinator = container.dataDeletionCoordinator,
            medicationDeletionCoordinator = container.medicationDeletionCoordinator,
            clock = container.clock,
            zoneProvider = container.zoneProvider,
        )
    }

    private val foregroundGenerationError = MutableStateFlow<String?>(null)

    private val notificationOccurrenceId = MutableStateFlow<String?>(null)

    private val reminderSettingsRequested = MutableStateFlow(false)

    private val startupDeletionState = MutableStateFlow(
            StartupDeletionState.CHECKING,
        )

    private val startupDeletionFailure = MutableStateFlow<ir.carepack.core.error.SafeAppFailure?>(null)

    private var foregroundReconciliationJob: Job? = null

    private var notificationValidationJob: Job? = null

    private var deletionRecoveryJob: Job? = null

    private var deferredNotificationIntent: Intent? = null

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val deletionState by
            startupDeletionState.collectAsStateWithLifecycle()

            val deletionFailure by
            startupDeletionFailure.collectAsStateWithLifecycle()

            val generationError by
            foregroundGenerationError.collectAsStateWithLifecycle()

            val pendingNotificationOccurrenceId by
            notificationOccurrenceId.collectAsStateWithLifecycle()

            val pendingReminderSettingsRequest by
            reminderSettingsRequested.collectAsStateWithLifecycle()

            val userExperienceState by
            container.userExperiencePreferenceStore
                .state.collectAsStateWithLifecycle(
                    initialValue = UserExperiencePreferenceState(),
                )

            CarePackTheme(
                seniorMode = userExperienceState
                        .seniorMode,
            ) {
                when (deletionState) {
                    StartupDeletionState.CHECKING -> {
                        StartupDeletionRecoveryScreen(
                            isRetryAvailable = false,
                            isStorageResetRequired = false,
                            onRetry = {},
                            onOpenStorageSettings = {},
                        )
                    }

                    StartupDeletionState.FAILED -> {
                        val retryable = deletionFailure?.retryable != false

                        StartupDeletionRecoveryScreen(
                            isRetryAvailable = retryable,
                            isStorageResetRequired = !retryable,
                            onRetry = ::recoverIncompleteDeletion,
                            onOpenStorageSettings = ::openAppStorageSettings,
                        )
                    }

                    StartupDeletionState.READY -> {
                        ForegroundGenerationErrorHost(
                            errorMessage = generationError,
                            onRetry = ::reconcileForegroundState,
                        ) {
                            CarePackApp(
                                dependencies = uiDependencies,
                                notificationOccurrenceId = pendingNotificationOccurrenceId,
                                onNotificationOccurrenceHandled = {
                                    notificationOccurrenceId.value = null
                                },
                                openReminderSettingsRequested = pendingReminderSettingsRequest,
                                onReminderSettingsRequestHandled = {
                                    reminderSettingsRequested.value = false
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

    override fun onPostResume() {
        super.onPostResume()

        if (!isDeviceLocked()) {
            deferredNotificationIntent?.also { pendingIntent ->
                    deferredNotificationIntent = null
                    handleNotificationIntent(pendingIntent)
                }
        }
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

        if (
            startupDeletionState.value == StartupDeletionState.READY
        ) {
            reconcileForegroundState()
        }
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
        if (
            startupDeletionState.value != StartupDeletionState.READY
        ) {
            return
        }

        foregroundReconciliationJob?.cancel()

        foregroundReconciliationJob = lifecycleScope.launch {
                foregroundGenerationError.value = null

                when (
                    container.appReconciler.reconcile(
                        ReconciliationReason.APPLICATION_FOREGROUND,
                    )) {
                    is AppReconciliationOutcome.Completed -> Unit
                    is AppReconciliationOutcome.Failed ->
                        foregroundGenerationError.value = getString(R.string.storage_error)
                }
            }
    }

    private fun handleNotificationIntent(
        intent: Intent?,
    ) {
        if (intent == null) {
            return
        }

        if (isDeviceLocked()) {
            deferredNotificationIntent = Intent(intent)
            return
        }

        if (
            ReminderNotificationContract.isOpenReminderSettingsIntent(intent)
        ) {
            reminderSettingsRequested.value = true
            return
        }

        notificationValidationJob?.cancel()
        notificationValidationJob = lifecycleScope.launch {
                val occurrenceId = container
                        .notificationNavigationValidator.validatedOccurrenceId(intent)
                        ?: return@launch

                notificationOccurrenceId.value = occurrenceId
            }
    }

    private fun recoverIncompleteDeletion() {
        deletionRecoveryJob?.cancel()
        deletionRecoveryJob = lifecycleScope.launch {
                startupDeletionState.value = StartupDeletionState.CHECKING
                startupDeletionFailure.value = null

                val outcome = container.appReconciler.reconcile(
                        ReconciliationReason.APPLICATION_FOREGROUND,
                    )

                when (outcome) {
                    is AppReconciliationOutcome.Completed -> {
                        startupDeletionFailure.value = null
                        startupDeletionState.value = StartupDeletionState.READY
                    }

                    is AppReconciliationOutcome.Failed -> {
                        startupDeletionFailure.value = outcome.failure
                        startupDeletionState.value = StartupDeletionState.FAILED
                    }
                }
            }
    }

    private fun openAppStorageSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun isDeviceLocked(): Boolean = checkNotNull(
            getSystemService(KeyguardManager::class.java),
        ).isDeviceLocked

}

private enum class StartupDeletionState {
    CHECKING,
    READY,
    FAILED,
}

@Composable
private fun StartupDeletionRecoveryScreen(
    isRetryAvailable: Boolean,
    isStorageResetRequired: Boolean,
    onRetry: () -> Unit,
    onOpenStorageSettings: () -> Unit,
) {
    var resetConfirmationStep by
        remember {
            mutableStateOf(0)
        }
    Scaffold(
        modifier = Modifier
                .fillMaxSize().testTag(
                    "startup_deletion_recovery_screen",
                ),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                    .fillMaxSize().padding(
                        paddingValues,
                    ).padding(
                        horizontal = 24.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isRetryAvailable || isStorageResetRequired) {
                Text(
                    text = stringResource(
                            if (isStorageResetRequired) {
                                R.string.startup_recovery_corruption_title
                            } else {
                                R.string.carepack_delete_all_failed
                            },
                        ),
                    style = MaterialTheme
                            .typography.headlineSmall,
                    modifier = Modifier
                            .carePackHeading().carePackPoliteLiveRegion()
                            .testTag(
                                "startup_deletion_recovery_error",
                            ),
                )

                if (isRetryAvailable) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                                .padding(
                                    top = 16.dp,
                                ).testTag(
                                    "startup_deletion_recovery_retry",
                                ),
                    ) {
                        Text(
                            text = stringResource(
                                    R.string.retry_action,
                                ),
                        )
                    }
                }

                if (isStorageResetRequired) {
                    Text(
                        text = stringResource(
                                R.string.startup_recovery_corruption_body,
                            ),
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    Button(
                        onClick = {
                            resetConfirmationStep = 1
                        },
                        modifier = Modifier
                                .padding(top = 16.dp).testTag(
                                    "startup_deletion_recovery_reset",
                                ),
                    ) {
                        Text(
                            text = stringResource(
                                    R.string.startup_recovery_reset_action,
                                ),
                        )
                    }
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(
                            "startup_deletion_recovery_progress",
                        ),
                )

                Text(
                    text = stringResource(
                            R.string.carepack_delete_all_progress,
                        ),
                    style = MaterialTheme
                            .typography.bodyLarge,
                    modifier = Modifier
                            .padding(
                                top = 16.dp,
                            ).carePackPoliteLiveRegion(),
                )
            }
        }
    }

    if (resetConfirmationStep == 1) {
        AlertDialog(
            onDismissRequest = {
                resetConfirmationStep = 0
            },
            title = {
                Text(
                    stringResource(
                        R.string.startup_recovery_reset_confirm_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.startup_recovery_reset_confirm_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfirmationStep = 2
                    },
                    modifier = Modifier.testTag(
                            "startup_deletion_recovery_reset_continue",
                        ),
                ) {
                    Text(
                        stringResource(
                            R.string.continue_action,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        resetConfirmationStep = 0
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (resetConfirmationStep == 2) {
        AlertDialog(
            onDismissRequest = {
                resetConfirmationStep = 1
            },
            title = {
                Text(
                    stringResource(
                        R.string.startup_recovery_reset_final_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.startup_recovery_reset_final_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfirmationStep = 0
                        onOpenStorageSettings()
                    },
                    modifier = Modifier.testTag(
                            "startup_deletion_recovery_reset_open_settings",
                        ),
                ) {
                    Text(
                        stringResource(
                            R.string.startup_recovery_open_settings,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        resetConfirmationStep = 1
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
