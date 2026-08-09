package ir.carepack.app

import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.error.AppFailureKind
import ir.carepack.core.error.AppOperationStage
import ir.carepack.core.error.SafeAppFailure
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.careplan.AddScheduleCommand
import ir.carepack.domain.careplan.AddScheduleOutcome
import ir.carepack.domain.careplan.ArchiveMedicationOutcome
import ir.carepack.domain.careplan.CarePlanOverview
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.careplan.MedicationEditorSnapshot
import ir.carepack.domain.careplan.ScheduleEditorSnapshot
import ir.carepack.domain.careplan.SetupProgress
import ir.carepack.domain.careplan.StopMedicationOutcome
import ir.carepack.domain.careplan.UpdateMedicationTextCommand
import ir.carepack.domain.careplan.UpdateMedicationTextOutcome
import ir.carepack.domain.careplan.UpdateRecipientNameCommand
import ir.carepack.domain.careplan.UpdateRecipientNameOutcome
import ir.carepack.domain.careplan.UpdateScheduleCommand
import ir.carepack.domain.careplan.UpdateScheduleOutcome
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.occurrence.GenerationSummary
import ir.carepack.domain.occurrence.OccurrenceGenerator
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.RemindLaterOutcome
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderHealth
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.DataDeletionResult
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionPreview
import ir.carepack.settings.deletion.MedicationDeletionPreviewResult
import ir.carepack.settings.deletion.MedicationDeletionRecoveryResult
import ir.carepack.settings.deletion.MedicationDeletionResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPartialFailureContractTest {

    private val clock =
        Clock.fixed(
            Instant.parse("2026-08-08T12:00:00Z"),
            ZoneOffset.UTC,
        )

    @Test
    fun appReconciler_partialFailureBecomesRecoverableFailureAndNeverHealthy() =
        runTest {
            val preferences = RecordingReminderPreferenceStore()
            val reconciler =
                AppReconciler(
                    medicationDeletionCoordinator = NoPendingMedicationDeletion(),
                    dataDeletionCoordinator = NoPendingDataDeletion(),
                    occurrenceGenerator = NoOpOccurrenceGenerator(),
                    reminderCoordinator = PartialFailureReminderCoordinator(),
                    reminderPreferenceStore = preferences,
                    clock = clock,
                    zoneProvider = ZoneProvider { ZoneOffset.UTC },
                    operationGate = AppOperationGate(),
                )

            val outcome =
                reconciler.reconcile(
                    ReconciliationReason.APPLICATION_FOREGROUND,
                )

            assertTrue(outcome is AppReconciliationOutcome.Failed)
            val failed = outcome as AppReconciliationOutcome.Failed
            assertEquals(AppOperationStage.RECONCILING_REMINDERS, failed.stage)
            assertEquals(AppFailureKind.PLATFORM, failed.failure.kind)
            assertTrue(failed.failure.retryable)
            assertEquals(0, preferences.healthyCount)
            assertEquals(1, preferences.failures.size)
        }

    @Test
    fun carePlanPostCommit_partialFailureKeepsCommitAndMarksPendingRetry() =
        runTest {
            val preferences = RecordingReminderPreferenceStore()
            val delegate = RecordingCarePlanService()
            val service =
                ReminderAwareCarePlanService(
                    delegate = delegate,
                    reminderCoordinator = PartialFailureReminderCoordinator(),
                    reminderPreferenceStore = preferences,
                    operationGate = AppOperationGate(),
                    clock = clock,
                )

            val outcome =
                service.createMedicationAndSchedule(
                    CreateMedicationScheduleCommand(
                        recipientId = "recipient",
                        medicationName = "name",
                        instruction = "instruction",
                        weekdays = emptySet(),
                        minutesOfDay = emptyList(),
                        startDate = null,
                        endDate = null,
                        zoneId = "UTC",
                    ),
                )

            assertTrue(outcome is CreateMedicationScheduleOutcome.Created)
            assertEquals(1, delegate.committedMutations)
            assertEquals(0, preferences.healthyCount)
            assertEquals(1, preferences.failures.size)
        }

    @Test
    fun reportPostCommit_partialFailureKeepsReportAndMarksPendingRetry() =
        runTest {
            val preferences = RecordingReminderPreferenceStore()
            val delegate = RecordingReportService()
            val service =
                ReminderAwareCaregiverReportService(
                    delegate = delegate,
                    reminderCoordinator = PartialFailureReminderCoordinator(),
                    reminderPreferenceStore = preferences,
                    operationGate = AppOperationGate(),
                    clock = clock,
                )

            val outcome =
                service.setReport(
                    occurrenceId = "occurrence",
                    newState = CaregiverReportState.GIVEN,
                )

            assertTrue(outcome is SetReportOutcome.Changed)
            assertEquals(1, delegate.committedReports)
            assertEquals(0, preferences.healthyCount)
            assertEquals(1, preferences.failures.size)
        }

    private class PartialFailureReminderCoordinator : ReminderCoordinator {
        override suspend fun currentStatus(): ReminderStatus = status()

        override suspend fun reconcile(
            reason: ReconciliationReason,
        ): ReminderReconciliationResult =
            ReminderReconciliationResult.PartialFailure(
                reason = reason,
                status = status(),
                scheduledCount = 1,
                cancelledCount = 0,
                failedOperationCount = 1,
            )

        override suspend fun handleAlarmFired(
            occurrenceId: String,
        ): AlarmFireResult =
            error("Not used by this contract test")

        override suspend fun remindLater(
            occurrenceId: String,
            delayMinutes: Long,
        ): RemindLaterOutcome = RemindLaterOutcome.SchedulingFailed

        override suspend fun cancelAllOwnedReminderState() = Unit

        private fun status() =
            ReminderStatus(
                remindersEnabled = true,
                notificationPermissionGranted = true,
                hasActiveSchedule = true,
                exactAlarmCapabilityGranted = true,
                availability = ReminderAvailability.EXACT,
            )
    }

    private class RecordingReminderPreferenceStore : ReminderPreferenceStore {
        private val mutableState = MutableStateFlow(ReminderPreferenceState())
        override val state: Flow<ReminderPreferenceState> = mutableState
        var healthyCount = 0
        val failures = mutableListOf<SafeAppFailure>()

        override suspend fun setRemindersEnabled(enabled: Boolean) = Unit

        override suspend fun observeDeviceZone(
            zoneId: String,
        ): TimezoneObservation = TimezoneObservation.Unchanged

        override suspend fun dismissTimezoneWarning() = Unit

        override suspend fun markHealthy() {
            healthyCount += 1
            mutableState.value = mutableState.value.copy(health = ReminderHealth.Healthy)
        }

        override suspend fun markFailure(
            failure: SafeAppFailure,
            failedAtEpochMillis: Long,
        ) {
            failures += failure
            mutableState.value =
                mutableState.value.copy(
                    health =
                        ReminderHealth.PendingRetry(
                            failure = failure,
                            failedAtEpochMillis = failedAtEpochMillis,
                        ),
                )
        }
    }

    private class NoPendingMedicationDeletion : MedicationDeletionCoordinator {
        override suspend fun loadPreview(
            medicationId: String,
        ): MedicationDeletionPreviewResult = MedicationDeletionPreviewResult.NotFound

        override suspend fun deleteMedication(
            expectedPreview: MedicationDeletionPreview,
        ): MedicationDeletionResult = MedicationDeletionResult.AlreadyDeleted

        override suspend fun resumeIncompleteDeletionIfNeeded():
            MedicationDeletionRecoveryResult =
            MedicationDeletionRecoveryResult.NoDeletionPending
    }

    private class NoPendingDataDeletion : DataDeletionCoordinator {
        override suspend fun deleteEverything(): DataDeletionResult = DataDeletionResult.Completed

        override suspend fun resumeIncompleteDeletionIfNeeded(): DataDeletionResult =
            DataDeletionResult.NoDeletionPending
    }

    private class NoOpOccurrenceGenerator : OccurrenceGenerator {
        override suspend fun guaranteeWindowForSchedule(
            scheduleVersionId: String,
            anchorDate: java.time.LocalDate,
            now: Instant,
        ): GenerationSummary = emptySummary()

        override suspend fun guaranteeWindowForAll(
            anchorDate: java.time.LocalDate,
            now: Instant,
        ): GenerationSummary = emptySummary()

        override suspend fun guaranteeMaintenanceWindowForAll(
            anchorDate: java.time.LocalDate,
            now: Instant,
        ): GenerationSummary = emptySummary()

        private fun emptySummary() =
            GenerationSummary(
                occurrences = emptyList(),
                skippedCandidateCount = 0,
            )
    }

    private class RecordingCarePlanService : CarePlanService {
        var committedMutations = 0

        override suspend fun createRecipient(
            command: CreateRecipientCommand,
        ): CreateRecipientOutcome = CreateRecipientOutcome.Created("recipient")

        override suspend fun updateRecipientName(
            command: UpdateRecipientNameCommand,
        ): UpdateRecipientNameOutcome = UpdateRecipientNameOutcome.Updated

        override suspend fun createMedicationAndSchedule(
            command: CreateMedicationScheduleCommand,
        ): CreateMedicationScheduleOutcome {
            committedMutations += 1
            return CreateMedicationScheduleOutcome.Created(
                medicationId = "medication",
                scheduleSeriesId = "series",
                scheduleVersionId = "version",
                occurrenceIds = listOf("occurrence"),
            )
        }

        override suspend fun addSchedule(
            command: AddScheduleCommand,
        ): AddScheduleOutcome = error("Not used")

        override suspend fun updateMedicationText(
            command: UpdateMedicationTextCommand,
        ): UpdateMedicationTextOutcome = error("Not used")

        override suspend fun updateSchedule(
            command: UpdateScheduleCommand,
        ): UpdateScheduleOutcome = error("Not used")

        override suspend fun stopMedication(
            medicationId: String,
        ): StopMedicationOutcome = error("Not used")

        override suspend fun archiveMedication(
            medicationId: String,
        ): ArchiveMedicationOutcome = error("Not used")

        override suspend fun getSetupProgress(): SetupProgress = SetupProgress.Empty

        override fun observeCarePlan(): Flow<CarePlanOverview?> = emptyFlow()

        override suspend fun getMedicationEditor(
            medicationId: String,
        ): MedicationEditorSnapshot? = null

        override suspend fun getScheduleEditor(
            scheduleSeriesId: String,
        ): ScheduleEditorSnapshot? = null
    }

    private class RecordingReportService : CaregiverReportService {
        var committedReports = 0

        override suspend fun setReport(
            occurrenceId: String,
            newState: CaregiverReportState,
        ): SetReportOutcome {
            committedReports += 1
            return SetReportOutcome.Changed(
                ReportChange(
                    occurrenceId = occurrenceId,
                    previousState = null,
                    newState = newState,
                    changedAtEpochMillis = 1L,
                ),
            )
        }

        override suspend fun restorePrevious(
            change: ReportChange,
        ): UndoReportOutcome = error("Not used")
    }
}
