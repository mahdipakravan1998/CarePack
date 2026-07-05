package ir.carepack.feature.reminder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.domain.reminder.ExactAlarmReadiness
import ir.carepack.domain.reminder.ManufacturerGuidance
import ir.carepack.domain.reminder.NotificationPermissionReadiness
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReadiness
import ir.carepack.domain.reminder.ReminderReadinessPolicy
import ir.carepack.domain.reminder.ReminderReadinessStatus
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.reminder.permission.AndroidBatteryOptimizationGateway
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.reminder.permission.NotificationPermissionGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NotificationPermissionUiState {
    NOT_REQUIRED,
    GRANTED,
    DENIED,
}

data class ReminderSettingsUiState(
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val remindersEnabled: Boolean = false,
    val notificationPermissionState:
    NotificationPermissionUiState =
        NotificationPermissionUiState.DENIED,
    val notificationRuntimePermissionRequired:
    Boolean = false,
    val hasActiveSchedule: Boolean = false,
    val exactAlarmCapabilityGranted:
    Boolean = false,
    val availability: ReminderAvailability =
        ReminderAvailability.DISABLED,
    val readiness: ReminderReadiness? = null,
    val showNotificationRationale:
    Boolean = false,
    val showExactAlarmRationale:
    Boolean = false,
    val showOemGuidance:
    Boolean = true,
    val errorMessage: String? = null,
)

private data class ReminderSettingsTransientState(
    val isApplying: Boolean = false,
    val showNotificationRationale:
    Boolean = false,
    val showExactAlarmRationale:
    Boolean = false,
    val showOemGuidance:
    Boolean = true,
    val errorMessage: String? = null,
)

