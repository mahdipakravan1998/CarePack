package ir.carepack.domain.careplan

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarePlanManagementIntegrationTest {

    private val anchorDate =
        LocalDate.parse(
            "2026-06-24",
        )

    @Test
    fun invalidMedicationSchedule_writesNothing() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val recipientId =
                    fixture.createOrGetRecipient()

                val outcome =
                    fixture
                        .carePlanService
                        .createMedicationAndSchedule(
                            CreateMedicationScheduleCommand(
                                recipientId =
                                    recipientId,
                                medicationName = " ",
                                instruction = " ",
                                weekdays =
                                    emptySet(),
                                minutesOfDay =
                                    emptyList(),
                                startDate =
                                    anchorDate.plusDays(
                                        2,
                                    ),
                                endDate =
                                    anchorDate.plusDays(
                                        1,
                                    ),
                                zoneId =
                                    "Invalid/Zone",
                            ),
                        )

                assertTrue(
                    outcome is
                            CreateMedicationScheduleOutcome
                            .Invalid,
                )

                val errors =
                    (
                            outcome as
                                    CreateMedicationScheduleOutcome
                                    .Invalid
                            ).errors

                assertTrue(
                    errors.any {
                        it.field ==
                                CarePlanField
                                    .MEDICATION_NAME
                    },
                )

                assertTrue(
                    errors.any {
                        it.field ==
                                CarePlanField
                                    .INSTRUCTION
                    },
                )

                assertTrue(
                    errors.any {
                        it.field ==
                                CarePlanField
                                    .WEEKDAYS
                    },
                )

                assertTrue(
                    errors.any {
                        it.field ==
                                CarePlanField
                                    .TIMES
                    },
                )

                assertTrue(
                    errors.any {
                        it.field ==
                                CarePlanField
                                    .END_DATE
                    },
                )

                assertTrue(
                    errors.any {
                        it.field ==
                                CarePlanField
                                    .ZONE_ID
                    },
                )

                assertEquals(
                    0,
                    fixture
                        .database
                        .medicationDao()
                        .count(),
                )

                assertEquals(
                    0,
                    fixture
                        .database
                        .occurrenceDao()
                        .count(),
                )
            }
        }

    @Test
    fun createMedicationSchedule_generatesPersistentOverviewAndOccurrences() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val recipientId =
                    fixture.createOrGetRecipient(
                        displayName = "پدر",
                    )

                val plan =
                    fixture.createPlan(
                        recipientId = recipientId,
                        medicationName = "داروی قلب",
                        instruction = "صبح با آب",
                        weekdays =
                            DayOfWeek
                                .entries
                                .toSet(),
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                        zoneId = "UTC",
                    )

                assertFalse(
                    plan.occurrenceIds.isEmpty(),
                )

                val overview =
                    fixture
                        .carePlanService
                        .observeCarePlan()
                        .first()

                assertEquals(
                    recipientId,
                    overview?.recipientId,
                )

                assertEquals(
                    "پدر",
                    overview?.recipientDisplayName,
                )

                val medication =
                    overview
                        ?.medications
                        ?.single()

                assertEquals(
                    plan.medicationId,
                    medication?.medicationId,
                )

                assertEquals(
                    "داروی قلب",
                    medication?.name,
                )

                assertEquals(
                    "صبح با آب",
                    medication?.instruction,
                )

                assertEquals(
                    listOf(
                        java.time.LocalTime.of(
                            12,
                            0,
                        ),
                    ),
                    medication
                        ?.schedule
                        ?.times,
                )

                assertEquals(
                    "UTC",
                    medication
                        ?.schedule
                        ?.zoneId,
                )

                val occurrences =
                    fixture
                        .occurrencesForMedication(
                            plan.medicationId,
                        )

                assertEquals(
                    plan
                        .occurrenceIds
                        .sorted(),
                    occurrences
                        .map {
                            it.id
                        }
                        .sorted(),
                )

                assertTrue(
                    occurrences.all {
                        it.zoneIdSnapshot ==
                                "UTC" &&
                                it.instructionSnapshot ==
                                "صبح با آب"
                    },
                )
            }
        }

    @Test
    fun updateMedicationText_snapshotsNewVersionAndPreservesReportedOccurrence() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        medicationName = "داروی قدیمی",
                        instruction = "دستور قدیمی",
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                        zoneId = "UTC",
                    )

                val reportedOccurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date = anchorDate,
                        minuteOfDay =
                            12 * 60,
                    )

                fixture.report(
                    occurrenceId =
                        reportedOccurrence.id,
                    state =
                        CaregiverReportState.GIVEN,
                )

                val outcome =
                    fixture
                        .carePlanService
                        .updateMedicationText(
                            UpdateMedicationTextCommand(
                                medicationId =
                                    plan.medicationId,
                                medicationName =
                                    "داروی جدید",
                                instruction =
                                    "دستور جدید",
                            ),
                        )

                assertEquals(
                    UpdateMedicationTextOutcome.Updated,
                    outcome,
                )

                assertEquals(
                    2,
                    fixture
                        .database
                        .scheduleDao()
                        .countVersions(),
                )

                val oldRow =
                    fixture
                        .database
                        .occurrenceDao()
                        .getById(
                            reportedOccurrence.id,
                        )

                assertEquals(
                    OccurrenceLifecycle.ACTIVE.name,
                    oldRow?.lifecycle,
                )

                assertEquals(
                    "داروی قدیمی",
                    oldRow?.medicationNameSnapshot,
                )

                assertEquals(
                    "دستور قدیمی",
                    oldRow?.instructionSnapshot,
                )

                val allOccurrences =
                    fixture
                        .occurrencesForMedication(
                            plan.medicationId,
                        )

                assertTrue(
                    allOccurrences.any {
                        it.scheduleVersionId !=
                                plan.scheduleVersionId &&
                                it.medicationNameSnapshot ==
                                "داروی جدید" &&
                                it.instructionSnapshot ==
                                "دستور جدید"
                    },
                )

                assertTrue(
                    allOccurrences.any {
                        it.scheduleVersionId ==
                                plan.scheduleVersionId &&
                                it.lifecycle ==
                                OccurrenceLifecycle
                                    .CANCELLED
                                    .name &&
                                it.cancellationReason ==
                                OccurrenceCancellationReason
                                    .MEDICATION_UPDATED
                                    .name
                    },
                )
            }
        }

    @Test
    fun updateSchedule_closesOldVersionAndCancelsFutureUnreportedOccurrences() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        medicationName = "داروی زمان‌دار",
                        instruction = "دستور",
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                        zoneId = "UTC",
                    )

                val outcome =
                    fixture
                        .carePlanService
                        .updateSchedule(
                            UpdateScheduleCommand(
                                medicationId =
                                    plan.medicationId,
                                weekdays =
                                    DayOfWeek
                                        .entries
                                        .toSet(),
                                minutesOfDay =
                                    listOf(
                                        13 * 60,
                                    ),
                                startDate = null,
                                endDate = null,
                                zoneId = "UTC",
                            ),
                        )

                assertEquals(
                    UpdateScheduleOutcome.Updated,
                    outcome,
                )

                val occurrences =
                    fixture
                        .occurrencesForMedication(
                            plan.medicationId,
                        )

                assertTrue(
                    occurrences.any {
                        it.scheduleVersionId ==
                                plan.scheduleVersionId &&
                                it.lifecycle ==
                                OccurrenceLifecycle
                                    .CANCELLED
                                    .name &&
                                it.cancellationReason ==
                                OccurrenceCancellationReason
                                    .SCHEDULE_REPLACED
                                    .name
                    },
                )

                assertTrue(
                    occurrences.any {
                        it.scheduleVersionId !=
                                plan.scheduleVersionId &&
                                it.minuteOfDay ==
                                13 * 60 &&
                                it.lifecycle ==
                                OccurrenceLifecycle
                                    .ACTIVE
                                    .name
                    },
                )
            }
        }

    @Test
    fun activeMedication_mustStopBeforeArchive() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                    )

                assertEquals(
                    ArchiveMedicationOutcome.MustStopFirst,
                    fixture
                        .carePlanService
                        .archiveMedication(
                            plan.medicationId,
                        ),
                )

                assertEquals(
                    StopMedicationOutcome.Stopped,
                    fixture
                        .carePlanService
                        .stopMedication(
                            plan.medicationId,
                        ),
                )

                assertEquals(
                    ArchiveMedicationOutcome.Archived,
                    fixture
                        .carePlanService
                        .archiveMedication(
                            plan.medicationId,
                        ),
                )

                val overview =
                    fixture
                        .carePlanService
                        .observeCarePlan()
                        .first()

                assertTrue(
                    overview
                        ?.medications
                        .orEmpty()
                        .none {
                            it.medicationId ==
                                    plan.medicationId
                        },
                )
            }
        }
}
