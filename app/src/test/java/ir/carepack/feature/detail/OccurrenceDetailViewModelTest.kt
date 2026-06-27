package ir.carepack.feature.detail

import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalPhase
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.domain.today.TodayQueryService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OccurrenceDetailViewModelTest {

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
    fun changedReport_exposesUndoForExactlyEightSeconds() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeCaregiverReportService()

            val changedAt =
                FIXED_INSTANT
                    .toEpochMilli()

            reportService.enqueue(
                SetReportOutcome.Changed(
                    change =
                        ReportChange(
                            occurrenceId =
                                OCCURRENCE_ID,
                            previousState =
                                null,
                            newState =
                                CaregiverReportState
                                    .GIVEN,
                            changedAtEpochMillis =
                                changedAt,
                        ),
                ),
            )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            runCurrent()

            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )

            runCurrent()

            assertNotNull(
                viewModel.state.value.undo,
            )

            advanceTimeBy(7_999L)
            runCurrent()

            assertNotNull(
                viewModel.state.value.undo,
            )

            advanceTimeBy(1L)
            runCurrent()

            assertNull(
                viewModel.state.value.undo,
            )
        }

    @Test
    fun secondReportChange_replacesPreviousUndoToken() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeCaregiverReportService()

            reportService.enqueue(
                changedOutcome(
                    previousState = null,
                    newState =
                        CaregiverReportState
                            .GIVEN,
                    changedAt =
                        1_000L,
                ),
            )

            reportService.enqueue(
                changedOutcome(
                    previousState =
                        CaregiverReportState
                            .GIVEN,
                    newState =
                        CaregiverReportState
                            .UNKNOWN,
                    changedAt =
                        1_001L,
                ),
            )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            runCurrent()

            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )

            runCurrent()

            val firstToken =
                checkNotNull(
                    viewModel
                        .state
                        .value
                        .undo,
                ).token

            viewModel.setReport(
                CaregiverReportState.UNKNOWN,
            )

            runCurrent()

            val secondToken =
                checkNotNull(
                    viewModel
                        .state
                        .value
                        .undo,
                ).token

            assertNotEquals(
                firstToken,
                secondToken,
            )

            viewModel.undo(firstToken)
            runCurrent()

            assertEquals(
                0,
                reportService.restoreCalls,
            )

            viewModel.undo(secondToken)
            runCurrent()

            assertEquals(
                1,
                reportService.restoreCalls,
            )
        }

    @Test
    fun sameStateSelection_doesNotReplaceCurrentUndoToken() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeCaregiverReportService()

            reportService.enqueue(
                changedOutcome(
                    previousState = null,
                    newState =
                        CaregiverReportState
                            .GIVEN,
                    changedAt =
                        1_000L,
                ),
            )

            reportService.enqueue(
                SetReportOutcome.Unchanged(
                    occurrenceId =
                        OCCURRENCE_ID,
                    state =
                        CaregiverReportState
                            .GIVEN,
                ),
            )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            runCurrent()

            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )

            runCurrent()

            val firstUndo =
                checkNotNull(
                    viewModel
                        .state
                        .value
                        .undo,
                )

            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )

            runCurrent()

            assertEquals(
                firstUndo.token,
                viewModel
                    .state
                    .value
                    .undo
                    ?.token,
            )
        }

    private fun createViewModel(
        reportService:
        CaregiverReportService,
    ): OccurrenceDetailViewModel {
        return OccurrenceDetailViewModel(
            occurrenceId =
                OCCURRENCE_ID,
            todayQueryService =
                FakeTodayQueryService(),
            caregiverReportService =
                reportService,
            clock = FIXED_CLOCK,
            now =
                flowOf(FIXED_INSTANT),
        )
    }

    private fun changedOutcome(
        previousState:
        CaregiverReportState?,
        newState:
        CaregiverReportState,
        changedAt: Long,
    ): SetReportOutcome {
        return SetReportOutcome.Changed(
            change =
                ReportChange(
                    occurrenceId =
                        OCCURRENCE_ID,
                    previousState =
                        previousState,
                    newState = newState,
                    changedAtEpochMillis =
                        changedAt,
                ),
        )
    }

    private companion object {
        const val OCCURRENCE_ID =
            "occurrence-1"

        val FIXED_INSTANT:
                Instant =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        val FIXED_CLOCK:
                Clock =
            Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC,
            )
    }
}

private class FakeCaregiverReportService :
    CaregiverReportService {

    private val outcomes =
        ArrayDeque<SetReportOutcome>()

    var restoreCalls =
        0
        private set

    fun enqueue(
        outcome: SetReportOutcome,
    ) {
        outcomes.addLast(outcome)
    }

    override suspend fun setReport(
        occurrenceId: String,
        newState:
        CaregiverReportState,
    ): SetReportOutcome {
        return outcomes.removeFirst()
    }

    override suspend fun restorePrevious(
        change: ReportChange,
    ): UndoReportOutcome {
        restoreCalls += 1

        return UndoReportOutcome.Restored(
            occurrenceId =
                change.occurrenceId,
            restoredState =
                change.previousState,
        )
    }

}

private class FakeTodayQueryService :
    TodayQueryService {

    private val occurrence =
        OccurrenceDetail(
            occurrenceId =
                "occurrence-1",
            localDate =
                LocalDate.parse(
                    "2026-06-24",
                ),
            localTime =
                LocalTime.of(
                    12,
                    0,
                ),
            scheduledAt =
                Instant.parse(
                    "2026-06-24T08:30:00Z",
                ),
            medicationName =
                "داروی نمونه",
            medicationInstruction =
                "دستور نمونه",
            lifecycle =
                OccurrenceLifecycle.ACTIVE,
            reportState = null,
            zoneId = "Asia/Tehran",
            temporalPhase =
                TemporalPhase.UPCOMING,
            isOverdue = false,
        )

    private val occurrenceFlow =
        MutableStateFlow<
                OccurrenceDetail?
                >(
            occurrence,
        )

    override fun observeToday(
        localDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<TodayModel> {
        return flowOf(
            TodayModel(
                localDate = localDate,
                items = emptyList(),
                emptyState = null,
            ),
        )
    }

    override fun observeOccurrence(
        occurrenceId: String,
        now: Flow<Instant>,
    ): Flow<OccurrenceDetail?> {
        return occurrenceFlow
    }

    override fun observeRecentHistory(
        anchorDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<List<HistoryDay>> {
        return flowOf(emptyList())
    }
}
