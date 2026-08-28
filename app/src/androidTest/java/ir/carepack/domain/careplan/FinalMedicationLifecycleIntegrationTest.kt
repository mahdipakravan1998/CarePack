package ir.carepack.domain.careplan

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinalMedicationLifecycleIntegrationTest {

    @Test
    fun activeToEndedToArchived_isOneWayReadOnlyAndKeepsHistoricalReport() = runBlocking {
        CarePlanRoomTestFixture.create(
            initialInstant = Instant.parse("2026-06-24T11:59:59Z"),
        ).use { fixture ->
            val plan = fixture.createPlan(minutesOfDay = listOf(12 * 60))
            val occurrence = fixture.occurrenceOn(
                medicationId = plan.medicationId,
                date = LocalDate.parse("2026-06-24"),
                minuteOfDay = 12 * 60,
            )
            fixture.report(occurrence.id, CaregiverReportState.GIVEN)

            assertEquals(
                ArchiveMedicationOutcome.MustStopFirst,
                fixture.carePlanService.archiveMedication(plan.medicationId),
            )
            assertEquals(
                StopMedicationOutcome.Stopped,
                fixture.carePlanService.stopMedication(plan.medicationId),
            )
            assertEquals(
                UpdateMedicationTextOutcome.NotEditable,
                fixture.carePlanService.updateMedicationText(
                    UpdateMedicationTextCommand(
                        medicationId = plan.medicationId,
                        medicationName = "نام تازه",
                        instruction = "متن تازه",
                    ),
                ),
            )
            assertTrue(
                fixture.carePlanService.updateSchedule(
                    UpdateScheduleCommand(
                        scheduleSeriesId = plan.scheduleSeriesId,
                        weekdays = java.time.DayOfWeek.entries.toSet(),
                        minutesOfDay = listOf(15 * 60),
                        startDate = null,
                        endDate = null,
                        zoneId = "UTC",
                    ),
                ) != UpdateScheduleOutcome.Updated,
            )
            assertEquals(
                AddScheduleOutcome.NotEditable,
                fixture.carePlanService.addSchedule(
                    AddScheduleCommand(
                        medicationId = plan.medicationId,
                        weekdays = setOf(java.time.DayOfWeek.WEDNESDAY),
                        minutesOfDay = listOf(14 * 60),
                        startDate = null,
                        endDate = null,
                        zoneId = "UTC",
                    ),
                ),
            )
            assertEquals(
                ArchiveMedicationOutcome.Archived,
                fixture.carePlanService.archiveMedication(plan.medicationId),
            )
            assertEquals(
                ArchiveMedicationOutcome.AlreadyArchived,
                fixture.carePlanService.archiveMedication(plan.medicationId),
            )
            assertTrue(
                fixture.carePlanService.observeCarePlan().first()
                    ?.medications.orEmpty().none { it.medicationId == plan.medicationId },
            )
            val archived = fixture.carePlanService.observeArchivedMedications().first()
                .single { it.medicationId == plan.medicationId }
            assertNotNull(archived.endedAt)
            assertNotNull(archived.archivedAt)
            assertEquals(
                archived,
                fixture.carePlanService.getArchivedMedication(plan.medicationId),
            )
            assertEquals(1, fixture.database.reportingDao().countReportsForOccurrence(occurrence.id))
            assertEquals(
                UpdateMedicationTextOutcome.NotEditable,
                fixture.carePlanService.updateMedicationText(
                    UpdateMedicationTextCommand(
                        medicationId = plan.medicationId,
                        medicationName = "بازگردانی",
                        instruction = "نباید انجام شود",
                    ),
                ),
            )
            assertTrue(
                fixture.carePlanService.updateSchedule(
                    UpdateScheduleCommand(
                        scheduleSeriesId = plan.scheduleSeriesId,
                        weekdays = java.time.DayOfWeek.entries.toSet(),
                        minutesOfDay = listOf(15 * 60),
                        startDate = null,
                        endDate = null,
                        zoneId = "UTC",
                    ),
                ) != UpdateScheduleOutcome.Updated,
            )
        }
    }

    @Test
    fun laterTreatmentWithSameName_createsNewRecordAndLeavesArchivedHistoryUntouched() = runBlocking {
        CarePlanRoomTestFixture.create(
            initialInstant = Instant.parse("2026-06-24T11:59:59Z"),
        ).use { fixture ->
            val original = fixture.createPlan(
                medicationName = "لوزارتان",
                minutesOfDay = listOf(12 * 60),
            )
            val occurrence = fixture.occurrenceOn(
                medicationId = original.medicationId,
                date = LocalDate.parse("2026-06-24"),
                minuteOfDay = 12 * 60,
            )
            fixture.report(occurrence.id, CaregiverReportState.GIVEN)
            assertEquals(StopMedicationOutcome.Stopped, fixture.carePlanService.stopMedication(original.medicationId))
            assertEquals(ArchiveMedicationOutcome.Archived, fixture.carePlanService.archiveMedication(original.medicationId))

            val restarted = fixture.createPlan(
                recipientId = original.recipientId,
                medicationName = "لوزارتان",
                minutesOfDay = listOf(18 * 60),
            )

            assertTrue(restarted.medicationId != original.medicationId)
            assertNotNull(fixture.carePlanService.getArchivedMedication(original.medicationId))
            assertEquals(1, fixture.database.reportingDao().countReportsForOccurrence(occurrence.id))
            assertTrue(
                fixture.carePlanService.observeCarePlan().first()
                    ?.medications.orEmpty().any { it.medicationId == restarted.medicationId },
            )
        }
    }
}
