package ir.carepack.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import ir.carepack.feature.deletion.DeleteAllDataScreen
import ir.carepack.feature.deletion.DeleteAllDataUiState
import ir.carepack.feature.privacy.PrivacyExternalActionError
import ir.carepack.feature.privacy.PrivacyScreen
import ir.carepack.feature.privacy.PrivacyUiState
import ir.carepack.feature.reporting.TodayReportScreen
import ir.carepack.feature.reporting.TodayReportUiState
import ir.carepack.settings.deletion.DataDeletionStage
import ir.carepack.ui.theme.CarePackTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportingPrivacyDeletionComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun reportPreview_displaysExactTextAndExplicitActionsAtTwoHundredPercentFont() {
        var includeNameValue:
                Boolean? =
            null

        var copyCount =
            0

        var shareCount =
            0

        composeRule.setContent {
            val currentDensity =
                LocalDensity.current

            CompositionLocalProvider(
                LocalDensity provides
                        Density(
                            density =
                                currentDensity
                                    .density,
                            fontScale = 2f,
                        ),
            ) {
                CarePackTheme {
                    TodayReportScreen(
                        state =
                            TodayReportUiState(
                                date =
                                    REPORT_DATE,
                                isLoading = false,
                                includeRecipientName =
                                    false,
                                reportText =
                                    REPORT_TEXT,
                            ),
                        snackbarHostState =
                            remember {
                                SnackbarHostState()
                            },
                        onIncludeRecipientNameChanged = {
                            includeNameValue =
                                it
                        },
                        onCopy = {
                            copyCount += 1
                        },
                        onShare = {
                            shareCount += 1
                        },
                        onRetry = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(
                "today_report_preview",
            )
            .assertTextEquals(
                REPORT_TEXT,
            )
            .assertContentDescriptionEquals(
                REPORT_TEXT,
            )

        composeRule
            .onNodeWithTag(
                "today_report_include_name",
            )
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                true,
                includeNameValue,
            )
        }

        composeRule
            .onNodeWithTag(
                "today_report_copy",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithTag(
                "today_report_share",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                1,
                copyCount,
            )

            assertEquals(
                1,
                shareCount,
            )
        }
    }

    @Test
    fun privacyScreen_remainsUsefulWhenExternalPolicyCannotOpen() {
        var policyOpenCount =
            0

        var dismissCount =
            0

        composeRule.setContent {
            CarePackTheme {
                PrivacyScreen(
                    state =
                        PrivacyUiState(
                            externalActionError =
                                PrivacyExternalActionError
                                    .POLICY_ADDRESS_UNAVAILABLE,
                        ),
                    onOpenPublicPolicy = {
                        policyOpenCount += 1
                    },
                    onDismissExternalError = {
                        dismissCount += 1
                    },
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "privacy_local_storage",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "privacy_external_error",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "privacy_open_public_policy",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithTag(
                "privacy_external_error_dismiss",
            )
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                1,
                policyOpenCount,
            )

            assertEquals(
                1,
                dismissCount,
            )
        }
    }

    @Test
    fun deletion_requiresExplicitDestructiveConfirmation() {
        var requestCount =
            0

        var confirmCount =
            0

        var cancelCount =
            0

        composeRule.setContent {
            CarePackTheme {
                DeleteAllDataScreen(
                    state =
                        DeleteAllDataUiState(
                            showConfirmation =
                                true,
                        ),
                    onRequestDeletion = {
                        requestCount += 1
                    },
                    onDismissConfirmation = {
                        cancelCount += 1
                    },
                    onConfirmDeletion = {
                        confirmCount += 1
                    },
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "delete_all_data_confirmation",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "delete_all_data_confirmation_body",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "delete_all_data_confirm",
            )
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                1,
                confirmCount,
            )

            assertEquals(
                0,
                requestCount,
            )

            assertEquals(
                0,
                cancelCount,
            )
        }
    }

    @Test
    fun deletionProgress_doesNotExposeAnotherSuccessOrDeleteAction() {
        composeRule.setContent {
            CarePackTheme {
                DeleteAllDataScreen(
                    state =
                        DeleteAllDataUiState(
                            isDeleting = true,
                            deletionCompleted =
                                false,
                        ),
                    onRequestDeletion = {},
                    onDismissConfirmation = {},
                    onConfirmDeletion = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "delete_all_data_progress",
            )
            .performScrollTo()
            .assertIsDisplayed()

        val deleteActionNodes =
            composeRule
                .onAllNodesWithTag(
                    testTag =
                        "delete_all_data_request",
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )

        assertTrue(
            deleteActionNodes.isEmpty(),
        )
    }

    @Test
    fun deletionFailure_keepsRetryReachableAtLargeFontScale() {
        var retryCount =
            0

        composeRule.setContent {
            val currentDensity =
                LocalDensity.current

            CompositionLocalProvider(
                LocalDensity provides
                        Density(
                            density =
                                currentDensity
                                    .density,
                            fontScale = 2f,
                        ),
            ) {
                CarePackTheme {
                    DeleteAllDataScreen(
                        state =
                            DeleteAllDataUiState(
                                failedStage =
                                    DataDeletionStage
                                        .CLEARING_TEMPORARY_DATA,
                            ),
                        onRequestDeletion = {},
                        onDismissConfirmation = {},
                        onConfirmDeletion = {},
                        onRetry = {
                            retryCount += 1
                        },
                        onBack = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(
                "delete_all_data_retry",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                1,
                retryCount,
            )
        }
    }

    private companion object {

        val REPORT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                6,
                24,
            )

        const val REPORT_TEXT =
            "گزارش امروز کرپک\nتاریخ: 2026-06-24\nاین متن دقیق پیش‌نمایش است."
    }
}