class ReminderSettingsViewModel(
    private val preferenceStore:
    ReminderPreferenceStore,
    private val reminderCoordinator:
    ReminderCoordinator,
    private val notificationPermissionGateway:
    NotificationPermissionGateway,
    private val batteryOptimizationState:
        () -> BatteryOptimizationState = {
        BatteryOptimizationState.UNKNOWN
    },
    private val manufacturer:
        () -> String? = {
        Build.MANUFACTURER
    },
) : ViewModel() {

    private val operationMutex =
        Mutex()

    private val mutableStatus =
        MutableStateFlow<ReminderStatus?>(
            null,
        )

    private val mutableTransientState =
        MutableStateFlow(
            ReminderSettingsTransientState(),
        )

    val state =
        combine(
            preferenceStore.state,
            mutableStatus,
            mutableTransientState,
        ) {
                preferenceState,
                status,
                transientState ->
            val runtimePermissionRequired =
                notificationPermissionGateway
                    .requiresRuntimePermission()

            val permissionUiState =
                permissionUiStateFor(
                    runtimePermissionRequired =
                        runtimePermissionRequired,
                    status =
                        status,
                )

            val readiness =
                status?.let {
                        currentStatus ->
                    ReminderReadinessPolicy.evaluate(
                        remindersEnabled =
                            preferenceState
                                .remindersEnabled,
                        hasActiveSchedule =
                            currentStatus
                                .hasActiveSchedule,
                        notificationRuntimePermissionRequired =
                            runtimePermissionRequired,
                        notificationPermissionGranted =
                            currentStatus
                                .notificationPermissionGranted,
                        canScheduleExactAlarms =
                            currentStatus
                                .exactAlarmCapabilityGranted,
                        exactAlarmRelevant =
                            currentStatus
                                .hasActiveSchedule &&
                                    preferenceState
                                        .remindersEnabled,
                        batteryOptimizationState =
                            batteryOptimizationState(),
                        manufacturer =
                            manufacturer(),
                    )
                }

            ReminderSettingsUiState(
                isLoading =
                    status == null,
                isApplying =
                    transientState
                        .isApplying,
                remindersEnabled =
                    preferenceState
                        .remindersEnabled,
                notificationPermissionState =
                    permissionUiState,
                notificationRuntimePermissionRequired =
                    runtimePermissionRequired,
                hasActiveSchedule =
                    status
                        ?.hasActiveSchedule
                        ?: false,
                exactAlarmCapabilityGranted =
                    status
                        ?.exactAlarmCapabilityGranted
                        ?: false,
                availability =
                    status
                        ?.availability
                        ?: ReminderAvailability
                            .DISABLED,
                readiness =
                    readiness,
                showNotificationRationale =
                    transientState
                        .showNotificationRationale,
                showExactAlarmRationale =
                    transientState
                        .showExactAlarmRationale,
                showOemGuidance =
                    transientState
                        .showOemGuidance,
                errorMessage =
                    transientState
                        .errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.Eagerly,
            initialValue =
                ReminderSettingsUiState(
                    notificationRuntimePermissionRequired =
                        notificationPermissionGateway
                            .requiresRuntimePermission(),
                ),
        )

    init {
        loadInitialStatus()
    }

    fun setRemindersEnabled(
        enabled: Boolean,
    ) {
        runOperation {
            preferenceStore
                .setRemindersEnabled(
                    enabled = enabled,
                )

            val status =
                reminderCoordinator
                    .reconcile(
                        reason =
                            ReconciliationReason
                                .REMINDER_PREFERENCE_CHANGED,
                    )
                    .status

            mutableStatus.value =
                status

            val readiness =
                currentReadinessFor(
                    remindersEnabled =
                        enabled,
                    status =
                        status,
                )

            mutableTransientState.update {
                    transient ->
                transient.copy(
                    showNotificationRationale =
                        enabled &&
                                readiness
                                    .notificationPermission ==
                                NotificationPermissionReadiness
                                    .DENIED,
                    showExactAlarmRationale =
                        enabled &&
                                readiness
                                    .exactAlarm ==
                                ExactAlarmReadiness
                                    .UNAVAILABLE,
                    showOemGuidance =
                        true,
                )
            }
        }
    }

    fun showNotificationPermissionExplanation() {
        val currentState =
            state.value

        if (
            !currentState.remindersEnabled ||
            currentState
                .notificationPermissionState !=
            NotificationPermissionUiState.DENIED
        ) {
            return
        }

        mutableTransientState.update {
                transient ->
            transient.copy(
                showNotificationRationale =
                    true,
                errorMessage = null,
            )
        }
    }

    fun dismissNotificationPermissionExplanation() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showNotificationRationale =
                    false,
            )
        }
    }

    fun onNotificationPermissionRequestCompleted() {
        dismissNotificationPermissionExplanation()

        reconcilePlatformState(
            reason =
                ReconciliationReason
                    .NOTIFICATION_PERMISSION_CHANGED,
        )
    }

    fun showExactAlarmExplanation() {
        val currentState =
            state.value

        val canRequest =
            currentState
                .remindersEnabled &&
                    currentState
                        .notificationPermissionState !=
                    NotificationPermissionUiState
                        .DENIED &&
                    currentState
                        .hasActiveSchedule &&
                    currentState
                        .readiness
                        ?.exactAlarm ==
                    ExactAlarmReadiness
                        .UNAVAILABLE

        if (!canRequest) {
            return
        }

        mutableTransientState.update {
                transient ->
            transient.copy(
                showExactAlarmRationale =
                    true,
                errorMessage = null,
            )
        }
    }

    fun dismissExactAlarmExplanation() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showExactAlarmRationale =
                    false,
            )
        }
    }

    fun onExactAlarmSettingsReturned() {
        dismissExactAlarmExplanation()

        reconcilePlatformState(
            reason =
                ReconciliationReason
                    .EXACT_ALARM_CAPABILITY_CHANGED,
        )
    }

    fun onNotificationSettingsReturned() {
        reconcilePlatformState(
            reason =
                ReconciliationReason
                    .NOTIFICATION_PERMISSION_CHANGED,
        )
    }

    fun refreshPlatformState() {
        reconcilePlatformState(
            reason =
                ReconciliationReason
                    .MANUAL_RETRY,
        )
    }

    fun continueWithoutPermissionChanges() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showNotificationRationale =
                    false,
                showExactAlarmRationale =
                    false,
                errorMessage = null,
            )
        }
    }

    fun showOemGuidance() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showOemGuidance =
                    true,
            )
        }
    }

    fun onPlatformLaunchFailed() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showNotificationRationale =
                    false,
                showExactAlarmRationale =
                    false,
                errorMessage =
                    "باز کردن تنظیمات اندروید انجام نشد.",
            )
        }
    }

    fun clearError() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                errorMessage = null,
            )
        }
    }

    private fun loadInitialStatus() {
        runOperation {
            mutableStatus.value =
                reminderCoordinator
                    .currentStatus()
        }
    }

    private fun reconcilePlatformState(
        reason: ReconciliationReason,
    ) {
        runOperation {
            mutableStatus.value =
                reminderCoordinator
                    .reconcile(
                        reason = reason,
                    )
                    .status
        }
    }

    private fun currentReadinessFor(
        remindersEnabled: Boolean,
        status: ReminderStatus,
    ): ReminderReadiness {
        return ReminderReadinessPolicy.evaluate(
            remindersEnabled =
                remindersEnabled,
            hasActiveSchedule =
                status.hasActiveSchedule,
            notificationRuntimePermissionRequired =
                notificationPermissionGateway
                    .requiresRuntimePermission(),
            notificationPermissionGranted =
                status.notificationPermissionGranted,
            canScheduleExactAlarms =
                status.exactAlarmCapabilityGranted,
            exactAlarmRelevant =
                remindersEnabled &&
                        status.hasActiveSchedule,
            batteryOptimizationState =
                batteryOptimizationState(),
            manufacturer =
                manufacturer(),
        )
    }

    private fun runOperation(
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            operationMutex.withLock {
                mutableTransientState.update {
                        transient ->
                    transient.copy(
                        isApplying = true,
                        errorMessage = null,
                    )
                }

                try {
                    operation()
                } catch (
                    cancellation:
                    CancellationException,
                ) {
                    throw cancellation
                } catch (_: Exception) {
                    mutableTransientState.update {
                            transient ->
                        transient.copy(
                            errorMessage =
                                "به‌روزرسانی تنظیمات یادآور انجام نشد.",
                        )
                    }
                } finally {
                    mutableTransientState.update {
                            transient ->
                        transient.copy(
                            isApplying = false,
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun factory(
            preferenceStore:
            ReminderPreferenceStore,
            reminderCoordinator:
            ReminderCoordinator,
            notificationPermissionGateway:
            NotificationPermissionGateway,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ReminderSettingsViewModel(
                        preferenceStore =
                            preferenceStore,
                        reminderCoordinator =
                            reminderCoordinator,
                        notificationPermissionGateway =
                            notificationPermissionGateway,
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderSettingsRoute(
    viewModel: ReminderSettingsViewModel,
    onBack: () -> Unit,
    onReviewSchedules: () -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

    val batteryOptimizationGateway =
        remember(context) {
            AndroidBatteryOptimizationGateway(
                context =
                    context,
            )
        }

    val screenState =
        state.withPlatformReadiness(
            batteryOptimizationState =
                batteryOptimizationGateway
                    .currentState(),
            manufacturer =
                Build.MANUFACTURER,
        )

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission(),
        ) {
            viewModel
                .onNotificationPermissionRequestCompleted()
        }

    val notificationSettingsLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartActivityForResult(),
        ) {
            viewModel
                .onNotificationSettingsReturned()
        }

    val exactAlarmSettingsLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartActivityForResult(),
        ) {
            viewModel
                .onExactAlarmSettingsReturned()
        }

    val batterySettingsLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartActivityForResult(),
        ) {
            viewModel
                .refreshPlatformState()
        }

    DisposableEffect(
        lifecycleOwner,
    ) {
        val observer =
            LifecycleEventObserver {
                    _, event ->
                if (
                    event ==
                    Lifecycle.Event.ON_RESUME
                ) {
                    viewModel
                        .refreshPlatformState()
                }
            }

        lifecycleOwner
            .lifecycle
            .addObserver(observer)

        onDispose {
            lifecycleOwner
                .lifecycle
                .removeObserver(observer)
        }
    }

    ReminderSettingsScreen(
        state =
            screenState,
        onBack =
            onBack,
        onRemindersEnabledChanged =
            viewModel::setRemindersEnabled,
        onRequestNotificationPermission =
            viewModel::showNotificationPermissionExplanation,
        onOpenNotificationSettings = {
            val intent =
                Intent(
                    Settings
                        .ACTION_APP_NOTIFICATION_SETTINGS,
                ).apply {
                    putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        context.packageName,
                    )
                }

            runCatching {
                notificationSettingsLauncher
                    .launch(intent)
            }.onFailure {
                viewModel
                    .onPlatformLaunchFailed()
            }
        },
        onRequestExactAlarmAccess =
            viewModel::showExactAlarmExplanation,
        onOpenBatterySettings = {
            val intent =
                Intent(
                    Settings
                        .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                )

            runCatching {
                batterySettingsLauncher
                    .launch(intent)
            }.onFailure {
                viewModel
                    .onPlatformLaunchFailed()
            }
        },
        onReviewSchedules =
            onReviewSchedules,
        onContinueAnyway =
            viewModel::continueWithoutPermissionChanges,
        onShowOemGuidance =
            viewModel::showOemGuidance,
        onRetry =
            viewModel::refreshPlatformState,
    )

    if (
        state.showNotificationRationale
    ) {
        AlertDialog(
            onDismissRequest =
                viewModel::dismissNotificationPermissionExplanation,
            title = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .notification_permission_rationale_title,
                        ),
                )
            },
            text = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .notification_permission_rationale_body,
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel
                            .dismissNotificationPermissionExplanation()

                        if (
                            Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.TIRAMISU
                        ) {
                            notificationPermissionLauncher
                                .launch(
                                    Manifest.permission
                                        .POST_NOTIFICATIONS,
                                )
                        } else {
                            viewModel
                                .onNotificationPermissionRequestCompleted()
                        }
                    },
                    modifier =
                        Modifier.testTag(
                            "notification_rationale_continue",
                        ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .continue_action,
                            ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick =
                        viewModel::continueWithoutPermissionChanges,
                    modifier =
                        Modifier.testTag(
                            "notification_rationale_cancel",
                        ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .continue_without_permissions,
                            ),
                    )
                }
            },
            modifier =
                Modifier.testTag(
                    "notification_permission_rationale",
                ),
        )
    }

    if (
        state.showExactAlarmRationale
    ) {
        AlertDialog(
            onDismissRequest =
                viewModel::dismissExactAlarmExplanation,
            title = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .exact_alarm_rationale_title,
                        ),
                )
            },
            text = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .exact_alarm_rationale_body,
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel
                            .dismissExactAlarmExplanation()

                        val intent =
                            Intent(
                                Settings
                                    .ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse(
                                    "package:${context.packageName}",
                                ),
                            )

                        runCatching {
                            exactAlarmSettingsLauncher
                                .launch(intent)
                        }.onFailure {
                            viewModel
                                .onPlatformLaunchFailed()
                        }
                    },
                    modifier =
                        Modifier.testTag(
                            "exact_alarm_rationale_continue",
                        ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .open_device_settings,
                            ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick =
                        viewModel::continueWithoutPermissionChanges,
                    modifier =
                        Modifier.testTag(
                            "exact_alarm_rationale_cancel",
                        ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .continue_with_approximate_reminders,
                            ),
                    )
                }
            },
            modifier =
                Modifier.testTag(
                    "exact_alarm_rationale",
                ),
        )
    }
}

