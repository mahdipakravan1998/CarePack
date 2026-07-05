package ir.carepack.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import ir.carepack.R
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderReadinessPolicy
import ir.carepack.feature.reminder.NotificationPermissionUiState
import ir.carepack.feature.reminder.ReminderSettingsScreen
import ir.carepack.feature.reminder.ReminderSettingsUiState
import ir.carepack.feature.today.TodayScreen
import ir.carepack.feature.today.TodayUiState
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.ui.theme.CarePackTheme
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReminderComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    private val context: Context
        get() =
            ApplicationProvider
                .getApplicationContext()

    @Test
    fun disabledReminders_showNoPermissionOrExactAccessActions() {
        composeRule.setContent {
            CarePackTheme {
                ReminderSettingsScreen(
                    state =
                        reminderState(
                            remindersEnabled = false,
                            permissionState =
                                NotificationPermissionUiState
                                    .DENIED,
                            hasActiveSchedule = true,
                            exactAlarmCapabilityGranted = false,
                            availability =
                                ReminderAvailability
                                    .DISABLED,
                            batteryOptimizationState =
                                BatteryOptimizationState.UNKNOWN,
                            manufacturer = "Google",
                        ),
                    onBack = {},
                    onRemindersEnabledChanged = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationSettings = {},
                    onRequestExactAlarmAccess = {},
                    onReviewSchedules = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "reminders_enabled_switch",
            )
            .assertIsDisplayed()
            .assertIsOff()

        assertTagDoesNotExist(
            "request_notification_permission",
        )

        assertTagDoesNotExist(
            "request_exact_alarm_access",
        )

        composeRule
            .onNodeWithTag(
                "reminder_delivery_status",
            )
            .assertIsDisplayed()
    }

    @Test
    fun deniedNotificationPermission_showsContextualActionAndKeepsToggleUsable() {
        val permissionRequests =
            AtomicInteger(0)

        val toggleValue =
            AtomicBoolean(true)

        composeRule.setContent {
            CarePackTheme {
                ReminderSettingsScreen(
                    state =
                        reminderState(
                            remindersEnabled = true,
                            permissionState =
                                NotificationPermissionUiState
                                    .DENIED,
                            hasActiveSchedule = true,
                            exactAlarmCapabilityGranted = false,
                            availability =
                                ReminderAvailability
                                    .NOTIFICATION_PERMISSION_REQUIRED,
                            batteryOptimizationState =
                                BatteryOptimizationState.UNKNOWN,
                            manufacturer = "Google",
                        ),
                    onBack = {},
                    onRemindersEnabledChanged = {
                            enabled ->
                        toggleValue.set(
                            enabled,
                        )
                    },
                    onRequestNotificationPermission = {
                        permissionRequests
                            .incrementAndGet()
                    },
                    onOpenNotificationSettings = {},
                    onRequestExactAlarmAccess = {},
                    onReviewSchedules = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "reminders_enabled_switch",
            )
            .assertIsOn()
            .performClick()

        assertFalse(
            toggleValue.get(),
        )

        composeRule
            .onNodeWithTag(
                "request_notification_permission",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            1,
            permissionRequests.get(),
        )

        composeRule
            .onNodeWithTag(
                "notification_permission_status",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "continue_without_permissions",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun approximateMode_showsTruthfulStatusAndExactAccessAction() {
        composeRule.setContent {
            CarePackTheme {
                ReminderSettingsScreen(
                    state =
                        reminderState(
                            remindersEnabled = true,
                            permissionState =
                                NotificationPermissionUiState
                                    .GRANTED,
                            hasActiveSchedule = true,
                            exactAlarmCapabilityGranted = false,
                            availability =
                                ReminderAvailability
                                    .APPROXIMATE,
                            batteryOptimizationState =
                                BatteryOptimizationState.IGNORED,
                            manufacturer = "Google",
                        ),
                    onBack = {},
                    onRemindersEnabledChanged = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationSettings = {},
                    onRequestExactAlarmAccess = {},
                    onReviewSchedules = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "reminder_delivery_status",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "request_exact_alarm_access",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "continue_with_approximate_reminders",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "reminder_delivery_limitations",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                context.getString(
                    R.string
                        .reminder_delivery_limitations,
                ),
            )
    }

    @Test
    fun exactAccessAction_isHiddenWithoutRealSchedule() {
        composeRule.setContent {
            CarePackTheme {
                ReminderSettingsScreen(
                    state =
                        reminderState(
                            remindersEnabled = true,
                            permissionState =
                                NotificationPermissionUiState
                                    .GRANTED,
                            hasActiveSchedule = false,
                            exactAlarmCapabilityGranted = false,
                            availability =
                                ReminderAvailability
                                    .NO_ACTIVE_SCHEDULE,
                            batteryOptimizationState =
                                BatteryOptimizationState.UNKNOWN,
                            manufacturer = "Google",
                        ),
                    onBack = {},
                    onRemindersEnabledChanged = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationSettings = {},
                    onRequestExactAlarmAccess = {},
                    onReviewSchedules = {},
                    onRetry = {},
                )
            }
        }

        assertTagDoesNotExist(
            "request_exact_alarm_access",
        )

        composeRule
            .onNodeWithTag(
                "review_schedules_from_reminders",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun batteryOptimizationNotIgnored_showsBatteryGuidance() {
        composeRule.setContent {
            CarePackTheme {
                ReminderSettingsScreen(
                    state =
                        reminderState(
                            remindersEnabled = true,
                            permissionState =
                                NotificationPermissionUiState
                                    .GRANTED,
                            hasActiveSchedule = true,
                            exactAlarmCapabilityGranted = true,
                            availability =
                                ReminderAvailability.EXACT,
                            batteryOptimizationState =
                                BatteryOptimizationState
                                    .NOT_IGNORED,
                            manufacturer = "Google",
                        ),
                    onBack = {},
                    onRemindersEnabledChanged = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationSettings = {},
                    onRequestExactAlarmAccess = {},
                    onOpenBatterySettings = {},
                    onReviewSchedules = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "battery_guidance_card",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "battery_optimization_status",
            )
            .assertIsDisplayed()
            .assertTextEquals(
                context.getString(
                    R.string
                        .battery_optimization_not_ignored,
                ),
            )

        composeRule
            .onNodeWithTag(
                "open_battery_settings",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun xiaomiGuidance_isActionableAndManufacturerSpecific() {
        composeRule.setContent {
            CarePackTheme {
                ReminderSettingsScreen(
                    state =
                        reminderState(
                            remindersEnabled = true,
                            permissionState =
                                NotificationPermissionUiState
                                    .GRANTED,
                            hasActiveSchedule = true,
                            exactAlarmCapabilityGranted = true,
                            availability =
                                ReminderAvailability.EXACT,
                            batteryOptimizationState =
                                BatteryOptimizationState.IGNORED,
                            manufacturer = "XIAOMI",
                        ),
                    onBack = {},
                    onRemindersEnabledChanged = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationSettings = {},
                    onRequestExactAlarmAccess = {},
                    onReviewSchedules = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "oem_guidance_card",
            )
            .performScrollTo()
            .assertIsDisplayed()

        val body =
            composeRule
                .onNodeWithTag(
                    "oem_guidance_body",
                )

        body.assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "oem_guidance_action_0",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "oem_guidance_action_1",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "view_oem_guidance",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun samsungGuidance_isActionableAndManufacturerSpecific() {
        composeRule.setContent {
            CarePackTheme {
                ReminderSettingsScreen(
                    state =
                        reminderState(
                            remindersEnabled = true,
                            permissionState =
                                NotificationPermissionUiState
                                    .GRANTED,
                            hasActiveSchedule = true,
                            exactAlarmCapabilityGranted = true,
                            availability =
                                ReminderAvailability.EXACT,
                            batteryOptimizationState =
                                BatteryOptimizationState.IGNORED,
                            manufacturer = "SAMSUNG",
                        ),
                    onBack = {},
                    onRemindersEnabledChanged = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationSettings = {},
                    onRequestExactAlarmAccess = {},
                    onReviewSchedules = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "oem_guidance_card",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "oem_guidance_action_0",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "oem_guidance_action_1",
            )
            .assertIsDisplayed()
    }

    @Test
    fun genericGuidance_isVisibleForUnknownManufacturerWithoutGuaranteedClaim() {
        composeRule.setContent {
            CarePackTheme {
                ReminderSettingsScreen(
                    state =
                        reminderState(
                            remindersEnabled = true,
                            permissionState =
                                NotificationPermissionUiState
                                    .GRANTED,
                            hasActiveSchedule = true,
                            exactAlarmCapabilityGranted = true,
                            availability =
                                ReminderAvailability.EXACT,
                            batteryOptimizationState =
                                BatteryOptimizationState.UNKNOWN,
                            manufacturer = "Unknown",
                        ),
                    onBack = {},
                    onRemindersEnabledChanged = {},
                    onRequestNotificationPermission = {},
                    onOpenNotificationSettings = {},
                    onRequestExactAlarmAccess = {},
                    onReviewSchedules = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "oem_guidance_card",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "oem_guidance_body",
            )
            .assertIsDisplayed()
            .assertTextEquals(
                context.getString(
                    R.string
                        .generic_oem_guidance_body,
                ),
            )
    }

    @Test
    fun todayScreen_keepsCoreActionsAvailableWhenEmpty() {
        val carePlanOpenCount =
            AtomicInteger(0)

        composeRule.setContent {
            CarePackTheme {
                TodayScreen(
                    state =
                        TodayUiState(
                            localDate =
                                TEST_DATE,
                            isLoading =
                                false,
                            items =
                                emptyList(),
                            emptyState =
                                TodayEmptyState
                                    .NO_OCCURRENCES,
                            isHistoryLoading =
                                false,
                        ),
                    onTodaySelected = {},
                    onHistorySelected = {},
                    onRetry = {},
                    onOpenCarePlan = {
                        carePlanOpenCount
                            .incrementAndGet()
                    },
                    onOpenSettings = {},
                    onOpenOccurrence = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "today_empty",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "today_screen",
            )
            .assertIsDisplayed()
    }

    private fun reminderState(
        remindersEnabled: Boolean,
        permissionState:
        NotificationPermissionUiState,
        hasActiveSchedule: Boolean,
        exactAlarmCapabilityGranted: Boolean,
        availability: ReminderAvailability,
        batteryOptimizationState:
        BatteryOptimizationState,
        manufacturer: String?,
    ): ReminderSettingsUiState {
        val permissionGranted =
            permissionState ==
                    NotificationPermissionUiState
                        .GRANTED ||
                    permissionState ==
                    NotificationPermissionUiState
                        .NOT_REQUIRED

        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled =
                    remindersEnabled,
                hasActiveSchedule =
                    hasActiveSchedule,
                notificationRuntimePermissionRequired =
                    permissionState !=
                            NotificationPermissionUiState
                                .NOT_REQUIRED,
                notificationPermissionGranted =
                    permissionGranted,
                canScheduleExactAlarms =
                    exactAlarmCapabilityGranted,
                exactAlarmRelevant =
                    remindersEnabled &&
                            hasActiveSchedule,
                batteryOptimizationState =
                    batteryOptimizationState,
                manufacturer =
                    manufacturer,
            )

        return ReminderSettingsUiState(
            isLoading = false,
            remindersEnabled =
                remindersEnabled,
            notificationPermissionState =
                permissionState,
            notificationRuntimePermissionRequired =
                permissionState !=
                        NotificationPermissionUiState
                            .NOT_REQUIRED,
            hasActiveSchedule =
                hasActiveSchedule,
            exactAlarmCapabilityGranted =
                exactAlarmCapabilityGranted,
            availability =
                availability,
            readiness =
                readiness,
        )
    }

    private fun assertTagDoesNotExist(
        tag: String,
    ) {
        val nodes =
            composeRule
                .onAllNodesWithTag(
                    testTag = tag,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )

        assertTrue(
            nodes.isEmpty(),
        )
    }

    private companion object {
        val TEST_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )
    }
}
