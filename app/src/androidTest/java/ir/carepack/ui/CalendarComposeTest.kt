package ir.carepack.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.calendar.JalaliMonthModelFactory
import ir.carepack.domain.calendar.JalaliYearMonth
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.report.RangeOccurrenceEntry
import ir.carepack.domain.report.RangeOccurrenceReportState
import ir.carepack.domain.report.RangeSummaryBuilder
import ir.carepack.domain.report.ReportDateRange
import ir.carepack.data.service.RoomDateRangeSummaryService
import ir.carepack.feature.calendar.CalendarRoute
import ir.carepack.feature.calendar.CalendarScreen
import ir.carepack.feature.calendar.CalendarUiState
import ir.carepack.testing.CarePlanRoomTestFixture
import ir.carepack.testing.InstrumentedUserExperiencePreferenceStore
import ir.carepack.ui.theme.CarePackTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun monthStatusSelectedDayAndReportEntryAreFunctional() {
        val selectedDates =
            mutableListOf<LocalDate>()

        val openedOccurrences =
            mutableListOf<String>()

        var rangeReportOpened = false

        val state =
            populatedCalendarState(
                seniorMode =
                    SeniorMode.STANDARD,
            )

        composeRule.setContent {
            CarePackTheme {
                CalendarScreen(
                    state = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onToday = {},
                    onDateSelected = {
                        selectedDates += it
                    },
                    onOpenOccurrence = {
                        openedOccurrences += it
                    },
                    onOpenRangeReport = {
                        rangeReportOpened = true
                    },
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "calendar_screen",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "calendar_month_title",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "calendar_day_${TODAY.toEpochDay()}",
            )
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            listOf(TODAY),
            selectedDates,
        )

        composeRule
            .onNodeWithTag(
                "calendar_occurrence_$OCCURRENCE_ID",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "calendar_open_occurrence_$OCCURRENCE_ID",
            )
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf(OCCURRENCE_ID),
            openedOccurrences,
        )

        composeRule
            .onNodeWithTag(
                "calendar_open_range_report",
            )
            .performScrollTo()
            .performClick()

        assertTrue(rangeReportOpened)
    }

    @Test
    fun openingAndSelectingCalendarDayDoesNotCreateReport() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        CALENDAR_TEST_INSTANT,
                )
                .use { fixture ->
                    val plan =
                        fixture.createPlan(
                            medicationName =
                                "داروی تقویم",
                            instruction = "صبح",
                            minutesOfDay =
                                listOf(12 * 60),
                            startDate = TODAY,
                            endDate = TODAY,
                        )

                    val occurrence =
                        fixture.occurrenceOn(
                            medicationId =
                                plan.medicationId,
                            date = TODAY,
                            minuteOfDay = 12 * 60,
                        )

                    assertEquals(
                        0,
                        fixture
                            .database
                            .reportingDao()
                            .countReports(),
                    )

                    composeRule.setContent {
                        CarePackTheme {
                            CalendarRoute(
                                summaryService =
                                    RoomDateRangeSummaryService(
                                        database =
                                            fixture.database,
                                    ),
                                userExperiencePreferenceStore =
                                    InstrumentedUserExperiencePreferenceStore(),
                                clock = fixture.clock,
                                zoneProvider =
                                    ZoneProvider {
                                        ZoneId.of("UTC")
                                    },
                                onOpenOccurrence = {},
                                onOpenRangeReport = {},
                            )
                        }
                    }

                    waitForTag(
                        "calendar_day_${TODAY.toEpochDay()}",
                    )

                    composeRule
                        .onNodeWithTag(
                            "calendar_day_${TODAY.toEpochDay()}",
                        )
                        .performClick()

                    composeRule.waitForIdle()

                    assertEquals(
                        0,
                        fixture
                            .database
                            .reportingDao()
                            .countReports(),
                    )

                    assertEquals(
                        occurrence.id,
                        fixture
                            .database
                            .occurrenceDao()
                            .getById(
                                occurrence.id,
                            )
                            ?.id,
                    )
                }
        }

    @Test
    fun simpleModeAtLargeFontKeepsCriticalCalendarActionsReachable() {
        val state =
            populatedCalendarState(
                seniorMode =
                    SeniorMode.SIMPLE,
            )

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
                    CalendarScreen(
                        state = state,
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onToday = {},
                        onDateSelected = {},
                        onOpenOccurrence = {},
                        onOpenRangeReport = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(
                "calendar_today",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "calendar_open_range_report",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "calendar_open_occurrence_$OCCURRENCE_ID",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun populatedCalendarState(
        seniorMode: SeniorMode,
    ): CalendarUiState {
        val displayedMonth =
            JalaliYearMonth.from(TODAY)

        val monthModel =
            JalaliMonthModelFactory.create(
                displayedMonth = displayedMonth,
                today = TODAY,
                selectedDate = TODAY,
                firstDayOfWeek =
                    DayOfWeek.SATURDAY,
            )

        val entry =
            RangeOccurrenceEntry(
                occurrenceId = OCCURRENCE_ID,
                localDate = TODAY,
                localTime = LocalTime.of(8, 0),
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

        val summary =
            RangeSummaryBuilder.build(
                range =
                    ReportDateRange(
                        startDate =
                            monthModel.firstVisibleDate,
                        endDate =
                            monthModel.lastVisibleDate,
                    ),
                entries = listOf(entry),
            )

        return CalendarUiState(
            today = TODAY,
            selectedDate = TODAY,
            displayedMonth = displayedMonth,
            firstDayOfWeek =
                DayOfWeek.SATURDAY,
            monthModel = monthModel,
            summary = summary,
            selectedDaySummary =
                summary.summaryFor(TODAY),
            seniorMode = seniorMode,
            isLoading = false,
            failure = null,
        )
    }

    private fun waitForTag(
        tag: String,
    ) {
        composeRule.waitUntil(
            timeoutMillis = WAIT_TIMEOUT_MILLIS,
        ) {
            composeRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val OCCURRENCE_ID =
            "calendar-occurrence"

        const val WAIT_TIMEOUT_MILLIS =
            5_000L

        val CALENDAR_TEST_INSTANT: Instant =
            Instant.parse(
                "2026-03-21T08:00:00Z",
            )

        val TODAY: LocalDate =
            LocalDate.parse(
                "2026-03-21",
            )
    }
}