@Composable
fun ReminderSettingsScreen(
    state: ReminderSettingsUiState,
    onBack: () -> Unit,
    onRemindersEnabledChanged:
        (Boolean) -> Unit,
    onRequestNotificationPermission:
        () -> Unit,
    onOpenNotificationSettings:
        () -> Unit,
    onRequestExactAlarmAccess:
        () -> Unit,
    onOpenBatterySettings: () -> Unit = {},
    onReviewSchedules: () -> Unit,
    onContinueAnyway: () -> Unit = {},
    onShowOemGuidance: () -> Unit = {},
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(24.dp),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            TextButton(
                onClick = onBack,
                modifier =
                    Modifier.testTag(
                        "reminder_settings_back",
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.back,
                        ),
                )
            }

            Text(
                text =
                    stringResource(
                        R.string
                            .reminder_settings_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .reminder_settings_intro,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                modifier =
                    Modifier.testTag(
                        "reminder_settings_intro",
                    ),
            )

            ReminderToggleCard(
                state = state,
                onRemindersEnabledChanged =
                    onRemindersEnabledChanged,
            )

            ReadinessSummaryCard(
                readiness =
                    state.readiness,
            )

            ReminderStatusCard(
                title =
                    stringResource(
                        R.string
                            .notification_permission_status_label,
                    ),
                body =
                    notificationPermissionText(
                        state =
                            state
                                .notificationPermissionState,
                    ),
                testTag =
                    "notification_permission_status",
            )

            ReminderStatusCard(
                title =
                    stringResource(
                        R.string
                            .reminder_delivery_mode_label,
                    ),
                body =
                    reminderAvailabilityText(
                        state =
                            state,
                    ),
                testTag =
                    "reminder_delivery_status",
            )

            PermissionActionSection(
                state = state,
                onRequestNotificationPermission =
                    onRequestNotificationPermission,
                onOpenNotificationSettings =
                    onOpenNotificationSettings,
                onRequestExactAlarmAccess =
                    onRequestExactAlarmAccess,
                onReviewSchedules =
                    onReviewSchedules,
                onContinueAnyway =
                    onContinueAnyway,
                onShowOemGuidance =
                    onShowOemGuidance,
            )

            BatteryGuidanceSection(
                state =
                    state,
                onOpenBatterySettings =
                    onOpenBatterySettings,
            )

            if (
                state.showOemGuidance
            ) {
                OemGuidanceSection(
                    guidance =
                        state
                            .readiness
                            ?.manufacturerGuidance,
                )
            }

            Text(
                text =
                    stringResource(
                        R.string
                            .reminder_delivery_limitations,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                modifier =
                    Modifier.testTag(
                        "reminder_delivery_limitations",
                    ),
            )

            if (
                state.isLoading ||
                state.isApplying
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                "reminder_settings_loading",
                            ),
                    horizontalArrangement =
                        Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage?.let {
                    errorMessage ->
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                "reminder_settings_error",
                            ),
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                16.dp,
                            ),
                    ) {
                        Text(
                            text = errorMessage,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    12.dp,
                                ),
                        )

                        Button(
                            onClick = onRetry,
                            modifier =
                                Modifier.testTag(
                                    "reminder_settings_retry",
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
    }
}

