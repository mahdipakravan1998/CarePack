package ir.carepack.reminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.carepack.CarePackApplication
import ir.carepack.app.AppReconciliationOutcome
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderDiagnosticEventType
import ir.carepack.domain.reminder.recordReminderDiagnostic

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_FIRE_REMINDER) {
            return
        }

        val application =
            context.applicationContext as?
                CarePackApplication ?: return

        when (intent.getStringExtra(EXTRA_ALARM_TYPE)) {
            ALARM_TYPE_TEST ->
                handleTestAlarm(application)
            ALARM_TYPE_OCCURRENCE,
            null,
                -> handleOccurrenceAlarm(application, intent)
        }
    }

    private fun handleOccurrenceAlarm(
        application: CarePackApplication,
        intent: Intent,
    ) {
        val occurrenceId =
            intent.getStringExtra(EXTRA_OCCURRENCE_ID)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return

        application.container.reminderDiagnosticSink
            .recordReminderDiagnostic(
                type =
                    ReminderDiagnosticEventType.RECEIVER_FIRED,
                clock = application.container.clock,
                occurrenceId = occurrenceId,
            )

        ReceiverExecutionBoundary().launch(
            receiver = this,
            operation = {
                val maintenance =
                    application.container.appReconciler
                        .reconcile(
                            ReconciliationReason.ALARM_FIRED,
                        )

                when (maintenance) {
                    is AppReconciliationOutcome.Completed -> {
                        application.container.reminderCoordinator
                            .handleAlarmFired(occurrenceId)
                        application.container
                            .systemReconciliationRetryScheduler
                            .markSuccessful()
                    }

                    is AppReconciliationOutcome.Failed -> {
                        application.container.reminderPreferenceStore
                            .markFailure(
                                failure = maintenance.failure,
                                failedAtEpochMillis =
                                    application.container.clock
                                        .instant()
                                        .toEpochMilli(),
                            )
                        application.container
                            .systemReconciliationRetryScheduler
                            .scheduleNextRetry()
                    }
                }
            },
            onFailure = { failure ->
                application.container.reminderPreferenceStore
                    .markFailure(
                        failure = failure,
                        failedAtEpochMillis =
                            application.container.clock
                                .instant()
                                .toEpochMilli(),
                    )
                application.container
                    .systemReconciliationRetryScheduler
                    .scheduleNextRetry()
            },
        )
    }

    private fun handleTestAlarm(
        application: CarePackApplication,
    ) {
        ReceiverExecutionBoundary().launch(
            receiver = this,
            operation = {
                application.container.reminderTestCoordinator
                    .handleTestAlarmFired()
            },
        )
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
        const val ALARM_TYPE_OCCURRENCE = "occurrence"
        const val ALARM_TYPE_TEST = "test"
    }
}
