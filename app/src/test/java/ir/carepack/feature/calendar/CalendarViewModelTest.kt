package ir.carepack.feature.calendar

import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.calendar.JalaliYearMonth
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.report.RangeOccurrenceEntry
import ir.carepack.domain.report.RangeOccurrenceReportState
import ir.carepack.testing.InMemoryUserExperiencePreferenceStore
import ir.carepack.testing.RecordingDateRangeSummaryService
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val dispatcher =
        StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_usesTodaySelectsTodayAndLoadsVisibleGridRange() =
        runTest(dispatcher) {
            val summaryService =
                RecordingDateRangeSummaryService(
                    initialEntries =
                        listOf(
                            entry(
                                id = "today",
                                date = TODAY,
                            ),
                        ),
                )

            val viewModel =
                viewModel(
                    summaryService = summaryService,
                )

            advanceUntilIdle()

            val state =
                viewModel.state.value

            assertEquals(TODAY, state.today)
            assertEquals(TODAY, state.selectedDate)
            assertEquals(
                JalaliYearMonth(
                    year = 1404,
                    month = 1,
                ),
                state.displayedMonth,
            )
            assertEquals(
                DayOfWeek.SATURDAY,
                state.firstDayOfWeek,
            )
            assertFalse(state.isLoading)
            assertEquals(1, state.summary?.totalOccurrenceCount)
            assertEquals(
                1,
                state.selectedDaySummary
                    ?.totalOccurrenceCount,
            )
            assertEquals(1, summaryService.observedRanges.size)
            assertEquals(
                state.monthModel.firstVisibleDate,
                summaryService.observedRanges
                    .single().startDate,
            )
            assertEquals(
                state.monthModel.lastVisibleDate,
                summaryService.observedRanges
                    .single().endDate,
            )
        }

    @Test
    fun previousAndNextMonth_updateMonthSelectionAndObservation() =
        runTest(dispatcher) {
            val summaryService =
                RecordingDateRangeSummaryService()

            val viewModel =
                viewModel(summaryService)

            advanceUntilIdle()

            viewModel.showPreviousMonth()
            advanceUntilIdle()

            assertEquals(
                JalaliYearMonth(
                    year = 1403,
                    month = 12,
                ),
                viewModel.state.value.displayedMonth,
            )
            assertEquals(
                viewModel.state.value
                    .displayedMonth
                    .firstLocalDate(),
                viewModel.state.value.selectedDate,
            )

            viewModel.showNextMonth()
            advanceUntilIdle()

            assertEquals(
                JalaliYearMonth(
                    year = 1404,
                    month = 1,
                ),
                viewModel.state.value.displayedMonth,
            )
            assertEquals(TODAY, viewModel.state.value.selectedDate)
            assertTrue(summaryService.observedRanges.size >= 3)
        }

    @Test
    fun selectingAdjacentMonthDate_movesDisplayedMonthAndLoadsItsSummary() =
        runTest(dispatcher) {
            val summaryService =
                RecordingDateRangeSummaryService()

            val viewModel =
                viewModel(summaryService)

            advanceUntilIdle()

            val adjacentDate =
                LocalDate.parse(
                    "2025-04-21",
                )

            viewModel.selectDate(adjacentDate)
            advanceUntilIdle()

            assertEquals(adjacentDate, viewModel.state.value.selectedDate)
            assertEquals(
                JalaliYearMonth.from(adjacentDate),
                viewModel.state.value.displayedMonth,
            )
            assertTrue(
                viewModel.state.value.monthModel.cells
                    .single {
                        it.localDate == adjacentDate
                    }
                    .isSelected,
            )
        }

    @Test
    fun selectingDateNeverCreatesOrChangesReportState() =
        runTest(dispatcher) {
            val entry =
                entry(
                    id = "unreported",
                    date = TODAY,
                    state =
                        RangeOccurrenceReportState.NO_REPORT,
                )

            val summaryService =
                RecordingDateRangeSummaryService(
                    initialEntries = listOf(entry),
                )

            val viewModel =
                viewModel(summaryService)

            advanceUntilIdle()
            viewModel.selectDate(TODAY)
            advanceUntilIdle()

            assertEquals(
                RangeOccurrenceReportState.NO_REPORT,
                viewModel.state.value
                    .selectedDaySummary
                    ?.entries
                    ?.single()
                    ?.reportState,
            )
            assertTrue(summaryService.requestedRanges.isEmpty())
        }

    @Test
    fun preferenceChangesUpdateWeekStartAndSimpleModeWithoutChangingSummary() =
        runTest(dispatcher) {
            val summaryService =
                RecordingDateRangeSummaryService(
                    initialEntries =
                        listOf(
                            entry(
                                id = "today",
                                date = TODAY,
                            ),
                        ),
                )

            val experienceStore =
                InMemoryUserExperiencePreferenceStore()

            val viewModel =
                viewModel(
                    summaryService = summaryService,
                    experienceStore = experienceStore,
                    locale = Locale.US,
                )

            advanceUntilIdle()

            experienceStore.setFirstDayOfWeekPreference(
                FirstDayOfWeekPreference.MONDAY,
            )
            experienceStore.setSeniorMode(
                SeniorMode.SIMPLE,
            )
            advanceUntilIdle()

            assertEquals(
                DayOfWeek.MONDAY,
                viewModel.state.value.firstDayOfWeek,
            )
            assertEquals(
                SeniorMode.SIMPLE,
                viewModel.state.value.seniorMode,
            )
            assertEquals(
                1,
                viewModel.state.value
                    .summary?.totalOccurrenceCount,
            )
            assertEquals(
                DayOfWeek.MONDAY,
                viewModel.state.value
                    .monthModel.weekdayOrder.first(),
            )
        }

    @Test
    fun refreshRestartsBoundedObservation() =
        runTest(dispatcher) {
            val summaryService =
                RecordingDateRangeSummaryService()

            val viewModel =
                viewModel(summaryService)

            advanceUntilIdle()
            val initialCount =
                summaryService.observedRanges.size

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(
                initialCount + 1,
                summaryService.observedRanges.size,
            )
        }

    @Test
    fun observationFailureProducesStableErrorState() =
        runTest(dispatcher) {
            val summaryService =
                RecordingDateRangeSummaryService().apply {
                    observationFailure =
                        IllegalStateException(
                            "Load failed.",
                        )
                }

            val viewModel =
                viewModel(summaryService)

            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(
                CalendarFailure.LOAD_FAILED,
                viewModel.state.value.failure,
            )
            assertNotNull(viewModel.state.value.monthModel)
        }

    @Test
    fun showTodayRestoresTodayAfterBrowsingAnotherMonth() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    RecordingDateRangeSummaryService(),
                )

            advanceUntilIdle()
            viewModel.showNextMonth()
            advanceUntilIdle()
            assertTrue(
                viewModel.state.value.displayedMonth !=
                        JalaliYearMonth.from(TODAY),
            )

            viewModel.showToday()
            advanceUntilIdle()

            assertEquals(TODAY, viewModel.state.value.selectedDate)
            assertEquals(
                JalaliYearMonth.from(TODAY),
                viewModel.state.value.displayedMonth,
            )
        }

    private fun viewModel(
        summaryService:
        RecordingDateRangeSummaryService,
        experienceStore:
        InMemoryUserExperiencePreferenceStore =
            InMemoryUserExperiencePreferenceStore(
                UserExperiencePreferenceState(
                    firstDayOfWeekPreference =
                        FirstDayOfWeekPreference.SATURDAY,
                ),
            ),
        locale: Locale = Locale("fa", "IR"),
    ): CalendarViewModel =
        CalendarViewModel(
            summaryService = summaryService,
            userExperiencePreferenceStore =
                experienceStore,
            clock =
                Clock.fixed(
                    NOW,
                    ZoneOffset.UTC,
                ),
            zoneProvider =
                ZoneProvider {
                    ZoneId.of("UTC")
                },
            locale = locale,
        )

    private fun entry(
        id: String,
        date: LocalDate,
        state: RangeOccurrenceReportState =
            RangeOccurrenceReportState.NO_REPORT,
    ): RangeOccurrenceEntry =
        RangeOccurrenceEntry(
            occurrenceId = id,
            localDate = date,
            localTime = LocalTime.of(8, 0),
            zoneIdSnapshot = "UTC",
            scheduledAt = NOW,
            medicationName = "داروی آزمون",
            instruction = "بعد از غذا",
            medicationType = "قرص",
            dosageText = "یک",
            doseUnit = "عدد",
            reportState = state,
        )

    private companion object {
        val NOW: Instant =
            Instant.parse(
                "2025-03-21T08:00:00Z",
            )

        val TODAY: LocalDate =
            LocalDate.parse(
                "2025-03-21",
            )
    }
}