@Composable
private fun ReadinessSummaryCard(
    readiness: ReminderReadiness?,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "reminder_readiness_summary",
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
                        R.string
                            .reminder_readiness_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )

            Text(
                text =
                    readiness
                        ?.message
                        ?: stringResource(
                            R.string
                                .reminder_readiness_loading,
                        ),
                modifier =
                    Modifier.testTag(
                        "reminder_readiness_message",
                    ),
            )

            Text(
                text =
                    readinessStatusText(
                        readiness =
                            readiness,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                modifier =
                    Modifier.testTag(
                        "reminder_readiness_status",
                    ),
            )
        }
    }
}

@Composable
private fun PermissionActionSection(
    state: ReminderSettingsUiState,
    onRequestNotificationPermission:
        () -> Unit,
    onOpenNotificationSettings:
        () -> Unit,
    onRequestExactAlarmAccess:
        () -> Unit,
    onReviewSchedules: () -> Unit,
    onContinueAnyway: () -> Unit,
    onShowOemGuidance: () -> Unit,
) {
    if (
        state.remindersEnabled &&
        state
            .notificationPermissionState ==
        NotificationPermissionUiState
            .DENIED
    ) {
        Button(
            onClick =
                onRequestNotificationPermission,
            enabled =
                !state.isApplying,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "request_notification_permission",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .request_notification_permission,
                    ),
            )
        }

        OutlinedButton(
            onClick =
                onOpenNotificationSettings,
            enabled =
                !state.isApplying,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "open_notification_settings",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .open_notification_settings,
                    ),
            )
        }

        TextButton(
            onClick =
                onContinueAnyway,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "continue_without_permissions",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .continue_without_permissions,
                    ),
            )
        }
    }

    if (
        state.remindersEnabled &&
        state
            .notificationPermissionState !=
        NotificationPermissionUiState
            .DENIED &&
        !state.hasActiveSchedule
    ) {
        OutlinedButton(
            onClick =
                onReviewSchedules,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "review_schedules_from_reminders",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .review_schedules,
                    ),
            )
        }
    }

    if (
        state.remindersEnabled &&
        state
            .notificationPermissionState !=
        NotificationPermissionUiState
            .DENIED &&
        state.hasActiveSchedule &&
        state
            .readiness
            ?.exactAlarm ==
        ExactAlarmReadiness
            .UNAVAILABLE
    ) {
        Button(
            onClick =
                onRequestExactAlarmAccess,
            enabled =
                !state.isApplying,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "request_exact_alarm_access",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .request_exact_alarm_access,
                    ),
            )
        }

        TextButton(
            onClick =
                onContinueAnyway,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "continue_with_approximate_reminders",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .continue_with_approximate_reminders,
                    ),
            )
        }
    }

    if (
        state
            .readiness
            ?.manufacturerGuidanceNeeded == true
    ) {
        OutlinedButton(
            onClick =
                onShowOemGuidance,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "view_oem_guidance",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .view_oem_guidance,
                    ),
            )
        }
    }
}

