package ir.carepack.reminder.receiver

import ir.carepack.app.AppReconciliationOutcome
import ir.carepack.core.error.AppFailureKind
import ir.carepack.core.error.AppOperationStage
import ir.carepack.core.error.SafeAppFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemReconciliationReceiverPolicyTest {

    @Test
    fun failedOutcomeSchedulesRetryAndNeverMarksSuccessful() {
        var successCount = 0
        var retryCount = 0

        dispatchSystemReconciliationOutcome(
            outcome =
                AppReconciliationOutcome.Failed(
                    stage =
                        AppOperationStage.RECONCILING_REMINDERS,
                    failure =
                        SafeAppFailure(
                            kind = AppFailureKind.PLATFORM,
                            stage =
                                AppOperationStage.RECONCILING_REMINDERS,
                            retryable = true,
                        ),
                ),
            onSuccessful = {
                successCount += 1
            },
            onRetry = {
                retryCount += 1
            },
        )

        assertEquals(0, successCount)
        assertEquals(1, retryCount)
    }
}
