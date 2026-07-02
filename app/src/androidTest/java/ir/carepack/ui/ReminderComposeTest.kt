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
import ir.carepack.feature.reminder.NotificationPermissionUiState
import ir.carepack.feature.reminder.ReminderSettingsScreen
import ir.carepack.feature.reminder.ReminderSettingsUiState
import ir.carepack.feature.today.TodayScreen
import ir.carepack.feature.today.TodayUiState
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
                        ReminderSettingsUiState(
                            isLoading = false,
                            remindersEnabled =
                                false,
                            notificationPermissionState =
                                NotificationPermissionUiState
                                    .DENIED,
                            notificationRuntimePermissionRequired =
                                true,
                            hasActiveSchedule =
                                true,
                            exactAlarmCapabilityGranted =
                                false,
                            availability =
                                ReminderAvailability
                                    .DISABLED,
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
                        ReminderSettingsUiState(
                            isLoading = false,
                            remindersEnabled =
                                true,
                            notificationPermissionState =
                                NotificationPermissionUiState
                                    .DENIED,
                            notificationRuntimePermissionRequired =
                                true,
                            hasActiveSchedule =
                                true,
                            exactAlarmCapabilityGranted =
                                false,
                            availability =
                                ReminderAvailability
                                    .NOTIFICATION_PERMISSION_REQUIRED,
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
    }

    @Test
    fun approximateMode_showsTruthfulStatusAndExactAccessAction() {
        composeRule.setContent {
            CarePackTheme {
                ReminderSettingsScreen(
                    state =
                        ReminderSettingsUiState(
                            isLoading = false,
                            remindersEnabled =
                                true,
                            notificationPermissionState =
                                NotificationPermissionUiState
                                    .GRANTED,
                            notificationRuntimePermissionRequired =
                                true,
                            hasActiveSchedule =
                                true,
                            exactAlarmCapabilityGranted =
                                false,
                            availability =
                                ReminderAvailability
                                    .APPROXIMATE,
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
                        ReminderSettingsUiState(
                            isLoading = false,
                            remindersEnabled =
                                true,
                            notificationPermissionState =
                                NotificationPermissionUiState
                                    .GRANTED,
                            notificationRuntimePermissionRequired =
                                true,
                            hasActiveSchedule =
                                false,
                            exactAlarmCapabilityGranted =
                                false,
                            availability =
                                ReminderAvailability
                                    .NO_ACTIVE_SCHEDULE,
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
