package ir.carepack.domain.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.careplan.StopMedicationOutcome
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaregiverReportEligibilityIntegrationTest {

    @Test
    fun reportMutation_rejectsBeforeTime_allowsExactAndHistoricalActiveEdit() = runBlocking {
        CarePlanRoomTestFixture.create(
            initialInstant = Instant.parse("2026-06-24T11:59:59Z"),
        ).use { fixture ->
            val plan = fixture.createPlan(minutesOfDay = listOf(12 * 60))
            val occurrence = fixture.occurrenceOn(
                medicationId = plan.medicationId,
                date = LocalDate.parse("2026-06-24"),
                minuteOfDay = 12 * 60,
            )

            assertEquals(
                SetReportOutcome.BeforeScheduledTimeRejected,
                fixture.reportService.setReport(
                    occurrence.id,
                    CaregiverReportState.GIVEN,
                ),
            )
            fixture.advanceTo(Instant.parse("2026-06-24T12:00:00Z"))
            assertTrue(
                fixture.reportService.setReport(
                    occurrence.id,
                    CaregiverReportState.GIVEN,
                ) is SetReportOutcome.Changed,
            )
            assertEquals(
                StopMedicationOutcome.Stopped,
                fixture.carePlanService.stopMedication(plan.medicationId),
            )
            fixture.advanceTo(Instant.parse("2026-07-24T12:00:00Z"))
            assertTrue(
                fixture.reportService.setReport(
                    occurrence.id,
                    CaregiverReportState.NOT_GIVEN,
                ) is SetReportOutcome.Changed,
            )
        }
    }

    @Test
    fun cancelledOccurrence_rejectsNewAndExistingReportMutationAfterScheduledTime() = runBlocking {
        CarePlanRoomTestFixture.create(
            initialInstant = Instant.parse("2026-06-24T12:00:00Z"),
        ).use { fixture ->
            val plan = fixture.createPlan(minutesOfDay = listOf(13 * 60))
            val occurrence = fixture.occurrenceOn(
                medicationId = plan.medicationId,
                date = LocalDate.parse("2026-06-24"),
                minuteOfDay = 13 * 60,
            )
            fixture.advanceTo(Instant.parse("2026-06-24T13:00:00Z"))
            assertTrue(
                fixture.reportService.setReport(
                    occurrence.id,
                    CaregiverReportState.GIVEN,
                ) is SetReportOutcome.Changed,
            )
            fixture.database.openHelper.writableDatabase.execSQL(
                "UPDATE occurrences SET lifecycle = 'CANCELLED' WHERE id = ?",
                arrayOf(occurrence.id),
            )
            assertEquals(
                SetReportOutcome.CancelledOccurrenceRejected,
                fixture.reportService.setReport(
                    occurrence.id,
                    CaregiverReportState.NOT_GIVEN,
                ),
            )
            assertEquals(1, fixture.database.reportingDao().countReportsForOccurrence(occurrence.id))
            assertEquals(
                CaregiverReportState.GIVEN.name,
                fixture.database.reportingDao().getReport(occurrence.id)?.state,
            )
        }
    }

    @Test
    fun todayAndDetailEligibility_mirrorDomainReportGuardForUpcomingAndCancelledOccurrences() = runBlocking {
        CarePlanRoomTestFixture.create(
            initialInstant = Instant.parse("2026-06-24T11:59:59Z"),
        ).use { fixture ->
            val plan = fixture.createPlan(minutesOfDay = listOf(12 * 60))
            val occurrence = fixture.occurrenceOn(
                medicationId = plan.medicationId,
                date = LocalDate.parse("2026-06-24"),
                minuteOfDay = 12 * 60,
            )
            val upcomingNow = Instant.parse("2026-06-24T11:59:59Z")

            val upcomingToday = fixture.todayQueryService.observeToday(
                LocalDate.parse("2026-06-24"),
                flowOf(upcomingNow),
            ).first().items.single { it.occurrenceId == occurrence.id }
            val upcomingDetail = fixture.todayQueryService.observeOccurrence(
                occurrence.id,
                flowOf(upcomingNow),
            ).first()
            assertTrue(!upcomingToday.canMutateReport)
            assertTrue(upcomingDetail?.canMutateReport == false)
            assertEquals(
                SetReportOutcome.BeforeScheduledTimeRejected,
                fixture.reportService.setReport(occurrence.id, CaregiverReportState.GIVEN),
            )

            fixture.database.openHelper.writableDatabase.execSQL(
                "UPDATE occurrences SET lifecycle = 'CANCELLED' WHERE id = ?",
                arrayOf(occurrence.id),
            )
            val dueNow = Instant.parse("2026-06-24T12:00:00Z")
            val cancelledDetail = fixture.todayQueryService.observeOccurrence(
                occurrence.id,
                flowOf(dueNow),
            ).first()
            assertTrue(cancelledDetail?.canMutateReport == false)
            assertEquals(
                SetReportOutcome.CancelledOccurrenceRejected,
                fixture.reportService.setReport(occurrence.id, CaregiverReportState.GIVEN),
            )
        }
    }
}
