package ir.carepack.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.CarePackApplication
import ir.carepack.data.preferences.DataStoreDataDeletionMarkerStore
import ir.carepack.data.preferences.DataStoreMedicationDeletionMarkerStore
import ir.carepack.data.preferences.carePackDataStore
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.settings.deletion.DataDeletionMarker
import ir.carepack.settings.deletion.DataDeletionMarkerReadResult
import ir.carepack.settings.deletion.DataDeletionMarkerStage
import ir.carepack.settings.deletion.MedicationDeletionMarker
import ir.carepack.settings.deletion.MedicationDeletionMarkerReadResult
import ir.carepack.settings.deletion.MedicationDeletionMarkerStage
import ir.carepack.settings.deletion.RoomMedicationDeletionDataSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryMarkerOrchestrationIntegrationTest {

    private lateinit var context: Context
    private lateinit var application: CarePackApplication

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        application = context.applicationContext as CarePackApplication
        resetState()
    }

    @After
    fun tearDown() = runBlocking {
        resetState()
    }

    @Test
    fun medicationDeletionMarker_bootReason_recoversBeforeMaintenance() =
        runBlocking {
            val target = createPlan("داروی هدف")
            val preserved = createPlan("داروی باقی‌مانده")
            val graph =
                checkNotNull(
                    RoomMedicationDeletionDataSource(
                        application.container.database,
                    ).loadGraph(target.medicationId),
                )

            DataStoreMedicationDeletionMarkerStore(context)
                .save(
                    MedicationDeletionMarker.create(
                        expectedPreview = graph.preview,
                        scheduleSeriesIds = graph.scheduleSeriesIds.toSet(),
                        occurrenceIds = graph.occurrenceIds.toSet(),
                        stage =
                            MedicationDeletionMarkerStage
                                .PLATFORM_CLEANUP_PENDING,
                        startedAtEpochMillis =
                            Instant.parse("2026-06-24T08:00:00Z")
                                .toEpochMilli(),
                    ),
                )

            val outcome =
                application.container.appReconciler
                    .reconcile(ReconciliationReason.BOOT_COMPLETED)

            assertTrue(outcome is AppReconciliationOutcome.Completed)
            assertNull(
                application.container.database.medicationDao()
                    .getById(target.medicationId),
            )
            assertTrue(
                application.container.database.occurrenceDao()
                    .getForMedication(target.medicationId)
                    .isEmpty(),
            )
            assertTrue(
                application.container.database.medicationDao()
                    .getById(preserved.medicationId) != null,
            )
            assertEquals(
                MedicationDeletionMarkerReadResult.Absent,
                DataStoreMedicationDeletionMarkerStore(context)
                    .state
                    .first(),
            )
        }

    @Test
    fun deleteAllMarker_timezoneReason_recoversBeforeMaintenance() =
        runBlocking {
            createPlan("داروی حذف کامل")

            DataStoreDataDeletionMarkerStore(context)
                .save(
                    DataDeletionMarker.create(
                        operationId = "delete-all-timezone-recovery",
                        stage = DataDeletionMarkerStage.DOMAIN_DATA_PENDING,
                        startedAtEpochMillis =
                            Instant.parse("2026-06-24T08:00:00Z")
                                .toEpochMilli(),
                    ),
                )

            val outcome =
                application.container.appReconciler
                    .reconcile(ReconciliationReason.TIMEZONE_CHANGED)

            assertTrue(outcome is AppReconciliationOutcome.Completed)
            assertEquals(
                0,
                application.container.database.careRecipientDao().count(),
            )
            assertEquals(
                DataDeletionMarkerReadResult.Absent,
                DataStoreDataDeletionMarkerStore(context)
                    .state
                    .first(),
            )
        }

    @Test
    fun recreatedMarkerStore_resumesPendingMedicationDeletion() =
        runBlocking {
            val target = createPlan("داروی بازیابی پس از بازسازی")
            val graph =
                checkNotNull(
                    RoomMedicationDeletionDataSource(
                        application.container.database,
                    ).loadGraph(target.medicationId),
                )
            val firstStore = DataStoreMedicationDeletionMarkerStore(context)
            firstStore.save(
                MedicationDeletionMarker.create(
                    expectedPreview = graph.preview,
                    scheduleSeriesIds = graph.scheduleSeriesIds.toSet(),
                    occurrenceIds = graph.occurrenceIds.toSet(),
                    stage = MedicationDeletionMarkerStage.DATABASE_DELETE_PENDING,
                    startedAtEpochMillis = 1L,
                ),
            )

            val recreatedStore = DataStoreMedicationDeletionMarkerStore(context)
            assertTrue(
                recreatedStore.state.first() is
                    MedicationDeletionMarkerReadResult.Valid,
            )

            val outcome =
                application.container.appReconciler
                    .reconcile(ReconciliationReason.PACKAGE_REPLACED)

            assertTrue(outcome is AppReconciliationOutcome.Completed)
            assertNull(
                application.container.database.medicationDao()
                    .getById(target.medicationId),
            )
            assertEquals(
                MedicationDeletionMarkerReadResult.Absent,
                recreatedStore.state.first(),
            )
        }

    private suspend fun createPlan(
        medicationName: String,
    ): CreateMedicationScheduleOutcome.Created {
        val recipient =
            application.container.carePlanService
                .createRecipient(
                    CreateRecipientCommand(
                        displayName = "فرد آزمون",
                    ),
                )
        val recipientId =
            when (recipient) {
                is CreateRecipientOutcome.Created -> recipient.recipientId
                is CreateRecipientOutcome.AlreadyExists -> recipient.recipientId
                is CreateRecipientOutcome.Invalid ->
                    error("Recipient fixture must be valid.")
            }

        return application.container.carePlanService
            .createMedicationAndSchedule(
                CreateMedicationScheduleCommand(
                    recipientId = recipientId,
                    medicationName = medicationName,
                    instruction = "دستور آزمون",
                    weekdays = DayOfWeek.entries.toSet(),
                    minutesOfDay = listOf(12 * 60),
                    startDate = TODAY,
                    endDate = TODAY.plusDays(2),
                    zoneId = "UTC",
                ),
            ) as CreateMedicationScheduleOutcome.Created
    }

    private suspend fun resetState() {
        application.container.database.clearAllTables()
        context.carePackDataStore.edit { it.clear() }
        application.container.snoozedReminderStore.clear()
        application.container.notificationGateway.cancelAll()
        application.container.reminderCoordinator
            .cancelAllOwnedReminderState()
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.parse("2026-06-24")
    }
}
