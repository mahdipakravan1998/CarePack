package ir.carepack.domain.reminder

interface ReminderCoordinator {

    suspend fun currentStatus():
            ReminderStatus

    suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult

    suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult

    suspend fun cancelAllOwnedReminderState()
}
