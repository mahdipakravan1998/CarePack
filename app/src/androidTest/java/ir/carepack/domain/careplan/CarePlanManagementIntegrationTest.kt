package ir.carepack.domain.careplan

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.Instant
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
                                schedulePattern =
                                    FixedTimeSchedule(
                                        minutesOfDay =
                                            emptyList(),
                                    ),
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
    fun createFixedTimeMedicationSchedule_generatesPersistentOverviewAndOccurrences() =
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
                            DayOfWeek.entries.toSet(),
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                        schedulePattern =
                            FixedTimeSchedule(
                                minutesOfDay =
                                    listOf(
                                        12 * 60,
                                    ),
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
                    1,
                    medication
                        ?.schedules
                        ?.size,
                )

                assertTrue(
                    medication
                        ?.schedules
                        ?.single()
                        ?.schedulePattern
                            is FixedTimeSchedule,
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
            }
        }

    @Test
    fun medicationCanHaveMultipleActiveSchedules() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        medicationName =
                            "داروی چندبرنامه‌ای",
                        instruction =
                            "دستور",
                        minutesOfDay =
                            listOf(
                                8 * 60,
                            ),
                        startDate =
                            anchorDate,
                        endDate =
                            anchorDate,
                    )

                val secondSchedule =
                    fixture.addSchedule(
                        medicationId =
                            plan.medicationId,
                        minutesOfDay =
                            listOf(
                                20 * 60,
                            ),
                        startDate =
                            anchorDate,
                        endDate =
                            anchorDate,
                    )

                val overview =
                    fixture
                        .carePlanService
                        .observeCarePlan()
                        .first()

                val medication =
                    overview
                        ?.medications
                        ?.single {
                            it.medicationId ==
                                    plan.medicationId
                        }

                assertEquals(
                    2,
                    medication
                        ?.schedules
                        ?.size,
                )

                assertEquals(
                    setOf(
                        plan.scheduleSeriesId,
                        secondSchedule.scheduleSeriesId,
                    ),
                    medication
                        ?.schedules
                        ?.map {
                            it.scheduleSeriesId
                        }
                        ?.toSet(),
                )

                assertEquals(
                    listOf(
                        8 * 60,
                        20 * 60,
                    ),
                    fixture
                        .occurrencesForMedication(
                            plan.medicationId,
                        )
                        .filter {
                            it.localEpochDay ==
                                    anchorDate.toEpochDay()
                        }
                        .map {
                            it.minuteOfDay
                        }
                        .sorted(),
                )
            }
        }

    @Test
    fun addingScheduleToExistingMedicationCreatesExpectedOccurrences() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        minutesOfDay =
                            listOf(
                                9 * 60,
                            ),
                        startDate =
                            anchorDate,
                        endDate =
                            anchorDate,
                    )

                val added =
                    fixture.addSchedule(
                        medicationId =
                            plan.medicationId,
                        weekdays =
                            setOf(
                                DayOfWeek.WEDNESDAY,
                            ),
                        minutesOfDay =
                            listOf(
                                14 * 60,
                                18 * 60,
                            ),
                        startDate =
                            anchorDate,
                        endDate =
                            anchorDate,
                    )

                assertFalse(
                    added.occurrenceIds.isEmpty(),
                )

                assertEquals(
                    listOf(
                        14 * 60,
                        18 * 60,
                    ),
                    fixture
                        .occurrencesForSchedule(
                            added.scheduleVersionId,
                        )
                        .filter {
                            it.localEpochDay ==
                                    anchorDate.toEpochDay()
                        }
                        .map {
                            it.minuteOfDay
                        }
                        .sorted(),
                )
            }
        }

    @Test
    fun editingOneScheduleDoesNotCorruptAnotherSchedule() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                            ),
                        startDate =
                            anchorDate,
                        endDate =
                            anchorDate.plusDays(
                                1,
                            ),
                    )

                val secondSchedule =
                    fixture.addSchedule(
                        medicationId =
                            plan.medicationId,
                        minutesOfDay =
                            listOf(
                                20 * 60,
                            ),
                        startDate =
                            anchorDate,
                        endDate =
                            anchorDate.plusDays(
                                1,
                            ),
                    )

                val secondScheduleIdsBefore =
                    fixture
                        .occurrencesForSchedule(
                            secondSchedule
                                .scheduleVersionId,
                        )
                        .map {
                            it.id
                        }
                        .sorted()

                fixture.moveTo(
                    Instant.parse(
                        "2026-06-24T09:00:00Z",
                    ),
                )

                val outcome =
                    fixture
                        .carePlanService
                        .updateSchedule(
                            UpdateScheduleCommand(
                                scheduleSeriesId =
                                    plan.scheduleSeriesId,
                                weekdays =
                                    setOf(
                                        DayOfWeek.WEDNESDAY,
                                        DayOfWeek.THURSDAY,
                                    ),
                                minutesOfDay =
                                    listOf(
                                        10 * 60,
                                    ),
                                schedulePattern =
                                    FixedTimeSchedule(
                                        minutesOfDay =
                                            listOf(
                                                10 * 60,
                                            ),
                                    ),
                                startDate =
                                    anchorDate,
                                endDate =
                                    anchorDate.plusDays(
                                        1,
                                    ),
                                zoneId = "UTC",
                            ),
                        )

                assertEquals(
                    UpdateScheduleOutcome.Updated,
                    outcome,
                )

                val secondScheduleIdsAfter =
                    fixture
                        .occurrencesForSchedule(
                            secondSchedule
                                .scheduleVersionId,
                        )
                        .map {
                            it.id
                        }
                        .sorted()

                assertEquals(
                    secondScheduleIdsBefore,
                    secondScheduleIdsAfter,
                )

                assertTrue(
                    fixture
                        .occurrencesForMedication(
                            plan.medicationId,
                        )
                        .any {
                            it.scheduleVersionId !=
                                    plan.scheduleVersionId &&
                                    it.scheduleVersionId !=
                                    secondSchedule.scheduleVersionId &&
                                    it.minuteOfDay ==
                                    10 * 60
                        },
                )
            }
        }

    @Test
    fun reportedHistoryIsPreservedWhenOneOfMultipleSchedulesIsEdited() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                            ),
                        startDate =
                            anchorDate,
                        endDate =
                            anchorDate.plusDays(
                                1,
                            ),
                    )

                fixture.addSchedule(
                    medicationId =
                        plan.medicationId,
                    minutesOfDay =
                        listOf(
                            20 * 60,
                        ),
                    startDate =
                        anchorDate,
                    endDate =
                        anchorDate.plusDays(
                            1,
                        ),
                )

                val reportedOccurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date = anchorDate,
                        minuteOfDay =
                            8 * 60,
                    )

                fixture.report(
                    occurrenceId =
                        reportedOccurrence.id,
                    state =
                        CaregiverReportState.GIVEN,
                )

                fixture.moveTo(
                    Instant.parse(
                        "2026-06-24T09:00:00Z",
                    ),
                )

                assertEquals(
                    UpdateScheduleOutcome.Updated,
                    fixture
                        .carePlanService
                        .updateSchedule(
                            UpdateScheduleCommand(
                                scheduleSeriesId =
                                    plan.scheduleSeriesId,
                                weekdays =
                                    setOf(
                                        DayOfWeek.WEDNESDAY,
                                        DayOfWeek.THURSDAY,
                                    ),
                                minutesOfDay =
                                    listOf(
                                        10 * 60,
                                    ),
                                schedulePattern =
                                    FixedTimeSchedule(
                                        minutesOfDay =
                                            listOf(
                                                10 * 60,
                                            ),
                                    ),
                                startDate =
                                    anchorDate,
                                endDate =
                                    anchorDate.plusDays(
                                        1,
                                    ),
                                zoneId = "UTC",
                            ),
                        ),
                )

                val preservedOccurrence =
                    fixture
                        .database
                        .occurrenceDao()
                        .getById(
                            reportedOccurrence.id,
                        )

                assertEquals(
                    OccurrenceLifecycle.ACTIVE.name,
                    preservedOccurrence?.lifecycle,
                )

                assertEquals(
                    CaregiverReportState.GIVEN.name,
                    fixture
                        .database
                        .reportingDao()
                        .getReport(
                            reportedOccurrence.id,
                        )
                        ?.state,
                )
            }
        }

    @Test
    fun createIntervalSchedule_generatesStableOccurrences() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        START_OF_ANCHOR_DAY,
                )
                .use { fixture ->
                    val plan =
                        fixture.createPlan(
                            weekdays =
                                setOf(
                                    DayOfWeek.WEDNESDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    7 * 60,
                                    15 * 60,
                                    23 * 60,
                                ),
                            schedulePattern =
                                IntervalSchedule(
                                    intervalHours = 8,
                                    anchorMinuteOfDay =
                                        7 * 60,
                                ),
                            startDate = anchorDate,
                            endDate = anchorDate,
                            zoneId = "UTC",
                        )

                    val occurrences =
                        fixture
                            .occurrencesForMedication(
                                plan.medicationId,
                            )
                            .filter {
                                it.localEpochDay ==
                                        anchorDate.toEpochDay()
                            }
                            .map {
                                it.minuteOfDay
                            }
                            .sorted()

                    assertEquals(
                        listOf(
                            7 * 60,
                            15 * 60,
                            23 * 60,
                        ),
                        occurrences,
                    )

                    val beforeIds =
                        fixture
                            .occurrencesForMedication(
                                plan.medicationId,
                            )
                            .map {
                                it.id
                            }
                            .sorted()

                    fixture.guaranteeWindow(
                        anchorDate =
                            anchorDate,
                    )

                    val afterIds =
                        fixture
                            .occurrencesForMedication(
                                plan.medicationId,
                            )
                            .map {
                                it.id
                            }
                            .sorted()

                    assertEquals(
                        beforeIds,
                        afterIds,
                    )
                }
        }

    @Test
    fun updateSchedule_fromFixedToInterval_preservesReportedHistory() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        weekdays =
                            setOf(
                                DayOfWeek.WEDNESDAY,
                            ),
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                        schedulePattern =
                            FixedTimeSchedule(
                                minutesOfDay =
                                    listOf(
                                        12 * 60,
                                    ),
                            ),
                        startDate =
                            anchorDate,
                        endDate =
                            anchorDate.plusDays(
                                1,
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

                fixture.moveTo(
                    Instant.parse(
                        "2026-06-24T13:00:00Z",
                    ),
                )

                val outcome =
                    fixture
                        .carePlanService
                        .updateSchedule(
                            UpdateScheduleCommand(
                                scheduleSeriesId =
                                    plan.scheduleSeriesId,
                                weekdays =
                                    setOf(
                                        DayOfWeek.WEDNESDAY,
                                    ),
                                minutesOfDay =
                                    listOf(
                                        7 * 60,
                                        15 * 60,
                                        23 * 60,
                                    ),
                                schedulePattern =
                                    IntervalSchedule(
                                        intervalHours = 8,
                                        anchorMinuteOfDay =
                                            7 * 60,
                                    ),
                                startDate =
                                    anchorDate,
                                endDate =
                                    anchorDate.plusDays(
                                        1,
                                    ),
                                zoneId = "UTC",
                            ),
                        )

                assertEquals(
                    UpdateScheduleOutcome.Updated,
                    outcome,
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
                    CaregiverReportState.GIVEN.name,
                    fixture
                        .database
                        .reportingDao()
                        .getReport(
                            reportedOccurrence.id,
                        )
                        ?.state,
                )

                val occurrences =
                    fixture
                        .occurrencesForMedication(
                            plan.medicationId,
                        )

                assertTrue(
                    occurrences.any {
                        it.scheduleVersionId !=
                                plan.scheduleVersionId &&
                                it.minuteOfDay ==
                                15 * 60 &&
                                it.lifecycle ==
                                OccurrenceLifecycle
                                    .ACTIVE
                                    .name
                    },
                )
            }
        }

    @Test
    fun updateSchedule_fromIntervalToFixed_preservesReportedHistory() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        START_OF_ANCHOR_DAY,
                )
                .use { fixture ->
                    val plan =
                        fixture.createPlan(
                            weekdays =
                                setOf(
                                    DayOfWeek.WEDNESDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    7 * 60,
                                    15 * 60,
                                    23 * 60,
                                ),
                            schedulePattern =
                                IntervalSchedule(
                                    intervalHours = 8,
                                    anchorMinuteOfDay =
                                        7 * 60,
                                ),
                            startDate =
                                anchorDate,
                            endDate =
                                anchorDate.plusDays(
                                    1,
                                ),
                            zoneId = "UTC",
                        )

                    val reportedOccurrence =
                        fixture.occurrenceOn(
                            medicationId =
                                plan.medicationId,
                            date = anchorDate,
                            minuteOfDay =
                                7 * 60,
                        )

                    fixture.report(
                        occurrenceId =
                            reportedOccurrence.id,
                        state =
                            CaregiverReportState.GIVEN,
                    )

                    fixture.moveTo(
                        Instant.parse(
                            "2026-06-24T08:00:00Z",
                        ),
                    )

                    val outcome =
                        fixture
                            .carePlanService
                            .updateSchedule(
                                UpdateScheduleCommand(
                                    scheduleSeriesId =
                                        plan.scheduleSeriesId,
                                    weekdays =
                                        setOf(
                                            DayOfWeek.WEDNESDAY,
                                        ),
                                    minutesOfDay =
                                        listOf(
                                            12 * 60,
                                        ),
                                    schedulePattern =
                                        FixedTimeSchedule(
                                            minutesOfDay =
                                                listOf(
                                                    12 * 60,
                                                ),
                                        ),
                                    startDate =
                                        anchorDate,
                                    endDate =
                                        anchorDate.plusDays(
                                            1,
                                        ),
                                    zoneId = "UTC",
                                ),
                            )

                    assertEquals(
                        UpdateScheduleOutcome.Updated,
                        outcome,
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

                    assertTrue(
                        fixture
                            .occurrencesForMedication(
                                plan.medicationId,
                            )
                            .any {
                                it.scheduleVersionId !=
                                        plan.scheduleVersionId &&
                                        it.minuteOfDay ==
                                        12 * 60 &&
                                        it.lifecycle ==
                                        OccurrenceLifecycle
                                            .ACTIVE
                                            .name
                            },
                    )
                }
        }

    @Test
    fun updateMedicationText_snapshotsNewVersionAcrossAllSchedulesAndPreservesReportedOccurrence() =
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

                val secondSchedule =
                    fixture.addSchedule(
                        medicationId =
                            plan.medicationId,
                        minutesOfDay =
                            listOf(
                                18 * 60,
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

                assertTrue(
                    fixture
                        .occurrencesForMedication(
                            plan.medicationId,
                        )
                        .any {
                            it.scheduleVersionId ==
                                    secondSchedule.scheduleVersionId &&
                                    it.lifecycle ==
                                    OccurrenceLifecycle.CANCELLED.name &&
                                    it.cancellationReason ==
                                    OccurrenceCancellationReason
                                        .MEDICATION_UPDATED
                                        .name
                        },
                )
            }
        }

    @Test
    fun stopMedication_closesAndCancelsAllSchedules() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                    )

                val secondSchedule =
                    fixture.addSchedule(
                        medicationId =
                            plan.medicationId,
                        minutesOfDay =
                            listOf(
                                20 * 60,
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

                assertTrue(
                    fixture
                        .occurrencesForMedication(
                            plan.medicationId,
                        )
                        .any {
                            it.scheduleVersionId ==
                                    plan.scheduleVersionId &&
                                    it.lifecycle ==
                                    OccurrenceLifecycle
                                        .CANCELLED
                                        .name
                        },
                )

                assertTrue(
                    fixture
                        .occurrencesForMedication(
                            plan.medicationId,
                        )
                        .any {
                            it.scheduleVersionId ==
                                    secondSchedule.scheduleVersionId &&
                                    it.lifecycle ==
                                    OccurrenceLifecycle
                                        .CANCELLED
                                        .name
                        },
                )

                assertTrue(
                    fixture
                        .database
                        .scheduleDao()
                        .getOpenVersionsForMedication(
                            plan.medicationId,
                        )
                        .isEmpty(),
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

                fixture.addSchedule(
                    medicationId =
                        plan.medicationId,
                    minutesOfDay =
                        listOf(
                            18 * 60,
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

    private companion object {
        val START_OF_ANCHOR_DAY: Instant =
            Instant.parse(
                "2026-06-24T00:00:00Z",
            )
    }
}
