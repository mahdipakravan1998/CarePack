package ir.carepack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import ir.carepack.domain.reminder.TimezoneWarning
import ir.carepack.domain.careplan.ArchivedMedication
import ir.carepack.feature.careplan.ArchivedMedicationListScreen
import ir.carepack.feature.careplan.ArchivedMedicationListUiState
import ir.carepack.feature.careplan.ArchivedMedicationDetailScreen
import ir.carepack.feature.careplan.ArchivedMedicationDetailUiState
import ir.carepack.feature.reminder.TimezoneWarningBanner
import ir.carepack.feature.settings.SettingsScreen
import ir.carepack.feature.settings.SettingsUiState
import ir.carepack.ui.theme.CarePackTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Instant
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

    @Test
    fun archivedDetail_showsMedicationLifecycleContextAndOnlyPermanentDeleteMutation() {
        composeRule.setContent {
            CarePackTheme {
                ArchivedMedicationDetailScreen(
                    state = ArchivedMedicationDetailUiState(
                        isLoading = false,
                        medication = ArchivedMedication(
                            medicationId = "archived-1",
                            name = "لوزارتان",
                            instruction = "پس از صبحانه",
                            medicationType = "tablet",
                            dosageText = "50",
                            doseUnit = "mg",
                            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                            endedAt = Instant.parse("2026-02-01T00:00:00Z"),
                            archivedAt = Instant.parse("2026-03-01T00:00:00Z"),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onDeleteMedication = {},
                )
            }
        }

        composeRule.onNodeWithTag("archive_detail_name").assertIsDisplayed()
        composeRule.onNodeWithTag("archive_detail_ended_at").assertIsDisplayed()
        composeRule.onNodeWithTag("archive_detail_archived_at").assertIsDisplayed()
        composeRule.onNodeWithTag("archive_delete_permanently").assertIsDisplayed()
        listOf("archive_restore", "archive_edit_medication", "archive_add_schedule", "archive_edit_schedule")
            .forEach { tag ->
                assertTrue(
                    composeRule.onAllNodesWithTag(tag)
                        .fetchSemanticsNodes(atLeastOneRootRequired = false)
                        .isEmpty(),
                )
            }
    }
}
