package ir.carepack.feature.today

import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalStatus
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.testing.FakeReminderCoordinator
import ir.carepack.testing.InMemoryReminderPreferenceStore
import ir.carepack.testing.InMemoryUserExperiencePreferenceStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

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
    fun simpleMode_isExposedFromUserPreference() =
        runTest(dispatcher.scheduler) {
            val viewModel =
                createViewModel(
                    userExperienceStore =
                        InMemoryUserExperiencePreferenceStore(
                            UserExperiencePreferenceState(
                                seniorMode =
                                    SeniorMode.SIMPLE,
                            ),
                        ),
                )

            advanceUntilIdle()

            assertEquals(
                SeniorMode.SIMPLE,
                viewModel
                    .state
                    .value
                    .seniorMode,
            )
        }

    @Test
    fun givenAction_recordsGivenState() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeReportService(
                    outcome =
                        changedOutcome(
                            newState =
                                CaregiverReportState.GIVEN,
                        ),
                )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            advanceUntilIdle()

            viewModel.setReport(
                occurrenceId =
                    OCCURRENCE_ID,
                state =
                    CaregiverReportState.GIVEN,
            )

            runCurrent()

            assertEquals(
                listOf(
                    CaregiverReportState.GIVEN,
                ),
                reportService.recordedStates,
            )

            assertEquals(
                CaregiverReportState.GIVEN,
                viewModel
                    .state
                    .value
                    .undoChange
                    ?.newState,
            )
        }

    @Test
    fun notGivenAction_recordsNotGivenState() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeReportService(
                    outcome =
                        changedOutcome(
                            newState =
                                CaregiverReportState.NOT_GIVEN,
                        ),
                )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            advanceUntilIdle()

            viewModel.setReport(
                occurrenceId =
                    OCCURRENCE_ID,
                state =
                    CaregiverReportState.NOT_GIVEN,
            )

            runCurrent()

            assertEquals(
                listOf(
                    CaregiverReportState.NOT_GIVEN,
                ),
                reportService.recordedStates,
            )
        }

    @Test
    fun unknownAction_recordsUnknownState() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeReportService(
                    outcome =
                        changedOutcome(
                            newState =
                                CaregiverReportState.UNKNOWN,
                        ),
                )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            advanceUntilIdle()

            viewModel.setReport(
                occurrenceId =
                    OCCURRENCE_ID,
                state =
                    CaregiverReportState.UNKNOWN,
            )

            runCurrent()

            assertEquals(
                listOf(
                    CaregiverReportState.UNKNOWN,
                ),
                reportService.recordedStates,
            )
        }

    @Test
    fun remindLater_usesReminderCoordinatorAndDoesNotWriteReport() =
        runTest(dispatcher.scheduler) {
            val reportService =
                FakeReportService(
                    outcome =
                        changedOutcome(
                            newState =
                                CaregiverReportState.GIVEN,
                        ),
                )

            val reminderCoordinator =
                FakeReminderCoordinator()

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                    reminderCoordinator =
                        reminderCoordinator,
                )

            advanceUntilIdle()

            viewModel.remindLater(
                occurrenceId =
                    OCCURRENCE_ID,
            )

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

    @Test
    fun undoRestoresPreviousReportAndClearsUndoChange() =
        runTest(dispatcher.scheduler) {
            val change =
                ReportChange(
                    occurrenceId =
                        OCCURRENCE_ID,
                    previousState =
                        null,
                    newState =
                        CaregiverReportState.GIVEN,
                    changedAtEpochMillis =
                        1_000L,
                )

            val reportService =
                FakeReportService(
                    outcome =
                        SetReportOutcome.Changed(
                            change = change,
                        ),
                )

            val viewModel =
                createViewModel(
                    reportService =
                        reportService,
                )

            advanceUntilIdle()

            viewModel.setReport(
                occurrenceId =
                    OCCURRENCE_ID,
                state =
                    CaregiverReportState.GIVEN,
            )

            runCurrent()

            viewModel.undoReportChange()

            runCurrent()

            assertEquals(
                listOf(
                    change,
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

    private fun createViewModel(
        todayQueryService:
        TodayQueryService =
            FakeTodayQueryService(),
        reportService:
        FakeReportService =
            FakeReportService(
                changedOutcome(
                    CaregiverReportState.GIVEN,
                ),
            ),
        reminderCoordinator:
        FakeReminderCoordinator =
            FakeReminderCoordinator(),
        reminderPreferenceStore:
        InMemoryReminderPreferenceStore =
            InMemoryReminderPreferenceStore(),
        userExperienceStore:
        InMemoryUserExperiencePreferenceStore =
            InMemoryUserExperiencePreferenceStore(),
    ): TodayViewModel =
        TodayViewModel(
            todayQueryService =
                todayQueryService,
            caregiverReportService =
                reportService,
            reminderCoordinator =
                reminderCoordinator,
            reminderPreferenceStore =
                reminderPreferenceStore,
            userExperiencePreferenceStore =
                userExperienceStore,
            clock = FIXED_CLOCK,
            zoneProvider =
                FixedZoneProvider(),
            now =
                flowOf(
                    FIXED_INSTANT,
                ),
        )

    private fun changedOutcome(
        newState: CaregiverReportState,
    ): SetReportOutcome =
        SetReportOutcome.Changed(
            ReportChange(
                occurrenceId =
                    OCCURRENCE_ID,
                previousState =
                    null,
                newState =
                    newState,
                changedAtEpochMillis =
                    FIXED_INSTANT
                        .toEpochMilli(),
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

private class FakeTodayQueryService :
    TodayQueryService {

    override fun observeToday(
        localDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<TodayModel> =
        flowOf(
            TodayModel(
                localDate = localDate,
                items =
                    listOf(
                        TodayItem(
                            occurrenceId =
                                "occurrence-1",
                            localDate = localDate,
                            localTime =
                                LocalTime.of(
                                    12,
                                    0,
                                ),
                            medicationName =
                                "داروی نمونه",
                            medicationInstruction =
                                "بعد از غذا",
                            lifecycle =
                                OccurrenceLifecycle.ACTIVE,
                            reportState = null,
                            scheduledAt =
                                Instant.parse(
                                    "2026-06-24T08:30:00Z",
                                ),
                            temporalStatus =
                                TemporalStatus.DUE,
                            isOverdue = false,
                            canMutateReport = true,
                            canRemindLater = true,
                        ),
                    ),
                emptyState = null,
            ),
        )

    override fun observeOccurrence(
        occurrenceId: String,
        now: Flow<Instant>,
    ): Flow<OccurrenceDetail?> =
        MutableStateFlow(null)

    override fun observeRecentHistory(
        anchorDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<List<HistoryDay>> =
        flowOf(emptyList())
}

private class FakeReportService(
    private val outcome: SetReportOutcome,
) : CaregiverReportService {

    val recordedStates =
        mutableListOf<CaregiverReportState>()

    val restoredChanges =
        mutableListOf<ReportChange>()

    override suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        recordedStates += newState
        return outcome
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

private class FixedZoneProvider :
    ZoneProvider {
    override fun currentZone():
            ZoneId =
        ZoneOffset.UTC
}
