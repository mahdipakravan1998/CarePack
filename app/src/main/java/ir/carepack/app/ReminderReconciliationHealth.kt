package ir.carepack.app

import ir.carepack.core.error.SafeAppFailure
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.recoverableFailureOrNull

internal suspend fun ReminderPreferenceStore.recordReconciliationHealth(
    result: ReminderReconciliationResult,
    failedAtEpochMillis: () -> Long,
): SafeAppFailure? {
    val failure = result.recoverableFailureOrNull()
    if (failure == null) {
        markHealthy()
    } else {
        markFailure(
            failure = failure,
            failedAtEpochMillis = failedAtEpochMillis(),
        )
    }
    return failure
}
