package ir.carepack.domain.careplan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.testing.IncrementingIdSource
import ir.carepack.testing.MutableTestClock
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

    private lateinit var database:
            CarePackDatabase

    private lateinit var clock:
            MutableTestClock

    private lateinit var idSource:
            IncrementingIdSource

    private lateinit var generator:
            RoomOccurrenceGenerator

    private lateinit var service:
            RoomCarePlanService

    @Before
    fun setUp() {
        val context =
            ApplicationProvider
                .getApplicationContext<
                        Context
                        >()

        database =
            Room
                .inMemoryDatabaseBuilder(
                    context,
                    CarePackDatabase::class.java,
                )
                .build()

        clock =
            MutableTestClock(
                initialInstant =
                    Instant.parse(
                        "2026-06-24T06:00:00Z",
                    ),
            )

        idSource =
            IncrementingIdSource()

        generator =
            RoomOccurrenceGenerator(
                database = database,
                idSource = idSource,
                candidateResolver =
                    OccurrenceCandidateResolver(),
            )

        service =
            RoomCarePlanService(
                database = database,
                occurrenceGenerator =
                    generator,
                clock = clock,
                idSource = idSource,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun invalidMutation_writesNoRows() =
        runBlocking {
            val recipientId =
                createRecipient()

            val outcome =
                service
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId =
                                recipientId,
                            medicationName =
                                "",
                            instruction =
                                "",
                            weekdays =
                                emptySet(),
                            minutesOfDay =
                                emptyList(),
                            startDate =
                                LocalDate.parse(
                                    "2026-06-25",
                                ),
                            endDate =
                                LocalDate.parse(
                                    "2026-06-24",
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

            assertEquals(
                0,
                database
                    .medicationDao()
                    .count(),
            )

            assertEquals(
                0,
                database
                    .scheduleDao()
                    .countSeries(),
            )

            assertEquals(
                0,
                database
                    .scheduleDao()
                    .countVersions(),
            )

            assertEquals(
                0,
                database
                    .occurrenceDao()
                    .count(),
            )
        }

    @Test
    fun rollingWindow_isIdempotent_andRetainsOlderRows() =
        runBlocking {
            val plan =
                createPlan(
                    weekdays =
                        DayOfWeek.entries
                            .toSet(),
                    minutes =
                        listOf(
                            10 * 60,
                            18 * 60,
                        ),
                )

            val countAfterCreation =
                database
                    .occurrenceDao()
                    .count()

            assertTrue(
                countAfterCreation > 0,
            )

            clock.currentInstant =
                Instant.parse(
                    "2026-07-20T06:00:00Z",
                )

            val anchorDate =
                LocalDate.parse(
                    "2026-07-20",
                )

            generator
                .guaranteeWindowForAll(
                    anchorDate =
                        anchorDate,
                    now =
                        clock.instant(),
                )

            val countAfterAdvance =
                database
                    .occurrenceDao()
                    .count()

            assertTrue(
                countAfterAdvance >
                        countAfterCreation,
            )

            generator
                .guaranteeWindowForAll(
                    anchorDate =
                        anchorDate,
                    now =
                        clock.instant(),
                )

            assertEquals(
                countAfterAdvance,
                database
                    .occurrenceDao()
                    .count(),
            )

            assertNotNull(
                database
                    .scheduleDao()
                    .getDefinitionsForVersion(
                        plan.scheduleVersionId,
                    )
                    .firstOrNull(),
            )
        }

    @Test
    fun concurrentGeneration_keepsOneLogicalRow() =
        runBlocking {
            val plan =
                createPlan(
                    weekdays =
                        setOf(
                            DayOfWeek.WEDNESDAY,
                        ),
                    minutes =
                        listOf(
                            12 * 60,
                        ),
                    startDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                    endDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                )

            coroutineScope {
                List(8) {
                    async(
                        Dispatchers.Default,
                    ) {
                        generator
                            .guaranteeWindowForSchedule(
                                scheduleVersionId =
                                    plan
                                        .scheduleVersionId,
                                anchorDate =
                                    LocalDate.parse(
                                        "2026-06-24",
                                    ),
                                now =
                                    clock.instant(),
                            )
                    }
                }.awaitAll()
            }

            assertEquals(
                1,
                database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )
                    .size,
            )
        }

    @Test
    fun scheduleEdit_preservesReported_andCancelsOnlyFutureUnreported() =
        runBlocking {
            val plan =
                createPlan(
                    weekdays =
                        setOf(
                            DayOfWeek.WEDNESDAY,
                        ),
                    minutes =
                        listOf(
                            10 * 60,
                            12 * 60,
                        ),
                    startDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                    endDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                )

            val oldOccurrences =
                database
                    .occurrenceDao()
                    .getForMedication(
                        plan.medicationId,
                    )

            assertEquals(
                2,
                oldOccurrences.size,
            )

            val earlier =
                oldOccurrences.minBy {
                    it.scheduledAtEpochMillis
                }

            val later =
                oldOccurrences.maxBy {
                    it.scheduledAtEpochMillis
                }

            clock.currentInstant =
                Instant.parse(
                    "2026-06-24T07:00:00Z",
                )

            val reportService =
                RoomCaregiverReportService(
                    database = database,
                    clock = clock,
                )

            reportService.setReport(
                occurrenceId = earlier.id,
                newState = CaregiverReportState.GIVEN,
            )

            val outcome =
                service.updateSchedule(
                    UpdateScheduleCommand(
                        medicationId =
                            plan.medicationId,
                        weekdays =
                            setOf(
                                DayOfWeek.WEDNESDAY,
                            ),
                        minutesOfDay =
                            listOf(
                                14 * 60,
                            ),
                        startDate =
                            LocalDate.parse(
                                "2026-06-24",
                            ),
                        endDate =
                            LocalDate.parse(
                                "2026-06-24",
                            ),
                        zoneId =
                            "Asia/Tehran",
                    ),
                )

            assertEquals(
                UpdateScheduleOutcome.Updated,
                outcome,
            )

            val persistedEarlier =
                database
                    .occurrenceDao()
                    .getById(earlier.id)

            val persistedLater =
                database
                    .occurrenceDao()
                    .getById(later.id)

            assertEquals(
                OccurrenceLifecycle
                    .ACTIVE
                    .name,
                persistedEarlier?.lifecycle,
            )

            assertEquals(
                CaregiverReportState
                    .GIVEN
                    .name,
                database
                    .reportingDao()
                    .getReport(
                        earlier.id,
                    )
                    ?.state,
            )

            assertEquals(
                OccurrenceLifecycle
                    .CANCELLED
                    .name,
                persistedLater?.lifecycle,
            )

            assertEquals(
                OccurrenceCancellationReason
                    .SCHEDULE_REPLACED
                    .name,
                persistedLater
                    ?.cancellationReason,
            )

            val all =
                database
                    .occurrenceDao()
                    .getForMedication(
                        plan.medicationId,
                    )

            assertTrue(
                all.any {
                    it.scheduleVersionId !=
                            plan.scheduleVersionId &&
                            it.minuteOfDay ==
                            14 * 60
                },
            )
        }

    @Test
    fun occurrenceExactlyAtEditInstant_isNotCancelled() =
        runBlocking {
            val plan =
                createPlan(
                    weekdays =
                        setOf(
                            DayOfWeek.WEDNESDAY,
                        ),
                    minutes =
                        listOf(
                            12 * 60,
                        ),
                    startDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                    endDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                )

            val occurrence =
                database
                    .occurrenceDao()
                    .getForMedication(
                        plan.medicationId,
                    )
                    .single()

            clock.currentInstant =
                Instant.ofEpochMilli(
                    occurrence
                        .scheduledAtEpochMillis,
                )

            service.updateSchedule(
                UpdateScheduleCommand(
                    medicationId =
                        plan.medicationId,
                    weekdays =
                        setOf(
                            DayOfWeek.WEDNESDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            13 * 60,
                        ),
                    startDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                    endDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                    zoneId =
                        "Asia/Tehran",
                ),
            )

            assertEquals(
                OccurrenceLifecycle
                    .ACTIVE
                    .name,
                database
                    .occurrenceDao()
                    .getById(
                        occurrence.id,
                    )
                    ?.lifecycle,
            )
        }

    @Test
    fun medicationEdit_keepsOldSnapshot_andGeneratesNewSnapshot() =
        runBlocking {
            val plan =
                createPlan(
                    weekdays =
                        setOf(
                            DayOfWeek.WEDNESDAY,
                        ),
                    minutes =
                        listOf(
                            12 * 60,
                        ),
                    startDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                    endDate =
                        LocalDate.parse(
                            "2026-07-01",
                        ),
                )

            val oldOccurrence =
                database
                    .occurrenceDao()
                    .getForMedication(
                        plan.medicationId,
                    )
                    .first()

            clock.currentInstant =
                Instant.parse(
                    "2026-06-24T07:00:00Z",
                )

            val outcome =
                service.updateMedicationText(
                    UpdateMedicationTextCommand(
                        medicationId =
                            plan.medicationId,
                        medicationName =
                            "نام جدید",
                        instruction =
                            "دستور جدید",
                    ),
                )

            assertEquals(
                UpdateMedicationTextOutcome
                    .Updated,
                outcome,
            )

            val persistedOld =
                database
                    .occurrenceDao()
                    .getById(
                        oldOccurrence.id,
                    )

            assertEquals(
                "داروی نمونه",
                persistedOld
                    ?.medicationNameSnapshot,
            )

            assertEquals(
                "دستور نمونه",
                persistedOld
                    ?.medicationInstructionSnapshot,
            )

            val all =
                database
                    .occurrenceDao()
                    .getForMedication(
                        plan.medicationId,
                    )

            assertTrue(
                all.any {
                    it.scheduleVersionId !=
                            plan.scheduleVersionId &&
                            it.medicationNameSnapshot ==
                            "نام جدید" &&
                            it.medicationInstructionSnapshot ==
                            "دستور جدید"
                },
            )
        }

    @Test
    fun stopAndArchive_preserveDomainRows() =
        runBlocking {
            val plan =
                createPlan(
                    weekdays =
                        DayOfWeek.entries
                            .toSet(),
                    minutes =
                        listOf(
                            12 * 60,
                        ),
                )

            val occurrenceCount =
                database
                    .occurrenceDao()
                    .count()

            assertEquals(
                ArchiveMedicationOutcome
                    .MustStopFirst,
                service.archiveMedication(
                    plan.medicationId,
                ),
            )

            assertEquals(
                StopMedicationOutcome.Stopped,
                service.stopMedication(
                    plan.medicationId,
                ),
            )

            val medicationAfterStop =
                database
                    .medicationDao()
                    .getById(
                        plan.medicationId,
                    )

            assertNotNull(
                medicationAfterStop
                    ?.stoppedAtEpochMillis,
            )

            val futureOccurrences =
                database
                    .occurrenceDao()
                    .getForMedication(
                        plan.medicationId,
                    )
                    .filter {
                        it.scheduledAtEpochMillis >
                                clock
                                    .instant()
                                    .toEpochMilli()
                    }

            assertTrue(
                futureOccurrences.all {
                        occurrence ->
                    occurrence.lifecycle ==
                            OccurrenceLifecycle
                                .CANCELLED
                                .name ||
                            database
                                .reportingDao()
                                .getReport(
                                    occurrence.id,
                                ) != null
                },
            )

            assertEquals(
                ArchiveMedicationOutcome
                    .Archived,
                service.archiveMedication(
                    plan.medicationId,
                ),
            )

            assertEquals(
                occurrenceCount,
                database
                    .occurrenceDao()
                    .count(),
            )

            val overview =
                service
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

            assertNotNull(
                database
                    .medicationDao()
                    .getById(
                        plan.medicationId,
                    )
                    ?.archivedAtEpochMillis,
            )
        }

    private suspend fun createRecipient():
            String {
        val outcome =
            service.createRecipient(
                CreateRecipientCommand(
                    displayName =
                        "فرد نمونه",
                ),
            )

        return (
                outcome as
                        CreateRecipientOutcome.Created
                ).recipientId
    }

    private suspend fun createPlan(
        weekdays: Set<DayOfWeek>,
        minutes: List<Int>,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): CreatedPlan {
        val recipientId =
            database
                .careRecipientDao()
                .getSingleton()
                ?.id
                ?: createRecipient()

        val outcome =
            service
                .createMedicationAndSchedule(
                    CreateMedicationScheduleCommand(
                        recipientId =
                            recipientId,
                        medicationName =
                            "داروی نمونه",
                        instruction =
                            "دستور نمونه",
                        weekdays =
                            weekdays,
                        minutesOfDay =
                            minutes,
                        startDate =
                            startDate,
                        endDate =
                            endDate,
                        zoneId =
                            "Asia/Tehran",
                    ),
                ) as
                    CreateMedicationScheduleOutcome
                    .Created

        return CreatedPlan(
            medicationId =
                outcome.medicationId,
            scheduleVersionId =
                outcome.scheduleVersionId,
        )
    }
}

private data class CreatedPlan(
    val medicationId: String,
    val scheduleVersionId: String,
)
