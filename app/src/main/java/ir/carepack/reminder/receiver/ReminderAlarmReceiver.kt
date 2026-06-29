package ir.carepack.reminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.carepack.CarePackApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderAlarmReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (
            intent.action !=
            ACTION_FIRE_REMINDER
        ) {
            return
        }

        val occurrenceId =
            intent
                .getStringExtra(
                    EXTRA_OCCURRENCE_ID,
                )
                ?.trim()
                ?.takeIf(
                    String::isNotEmpty,
                )
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
                    .reminderCoordinator
                    .handleAlarmFired(
                        occurrenceId =
                            occurrenceId,
                    )
            } catch (_: Exception) {
                Unit
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE_REMINDER =
            "ir.carepack.action.FIRE_REMINDER"

        const val EXTRA_OCCURRENCE_ID =
            "ir.carepack.extra.ALARM_OCCURRENCE_ID"

        const val EXTRA_SCHEDULE_SERIES_ID =
            "ir.carepack.extra.ALARM_SCHEDULE_SERIES_ID"
    }
}
