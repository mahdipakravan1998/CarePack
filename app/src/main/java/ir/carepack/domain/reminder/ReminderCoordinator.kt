package ir.carepack.domain.reminder

interface ReminderCoordinator {

    suspend fun currentStatus(): ReminderStatus

    suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult

    suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult

    suspend fun remindLater(
        occurrenceId: String,
        delayMinutes: Long = DEFAULT_REMIND_LATER_MINUTES,
    ): RemindLaterOutcome = RemindLaterOutcome.SchedulingFailed

    suspend fun cancelReminderDelay(
        occurrenceId: String,
    ) {
        Unit
    }

    suspend fun cancelAllOwnedReminderState()

    companion object {
        const val DEFAULT_REMIND_LATER_MINUTES = 10L
    }
}