@Composable
private fun ReminderToggleCard(
    state: ReminderSettingsUiState,
    onRemindersEnabledChanged:
        (Boolean) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "reminder_toggle_card",
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier.weight(1f),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .reminders_enabled_label,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            4.dp,
                        ),
                )

                Text(
                    text =
                        stringResource(
                            R.string
                                .reminders_enabled_description,
                        ),
                )
            }

            Switch(
                checked =
                    state.remindersEnabled,
                onCheckedChange =
                    onRemindersEnabledChanged,
                enabled =
                    !state.isApplying &&
                            !state.isLoading,
                modifier =
                    Modifier.testTag(
                        "reminders_enabled_switch",
                    ),
            )
        }
    }
}

@Composable
private fun ReminderStatusCard(
    title: String,
    body: String,
    testTag: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(testTag),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            Text(text = body)
        }
    }
}

@Composable
private fun BatteryGuidanceSection(
    state: ReminderSettingsUiState,
    onOpenBatterySettings: () -> Unit,
) {
    val batteryText =
        when (
            state
                .readiness
                ?.batteryOptimizationState
        ) {
            BatteryOptimizationState.IGNORED -> {
                stringResource(
                    R.string
                        .battery_optimization_ignored,
                )
            }

            BatteryOptimizationState.NOT_IGNORED -> {
                stringResource(
                    R.string
                        .battery_optimization_not_ignored,
                )
            }

            BatteryOptimizationState.UNKNOWN,
            null,
                -> {
                stringResource(
                    R.string
                        .battery_optimization_unknown,
                )
            }
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "battery_guidance_card",
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
                    stringResource(
                        R.string
                            .battery_guidance_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .battery_guidance_body,
                    ),
            )

            Text(
                text =
                    batteryText,
                modifier =
                    Modifier.testTag(
                        "battery_optimization_status",
                    ),
            )

            OutlinedButton(
                onClick =
                    onOpenBatterySettings,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "open_battery_settings",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .open_battery_settings,
                        ),
                )
            }
        }
    }
}

