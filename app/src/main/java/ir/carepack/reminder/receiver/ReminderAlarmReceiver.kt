package ir.carepack.reminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.carepack.CarePackApplication
import ir.carepack.domain.reminder.ReminderDiagnosticEventType
import ir.carepack.domain.reminder.recordReminderDiagnostic
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

        val application =
            context.applicationContext as?
                    CarePackApplication
                ?: return

        when (
            intent.getStringExtra(
                EXTRA_ALARM_TYPE,
            )
        ) {
            ALARM_TYPE_TEST -> {
                handleTestAlarm(
                    application = application,
                )
            }

            ALARM_TYPE_OCCURRENCE,
            null,
                -> {
                handleOccurrenceAlarm(
                    application = application,
                    intent = intent,
                )
            }
        }
    }

    private fun handleOccurrenceAlarm(
        application: CarePackApplication,
        intent: Intent,
    ) {
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

        application
            .container
            .reminderDiagnosticSink
            .recordReminderDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .RECEIVER_FIRED,
                clock =
                    application
                        .container
                        .clock,
                occurrenceId =
                    occurrenceId,
            )

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

    private fun handleTestAlarm(
        application: CarePackApplication,
    ) {
        val pendingResult =
            goAsync()

        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO,
        ).launch {
            try {
                application
                    .container
                    .reminderTestCoordinator
                    .handleTestAlarmFired()
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

        const val EXTRA_ALARM_TYPE =
            "ir.carepack.extra.ALARM_TYPE"

        const val EXTRA_OCCURRENCE_ID =
            "ir.carepack.extra.ALARM_OCCURRENCE_ID"

        const val EXTRA_SCHEDULE_SERIES_ID =
            "ir.carepack.extra.ALARM_SCHEDULE_SERIES_ID"

        const val ALARM_TYPE_OCCURRENCE =
            "occurrence"

        const val ALARM_TYPE_TEST =
            "test"
    }
}
