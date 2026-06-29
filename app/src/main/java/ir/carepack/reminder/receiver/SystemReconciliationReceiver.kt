package ir.carepack.reminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.carepack.CarePackApplication
import ir.carepack.domain.reminder.ReconciliationReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SystemReconciliationReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val reason =
            intent.action
                .toReconciliationReason()
                ?: return

        val application =
            context.applicationContext as?
                    CarePackApplication
                ?: return

        val pendingResult =
            goAsync()

        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO,
        ).launch {
            try {
                application
                    .container
                    .appReconciler
                    .reconcile(
                        reason = reason,
                    )
            } catch (_: Exception) {
                Unit
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun String?
            .toReconciliationReason():
            ReconciliationReason? {
        return when (this) {
            Intent.ACTION_BOOT_COMPLETED -> {
                ReconciliationReason
                    .BOOT_COMPLETED
            }

            Intent.ACTION_TIME_CHANGED -> {
                ReconciliationReason
                    .TIME_CHANGED
            }

            Intent.ACTION_TIMEZONE_CHANGED -> {
                ReconciliationReason
                    .TIMEZONE_CHANGED
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ReconciliationReason
                    .PACKAGE_REPLACED
            }

            ACTION_EXACT_ALARM_PERMISSION_CHANGED -> {
                ReconciliationReason
                    .EXACT_ALARM_CAPABILITY_CHANGED
            }

            else -> {
                null
            }
        }
    }

    private companion object {
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
