package ir.carepack.feature.detail

import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalStatus
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.testing.FakeReminderCoordinator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        Dispatchers.setMain(
            dispatcher,
        )
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

            reportService.enqueue(
                changedOutcome(
                    previousState = null,
                    newState =
                        CaregiverReportState.GIVEN,
                    changedAt =
                        1_000L,
                ),
            )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            collectState(
                viewModel,
            )

            runCurrent()

            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )

            runCurrent()

            assertNotNull(
                viewModel
                    .state
                    .value
                    .undoChange,
            )

            advanceTimeBy(
                7_999L,
            )

            runCurrent()

            assertNotNull(
                viewModel
                    .state
                    .value
                    .undoChange,
            )

            advanceTimeBy(
                1L,
            )

            runCurrent()

            assertNull(
                viewModel
                    .state
                    .value
                    .undoChange,
            )
        }

    @Test
    fun secondReportChange_replacesPreviousUndoChange() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeCaregiverReportService()

            reportService.enqueue(
                changedOutcome(
                    previousState = null,
                    newState =
                        CaregiverReportState.GIVEN,
                    changedAt =
                        1_000L,
                ),
            )

            reportService.enqueue(
                changedOutcome(
                    previousState =
                        CaregiverReportState.GIVEN,
                    newState =
                        CaregiverReportState.UNKNOWN,
                    changedAt =
                        2_000L,
                ),
            )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            collectState(
                viewModel,
            )

            runCurrent()

            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )

            runCurrent()

            val firstChange =
                checkNotNull(
                    viewModel
                        .state
                        .value
                        .undoChange,
                )

            viewModel.setReport(
                CaregiverReportState.UNKNOWN,
            )

            runCurrent()

            val secondChange =
                checkNotNull(
                    viewModel
                        .state
                        .value
                        .undoChange,
                )

            assertNotEquals(
                firstChange.changedAtEpochMillis,
                secondChange.changedAtEpochMillis,
            )

            assertEquals(
                CaregiverReportState.UNKNOWN,
                secondChange.newState,
            )

            viewModel.undoReportChange()

            runCurrent()

            assertEquals(
                listOf(
                    secondChange,
                ),
                reportService.restoredChanges,
            )

            assertNull(
                viewModel
                    .state
                    .value
                    .undoChange,
            )
        }

    @Test
    fun sameStateSelection_doesNotReplaceCurrentUndoChange() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeCaregiverReportService()

            reportService.enqueue(
                changedOutcome(
                    previousState = null,
                    newState =
                        CaregiverReportState.GIVEN,
                    changedAt =
                        1_000L,
                ),
            )

            reportService.enqueue(
                SetReportOutcome.Unchanged(
                    occurrenceId =
                        OCCURRENCE_ID,
                    state =
                        CaregiverReportState.GIVEN,
                ),
            )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            collectState(
                viewModel,
            )

            runCurrent()

            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )

            runCurrent()

            val firstUndoChange =
                checkNotNull(
                    viewModel
                        .state
                        .value
                        .undoChange,
                )

            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )

            runCurrent()

            assertEquals(
                firstUndoChange,
                viewModel
                    .state
                    .value
                    .undoChange,
            )
        }

    @Test
    fun remindLater_usesReminderCoordinatorAndDoesNotRecordReport() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeCaregiverReportService()

            val reminderCoordinator =
                FakeReminderCoordinator()

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                    reminderCoordinator =
                        reminderCoordinator,
                )

            collectState(
                viewModel,
            )

            runCurrent()

            viewModel.remindLater()

            runCurrent()

            assertEquals(
                listOf(
                    OCCURRENCE_ID,
                ),
                reminderCoordinator
                    .remindLaterOccurrenceIds,
            )

            assertEquals(
                emptyList<CaregiverReportState>(),
                reportService.recordedStates,
            )
        }

    private fun TestScope.collectState(
        viewModel: OccurrenceDetailViewModel,
    ) {
        backgroundScope.launch(
            UnconfinedTestDispatcher(
                testScheduler,
            ),
        ) {
            viewModel
                .state
                .collect()
        }
    }

    private fun createViewModel(
        reportService:
        FakeCaregiverReportService,
        reminderCoordinator:
        FakeReminderCoordinator =
            FakeReminderCoordinator(),
    ): OccurrenceDetailViewModel =
        OccurrenceDetailViewModel(
            occurrenceId =
                OCCURRENCE_ID,
            todayQueryService =
                FakeTodayQueryService(),
            caregiverReportService =
                reportService,
            reminderCoordinator =
                reminderCoordinator,
            clock = FIXED_CLOCK,
            now =
                flowOf(
                    FIXED_INSTANT,
                ),
        )

    private fun changedOutcome(
        previousState:
        CaregiverReportState?,
        newState: CaregiverReportState,
        changedAt: Long,
    ): SetReportOutcome =
        SetReportOutcome.Changed(
            change =
                ReportChange(
                    occurrenceId =
                        OCCURRENCE_ID,
                    previousState =
                        previousState,
                    newState =
                        newState,
                    changedAtEpochMillis =
                        changedAt,
                ),
        )

    private companion object {
        const val OCCURRENCE_ID =
            "occurrence-1"

        val FIXED_INSTANT: Instant =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        val FIXED_CLOCK: Clock =
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

    val recordedStates =
        mutableListOf<CaregiverReportState>()

    val restoredChanges =
        mutableListOf<ReportChange>()

    fun enqueue(
        outcome: SetReportOutcome,
    ) {
        outcomes.addLast(
            outcome,
        )
    }

    override suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        recordedStates += newState

        return outcomes.removeFirstOrNull()
            ?: SetReportOutcome.Unchanged(
                occurrenceId =
                    occurrenceId,
                state = newState,
            )
    }

    override suspend fun restorePrevious(
        change: ReportChange,
    ): UndoReportOutcome {
        restoredChanges += change

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
            temporalStatus =
                TemporalStatus.UPCOMING,
            isOverdue = false,
            canMutateReport = false,
            canRemindLater = false,
        )

    private val occurrenceFlow =
        MutableStateFlow<OccurrenceDetail?>(
            occurrence,
        )

    override fun observeToday(
        localDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<TodayModel> =
        flowOf(
            TodayModel(
                localDate = localDate,
                items = emptyList(),
                emptyState = null,
            ),
        )

    override fun observeOccurrence(
        occurrenceId: String,
        now: Flow<Instant>,
    ): Flow<OccurrenceDetail?> =
        occurrenceFlow

    override fun observeRecentHistory(
        anchorDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<List<HistoryDay>> =
        flowOf(
            emptyList(),
        )
}
