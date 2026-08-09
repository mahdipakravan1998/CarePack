package ir.carepack.reminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.carepack.CarePackApplication
import ir.carepack.app.AppReconciliationOutcome
import ir.carepack.domain.reminder.ReconciliationReason

internal fun dispatchSystemReconciliationOutcome(
    outcome: AppReconciliationOutcome,
    onSuccessful: () -> Unit,
    onRetry: () -> Unit,
) {
    when (outcome) {
        is AppReconciliationOutcome.Completed -> onSuccessful()
        is AppReconciliationOutcome.Failed -> onRetry()
    }
}

class SystemReconciliationReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val reason =
            intent.action.toReconciliationReason()
                ?: return

        val application =
            context.applicationContext as?
                CarePackApplication ?: return

        val retryScheduler =
            application.container
                .systemReconciliationRetryScheduler

        ReceiverExecutionBoundary().launch(
            receiver = this,
            operation = {
                val outcome =
                    application.container.appReconciler
                        .reconcile(reason)

                dispatchSystemReconciliationOutcome(
                    outcome = outcome,
                    onSuccessful =
                        retryScheduler::markSuccessful,
                    onRetry = {
                        retryScheduler.scheduleNextRetry()
                    },
                )
            },
            onFailure = {
                retryScheduler.scheduleNextRetry()
            },
        )
    }

    private fun String?.toReconciliationReason():
        ReconciliationReason? =
        when (this) {
            Intent.ACTION_BOOT_COMPLETED ->
                ReconciliationReason.BOOT_COMPLETED
            Intent.ACTION_TIME_CHANGED ->
                ReconciliationReason.TIME_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED ->
                ReconciliationReason.TIMEZONE_CHANGED
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                ReconciliationReason.PACKAGE_REPLACED
            ACTION_EXACT_ALARM_PERMISSION_CHANGED ->
                ReconciliationReason
                    .EXACT_ALARM_CAPABILITY_CHANGED
            ACTION_RETRY_RECONCILIATION ->
                ReconciliationReason.MANUAL_RETRY
            else -> null
        }

    companion object {
        const val ACTION_RETRY_RECONCILIATION =
            "ir.carepack.action.RETRY_RECONCILIATION"

        private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