@Composable
private fun OemGuidanceSection(
    guidance: ManufacturerGuidance?,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "oem_guidance_card",
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
                    guidance
                        ?.title
                        ?: stringResource(
                            R.string
                                .oem_guidance_title,
                        ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )

            Text(
                text =
                    guidance
                        ?.body
                        ?: stringResource(
                            R.string
                                .generic_oem_guidance_body,
                        ),
                modifier =
                    Modifier.testTag(
                        "oem_guidance_body",
                    ),
            )

            guidance
                ?.actionItems
                .orEmpty()
                .forEachIndexed {
                        index,
                        actionItem ->
                    Text(
                        text =
                            "• $actionItem",
                        modifier =
                            Modifier.testTag(
                                "oem_guidance_action_$index",
                            ),
                    )
                }
        }
    }
}

@Composable
private fun notificationPermissionText(
    state: NotificationPermissionUiState,
): String {
    return stringResource(
        when (state) {
            NotificationPermissionUiState
                .NOT_REQUIRED -> {
                R.string
                    .notification_permission_not_required
            }

            NotificationPermissionUiState
                .GRANTED -> {
                R.string
                    .notification_permission_granted
            }

            NotificationPermissionUiState
                .DENIED -> {
                R.string
                    .notification_permission_denied
            }
        },
    )
}

