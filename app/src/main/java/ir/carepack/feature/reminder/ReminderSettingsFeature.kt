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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderStatus
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
        NotificationPermissionUiState
            .DENIED,
    val notificationRuntimePermissionRequired:
    Boolean = false,
    val hasActiveSchedule: Boolean = false,
    val exactAlarmCapabilityGranted:
    Boolean = false,
    val availability: ReminderAvailability =
        ReminderAvailability.DISABLED,
    val showNotificationRationale:
    Boolean = false,
    val showExactAlarmRationale:
    Boolean = false,
    val errorMessage: String? = null,
)

private data class ReminderSettingsTransientState(
    val isApplying: Boolean = false,
    val showNotificationRationale:
    Boolean = false,
    val showExactAlarmRationale:
    Boolean = false,
    val errorMessage: String? = null,
)

class ReminderSettingsViewModel(
    private val preferenceStore:
    ReminderPreferenceStore,
    private val reminderCoordinator:
    ReminderCoordinator,
    private val notificationPermissionGateway:
    NotificationPermissionGateway,
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
                showNotificationRationale =
                    transientState
                        .showNotificationRationale,
                showExactAlarmRationale =
                    transientState
                        .showExactAlarmRationale,
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

            mutableTransientState.update {
                    transient ->
                transient.copy(
                    showNotificationRationale =
                        enabled &&
                                notificationPermissionGateway
                                    .requiresRuntimePermission() &&
                                !status
                                    .notificationPermissionGranted,
                    showExactAlarmRationale =
                        false,
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
                    !currentState
                        .exactAlarmCapabilityGranted

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
        state = state,
        onBack = onBack,
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
        onReviewSchedules =
            onReviewSchedules,
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
                        viewModel::dismissNotificationPermissionExplanation,
                    modifier =
                        Modifier.testTag(
                            "notification_rationale_cancel",
                        ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .dismiss_for_later,
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
                        viewModel::dismissExactAlarmExplanation,
                    modifier =
                        Modifier.testTag(
                            "exact_alarm_rationale_cancel",
                        ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .dismiss_for_later,
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
    onReviewSchedules: () -> Unit,
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
                Text(text = "بازگشت")
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

            ReminderToggleCard(
                state = state,
                onRemindersEnabledChanged =
                    onRemindersEnabledChanged,
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
                        availability =
                            state.availability,
                    ),
                testTag =
                    "reminder_delivery_status",
            )

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
                !state
                    .exactAlarmCapabilityGranted
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
                            Text(text = "تلاش دوباره")
                        }
                    }
                }
            }
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
    availability: ReminderAvailability,
): String {
    return stringResource(
        when (availability) {
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
