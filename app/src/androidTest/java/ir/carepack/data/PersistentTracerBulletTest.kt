package ir.carepack.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.core.id.IdSource
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
import ir.carepack.domain.report.RecordGivenOutcome
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.today.RoomTodayQueryService
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
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
                SequenceIdSource("recipient-1")

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
                    occurrenceGenerator = generator,
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
                        CreateRecipientOutcome.AlreadyExists,
            )

            var databaseRejectedSecond = false

            try {
                database.careRecipientDao().insert(
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
                databaseRejectedSecond = true
            }

            assertTrue(databaseRejectedSecond)
            assertEquals(
                1,
                database.careRecipientDao().count(),
            )
        }

    @Test
    fun carePlanGraph_rollsBackWhenLaterInsertFails() =
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
                recipientService.createRecipient(
                    CreateRecipientCommand(
                        displayName = "آزمایش",
                    ),
                )

            val recipientId =
                (
                        recipientOutcome as
                                CreateRecipientOutcome.Created
                        ).recipientId

            val now =
                fixedClock
                    .instant()
                    .toEpochMilli()

            database.medicationDao().insert(
                MedicationEntity(
                    id = "existing-medication",
                    careRecipientId = recipientId,
                    name = "داروی موجود",
                    instruction = "دستور موجود",
                    createdAtEpochMillis = now,
                ),
            )

            database.scheduleDao().insertSeries(
                ScheduleSeriesEntity(
                    id = "collision-series",
                    medicationId =
                        "existing-medication",
                    createdAtEpochMillis = now,
                    stoppedAtEpochMillis = null,
                ),
            )

            val medicationCountBefore =
                database.medicationDao().count()

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
                    idSource = collisionIds,
                    candidateResolver =
                        OccurrenceCandidateResolver(),
                )

            val collisionService =
                RoomCarePlanService(
                    database = database,
                    occurrenceGenerator =
                        collisionGenerator,
                    clock = fixedClock,
                    idSource = collisionIds,
                )

            var failed = false

            try {
                collisionService
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId = recipientId,
                            medicationName =
                                "داروی جدید",
                            instruction =
                                "دستور جدید",
                            weekday =
                                DayOfWeek.WEDNESDAY,
                            localTime =
                                LocalTime.of(12, 0),
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
                database.medicationDao().count(),
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
                    occurrenceGenerator = generator,
                    clock = fixedClock,
                    idSource = ids,
                )

            val recipientOutcome =
                carePlanService.createRecipient(
                    CreateRecipientCommand(
                        displayName = "آزمایش",
                    ),
                )

            val recipientId =
                (
                        recipientOutcome as
                                CreateRecipientOutcome.Created
                        ).recipientId

            val planOutcome =
                carePlanService
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId = recipientId,
                            medicationName =
                                "داروی نمونه",
                            instruction =
                                "دستور غیرحساس نمونه",
                            weekday =
                                DayOfWeek.WEDNESDAY,
                            localTime =
                                LocalTime.of(12, 0),
                            zoneId =
                                "Asia/Tehran",
                        ),
                    )

            assertTrue(
                planOutcome is
                        CreateMedicationScheduleOutcome.Created,
            )

            val createdPlan =
                planOutcome as
                        CreateMedicationScheduleOutcome.Created

            assertEquals(
                listOf("occurrence-1"),
                createdPlan.occurrenceIds,
            )

            val anchorDate =
                LocalDate.parse("2026-06-24")

            val secondGeneration =
                generator.guaranteeForSchedule(
                    scheduleVersionId =
                        createdPlan.scheduleVersionId,
                    anchorDate = anchorDate,
                    now = fixedClock.instant(),
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
                database.occurrenceDao().count(),
            )

            val todayQuery =
                RoomTodayQueryService(database)

            val beforeReport =
                todayQuery
                    .observeToday(anchorDate)
                    .first()

            assertEquals(1, beforeReport.size)
            assertEquals(
                "occurrence-1",
                beforeReport.single().occurrenceId,
            )

            assertEquals(
                "داروی نمونه",
                beforeReport
                    .single()
                    .medicationName,
            )

            val reportService =
                RoomCaregiverReportService(
                    database = database,
                    clock = fixedClock,
                )

            val firstReport =
                reportService.recordGiven(
                    "occurrence-1",
                )

            val secondReport =
                reportService.recordGiven(
                    "occurrence-1",
                )

            assertTrue(
                firstReport is
                        RecordGivenOutcome.Recorded,
            )

            assertTrue(
                secondReport is
                        RecordGivenOutcome.Unchanged,
            )

            assertEquals(
                1,
                database
                    .caregiverReportDao()
                    .count(),
            )

            val afterReport =
                todayQuery
                    .observeToday(anchorDate)
                    .first()

            assertEquals(
                CaregiverReportState.GIVEN,
                afterReport.single().reportState,
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
                .guaranteeForEffectiveSchedules(
                    anchorDate = anchorDate,
                    now = fixedClock.instant(),
                )

            val reopenedToday =
                RoomTodayQueryService(database)
                    .observeToday(anchorDate)
                    .first()

            assertEquals(1, reopenedToday.size)

            assertEquals(
                "occurrence-1",
                reopenedToday
                    .single()
                    .occurrenceId,
            )

            assertEquals(
                CaregiverReportState.GIVEN,
                reopenedToday
                    .single()
                    .reportState,
            )

            assertEquals(
                1,
                database.occurrenceDao().count(),
            )

            assertEquals(
                1,
                database
                    .caregiverReportDao()
                    .count(),
            )
        }

    private fun openDatabase():
            CarePackDatabase {
        return Room.databaseBuilder(
            context,
            CarePackDatabase::class.java,
            databaseName,
        ).build()
    }
}

private class SequenceIdSource(
    vararg ids: String,
) : IdSource {

    private val remainingIds =
        ArrayDeque(ids.toList())

    override fun nextId(): String {
        check(remainingIds.isNotEmpty()) {
            "No test ID remains."
        }

        return remainingIds.removeFirst()
    }
}