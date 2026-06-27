package ir.carepack.reporting

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.testing.CarePlanRoomTestFixture
import ir.carepack.testing.CreatedTestPlan
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportingIntegrationTest {

    private lateinit var fixture: CarePlanRoomTestFixture

    @Before
    fun setUp() {
        fixture = CarePlanRoomTestFixture.create(
            initialInstant = HISTORY_START_INSTANT,
            idPrefix = "reporting-id",
        )
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun noReportAndExplicitUnknown_areDifferentRepresentations() = runBlocking {
        val plan = createPlan(minutesOfDay = listOf(9 * 60))
        moveToAnchorAndGuarantee()
        val occurrence = fixture.occurrenceForDate(plan.scheduleVersionId, ANCHOR_DATE)

        val before = fixture.todayQueryService.observeOccurrence(
            occurrenceId = occurrence.id,
            now = flowOf(fixture.clock.instant()),
        ).first()

        assertNull(before?.reportState)

        val outcome = fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.UNKNOWN,
        )

        assertTrue(outcome is SetReportOutcome.Changed)

        val after = fixture.todayQueryService.observeOccurrence(
            occurrenceId = occurrence.id,
            now = flowOf(fixture.clock.instant()),
        ).first()

        assertEquals(CaregiverReportState.UNKNOWN, after?.reportState)
        assertEquals(
            1,
            fixture.database.reportingDao().countReportsForOccurrence(occurrence.id),
        )
    }

    @Test
    fun explicitTransitions_keepOneRowAndPreserveRecordedAt() = runBlocking {
        val plan = createPlan(minutesOfDay = listOf(9 * 60))
        moveToAnchorAndGuarantee()
        val occurrence = fixture.occurrenceForDate(plan.scheduleVersionId, ANCHOR_DATE)

        fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.GIVEN,
        )
        val firstReport = checkNotNull(
            fixture.database.reportingDao().getReport(occurrence.id),
        )

        fixture.moveTo(fixture.clock.instant().plusMillis(1))
        fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.NOT_GIVEN,
        )
        fixture.moveTo(fixture.clock.instant().plusMillis(1))
        fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.UNKNOWN,
        )

        val finalReport = checkNotNull(
            fixture.database.reportingDao().getReport(occurrence.id),
        )

        assertEquals(CaregiverReportState.UNKNOWN.name, finalReport.state)
        assertEquals(firstReport.recordedAtEpochMillis, finalReport.recordedAtEpochMillis)
        assertTrue(finalReport.updatedAtEpochMillis > firstReport.updatedAtEpochMillis)
        assertEquals(
            1,
            fixture.database.reportingDao().countReportsForOccurrence(occurrence.id),
        )
    }

    @Test
    fun sameStateSelection_isIdempotent() = runBlocking {
        val plan = createPlan(minutesOfDay = listOf(9 * 60))
        moveToAnchorAndGuarantee()
        val occurrence = fixture.occurrenceForDate(plan.scheduleVersionId, ANCHOR_DATE)

        fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.GIVEN,
        )
        val persistedBefore = checkNotNull(
            fixture.database.reportingDao().getReport(occurrence.id),
        )

        val outcome = fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.GIVEN,
        )

        assertTrue(outcome is SetReportOutcome.Unchanged)
        assertEquals(
            persistedBefore,
            fixture.database.reportingDao().getReport(occurrence.id),
        )
        assertEquals(
            1,
            fixture.database.reportingDao().countReportsForOccurrence(occurrence.id),
        )
    }

    @Test
    fun undoToNoReport_removesOnlyReportRow() = runBlocking {
        val plan = createPlan(minutesOfDay = listOf(9 * 60))
        moveToAnchorAndGuarantee()
        val occurrence = fixture.occurrenceForDate(plan.scheduleVersionId, ANCHOR_DATE)

        val changed = fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.GIVEN,
        ) as SetReportOutcome.Changed

        val undoOutcome = fixture.reportService.restorePrevious(changed.change)

        assertEquals(
            UndoReportOutcome.Restored(
                occurrenceId = occurrence.id,
                restoredState = null,
            ),
            undoOutcome,
        )
        assertNull(fixture.database.reportingDao().getReport(occurrence.id))
        assertTrue(fixture.database.occurrenceDao().getById(occurrence.id) != null)
    }

    @Test
    fun staleUndo_cannotOverwriteNewerReport() = runBlocking {
        val plan = createPlan(minutesOfDay = listOf(9 * 60))
        moveToAnchorAndGuarantee()
        val occurrence = fixture.occurrenceForDate(plan.scheduleVersionId, ANCHOR_DATE)

        val firstChange = fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.GIVEN,
        ) as SetReportOutcome.Changed

        fixture.moveTo(fixture.clock.instant().plusMillis(1))
        fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.UNKNOWN,
        )

        assertEquals(
            UndoReportOutcome.NoLongerCurrent,
            fixture.reportService.restorePrevious(firstChange.change),
        )
        assertEquals(
            CaregiverReportState.UNKNOWN.name,
            fixture.database.reportingDao().getReport(occurrence.id)?.state,
        )
    }

    @Test
    fun cancelledOccurrence_rejectsNewReport() = runBlocking {
        val plan = createPlan(minutesOfDay = listOf(9 * 60))
        moveToAnchorAndGuarantee()
        val occurrence = fixture.occurrenceForDate(plan.scheduleVersionId, ANCHOR_DATE)

        fixture.database.occurrenceDao().cancelFutureUnreportedForVersion(
            scheduleVersionId = plan.scheduleVersionId,
            nowEpochMillis = fixture.clock.instant().minusSeconds(1).toEpochMilli(),
            cancelledAtEpochMillis = fixture.clock.instant().toEpochMilli(),
            cancellationReason = OccurrenceCancellationReason.SCHEDULE_REPLACED.name,
        )

        val outcome = fixture.reportService.setReport(
            occurrenceId = occurrence.id,
            newState = CaregiverReportState.GIVEN,
        )

        assertEquals(SetReportOutcome.CancelledOccurrenceRejected, outcome)
        assertNull(fixture.database.reportingDao().getReport(occurrence.id))
    }

    @Test
    fun today_isOrderedAndExcludesCancelledOccurrences() = runBlocking {
        val activePlan = createPlan(
            medicationName = "داروی فعال",
            minutesOfDay = listOf(11 * 60, 9 * 60),
        )
        val cancelledPlan = createPlan(
            medicationName = "داروی لغوشده",
            minutesOfDay = listOf(10 * 60),
        )
        moveToAnchorAndGuarantee()

        fixture.database.occurrenceDao().cancelFutureUnreportedForVersion(
            scheduleVersionId = cancelledPlan.scheduleVersionId,
            nowEpochMillis = fixture.clock.instant().minusSeconds(1).toEpochMilli(),
            cancelledAtEpochMillis = fixture.clock.instant().toEpochMilli(),
            cancellationReason = OccurrenceCancellationReason.SCHEDULE_REPLACED.name,
        )

        val model = fixture.todayQueryService.observeToday(
            localDate = ANCHOR_DATE,
            now = flowOf(fixture.clock.instant()),
        ).first()

        assertEquals(
            listOf(9 * 60, 11 * 60),
            model.items.map { it.localTime.hour * 60 + it.localTime.minute },
        )
        assertTrue(model.items.all { it.lifecycle == OccurrenceLifecycle.ACTIVE })
        assertTrue(model.items.all { it.medicationName == "داروی فعال" })
        assertNull(model.emptyState)
        assertEquals(
            2,
            fixture.database.occurrenceDao()
                .getForVersion(activePlan.scheduleVersionId)
                .count { it.localDateEpochDay == ANCHOR_DATE.toEpochDay() },
        )
    }

    @Test
    fun today_distinguishesNoMedicationsFromNoOccurrences() = runBlocking {
        createPlan(minutesOfDay = listOf(9 * 60))
        moveToAnchorAndGuarantee()

        val noOccurrences = fixture.todayQueryService.observeToday(
            localDate = ANCHOR_DATE.plusDays(30),
            now = flowOf(fixture.clock.instant()),
        ).first()

        assertEquals(TodayEmptyState.NO_OCCURRENCES, noOccurrences.emptyState)

        fixture.database.clearAllTables()

        val noMedications = fixture.todayQueryService.observeToday(
            localDate = ANCHOR_DATE,
            now = flowOf(fixture.clock.instant()),
        ).first()

        assertEquals(TodayEmptyState.NO_MEDICATIONS, noMedications.emptyState)
    }

    @Test
    fun recentHistory_isInclusiveEightDaysAndDoesNotDeleteOlderRows() = runBlocking {
        createPlan(minutesOfDay = listOf(9 * 60))
        moveToAnchorAndGuarantee()
        val totalBefore = fixture.database.occurrenceDao().count()

        val history = fixture.todayQueryService.observeRecentHistory(
            anchorDate = ANCHOR_DATE,
            now = flowOf(fixture.clock.instant()),
        ).first()
        val dates = history.map { it.localDate }

        assertTrue(dates.contains(ANCHOR_DATE))
        assertTrue(dates.contains(ANCHOR_DATE.minusDays(7)))
        assertFalse(dates.contains(ANCHOR_DATE.minusDays(8)))
        assertEquals(totalBefore, fixture.database.occurrenceDao().count())
        assertTrue(
            fixture.database.occurrenceDao().countBetweenDates(
                startEpochDay = ANCHOR_DATE.minusDays(8).toEpochDay(),
                endEpochDay = ANCHOR_DATE.minusDays(8).toEpochDay(),
            ) > 0,
        )
    }

    private suspend fun createPlan(
        medicationName: String = "داروی نمونه",
        minutesOfDay: List<Int>,
    ): CreatedTestPlan = fixture.createPlan(
        medicationName = medicationName,
        weekdays = DayOfWeek.entries.toSet(),
        minutesOfDay = minutesOfDay,
        startDate = HISTORY_START_DATE,
        endDate = null,
        zoneId = "UTC",
    )

    private suspend fun moveToAnchorAndGuarantee() {
        fixture.moveTo(ANCHOR_INSTANT)
        fixture.guaranteeWindow(ANCHOR_DATE)
    }

    private companion object {
        val HISTORY_START_DATE: LocalDate = LocalDate.parse("2026-06-16")
        val HISTORY_START_INSTANT: Instant = Instant.parse("2026-06-16T08:00:00Z")
        val ANCHOR_DATE: LocalDate = LocalDate.parse("2026-06-24")
        val ANCHOR_INSTANT: Instant = Instant.parse("2026-06-24T08:00:00Z")
    }
}
