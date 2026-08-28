package ir.carepack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import ir.carepack.domain.reminder.TimezoneWarning
import ir.carepack.feature.careplan.ArchivedMedicationListScreen
import ir.carepack.feature.careplan.ArchivedMedicationListUiState
import ir.carepack.feature.reminder.TimezoneWarningBanner
import ir.carepack.feature.settings.SettingsScreen
import ir.carepack.feature.settings.SettingsUiState
import ir.carepack.ui.theme.CarePackTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FinalProductAlignmentComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun timezoneWarning_isNonblockingAndProvidesReviewAndDismiss() {
        val reviewed = AtomicBoolean(false)
        val dismissed = AtomicBoolean(false)
        composeRule.setContent {
            CarePackTheme {
                TimezoneWarningBanner(
                    warning = TimezoneWarning(
                        previousZoneId = "Asia/Tehran",
                        currentZoneId = "Europe/Berlin",
                    ),
                    errorMessage = null,
                    onReviewSchedules = { reviewed.set(true) },
                    onDismiss = { dismissed.set(true) },
                )
            }
        }

        composeRule.onNodeWithTag("timezone_warning_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("timezone_warning_review").performClick()
        composeRule.onNodeWithTag("timezone_warning_dismiss").performClick()
        assertTrue(reviewed.get())
        assertTrue(dismissed.get())
    }

    @Test
    fun archiveEmptyState_isExplicit() {
        composeRule.setContent {
            CarePackTheme {
                ArchivedMedicationListScreen(
                    state = ArchivedMedicationListUiState(isLoading = false),
                    onBack = {},
                    onRetry = {},
                    onOpenMedication = {},
                )
            }
        }
        composeRule.onNodeWithTag("archive_empty").assertIsDisplayed()
    }

    @Test
    fun settingsRoot_hasRecipientEntryAndNoBackOrReportEntry() {
        composeRule.setContent {
            CarePackTheme {
                SettingsScreen(
                    state = SettingsUiState(recipientDisplayName = "مادر"),
                    onEditRecipient = {},
                    onOpenReminderSettings = {},
                    onOpenPrivacy = {},
                    onDeleteAllData = {},
                    onFirstDayOfWeekPreferenceChanged = {},
                    onSeniorModeChanged = {},
                )
            }
        }
        composeRule.onNodeWithTag("settings_edit_recipient").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("settings_back")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag("settings_today_report")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty(),
        )
    }
}