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
internal fun ReminderHealthCard(
    health: ReminderHealth,
    onRetry: () -> Unit,
) {
    val message =
        when (health) {
            ReminderHealth.Healthy ->
                return

            is ReminderHealth.PendingRetry ->
                stringResource(
                    R.string.reminder_health_pending_retry,
                )

            is ReminderHealth.Unavailable ->
                stringResource(
                    R.string.reminder_health_unavailable,
                )
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(
                    "reminder_health_card",
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
                text = message,
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            Button(
                onClick = onRetry,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "reminder_health_retry",
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

@Composable
internal fun ReminderTestCard(
    state: ReminderSettingsUiState,
    onSchedule: () -> Unit,
) {
    val experience =
        carePackExperience()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "reminder_test_card",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .reminder_test_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                modifier =
                    Modifier.carePackHeading(),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .reminder_test_description,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
            )

            Button(
                onClick = onSchedule,
                enabled =
                    !state.isSchedulingTest,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .carePackPrimaryAction()
                        .testTag(
                            "schedule_reminder_test",
                        ),
            ) {
                if (state.isSchedulingTest) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(
                                24.dp,
                            ),
                    )
                } else {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .reminder_test_action,
                            ),
                    )
                }
            }

            val statusText =
                when (state.reminderTestStatus) {
                    ReminderTestUiStatus.IDLE -> null

                    ReminderTestUiStatus.SCHEDULED_EXACT ->
                        stringResource(
                            R.string
                                .reminder_test_scheduled_exact,
                        )

                    ReminderTestUiStatus.SCHEDULED_APPROXIMATE ->
                        stringResource(
                            R.string
                                .reminder_test_scheduled_approximate,
                        )

                    ReminderTestUiStatus
                        .NOTIFICATION_PERMISSION_REQUIRED ->
                        stringResource(
                            R.string
                                .reminder_test_permission_required,
                        )

                    ReminderTestUiStatus.SCHEDULING_UNAVAILABLE ->
                        stringResource(
                            R.string
                                .reminder_test_scheduling_unavailable,
                        )
                }

            statusText?.let { message ->
                Text(
                    text = message,
                    color =
                        when (state.reminderTestStatus) {
                            ReminderTestUiStatus.SCHEDULED_EXACT,
                            ReminderTestUiStatus.SCHEDULED_APPROXIMATE,
                                -> MaterialTheme
                                .colorScheme
                                .primary

                            else -> MaterialTheme
                                .colorScheme
                                .error
                        },
                    modifier =
                        Modifier
                            .carePackPoliteLiveRegion()
                            .testTag(
                                "reminder_test_status",
                            ),
                )
            }

            Text(
                text =
                    stringResource(
                        R.string
                            .reminder_test_delivery_notice,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
            )
        }
    }
}

@Composable
internal fun ReadinessSummaryCard(
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
internal fun PermissionActionSection(
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
internal fun ReminderToggleCard(
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
internal fun ReminderStatusCard(
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
internal fun BatteryGuidanceSection(
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
internal fun OemGuidanceSection(
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
internal fun notificationPermissionText(
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
internal fun reminderAvailabilityText(
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
internal fun readinessStatusText(
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

internal fun ReminderSettingsUiState.withPlatformReadiness(
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
