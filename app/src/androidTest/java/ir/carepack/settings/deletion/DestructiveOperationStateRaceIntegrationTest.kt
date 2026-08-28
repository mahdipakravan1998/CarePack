package ir.carepack.settings.deletion

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.CarePackApplication
import ir.carepack.data.preferences.DataStoreDataDeletionMarkerStore
import ir.carepack.data.preferences.DataStoreMedicationDeletionMarkerStore
import ir.carepack.data.preferences.carePackDataStore
import ir.carepack.domain.careplan.AddScheduleCommand
import ir.carepack.domain.careplan.AddScheduleOutcome
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.careplan.UpdateMedicationTextCommand
import ir.carepack.domain.careplan.UpdateMedicationTextOutcome
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.report.SetReportOutcome
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
class DestructiveOperationStateRaceIntegrationTest {

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
    fun deleteAllAndReconcile_concurrentExecutionLeavesNoDomainOrMarkerResidue() =
        runBlocking {
            createPlan("داروی حذف کامل")

            runConcurrently(
                first = {
                    application.container.dataDeletionCoordinator
                        .deleteEverything()
                },
                second = {
                    application.container.appReconciler
                        .reconcile(ReconciliationReason.MANUAL_RETRY)
                },
            )

            assertDeleteAllStateIsClean()
        }

    @Test
    fun deleteAllAndMedicationDelete_concurrentExecutionLeavesNoDomainOrMarkerResidue() =
        runBlocking {
            val plan = createPlan("داروی حذف هم‌زمان")
            val preview =
                application.container.medicationDeletionCoordinator
                    .loadPreview(plan.medicationId)
            val available =
                preview as MedicationDeletionPreviewResult.Available

            runConcurrently(
                first = {
                    application.container.dataDeletionCoordinator
                        .deleteEverything()
                },
                second = {
                    application.container.medicationDeletionCoordinator
                        .deleteMedication(available.preview)
                },
            )

            assertDeleteAllStateIsClean()
        }

    @Test
    fun medicationDeleteAndCarePlanMutations_neverLeavePartialGraphOrMarker() =
        runBlocking {
            verifyMedicationRace("update") { plan, _ ->
                application.container.carePlanService
                    .updateMedicationText(
                        UpdateMedicationTextCommand(
                            medicationId = plan.medicationId,
                            medicationName = "نام جدید",
                            instruction = "دستور جدید",
                        ),
                    )
            }

            resetState()

            verifyMedicationRace("add") { plan, _ ->
                application.container.carePlanService
                    .addSchedule(
                        AddScheduleCommand(
                            medicationId = plan.medicationId,
                            weekdays = DayOfWeek.entries.toSet(),
                            minutesOfDay = listOf(18 * 60),
                            startDate = TODAY,
                            endDate = TODAY.plusDays(2),
                            zoneId = "UTC",
                        ),
                    )
            }

            resetState()

            verifyMedicationRace("stop") { plan, _ ->
                application.container.carePlanService
                    .stopMedication(plan.medicationId)
            }

            resetState()

            verifyArchiveRace()

            resetState()

            verifyMedicationRace("report") { _, occurrenceId ->
                application.container.caregiverReportService
                    .setReport(
                        occurrenceId = occurrenceId,
                        newState = CaregiverReportState.GIVEN,
                    )
            }

            resetState()

            verifyMedicationRace("snooze") { _, occurrenceId ->
                application.container.reminderCoordinator
                    .remindLater(
                        occurrenceId = occurrenceId,
                        delayMinutes = 10,
                    )
            }
        }


    private suspend fun verifyArchiveRace() {
        val plan = createPlan("داروی مسابقه archive")
        application.container.carePlanService
            .stopMedication(plan.medicationId)

        val preview =
            application.container.medicationDeletionCoordinator
                .loadPreview(plan.medicationId)
        val available =
            preview as MedicationDeletionPreviewResult.Available

        runConcurrently(
            first = {
                application.container.medicationDeletionCoordinator
                    .deleteMedication(available.preview)
            },
            second = {
                application.container.carePlanService
                    .archiveMedication(plan.medicationId)
            },
        )

        assertEquals(
            MedicationDeletionMarkerReadResult.Absent,
            DataStoreMedicationDeletionMarkerStore(context)
                .state
                .first(),
        )
    }

    private suspend fun verifyMedicationRace(
        label: String,
        mutation: suspend (CreateMedicationScheduleOutcome.Created, String) -> Any?,
    ) {
        val plan = createPlan("داروی مسابقه $label")
        val occurrenceId =
            application.container.database
                .occurrenceDao()
                .getForMedication(plan.medicationId)
                .first()
                .id
        val preview =
            application.container.medicationDeletionCoordinator
                .loadPreview(plan.medicationId)
        val available =
            preview as MedicationDeletionPreviewResult.Available

        runConcurrently(
            first = {
                application.container.medicationDeletionCoordinator
                    .deleteMedication(available.preview)
            },
            second = {
                mutation(plan, occurrenceId)
            },
        )

        val medication =
            application.container.database.medicationDao()
                .getById(plan.medicationId)
        val marker =
            DataStoreMedicationDeletionMarkerStore(context)
                .state
                .first()

        assertEquals(
            MedicationDeletionMarkerReadResult.Absent,
            marker,
        )

        if (medication == null) {
            assertTrue(
                application.container.database.occurrenceDao()
                    .getForMedication(plan.medicationId)
                    .isEmpty(),
            )
        } else {
            assertTrue(
                application.container.database.medicationDao()
                    .getDeletionScheduleSeriesIds(plan.medicationId)
                    .isNotEmpty(),
            )
        }
    }

    private suspend fun runConcurrently(
        first: suspend () -> Any?,
        second: suspend () -> Any?,
    ) = coroutineScope {
        val start = CompletableDeferred<Unit>()
        val firstJob = async {
            start.await()
            first()
        }
        val secondJob = async {
            start.await()
            second()
        }
        start.complete(Unit)
        firstJob.await()
        secondJob.await()
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

        val outcome =
            application.container.carePlanService
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
                )

        return outcome as CreateMedicationScheduleOutcome.Created
    }

    private suspend fun assertDeleteAllStateIsClean() {
        assertEquals(
            0,
            application.container.database.careRecipientDao().count(),
        )
        assertEquals(
            DataDeletionMarkerReadResult.Absent,
            DataStoreDataDeletionMarkerStore(context).state.first(),
        )
        assertEquals(
            MedicationDeletionMarkerReadResult.Absent,
            DataStoreMedicationDeletionMarkerStore(context).state.first(),
        )
        assertTrue(
            application.container.snoozedReminderStore
                .reminders
                .first()
                .isEmpty(),
        )
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
        val TODAY: LocalDate
            get() = LocalDate.now(ZoneOffset.UTC)
    }
}