@Composable
private fun reminderAvailabilityText(
    state: ReminderSettingsUiState,
): String {
    val readiness =
        state.readiness

    if (
        readiness
            ?.approximateFallbackAvailable == true
    ) {
        return stringResource(
            R.string
                .reminder_mode_approximate,
        )
    }

    return stringResource(
        when (state.availability) {
            ReminderAvailability.DISABLED -> {
                R.string
                    .reminder_mode_disabled
            }

            ReminderAvailability
                .NOTIFICATION_PERMISSION_REQUIRED -> {
                R.string
                    .reminder_mode_notification_unavailable
            }

            ReminderAvailability
                .NO_ACTIVE_SCHEDULE -> {
                R.string
                    .reminder_mode_no_active_schedule
            }

            ReminderAvailability.EXACT -> {
                R.string
                    .reminder_mode_exact
            }

            ReminderAvailability.APPROXIMATE -> {
                R.string
                    .reminder_mode_approximate
            }
        },
    )
}

@Composable
private fun readinessStatusText(
    readiness: ReminderReadiness?,
): String {
    return when (
        readiness?.status
    ) {
        ReminderReadinessStatus.READY -> {
            stringResource(
                R.string
                    .reminder_readiness_ready,
            )
        }

        ReminderReadinessStatus.REMINDERS_DISABLED -> {
            stringResource(
                R.string
                    .reminder_readiness_disabled,
            )
        }

        ReminderReadinessStatus.NO_ACTIVE_SCHEDULE -> {
            stringResource(
                R.string
                    .reminder_readiness_no_schedule,
            )
        }

        ReminderReadinessStatus.NOTIFICATION_PERMISSION_REQUIRED -> {
            stringResource(
                R.string
                    .reminder_readiness_notification_required,
            )
        }

        ReminderReadinessStatus.EXACT_ALARM_ACCESS_RECOMMENDED -> {
            stringResource(
                R.string
                    .reminder_readiness_exact_recommended,
            )
        }

        ReminderReadinessStatus.APPROXIMATE_DELIVERY -> {
            stringResource(
                R.string
                    .reminder_readiness_approximate,
            )
        }

        ReminderReadinessStatus.BATTERY_GUIDANCE_RECOMMENDED -> {
            stringResource(
                R.string
                    .reminder_readiness_battery,
            )
        }

        ReminderReadinessStatus.OEM_GUIDANCE_RECOMMENDED -> {
            stringResource(
                R.string
                    .reminder_readiness_oem,
            )
        }

        null -> {
            stringResource(
                R.string
                    .reminder_readiness_loading,
            )
        }
    }
}

private fun ReminderSettingsUiState.withPlatformReadiness(
    batteryOptimizationState:
    BatteryOptimizationState,
    manufacturer: String?,
): ReminderSettingsUiState {
    if (isLoading) {
        return this
    }

    val notificationPermissionGranted =
        notificationPermissionState ==
                NotificationPermissionUiState.GRANTED ||
                notificationPermissionState ==
                NotificationPermissionUiState.NOT_REQUIRED

    return copy(
        readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled =
                    remindersEnabled,
                hasActiveSchedule =
                    hasActiveSchedule,
                notificationRuntimePermissionRequired =
                    notificationRuntimePermissionRequired,
                notificationPermissionGranted =
                    notificationPermissionGranted,
                canScheduleExactAlarms =
                    exactAlarmCapabilityGranted,
                exactAlarmRelevant =
                    remindersEnabled &&
                            hasActiveSchedule,
                batteryOptimizationState =
                    batteryOptimizationState,
                manufacturer =
                    manufacturer,
            ),
    )
}

private fun permissionUiStateFor(
    runtimePermissionRequired: Boolean,
    status: ReminderStatus?,
): NotificationPermissionUiState =
    when {
        !runtimePermissionRequired -> {
            NotificationPermissionUiState
                .NOT_REQUIRED
        }

        status
            ?.notificationPermissionGranted ==
                true -> {
            NotificationPermissionUiState
                .GRANTED
        }

        else -> {
            NotificationPermissionUiState
                .DENIED
        }
    }
