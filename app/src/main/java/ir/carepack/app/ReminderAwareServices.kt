package ir.carepack.app

import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.error.AppOperationStage
import ir.carepack.core.error.rethrowIfCancellation
import ir.carepack.core.error.toSafeAppFailure
import ir.carepack.domain.careplan.AddScheduleCommand
import ir.carepack.domain.careplan.AddScheduleOutcome
import ir.carepack.domain.careplan.ArchiveMedicationOutcome
import ir.carepack.domain.careplan.ArchivedMedication
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
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import java.time.Clock
import kotlinx.coroutines.flow.Flow

class ReminderAwareCarePlanService(
    private val delegate: CarePlanService,
    private val reminderCoordinator: ReminderCoordinator,
    private val reminderPreferenceStore: ReminderPreferenceStore,
    private val operationGate: AppOperationGate,
    private val clock: Clock,
) : CarePlanService {

    override suspend fun createRecipient(
        command: CreateRecipientCommand,
    ): CreateRecipientOutcome = operationGate.withGate {
            delegate.createRecipient(command)
        }

    override suspend fun updateRecipientName(
        command: UpdateRecipientNameCommand,
    ): UpdateRecipientNameOutcome = operationGate.withGate {
            delegate.updateRecipientName(command)
        }

    override suspend fun createMedicationAndSchedule(
        command: CreateMedicationScheduleCommand,
    ): CreateMedicationScheduleOutcome = operationGate.withGate {
            val outcome = delegate.createMedicationAndSchedule(command)
            if (outcome is CreateMedicationScheduleOutcome.Created) {
                reconcileAfterCommit(
                    ReconciliationReason.CARE_PLAN_CHANGED,
                )
            }
            outcome
        }

    override suspend fun addSchedule(
        command: AddScheduleCommand,
    ): AddScheduleOutcome = operationGate.withGate {
            val outcome = delegate.addSchedule(command)
            if (outcome is AddScheduleOutcome.Created) {
                reconcileAfterCommit(
                    ReconciliationReason.CARE_PLAN_CHANGED,
                )
            }
            outcome
        }

    override suspend fun updateMedicationText(
        command: UpdateMedicationTextCommand,
    ): UpdateMedicationTextOutcome = operationGate.withGate {
            val outcome = delegate.updateMedicationText(command)
            if (outcome == UpdateMedicationTextOutcome.Updated) {
                reconcileAfterCommit(
                    ReconciliationReason.CARE_PLAN_CHANGED,
                )
            }
            outcome
        }

    override suspend fun updateSchedule(
        command: UpdateScheduleCommand,
    ): UpdateScheduleOutcome = operationGate.withGate {
            val outcome = delegate.updateSchedule(command)
            if (outcome == UpdateScheduleOutcome.Updated) {
                reconcileAfterCommit(
                    ReconciliationReason.CARE_PLAN_CHANGED,
                )
            }
            outcome
        }

    override suspend fun stopMedication(
        medicationId: String,
    ): StopMedicationOutcome = operationGate.withGate {
            val outcome = delegate.stopMedication(medicationId)
            if (outcome == StopMedicationOutcome.Stopped) {
                reconcileAfterCommit(
                    ReconciliationReason.CARE_PLAN_CHANGED,
                )
            }
            outcome
        }

    override suspend fun archiveMedication(
        medicationId: String,
    ): ArchiveMedicationOutcome = operationGate.withGate {
            val outcome = delegate.archiveMedication(medicationId)
            if (outcome == ArchiveMedicationOutcome.Archived) {
                reconcileAfterCommit(
                    ReconciliationReason.CARE_PLAN_CHANGED,
                )
            }
            outcome
        }

    override suspend fun getSetupProgress(): SetupProgress = delegate.getSetupProgress()

    override fun observeCarePlan(): Flow<CarePlanOverview?> = delegate.observeCarePlan()

    override fun observeArchivedMedications(): Flow<List<ArchivedMedication>> =
        delegate.observeArchivedMedications()

    override suspend fun getArchivedMedication(
        medicationId: String,
    ): ArchivedMedication? = delegate.getArchivedMedication(medicationId)

    override suspend fun getMedicationEditor(
        medicationId: String,
    ): MedicationEditorSnapshot? = delegate.getMedicationEditor(medicationId)

    override suspend fun getScheduleEditor(
        scheduleSeriesId: String,
    ): ScheduleEditorSnapshot? = delegate.getScheduleEditor(scheduleSeriesId)

    private suspend fun reconcileAfterCommit(
        reason: ReconciliationReason,
    ) {
        try {
            val result = reminderCoordinator.reconcile(reason)
            reminderPreferenceStore.recordReconciliationHealth(
                result = result,
                failedAtEpochMillis = { clock.instant().toEpochMilli() },
            )
        } catch (throwable: Throwable) {
            throwable.rethrowIfCancellation()
            reminderPreferenceStore.markFailure(
                failure = throwable.toSafeAppFailure(
                        AppOperationStage.RECONCILING_REMINDERS,
                    ),
                failedAtEpochMillis = clock.instant().toEpochMilli(),
            )
        }
    }
}

class ReminderAwareCaregiverReportService(
    private val delegate: CaregiverReportService,
    private val reminderCoordinator: ReminderCoordinator,
    private val reminderPreferenceStore: ReminderPreferenceStore,
    private val operationGate: AppOperationGate,
    private val clock: Clock,
) : CaregiverReportService {

    override suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome = operationGate.withGate {
            val outcome = delegate.setReport(
                    occurrenceId = occurrenceId,
                    newState = newState,
                )

            if (outcome is SetReportOutcome.Changed) {
                runAfterCommit {
                    reminderCoordinator.cancelReminderDelay(
                        outcome.change.occurrenceId,
                    )
                }
                reconcileAfterCommit()
            }

            outcome
        }

    override suspend fun restorePrevious(
        change: ReportChange,
    ): UndoReportOutcome = operationGate.withGate {
            val outcome = delegate.restorePrevious(change)
            if (outcome is UndoReportOutcome.Restored) {
                reconcileAfterCommit()
            }
            outcome
        }

    private suspend fun reconcileAfterCommit() {
        try {
            val result = reminderCoordinator.reconcile(
                    ReconciliationReason.REPORT_CHANGED,
                )
            reminderPreferenceStore.recordReconciliationHealth(
                result = result,
                failedAtEpochMillis = { clock.instant().toEpochMilli() },
            )
        } catch (throwable: Throwable) {
            throwable.rethrowIfCancellation()
            reminderPreferenceStore.markFailure(
                failure = throwable.toSafeAppFailure(
                        AppOperationStage.RECONCILING_REMINDERS,
                    ),
                failedAtEpochMillis = clock.instant().toEpochMilli(),
            )
        }
    }

    private suspend fun runAfterCommit(
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
        } catch (throwable: Throwable) {
            throwable.rethrowIfCancellation()
            reminderPreferenceStore.markFailure(
                failure = throwable.toSafeAppFailure(
                        AppOperationStage.RECONCILING_REMINDERS,
                    ),
                failedAtEpochMillis = clock.instant().toEpochMilli(),
            )
        }
    }
}
