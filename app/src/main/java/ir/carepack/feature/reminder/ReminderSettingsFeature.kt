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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.CompositionLocalProvider
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
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.reminder.ExactAlarmReadiness
import ir.carepack.domain.reminder.ManufacturerGuidance
import ir.carepack.domain.reminder.NotificationPermissionReadiness
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderDeliveryMode
import ir.carepack.domain.reminder.ReminderHealth
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReadiness
import ir.carepack.domain.reminder.ReminderReadinessPolicy
import ir.carepack.domain.reminder.ReminderReadinessStatus
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.ReminderTestCoordinator
import ir.carepack.domain.reminder.ReminderTestScheduleResult
import ir.carepack.reminder.permission.AndroidBatteryOptimizationGateway
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import ir.carepack.ui.experience.carePackExperience
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock



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

    CompositionLocalProvider(
        LocalCarePackExperience provides
                CarePackExperience.forMode(
                    state.seniorMode,
                ),
    ) {
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
            onScheduleTestReminder =
                viewModel::scheduleTestReminder,
            onContinueAnyway =
                viewModel::continueWithoutPermissionChanges,
            onShowOemGuidance =
                viewModel::showOemGuidance,
            onRetry =
                viewModel::refreshPlatformState,
            onRetryHealth =
                viewModel::retryReminderHealth,
        )
    }

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
    onScheduleTestReminder: () -> Unit,
    onContinueAnyway: () -> Unit = {},
    onShowOemGuidance: () -> Unit = {},
    onRetry: () -> Unit,
    onRetryHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val experience =
        carePackExperience()

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
                    .padding(
                        horizontal =
                            experience
                                .screenHorizontalPadding,
                        vertical =
                            experience
                                .screenVerticalPadding,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    experience.sectionSpacing,
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

            if (state.health !is ReminderHealth.Healthy) {
                ReminderHealthCard(
                    health = state.health,
                    onRetry = onRetryHealth,
                )
            }

            ReminderTestCard(
                state = state,
                onSchedule =
                    onScheduleTestReminder,
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
