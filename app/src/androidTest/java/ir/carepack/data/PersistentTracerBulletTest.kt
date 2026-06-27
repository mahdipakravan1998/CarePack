package ir.carepack.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.CareRecipientEntity
import ir.carepack.data.local.MedicationEntity
import ir.carepack.data.local.ScheduleSeriesEntity
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.careplan.RoomCarePlanService
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.today.RoomTodayQueryService
import ir.carepack.testing.SequenceIdSource
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistentTracerBulletTest {

    private lateinit var context: Context
    private lateinit var database: CarePackDatabase

    private val databaseName =
        "carepack-pr1-integration.db"

    private val fixedClock: Clock =
        Clock.fixed(
            Instant.parse(
                "2026-06-24T08:00:00Z",
            ),
            ZoneOffset.UTC,
        )

    private val anchorDate =
        LocalDate.parse(
            "2026-06-24",
        )

    @Before
    fun setUp() {
        context =
            ApplicationProvider
                .getApplicationContext()

        context.deleteDatabase(databaseName)

        database = openDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }

        context.deleteDatabase(databaseName)
    }

    @Test
    fun singletonRecipient_isEnforcedByServiceAndDatabase() =
        runBlocking {
            val idSource =
                SequenceIdSource(
                    "recipient-1",
                )

            val generator =
                RoomOccurrenceGenerator(
                    database = database,
                    idSource =
                        SequenceIdSource(
                            "unused-occurrence",
                        ),
                    candidateResolver =
                        OccurrenceCandidateResolver(),
                )

            val service =
                RoomCarePlanService(
                    database = database,
                    occurrenceGenerator =
                        generator,
                    clock = fixedClock,
                    idSource = idSource,
                )

            val first =
                service.createRecipient(
                    CreateRecipientCommand(
                        displayName = "آزمایش",
                    ),
                )

            assertTrue(
                first is
                        CreateRecipientOutcome.Created,
            )

            val second =
                service.createRecipient(
                    CreateRecipientCommand(
                        displayName = "دوم",
                    ),
                )

            assertTrue(
                second is
                        CreateRecipientOutcome
                        .AlreadyExists,
            )

            var databaseRejectedSecond =
                false

            try {
                database
                    .careRecipientDao()
                    .insert(
                        CareRecipientEntity(
                            id = "recipient-2",
                            singletonSlot = 1,
                            displayName = "دوم",
                            createdAtEpochMillis =
                                fixedClock
                                    .instant()
                                    .toEpochMilli(),
                        ),
                    )
            } catch (_: Exception) {
                databaseRejectedSecond =
                    true
            }

            assertTrue(
                databaseRejectedSecond,
            )

            assertEquals(
                1,
                database
                    .careRecipientDao()
                    .count(),
            )
        }

    @Test
    fun carePlanGraph_rollsBackCompletelyWhenLaterInsertFails() =
        runBlocking {
            val setupGenerator =
                RoomOccurrenceGenerator(
                    database = database,
                    idSource =
                        SequenceIdSource(
                            "unused-occurrence",
                        ),
                    candidateResolver =
                        OccurrenceCandidateResolver(),
                )

            val recipientService =
                RoomCarePlanService(
                    database = database,
                    occurrenceGenerator =
                        setupGenerator,
                    clock = fixedClock,
                    idSource =
                        SequenceIdSource(
                            "recipient-1",
                        ),
                )

            val recipientOutcome =
                recipientService
                    .createRecipient(
                        CreateRecipientCommand(
                            displayName =
                                "آزمایش",
                        ),
                    )

            val recipientId =
                (
                        recipientOutcome as
                                CreateRecipientOutcome
                                .Created
                        ).recipientId

            val now =
                fixedClock
                    .instant()
                    .toEpochMilli()

            database
                .medicationDao()
                .insert(
                    MedicationEntity(
                        id =
                            "existing-medication",
                        careRecipientId =
                            recipientId,
                        name =
                            "داروی موجود",
                        instruction =
                            "دستور موجود",
                        createdAtEpochMillis =
                            now,
                        stoppedAtEpochMillis =
                            null,
                        archivedAtEpochMillis =
                            null,
                    ),
                )

            database
                .scheduleDao()
                .insertSeries(
                    ScheduleSeriesEntity(
                        id =
                            "collision-series",
                        medicationId =
                            "existing-medication",
                        createdAtEpochMillis =
                            now,
                        stoppedAtEpochMillis =
                            null,
                    ),
                )

            val medicationCountBefore =
                database
                    .medicationDao()
                    .count()

            val seriesCountBefore =
                database
                    .scheduleDao()
                    .countSeries()

            val versionCountBefore =
                database
                    .scheduleDao()
                    .countVersions()

            val timeCountBefore =
                database
                    .scheduleDao()
                    .countTimes()

            val occurrenceCountBefore =
                database
                    .occurrenceDao()
                    .count()

            val collisionIds =
                SequenceIdSource(
                    "new-medication",
                    "collision-series",
                    "new-version",
                    "new-occurrence",
                )

            val collisionGenerator =
                RoomOccurrenceGenerator(
                    database = database,
                    idSource =
                        collisionIds,
                    candidateResolver =
                        OccurrenceCandidateResolver(),
                )

            val collisionService =
                RoomCarePlanService(
                    database = database,
                    occurrenceGenerator =
                        collisionGenerator,
                    clock = fixedClock,
                    idSource =
                        collisionIds,
                )

            var failed = false

            try {
                collisionService
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId =
                                recipientId,
                            medicationName =
                                "داروی جدید",
                            instruction =
                                "دستور جدید",
                            weekdays =
                                setOf(
                                    DayOfWeek
                                        .WEDNESDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    12 * 60,
                                ),
                            startDate =
                                anchorDate,
                            endDate =
                                anchorDate,
                            zoneId =
                                "Asia/Tehran",
                        ),
                    )
            } catch (_: Exception) {
                failed = true
            }

            assertTrue(failed)

            assertEquals(
                medicationCountBefore,
                database
                    .medicationDao()
                    .count(),
            )

            assertEquals(
                seriesCountBefore,
                database
                    .scheduleDao()
                    .countSeries(),
            )

            assertEquals(
                versionCountBefore,
                database
                    .scheduleDao()
                    .countVersions(),
            )

            assertEquals(
                timeCountBefore,
                database
                    .scheduleDao()
                    .countTimes(),
            )

            assertEquals(
                occurrenceCountBefore,
                database
                    .occurrenceDao()
                    .count(),
            )
        }

    @Test
    fun earlierSameDaySchedule_doesNotCreateArtificialOccurrence() =
        runBlocking {
            val ids =
                SequenceIdSource(
                    "recipient-1",
                    "medication-1",
                    "series-1",
                    "version-1",
                )

            val generator =
                RoomOccurrenceGenerator(
                    database = database,
                    idSource = ids,
                    candidateResolver =
                        OccurrenceCandidateResolver(),
                )

            val carePlanService =
                RoomCarePlanService(
                    database = database,
                    occurrenceGenerator =
                        generator,
                    clock = fixedClock,
                    idSource = ids,
                )

            val recipientOutcome =
                carePlanService
                    .createRecipient(
                        CreateRecipientCommand(
                            displayName =
                                "آزمایش",
                        ),
                    )

            val recipientId =
                (
                        recipientOutcome as
                                CreateRecipientOutcome
                                .Created
                        ).recipientId

            val planOutcome =
                carePlanService
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId =
                                recipientId,
                            medicationName =
                                "داروی نمونه",
                            instruction =
                                "دستور نمونه",
                            weekdays =
                                setOf(
                                    DayOfWeek
                                        .WEDNESDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    10 * 60,
                                ),
                            startDate =
                                anchorDate,
                            endDate =
                                anchorDate,
                            zoneId =
                                "Asia/Tehran",
                        ),
                    )

            assertTrue(
                planOutcome is
                        CreateMedicationScheduleOutcome
                        .Created,
            )

            val createdPlan =
                planOutcome as
                        CreateMedicationScheduleOutcome
                        .Created

            assertTrue(
                createdPlan
                    .occurrenceIds
                    .isEmpty(),
            )

            assertEquals(
                0,
                database
                    .occurrenceDao()
                    .count(),
            )
        }

    @Test
    fun tracerBullet_isIdempotentAndSurvivesDatabaseReopen() =
        runBlocking {
            val ids =
                SequenceIdSource(
                    "recipient-1",
                    "medication-1",
                    "series-1",
                    "version-1",
                    "occurrence-1",
                    "occurrence-unused",
                )

            val generator =
                RoomOccurrenceGenerator(
                    database = database,
                    idSource = ids,
                    candidateResolver =
                        OccurrenceCandidateResolver(),
                )

            val carePlanService =
                RoomCarePlanService(
                    database = database,
                    occurrenceGenerator =
                        generator,
                    clock = fixedClock,
                    idSource = ids,
                )

            val recipientOutcome =
                carePlanService
                    .createRecipient(
                        CreateRecipientCommand(
                            displayName =
                                "آزمایش",
                        ),
                    )

            val recipientId =
                (
                        recipientOutcome as
                                CreateRecipientOutcome
                                .Created
                        ).recipientId

            val planOutcome =
                carePlanService
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId =
                                recipientId,
                            medicationName =
                                "داروی نمونه",
                            instruction =
                                "دستور غیرحساس نمونه",
                            weekdays =
                                setOf(
                                    DayOfWeek
                                        .WEDNESDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    12 * 60,
                                ),
                            startDate =
                                anchorDate,
                            endDate =
                                anchorDate,
                            zoneId =
                                "Asia/Tehran",
                        ),
                    )

            assertTrue(
                planOutcome is
                        CreateMedicationScheduleOutcome
                        .Created,
            )

            val createdPlan =
                planOutcome as
                        CreateMedicationScheduleOutcome
                        .Created

            assertEquals(
                listOf(
                    "occurrence-1",
                ),
                createdPlan.occurrenceIds,
            )

            val persistedOccurrence =
                checkNotNull(
                    database
                        .occurrenceDao()
                        .getById(
                            "occurrence-1",
                        ),
                )

            assertEquals(
                "داروی نمونه",
                persistedOccurrence
                    .medicationNameSnapshot,
            )

            assertEquals(
                "دستور غیرحساس نمونه",
                persistedOccurrence
                    .medicationInstructionSnapshot,
            )

            val secondGeneration =
                generator
                    .guaranteeWindowForSchedule(
                        scheduleVersionId =
                            createdPlan
                                .scheduleVersionId,
                        anchorDate =
                            anchorDate,
                        now =
                            fixedClock.instant(),
                    )

            assertEquals(
                "occurrence-1",
                secondGeneration
                    .occurrences
                    .single()
                    .occurrenceId,
            )

            assertEquals(
                1,
                database
                    .occurrenceDao()
                    .count(),
            )

            val todayQuery =
                RoomTodayQueryService(
                    database,
                )

            val beforeReport =
                todayQuery
                    .observeToday(
                        localDate = anchorDate,
                        now = flowOf(fixedClock.instant()),
                    )
                    .first()
                    .items

            assertEquals(
                1,
                beforeReport.size,
            )

            assertEquals(
                "occurrence-1",
                beforeReport
                    .single()
                    .occurrenceId,
            )

            assertEquals(
                "داروی نمونه",
                beforeReport
                    .single()
                    .medicationName,
            )

            assertEquals(
                "دستور غیرحساس نمونه",
                beforeReport
                    .single()
                    .medicationInstruction,
            )

            val reportService =
                RoomCaregiverReportService(
                    database = database,
                    clock = fixedClock,
                )

            val firstReport =
                reportService.setReport(
                    occurrenceId = "occurrence-1",
                    newState = CaregiverReportState.GIVEN,
                )

            val secondReport =
                reportService.setReport(
                    occurrenceId = "occurrence-1",
                    newState = CaregiverReportState.GIVEN,
                )

            assertTrue(
                firstReport is
                        SetReportOutcome.Changed,
            )

            assertTrue(
                secondReport is
                        SetReportOutcome.Unchanged,
            )

            assertEquals(
                1,
                database
                    .reportingDao()
                    .countReports(),
            )

            val afterReport =
                todayQuery
                    .observeToday(
                        localDate = anchorDate,
                        now = flowOf(fixedClock.instant()),
                    )
                    .first()
                    .items

            assertEquals(
                CaregiverReportState.GIVEN,
                afterReport
                    .single()
                    .reportState,
            )

            database.close()

            database = openDatabase()

            val reopenedGenerator =
                RoomOccurrenceGenerator(
                    database = database,
                    idSource =
                        SequenceIdSource(
                            "different-proposed-id",
                        ),
                    candidateResolver =
                        OccurrenceCandidateResolver(),
                )

            reopenedGenerator
                .guaranteeWindowForAll(
                    anchorDate =
                        anchorDate,
                    now =
                        fixedClock.instant(),
                )

            val reopenedToday =
                RoomTodayQueryService(
                    database,
                )
                    .observeToday(
                        localDate = anchorDate,
                        now = flowOf(fixedClock.instant()),
                    )
                    .first()
                    .items

            assertEquals(
                1,
                reopenedToday.size,
            )

            assertEquals(
                "occurrence-1",
                reopenedToday
                    .single()
                    .occurrenceId,
            )

            assertEquals(
                "داروی نمونه",
                reopenedToday
                    .single()
                    .medicationName,
            )

            assertEquals(
                "دستور غیرحساس نمونه",
                reopenedToday
                    .single()
                    .medicationInstruction,
            )

            assertEquals(
                CaregiverReportState.GIVEN,
                reopenedToday
                    .single()
                    .reportState,
            )

            assertEquals(
                1,
                database
                    .occurrenceDao()
                    .count(),
            )

            assertEquals(
                1,
                database
                    .reportingDao()
                    .countReports(),
            )
        }

    @Test
    fun concurrentGivenRequests_createOneReportWithoutFailure() =
        runBlocking {
            val ids =
                SequenceIdSource(
                    "recipient-1",
                    "medication-1",
                    "series-1",
                    "version-1",
                    "occurrence-1",
                )

            val generator =
                RoomOccurrenceGenerator(
                    database = database,
                    idSource = ids,
                    candidateResolver =
                        OccurrenceCandidateResolver(),
                )

            val carePlanService =
                RoomCarePlanService(
                    database = database,
                    occurrenceGenerator =
                        generator,
                    clock = fixedClock,
                    idSource = ids,
                )

            val recipientOutcome =
                carePlanService
                    .createRecipient(
                        CreateRecipientCommand(
                            displayName =
                                "آزمایش",
                        ),
                    )

            val recipientId =
                (
                        recipientOutcome as
                                CreateRecipientOutcome
                                .Created
                        ).recipientId

            val planOutcome =
                carePlanService
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId =
                                recipientId,
                            medicationName =
                                "داروی نمونه",
                            instruction =
                                "دستور نمونه",
                            weekdays =
                                setOf(
                                    DayOfWeek
                                        .WEDNESDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    12 * 60,
                                ),
                            startDate =
                                anchorDate,
                            endDate =
                                anchorDate,
                            zoneId =
                                "Asia/Tehran",
                        ),
                    )

            val createdPlan =
                planOutcome as
                        CreateMedicationScheduleOutcome
                        .Created

            val occurrenceId =
                createdPlan
                    .occurrenceIds
                    .single()

            val reportService =
                RoomCaregiverReportService(
                    database = database,
                    clock = fixedClock,
                )

            val outcomes =
                coroutineScope {
                    List(
                        CONCURRENT_REQUEST_COUNT,
                    ) {
                        async(
                            Dispatchers.Default,
                        ) {
                            reportService.setReport(
                                occurrenceId = occurrenceId,
                                newState = CaregiverReportState.GIVEN,
                            )
                        }
                    }.awaitAll()
                }

            assertEquals(
                1,
                outcomes.count {
                    it is
                            SetReportOutcome
                            .Changed
                },
            )

            assertEquals(
                CONCURRENT_REQUEST_COUNT -
                        1,
                outcomes.count {
                    it is
                            SetReportOutcome
                            .Unchanged
                },
            )

            assertTrue(
                outcomes.all { outcome ->
                    outcome is
                            SetReportOutcome
                            .Changed ||
                            outcome is
                                    SetReportOutcome
                                    .Unchanged
                },
            )

            assertEquals(
                1,
                database
                    .reportingDao()
                    .countReports(),
            )

            val persistedReport =
                database
                    .reportingDao()
                    .getReport(
                        occurrenceId,
                    )

            assertEquals(
                CaregiverReportState
                    .GIVEN
                    .name,
                persistedReport?.state,
            )
        }

    private fun openDatabase():
            CarePackDatabase {
        return Room
            .databaseBuilder(
                context,
                CarePackDatabase::class.java,
                databaseName,
            )
            .build()
    }

    private companion object {
        const val CONCURRENT_REQUEST_COUNT =
            8
    }
}
