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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarePlanManagementContractTest {

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
                .getApplicationContext<Context>()

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
            IncrementingIdSource(
                prefix = "contract-id",
            )

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
    fun rollingWindow_isInclusive_midnightSafe_andRetentionSafe() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
                        listOf(
                            12 * 60,
                        ),
                )

            val initialRows =
                database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )

            assertEquals(
                8,
                initialRows.size,
            )

            val oldestOccurrenceId =
                initialRows
                    .first()
                    .id

            clock.currentInstant =
                Instant.parse(
                    "2026-06-24T20:31:00Z",
                )

            generator
                .guaranteeWindowForAll(
                    anchorDate =
                        LocalDate.parse(
                            "2026-06-25",
                        ),
                    now =
                        clock.instant(),
                )

            val afterMidnight =
                database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )

            assertEquals(
                9,
                afterMidnight.size,
            )

            assertNotNull(
                database
                    .occurrenceDao()
                    .getById(
                        oldestOccurrenceId,
                    ),
            )

            generator
                .guaranteeWindowForAll(
                    anchorDate =
                        LocalDate.parse(
                            "2026-06-25",
                        ),
                    now =
                        clock.instant(),
                )

            assertEquals(
                9,
                database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )
                    .size,
            )

            clock.currentInstant =
                Instant.parse(
                    "2026-07-01T06:00:00Z",
                )

            generator
                .guaranteeWindowForAll(
                    anchorDate =
                        LocalDate.parse(
                            "2026-07-01",
                        ),
                    now =
                        clock.instant(),
                )

            val completeWindow =
                database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )

            assertEquals(
                15,
                completeWindow.size,
            )

            assertEquals(
                LocalDate.parse(
                    "2026-06-24",
                ).toEpochDay(),
                completeWindow
                    .first()
                    .localDateEpochDay,
            )

            assertEquals(
                LocalDate.parse(
                    "2026-07-08",
                ).toEpochDay(),
                completeWindow
                    .last()
                    .localDateEpochDay,
            )

            assertNotNull(
                database
                    .occurrenceDao()
                    .getById(
                        oldestOccurrenceId,
                    ),
            )
        }

    @Test
    fun scheduleEdit_preservesReportedFutureOccurrence_andKeepsOneActiveVersion() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
                        listOf(
                            12 * 60,
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
                )

            val oldOccurrences =
                database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )

            val unreportedFuture =
                oldOccurrences.single {
                    it.minuteOfDay ==
                            12 * 60
                }

            val reportedFuture =
                oldOccurrences.single {
                    it.minuteOfDay ==
                            14 * 60
                }

            val reportService =
                RoomCaregiverReportService(
                    database = database,
                    clock = clock,
                )

            reportService.setReport(
                occurrenceId = reportedFuture.id,
                newState = CaregiverReportState.GIVEN,
            )

            clock.currentInstant =
                Instant.parse(
                    "2026-06-24T07:00:00Z",
                )

            val editInstant =
                clock
                    .instant()
                    .toEpochMilli()

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
                                16 * 60,
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

            val persistedUnreported =
                database
                    .occurrenceDao()
                    .getById(
                        unreportedFuture.id,
                    )

            assertEquals(
                OccurrenceLifecycle
                    .CANCELLED
                    .name,
                persistedUnreported
                    ?.lifecycle,
            )

            assertEquals(
                OccurrenceCancellationReason
                    .SCHEDULE_REPLACED
                    .name,
                persistedUnreported
                    ?.cancellationReason,
            )

            val persistedReported =
                database
                    .occurrenceDao()
                    .getById(
                        reportedFuture.id,
                    )

            assertEquals(
                OccurrenceLifecycle
                    .ACTIVE
                    .name,
                persistedReported
                    ?.lifecycle,
            )

            assertEquals(
                CaregiverReportState
                    .GIVEN
                    .name,
                database
                    .reportingDao()
                    .getReport(
                        reportedFuture.id,
                    )
                    ?.state,
            )

            val activeVersions =
                database
                    .scheduleDao()
                    .getOpenVersionsForMedication(
                        plan.medicationId,
                    )

            assertEquals(
                1,
                activeVersions.size,
            )

            assertEquals(
                2,
                activeVersions
                    .single()
                    .versionNumber,
            )

            assertEquals(
                1,
                database
                    .scheduleDao()
                    .countVersionsActiveAt(
                        seriesId =
                            plan.scheduleSeriesId,
                        instantEpochMillis =
                            editInstant - 1,
                    ),
            )

            assertEquals(
                1,
                database
                    .scheduleDao()
                    .countVersionsActiveAt(
                        seriesId =
                            plan.scheduleSeriesId,
                        instantEpochMillis =
                            editInstant,
                    ),
            )

            assertEquals(
                1,
                database
                    .scheduleDao()
                    .countVersionsActiveAt(
                        seriesId =
                            plan.scheduleSeriesId,
                        instantEpochMillis =
                            editInstant + 1,
                    ),
            )

            val allOccurrences =
                database
                    .occurrenceDao()
                    .getForMedication(
                        plan.medicationId,
                    )

            assertTrue(
                allOccurrences.any {
                    it.scheduleVersionId !=
                            plan.scheduleVersionId &&
                            it.minuteOfDay ==
                            16 * 60
                },
            )
        }

    @Test
    fun medicationTextEdit_preservesReportedSnapshot() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
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

            val reportedOccurrence =
                database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )
                    .first()

            RoomCaregiverReportService(
                database = database,
                clock = clock,
            ).setReport(
                occurrenceId = reportedOccurrence.id,
                newState = CaregiverReportState.GIVEN,
            )

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
                UpdateMedicationTextOutcome.Updated,
                outcome,
            )

            val persistedOld =
                database
                    .occurrenceDao()
                    .getById(
                        reportedOccurrence.id,
                    )

            assertEquals(
                OccurrenceLifecycle
                    .ACTIVE
                    .name,
                persistedOld?.lifecycle,
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

            assertEquals(
                CaregiverReportState
                    .GIVEN
                    .name,
                database
                    .reportingDao()
                    .getReport(
                        reportedOccurrence.id,
                    )
                    ?.state,
            )

            val allOccurrences =
                database
                    .occurrenceDao()
                    .getForMedication(
                        plan.medicationId,
                    )

            assertTrue(
                allOccurrences.any {
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
    fun stopMedication_preservesReportedFutureOccurrence() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
                        listOf(
                            12 * 60,
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
                )

            val occurrences =
                database
                    .occurrenceDao()
                    .getForVersion(
                        plan.scheduleVersionId,
                    )

            val unreportedFuture =
                occurrences.single {
                    it.minuteOfDay ==
                            12 * 60
                }

            val reportedFuture =
                occurrences.single {
                    it.minuteOfDay ==
                            14 * 60
                }

            RoomCaregiverReportService(
                database = database,
                clock = clock,
            ).setReport(
                occurrenceId = reportedFuture.id,
                newState = CaregiverReportState.GIVEN,
            )

            clock.currentInstant =
                Instant.parse(
                    "2026-06-24T07:00:00Z",
                )

            val occurrenceCountBefore =
                database
                    .occurrenceDao()
                    .count()

            val outcome =
                service.stopMedication(
                    plan.medicationId,
                )

            assertEquals(
                StopMedicationOutcome.Stopped,
                outcome,
            )

            assertEquals(
                OccurrenceLifecycle
                    .CANCELLED
                    .name,
                database
                    .occurrenceDao()
                    .getById(
                        unreportedFuture.id,
                    )
                    ?.lifecycle,
            )

            assertEquals(
                OccurrenceCancellationReason
                    .MEDICATION_STOPPED
                    .name,
                database
                    .occurrenceDao()
                    .getById(
                        unreportedFuture.id,
                    )
                    ?.cancellationReason,
            )

            assertEquals(
                OccurrenceLifecycle
                    .ACTIVE
                    .name,
                database
                    .occurrenceDao()
                    .getById(
                        reportedFuture.id,
                    )
                    ?.lifecycle,
            )

            assertEquals(
                CaregiverReportState
                    .GIVEN
                    .name,
                database
                    .reportingDao()
                    .getReport(
                        reportedFuture.id,
                    )
                    ?.state,
            )

            assertEquals(
                occurrenceCountBefore,
                database
                    .occurrenceDao()
                    .count(),
            )
        }

    private suspend fun createPlan(
        minutesOfDay: List<Int>,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): ContractPlan {
        val recipientOutcome =
            service.createRecipient(
                CreateRecipientCommand(
                    displayName =
                        "فرد نمونه",
                ),
            )

        val recipientId =
            when (recipientOutcome) {
                is CreateRecipientOutcome.Created ->
                    recipientOutcome.recipientId

                is CreateRecipientOutcome.AlreadyExists ->
                    recipientOutcome.recipientId

                is CreateRecipientOutcome.Invalid ->
                    error(
                        "Recipient creation failed.",
                    )
            }

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
                            DayOfWeek.entries
                                .toSet(),
                        minutesOfDay =
                            minutesOfDay,
                        startDate =
                            startDate,
                        endDate =
                            endDate,
                        zoneId =
                            "Asia/Tehran",
                    ),
                )

        val created =
            outcome as
                    CreateMedicationScheduleOutcome
                    .Created

        return ContractPlan(
            medicationId =
                created.medicationId,
            scheduleSeriesId =
                created.scheduleSeriesId,
            scheduleVersionId =
                created.scheduleVersionId,
        )
    }
}

private data class ContractPlan(
    val medicationId: String,
    val scheduleSeriesId: String,
    val scheduleVersionId: String,
)
