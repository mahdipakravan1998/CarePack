package ir.carepack.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.feature.deletion.DeleteAllDataScreen
import ir.carepack.feature.deletion.DeleteAllDataUiState
import ir.carepack.feature.privacy.PrivacyScreen
import ir.carepack.feature.reporting.TodayReportScreen
import ir.carepack.feature.reporting.TodayReportUiState
import ir.carepack.settings.deletion.DataDeletionStage
import ir.carepack.ui.theme.CarePackTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
                                currentDensity.density,
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
                                includeRecipientName = false,
                                reportText =
                                    REPORT_TEXT,
                            ),
                        snackbarHostState =
                            SnackbarHostState(),
                        onIncludeRecipientNameChanged = {
                                includeRecipientName ->
                            includeNameValue =
                                includeRecipientName
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
    fun privacyScreen_isLocalOnlyAndDoesNotExposeExternalPolicyAction() {
        var backCount =
            0

        composeRule.setContent {
            CarePackTheme {
                PrivacyScreen(
                    onBack = {
                        backCount += 1
                    },
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "privacy_screen",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "privacy_title",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "privacy_summary",
            )
            .assertIsDisplayed()

        assertTrue(
            composeRule
                .onAllNodesWithTag(
                    "privacy_policy_button",
                )
                .fetchSemanticsNodes()
                .isEmpty(),
        )

        composeRule
            .onNodeWithTag(
                "privacy_back",
            )
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                1,
                backCount,
            )
        }
    }

    @Test
    fun deleteEverything_confirmationProgressAndFailureStatesAreExplicit() {
        val state =
            mutableStateOf(
                DeleteAllDataUiState(),
            )

        var confirmCount =
            0

        var retryCount =
            0

        composeRule.setContent {
            CarePackTheme {
                DeleteAllDataScreen(
                    state =
                        state.value,
                    onRequestDeletion = {
                        state.value =
                            state
                                .value
                                .copy(
                                    showConfirmation =
                                        true,
                                )
                    },
                    onDismissConfirmation = {
                        state.value =
                            state
                                .value
                                .copy(
                                    showConfirmation =
                                        false,
                                )
                    },
                    onConfirmDeletion = {
                        confirmCount += 1
                        state.value =
                            state
                                .value
                                .copy(
                                    showConfirmation =
                                        false,
                                    isDeleting =
                                        true,
                                )
                    },
                    onRetry = {
                        retryCount += 1
                    },
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "delete_all_data_request",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithTag(
                "delete_all_data_confirmation",
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

            state.value =
                DeleteAllDataUiState(
                    failedStage =
                        DataDeletionStage
                            .CLEARING_DOMAIN_DATA,
                )
        }

        composeRule
            .onNodeWithTag(
                "delete_all_data_error",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "delete_all_data_retry",
            )
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                1,
                retryCount,
            )
        }
    }

    private companion object {
        val REPORT_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )

        const val REPORT_TEXT =
            "گزارش امروز کرپک\nداروی تست: داده شده"
    }
}
