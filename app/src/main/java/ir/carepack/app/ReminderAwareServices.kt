package ir.carepack.app

import ir.carepack.domain.careplan.ArchiveMedicationOutcome
import ir.carepack.domain.careplan.CarePlanOverview
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.careplan.MedicationEditorSnapshot
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
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class ReminderAwareCarePlanService(
    private val delegate: CarePlanService,
    private val reminderCoordinator:
    ReminderCoordinator,
) : CarePlanService {

    override suspend fun createRecipient(
        command: CreateRecipientCommand,
    ): CreateRecipientOutcome {
        return delegate.createRecipient(
            command = command,
        )
    }

    override suspend fun updateRecipientName(
        command: UpdateRecipientNameCommand,
    ): UpdateRecipientNameOutcome {
        return delegate.updateRecipientName(
            command = command,
        )
    }

    override suspend fun createMedicationAndSchedule(
        command: CreateMedicationScheduleCommand,
    ): CreateMedicationScheduleOutcome {
        val outcome =
            delegate
                .createMedicationAndSchedule(
                    command = command,
                )

        if (
            outcome is
                    CreateMedicationScheduleOutcome
                    .Created
        ) {
            reconcileAfterCommit(
                reason =
                    ReconciliationReason
                        .CARE_PLAN_CHANGED,
            )
        }

        return outcome
    }

    override suspend fun updateMedicationText(
        command: UpdateMedicationTextCommand,
    ): UpdateMedicationTextOutcome {
        val outcome =
            delegate.updateMedicationText(
                command = command,
            )

        if (
            outcome ==
            UpdateMedicationTextOutcome
                .Updated
        ) {
            reconcileAfterCommit(
                reason =
                    ReconciliationReason
                        .CARE_PLAN_CHANGED,
            )
        }

        return outcome
    }

    override suspend fun updateSchedule(
        command: UpdateScheduleCommand,
    ): UpdateScheduleOutcome {
        val outcome =
            delegate.updateSchedule(
                command = command,
            )

        if (
            outcome ==
            UpdateScheduleOutcome
                .Updated
        ) {
            reconcileAfterCommit(
                reason =
                    ReconciliationReason
                        .CARE_PLAN_CHANGED,
            )
        }

        return outcome
    }

    override suspend fun stopMedication(
        medicationId: String,
    ): StopMedicationOutcome {
        val outcome =
            delegate.stopMedication(
                medicationId =
                    medicationId,
            )

        if (
            outcome ==
            StopMedicationOutcome.Stopped
        ) {
            reconcileAfterCommit(
                reason =
                    ReconciliationReason
                        .CARE_PLAN_CHANGED,
            )
        }

        return outcome
    }

    override suspend fun archiveMedication(
        medicationId: String,
    ): ArchiveMedicationOutcome {
        val outcome =
            delegate.archiveMedication(
                medicationId =
                    medicationId,
            )

        if (
            outcome ==
            ArchiveMedicationOutcome.Archived
        ) {
            reconcileAfterCommit(
                reason =
                    ReconciliationReason
                        .CARE_PLAN_CHANGED,
            )
        }

        return outcome
    }

    override suspend fun getSetupProgress():
            SetupProgress {
        return delegate.getSetupProgress()
    }

    override fun observeCarePlan():
            Flow<CarePlanOverview?> {
        return delegate.observeCarePlan()
    }

    override suspend fun getMedicationEditor(
        medicationId: String,
    ): MedicationEditorSnapshot? {
        return delegate.getMedicationEditor(
            medicationId =
                medicationId,
        )
    }

    private suspend fun reconcileAfterCommit(
        reason: ReconciliationReason,
    ) {
        try {
            reminderCoordinator.reconcile(
                reason = reason,
            )
        } catch (
            cancellation:
            CancellationException,
        ) {
            throw cancellation
        } catch (_: Exception) {
            Unit
        }
    }
}

class ReminderAwareCaregiverReportService(
    private val delegate:
    CaregiverReportService,
    private val reminderCoordinator:
    ReminderCoordinator,
) : CaregiverReportService {

    override suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        val outcome =
            delegate.setReport(
                occurrenceId =
                    occurrenceId,
                newState =
                    newState,
            )

        if (
            outcome is
                    SetReportOutcome.Changed
        ) {
            reconcileAfterCommit()
        }

        return outcome
    }

    override suspend fun restorePrevious(
        change: ReportChange,
    ): UndoReportOutcome {
        val outcome =
            delegate.restorePrevious(
                change = change,
            )

        if (
            outcome is
                    UndoReportOutcome.Restored
        ) {
            reconcileAfterCommit()
        }

        return outcome
    }

    private suspend fun reconcileAfterCommit() {
        try {
            reminderCoordinator.reconcile(
                reason =
                    ReconciliationReason
                        .REPORT_CHANGED,
            )
        } catch (
            cancellation:
            CancellationException,
        ) {
            throw cancellation
        } catch (_: Exception) {
            Unit
        }
    }
}
