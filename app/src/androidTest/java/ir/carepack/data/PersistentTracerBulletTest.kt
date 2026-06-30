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
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.testing.IncrementingIdSource
import ir.carepack.testing.SequenceIdSource
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistentTracerBulletTest {

    private lateinit var context: Context
    private lateinit var database: CarePackDatabase

    private val databaseName =
        "carepack-persistent-tracer-bullet.db"

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

        context.deleteDatabase(
            databaseName,
        )

        database =
            openDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }

        context.deleteDatabase(
            databaseName,
        )
    }

    @Test
    fun singletonRecipient_isEnforcedByServiceAndDatabase() =
        runBlocking {
            val service =
                carePlanService(
                    database =
                        database,
                    idSource =
                        SequenceIdSource(
                            "recipient-1",
                        ),
                )

            val first =
                service.createRecipient(
                    CreateRecipientCommand(
                        displayName = "آزمایش",
                    ),
                )

            assertTrue(
                first is CreateRecipientOutcome.Created,
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

            var duplicateRejected =
                false

            try {
                val now =
                    fixedClock
                        .instant()
                        .toEpochMilli()

                database
                    .careRecipientDao()
                    .insert(
                        CareRecipientEntity(
                            id = "recipient-2",
                            singletonSlot = 1,
                            displayName = "دوم",
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                        ),
                    )
            } catch (_: Exception) {
                duplicateRejected = true
            }

            assertTrue(
                duplicateRejected,
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
            val recipientService =
                carePlanService(
                    database =
                        database,
                    idSource =
                        SequenceIdSource(
                            "recipient-1",
                        ),
                )

            val recipientOutcome =
                recipientService
                    .createRecipient(
                        CreateRecipientCommand(
                            displayName = "مادر",
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

            database
                .medicationDao()
                .insert(
                    MedicationEntity(
                        id = "existing-medication",
                        careRecipientId = recipientId,
                        name = "داروی موجود",
                        instructionText = "دستور موجود",
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        stoppedAtEpochMillis = null,
                        archivedAtEpochMillis = null,
                    ),
                )

            database
                .scheduleDao()
                .insertSeries(
                    ScheduleSeriesEntity(
                        id = "collision-series",
                        medicationId = "existing-medication",
                        createdAtEpochMillis = now,
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

            val collisionService =
                carePlanService(
                    database =
                        database,
                    idSource =
                        collisionIds,
                )

            var failed =
                false

            try {
                collisionService
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId = recipientId,
                            medicationName = "داروی جدید",
                            instruction = "دستور جدید",
                            weekdays =
                                setOf(
                                    DayOfWeek.WEDNESDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    12 * 60,
                                ),
                            startDate = anchorDate,
                            endDate = anchorDate,
                            zoneId = "UTC",
                        ),
                    )
            } catch (_: Exception) {
                failed = true
            }

            assertTrue(
                failed,
            )

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
    fun persistedCarePlanAndReport_surviveDatabaseReopen() =
        runBlocking {
            val service =
                carePlanService(
                    database =
                        database,
                    idSource =
                        IncrementingIdSource(
                            prefix =
                                "persisted-id",
                        ),
                )

            val recipientOutcome =
                service.createRecipient(
                    CreateRecipientCommand(
                        displayName = "مادر",
                    ),
                )

            val recipientId =
                (
                        recipientOutcome as
                                CreateRecipientOutcome.Created
                        ).recipientId

            val created =
                service
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId = recipientId,
                            medicationName = "داروی فشار",
                            instruction = "بعد از غذا",
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            minutesOfDay =
                                listOf(
                                    12 * 60,
                                ),
                            startDate = null,
                            endDate = null,
                            zoneId = "UTC",
                        ),
                    ) as CreateMedicationScheduleOutcome.Created

            val occurrence =
                database
                    .occurrenceDao()
                    .getForMedication(
                        created.medicationId,
                    )
                    .first()

            assertEquals(
                "UTC",
                occurrence.zoneIdSnapshot,
            )

            assertEquals(
                "داروی فشار",
                occurrence.medicationNameSnapshot,
            )

            assertEquals(
                "بعد از غذا",
                occurrence.instructionSnapshot,
            )

            val reportService =
                RoomCaregiverReportService(
                    database = database,
                    clock = fixedClock,
                )

            val reportOutcome =
                reportService.setReport(
                    occurrenceId =
                        occurrence.id,
                    newState =
                        CaregiverReportState.GIVEN,
                )

            assertTrue(
                reportOutcome is SetReportOutcome.Changed,
            )

            database.close()

            database =
                openDatabase()

            assertEquals(
                1,
                database
                    .careRecipientDao()
                    .count(),
            )

            assertEquals(
                1,
                database
                    .medicationDao()
                    .count(),
            )

            assertEquals(
                1,
                database
                    .reportingDao()
                    .countReports(),
            )

            val reloadedOccurrence =
                database
                    .occurrenceDao()
                    .getById(
                        occurrence.id,
                    )

            assertEquals(
                OccurrenceLifecycle.ACTIVE.name,
                reloadedOccurrence?.lifecycle,
            )

            assertEquals(
                CaregiverReportState.GIVEN.name,
                database
                    .reportingDao()
                    .getReport(
                        occurrence.id,
                    )
                    ?.state,
            )
        }

    @Test
    fun occurrenceGeneration_isIdempotentAndKeepsStableIds() =
        runBlocking {
            val idSource =
                IncrementingIdSource(
                    prefix = "stable-id",
                )

            val service =
                carePlanService(
                    database =
                        database,
                    idSource =
                        idSource,
                )

            val recipient =
                service
                    .createRecipient(
                        CreateRecipientCommand(
                            displayName = "مادر",
                        ),
                    ) as CreateRecipientOutcome.Created

            val created =
                service
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId =
                                recipient.recipientId,
                            medicationName =
                                "داروی پایدار",
                            instruction =
                                "هر روز",
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            minutesOfDay =
                                listOf(
                                    12 * 60,
                                ),
                            startDate = null,
                            endDate = null,
                            zoneId = "UTC",
                        ),
                    ) as CreateMedicationScheduleOutcome.Created

            val before =
                database
                    .occurrenceDao()
                    .getForVersion(
                        created.scheduleVersionId,
                    )
                    .map {
                        it.id
                    }
                    .sorted()

            val generator =
                RoomOccurrenceGenerator(
                    database = database,
                    idSource = idSource,
                    candidateResolver =
                        OccurrenceCandidateResolver(),
                )

            val repeated =
                generator
                    .guaranteeWindowForSchedule(
                        scheduleVersionId =
                            created.scheduleVersionId,
                        anchorDate = anchorDate,
                        now =
                            fixedClock.instant(),
                    )

            val after =
                database
                    .occurrenceDao()
                    .getForVersion(
                        created.scheduleVersionId,
                    )
                    .map {
                        it.id
                    }
                    .sorted()

            assertEquals(
                before,
                after,
            )

            assertEquals(
                0,
                repeated.insertedCount,
            )

            assertFalse(
                after.isEmpty(),
            )
        }

    private fun openDatabase():
            CarePackDatabase =
        Room
            .databaseBuilder(
                context,
                CarePackDatabase::class.java,
                databaseName,
            )
            .build()

    private fun carePlanService(
        database: CarePackDatabase,
        idSource: ir.carepack.core.id.IdSource,
    ): RoomCarePlanService {
        val generator =
            RoomOccurrenceGenerator(
                database = database,
                idSource = idSource,
                candidateResolver =
                    OccurrenceCandidateResolver(),
            )

        return RoomCarePlanService(
            database = database,
            occurrenceGenerator = generator,
            clock = fixedClock,
            idSource = idSource,
        )
    }
}
