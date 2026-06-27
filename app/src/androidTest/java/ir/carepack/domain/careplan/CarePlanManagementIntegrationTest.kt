package ir.carepack.domain.careplan

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarePlanManagementIntegrationTest {

    private lateinit var fixture: CarePlanRoomTestFixture

    @Before
    fun setUp() {
        fixture = CarePlanRoomTestFixture.create(
            initialInstant = INITIAL_INSTANT,
            idPrefix = "care-plan-id",
        )
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun invalidMutation_writesNoRows() = runBlocking {
        val recipientId = fixture.createOrGetRecipient()

        val outcome = fixture.carePlanService.createMedicationAndSchedule(
            CreateMedicationScheduleCommand(
                recipientId = recipientId,
                medicationName = "",
                instruction = "",
                weekdays = emptySet(),
                minutesOfDay = emptyList(),
                startDate = LocalDate.parse("2026-06-25"),
                endDate = LocalDate.parse("2026-06-24"),
                zoneId = "Invalid/Zone",
            ),
        )

        assertTrue(outcome is CreateMedicationScheduleOutcome.Invalid)
        assertEquals(0, fixture.database.medicationDao().count())
        assertEquals(0, fixture.database.scheduleDao().countSeries())
        assertEquals(0, fixture.database.scheduleDao().countVersions())
        assertEquals(0, fixture.database.occurrenceDao().count())
    }

    @Test
    fun rollingWindow_isInclusiveMidnightSafeIdempotentAndRetentionSafe() = runBlocking {
        val plan = fixture.createPlan(
            minutesOfDay = listOf(12 * 60),
        )

        val initialRows = fixture.database.occurrenceDao()
            .getForVersion(plan.scheduleVersionId)

        assertEquals(8, initialRows.size)
        val oldestOccurrenceId = initialRows.first().id

        fixture.moveTo(Instant.parse("2026-06-24T20:31:00Z"))
        fixture.guaranteeWindow(LocalDate.parse("2026-06-25"))

        val afterMidnight = fixture.database.occurrenceDao()
            .getForVersion(plan.scheduleVersionId)

        assertEquals(9, afterMidnight.size)
        assertNotNull(fixture.database.occurrenceDao().getById(oldestOccurrenceId))

        fixture.guaranteeWindow(LocalDate.parse("2026-06-25"))

        assertEquals(
            9,
            fixture.database.occurrenceDao()
                .getForVersion(plan.scheduleVersionId)
                .size,
        )

        fixture.moveTo(Instant.parse("2026-07-01T06:00:00Z"))
        fixture.guaranteeWindow(LocalDate.parse("2026-07-01"))

        val completeWindow = fixture.database.occurrenceDao()
            .getForVersion(plan.scheduleVersionId)

        assertEquals(15, completeWindow.size)
        assertEquals(
            LocalDate.parse("2026-06-24").toEpochDay(),
            completeWindow.first().localDateEpochDay,
        )
        assertEquals(
            LocalDate.parse("2026-07-08").toEpochDay(),
            completeWindow.last().localDateEpochDay,
        )
        assertNotNull(fixture.database.occurrenceDao().getById(oldestOccurrenceId))
    }

    @Test
    fun concurrentGeneration_keepsOneLogicalRow() = runBlocking {
        val plan = fixture.createPlan(
            weekdays = setOf(DayOfWeek.WEDNESDAY),
            minutesOfDay = listOf(12 * 60),
            startDate = ANCHOR_DATE,
            endDate = ANCHOR_DATE,
        )

        coroutineScope {
            List(CONCURRENT_GENERATION_COUNT) {
                async(Dispatchers.Default) {
                    fixture.occurrenceGenerator.guaranteeWindowForSchedule(
                        scheduleVersionId = plan.scheduleVersionId,
                        anchorDate = ANCHOR_DATE,
                        now = fixture.clock.instant(),
                    )
                }
            }.awaitAll()
        }

        assertEquals(
            1,
            fixture.database.occurrenceDao()
                .getForVersion(plan.scheduleVersionId)
                .size,
        )
    }

    @Test
    fun scheduleEdit_preservesReportedFutureOccurrenceAndKeepsOneActiveVersion() = runBlocking {
        val plan = fixture.createPlan(
            weekdays = setOf(DayOfWeek.WEDNESDAY),
            minutesOfDay = listOf(12 * 60, 14 * 60),
            startDate = ANCHOR_DATE,
            endDate = ANCHOR_DATE,
        )

        val oldOccurrences = fixture.database.occurrenceDao()
            .getForVersion(plan.scheduleVersionId)
        val unreportedFuture = oldOccurrences.single { it.minuteOfDay == 12 * 60 }
        val reportedFuture = oldOccurrences.single { it.minuteOfDay == 14 * 60 }

        fixture.reportService.setReport(
            occurrenceId = reportedFuture.id,
            newState = CaregiverReportState.GIVEN,
        )

        fixture.moveTo(Instant.parse("2026-06-24T07:00:00Z"))
        val editInstant = fixture.clock.instant().toEpochMilli()

        val outcome = fixture.carePlanService.updateSchedule(
            UpdateScheduleCommand(
                medicationId = plan.medicationId,
                weekdays = setOf(DayOfWeek.WEDNESDAY),
                minutesOfDay = listOf(16 * 60),
                startDate = ANCHOR_DATE,
                endDate = ANCHOR_DATE,
                zoneId = "Asia/Tehran",
            ),
        )

        assertEquals(UpdateScheduleOutcome.Updated, outcome)

        val persistedUnreported = fixture.database.occurrenceDao()
            .getById(unreportedFuture.id)
        assertEquals(OccurrenceLifecycle.CANCELLED.name, persistedUnreported?.lifecycle)
        assertEquals(
            OccurrenceCancellationReason.SCHEDULE_REPLACED.name,
            persistedUnreported?.cancellationReason,
        )

        val persistedReported = fixture.database.occurrenceDao()
            .getById(reportedFuture.id)
        assertEquals(OccurrenceLifecycle.ACTIVE.name, persistedReported?.lifecycle)
        assertEquals(
            CaregiverReportState.GIVEN.name,
            fixture.database.reportingDao().getReport(reportedFuture.id)?.state,
        )

        val activeVersions = fixture.database.scheduleDao()
            .getOpenVersionsForMedication(plan.medicationId)
        assertEquals(1, activeVersions.size)
        assertEquals(2, activeVersions.single().versionNumber)

        listOf(editInstant - 1, editInstant, editInstant + 1).forEach { instant ->
            assertEquals(
                1,
                fixture.database.scheduleDao().countVersionsActiveAt(
                    seriesId = plan.scheduleSeriesId,
                    instantEpochMillis = instant,
                ),
            )
        }

        assertTrue(
            fixture.database.occurrenceDao()
                .getForMedication(plan.medicationId)
                .any {
                    it.scheduleVersionId != plan.scheduleVersionId &&
                            it.minuteOfDay == 16 * 60
                },
        )
    }

    @Test
    fun occurrenceExactlyAtEditInstant_isNotCancelled() = runBlocking {
        val plan = fixture.createPlan(
            weekdays = setOf(DayOfWeek.WEDNESDAY),
            minutesOfDay = listOf(12 * 60),
            startDate = ANCHOR_DATE,
            endDate = ANCHOR_DATE,
        )
        val occurrence = fixture.database.occurrenceDao()
            .getForMedication(plan.medicationId)
            .single()

        fixture.moveTo(Instant.ofEpochMilli(occurrence.scheduledAtEpochMillis))

        fixture.carePlanService.updateSchedule(
            UpdateScheduleCommand(
                medicationId = plan.medicationId,
                weekdays = setOf(DayOfWeek.WEDNESDAY),
                minutesOfDay = listOf(13 * 60),
                startDate = ANCHOR_DATE,
                endDate = ANCHOR_DATE,
                zoneId = "Asia/Tehran",
            ),
        )

        assertEquals(
            OccurrenceLifecycle.ACTIVE.name,
            fixture.database.occurrenceDao().getById(occurrence.id)?.lifecycle,
        )
    }

    @Test
    fun medicationTextEdit_preservesReportedSnapshotAndGeneratesNewSnapshot() = runBlocking {
        val plan = fixture.createPlan(
            weekdays = setOf(DayOfWeek.WEDNESDAY),
            minutesOfDay = listOf(12 * 60),
            startDate = ANCHOR_DATE,
            endDate = LocalDate.parse("2026-07-01"),
        )
        val reportedOccurrence = fixture.database.occurrenceDao()
            .getForVersion(plan.scheduleVersionId)
            .first()

        fixture.reportService.setReport(
            occurrenceId = reportedOccurrence.id,
            newState = CaregiverReportState.GIVEN,
        )
        fixture.moveTo(Instant.parse("2026-06-24T07:00:00Z"))

        val outcome = fixture.carePlanService.updateMedicationText(
            UpdateMedicationTextCommand(
                medicationId = plan.medicationId,
                medicationName = "نام جدید",
                instruction = "دستور جدید",
            ),
        )

        assertEquals(UpdateMedicationTextOutcome.Updated, outcome)

        val persistedOld = fixture.database.occurrenceDao()
            .getById(reportedOccurrence.id)
        assertEquals(OccurrenceLifecycle.ACTIVE.name, persistedOld?.lifecycle)
        assertEquals("داروی نمونه", persistedOld?.medicationNameSnapshot)
        assertEquals("دستور نمونه", persistedOld?.medicationInstructionSnapshot)
        assertEquals(
            CaregiverReportState.GIVEN.name,
            fixture.database.reportingDao().getReport(reportedOccurrence.id)?.state,
        )

        assertTrue(
            fixture.database.occurrenceDao()
                .getForMedication(plan.medicationId)
                .any {
                    it.scheduleVersionId != plan.scheduleVersionId &&
                            it.medicationNameSnapshot == "نام جدید" &&
                            it.medicationInstructionSnapshot == "دستور جدید"
                },
        )
    }

    @Test
    fun stopAndArchive_preserveReportedOccurrenceAndAllDomainRows() = runBlocking {
        val plan = fixture.createPlan(
            weekdays = setOf(DayOfWeek.WEDNESDAY),
            minutesOfDay = listOf(12 * 60, 14 * 60),
            startDate = ANCHOR_DATE,
            endDate = ANCHOR_DATE,
        )
        val occurrences = fixture.database.occurrenceDao()
            .getForVersion(plan.scheduleVersionId)
        val unreportedFuture = occurrences.single { it.minuteOfDay == 12 * 60 }
        val reportedFuture = occurrences.single { it.minuteOfDay == 14 * 60 }

        fixture.reportService.setReport(
            occurrenceId = reportedFuture.id,
            newState = CaregiverReportState.GIVEN,
        )
        fixture.moveTo(Instant.parse("2026-06-24T07:00:00Z"))

        val occurrenceCountBefore = fixture.database.occurrenceDao().count()

        assertEquals(
            ArchiveMedicationOutcome.MustStopFirst,
            fixture.carePlanService.archiveMedication(plan.medicationId),
        )
        assertEquals(
            StopMedicationOutcome.Stopped,
            fixture.carePlanService.stopMedication(plan.medicationId),
        )

        assertNotNull(
            fixture.database.medicationDao()
                .getById(plan.medicationId)
                ?.stoppedAtEpochMillis,
        )
        assertEquals(
            OccurrenceLifecycle.CANCELLED.name,
            fixture.database.occurrenceDao().getById(unreportedFuture.id)?.lifecycle,
        )
        assertEquals(
            OccurrenceCancellationReason.MEDICATION_STOPPED.name,
            fixture.database.occurrenceDao().getById(unreportedFuture.id)?.cancellationReason,
        )
        assertEquals(
            OccurrenceLifecycle.ACTIVE.name,
            fixture.database.occurrenceDao().getById(reportedFuture.id)?.lifecycle,
        )
        assertEquals(
            CaregiverReportState.GIVEN.name,
            fixture.database.reportingDao().getReport(reportedFuture.id)?.state,
        )
        assertEquals(occurrenceCountBefore, fixture.database.occurrenceDao().count())

        assertEquals(
            ArchiveMedicationOutcome.Archived,
            fixture.carePlanService.archiveMedication(plan.medicationId),
        )
        assertEquals(occurrenceCountBefore, fixture.database.occurrenceDao().count())
        assertTrue(
            fixture.carePlanService.observeCarePlan()
                .first()
                ?.medications
                .orEmpty()
                .none { it.medicationId == plan.medicationId },
        )
        assertNotNull(
            fixture.database.medicationDao()
                .getById(plan.medicationId)
                ?.archivedAtEpochMillis,
        )
    }

    private companion object {
        val INITIAL_INSTANT: Instant = Instant.parse("2026-06-24T06:00:00Z")
        val ANCHOR_DATE: LocalDate = LocalDate.parse("2026-06-24")
        const val CONCURRENT_GENERATION_COUNT = 8
    }
}
