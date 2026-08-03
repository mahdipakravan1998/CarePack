package ir.carepack.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.report.RangeOccurrenceEntry
import ir.carepack.domain.report.RangeOccurrenceReportState
import ir.carepack.domain.report.RangeReportPeriod
import ir.carepack.domain.report.RangeSummaryBuilder
import ir.carepack.feature.reporting.RangeReportScreen
import ir.carepack.feature.reporting.RangeReportUiState
import ir.carepack.ui.theme.CarePackTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RangeReportComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun defaultSevenDayReportCanSwitchToThirtyDays() {
        val selectedPeriods =
            mutableListOf<RangeReportPeriod>()

        composeRule.setContent {
            CarePackTheme {
                RangeReportScreen(
                    state =
                        populatedState(
                            period =
                                RangeReportPeriod
                                    .SEVEN_DAYS,
                        ),
                    snackbarHostState =
                        SnackbarHostState(),
                    onBack = {},
                    onPeriodSelected = {
                        selectedPeriods += it
                    },
                    onIncludeRecipientNameChanged = {},
                    onCopyReport = {},
                    onShareReport = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "range_report_period_7",
            )
            .assertIsDisplayed()
            .assertIsSelected()

        composeRule
            .onNodeWithTag(
                "range_report_period_30",
            )
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            listOf(
                RangeReportPeriod.THIRTY_DAYS,
            ),
            selectedPeriods,
        )
    }

    @Test
    fun copyAndShareRunOnlyAfterExplicitActions() {
        var copyCount = 0
        var shareCount = 0

        composeRule.setContent {
            CarePackTheme {
                RangeReportScreen(
                    state = populatedState(),
                    snackbarHostState =
                        SnackbarHostState(),
                    onBack = {},
                    onPeriodSelected = {},
                    onIncludeRecipientNameChanged = {},
                    onCopyReport = {
                        copyCount += 1
                    },
                    onShareReport = {
                        shareCount += 1
                    },
                    onRetry = {},
                )
            }
        }

        composeRule.waitForIdle()

        assertEquals(0, copyCount)
        assertEquals(0, shareCount)

        composeRule
            .onNodeWithTag(
                "range_report_copy",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, copyCount)
        assertEquals(0, shareCount)

        composeRule
            .onNodeWithTag(
                "range_report_share",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, copyCount)
        assertEquals(1, shareCount)
    }

    @Test
    fun emptyRangeHasPreviewAndClearEmptyState() {
        val summary =
            RangeSummaryBuilder.build(
                range =
                    RangeReportPeriod
                        .SEVEN_DAYS
                        .rangeEndingAt(TODAY),
                entries = emptyList(),
            )

        composeRule.setContent {
            CarePackTheme {
                RangeReportScreen(
                    state =
                        RangeReportUiState(
                            today = TODAY,
                            period =
                                RangeReportPeriod
                                    .SEVEN_DAYS,
                            summary = summary,
                            reportText = EMPTY_TEXT,
                            isLoading = false,
                        ),
                    snackbarHostState =
                        SnackbarHostState(),
                    onBack = {},
                    onPeriodSelected = {},
                    onIncludeRecipientNameChanged = {},
                    onCopyReport = {},
                    onShareReport = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "range_report_empty",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "range_report_preview_text",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun simpleModeAtLargeFontKeepsPrimaryActionsReachable() {
        composeRule.setContent {
            CarePackTheme(
                seniorMode = SeniorMode.SIMPLE,
            ) {
                val density =
                    LocalDensity.current

                CompositionLocalProvider(
                    LocalDensity provides
                            Density(
                                density = density.density,
                                fontScale = 2f,
                            ),
                ) {
                    RangeReportScreen(
                        state =
                            populatedState(
                                seniorMode =
                                    SeniorMode.SIMPLE,
                            ),
                        snackbarHostState =
                            SnackbarHostState(),
                        onBack = {},
                        onPeriodSelected = {},
                        onIncludeRecipientNameChanged = {},
                        onCopyReport = {},
                        onShareReport = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(
                "range_report_include_recipient_name_row",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "range_report_copy",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "range_report_share",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun populatedState(
        period: RangeReportPeriod =
            RangeReportPeriod.SEVEN_DAYS,
        seniorMode: SeniorMode =
            SeniorMode.STANDARD,
    ): RangeReportUiState {
        val range =
            period.rangeEndingAt(TODAY)

        val entry =
            RangeOccurrenceEntry(
                occurrenceId =
                    "range-ui-occurrence",
                localDate = TODAY,
                localTime =
                    LocalTime.of(8, 0),
                zoneIdSnapshot = "UTC",
                scheduledAt =
                    Instant.parse(
                        "2026-03-21T08:00:00Z",
                    ),
                medicationName =
                    "داروی صبح",
                instruction =
                    "بعد از صبحانه",
                medicationType = "قرص",
                dosageText = "یک",
                doseUnit = "عدد",
                reportState =
                    RangeOccurrenceReportState.GIVEN,
            )

        return RangeReportUiState(
            today = TODAY,
            period = period,
            includeRecipientName = false,
            seniorMode = seniorMode,
            summary =
                RangeSummaryBuilder.build(
                    range = range,
                    entries = listOf(entry),
                ),
            reportText = REPORT_TEXT,
            isLoading = false,
            isSharing = false,
        )
    }

    private companion object {
        const val REPORT_TEXT =
            "گزارش ۷ روزه CarePack\nجمع نوبت‌ها: ۱"

        const val EMPTY_TEXT =
            "گزارش ۷ روزه CarePack\nدر این بازه نوبتی ثبت نشده است."

        val TODAY: LocalDate =
            LocalDate.parse(
                "2026-03-21",
            )
    }
}
