package ir.carepack.reminder.diagnostic

import android.util.Log
import ir.carepack.domain.reminder.ReminderDiagnosticEvent
import ir.carepack.domain.reminder.ReminderDiagnosticSink

class LogcatReminderDiagnosticSink(
    private val enabled: Boolean,
) : ReminderDiagnosticSink {

    override fun record(
        event: ReminderDiagnosticEvent,
    ) {
        if (!enabled) {
            return
        }

        val message = buildMessage(
                event = event,
            )

        Log.d(
            TAG,
            message,
        )

        appendDebugEvent(
            message = message,
        )
    }

    private fun buildMessage(
        event: ReminderDiagnosticEvent,
    ): String = buildString {
            append("type=")
            append(event.type.name)

            append(" at=")
            append(event.occurredAtEpochMillis)

            event.occurrenceToken?.let { token ->
                append(" occurrence=")
                append(token)
            }

            event.alarmKeyToken?.let { token ->
                append(" alarm=")
                append(token)
            }

            event.availability?.let { availability ->
                append(" availability=")
                append(availability.name)
            }

            event.deliveryMode?.let { deliveryMode ->
                append(" delivery=")
                append(deliveryMode.name)
            }

            event.outcome?.let { outcome ->
                append(" outcome=")
                append(outcome)
            }
        }

    companion object {
        private const val TAG = "CarePackReminder"

        private const val MAX_DEBUG_EVENT_COUNT = 200

        private val lock = Any()

        private val debugEvents = mutableListOf<String>()

        fun clearDebugEvents() {
            synchronized(lock) {
                debugEvents.clear()
            }
        }

        fun readDebugEvents(): List<String> {
            return synchronized(lock) {
                debugEvents.toList()
            }
        }

        private fun appendDebugEvent(
            message: String,
        ) {
            synchronized(lock) {
                debugEvents += message

                while (
                    debugEvents.size >
                    MAX_DEBUG_EVENT_COUNT) {
                    debugEvents.removeAt(
                        0,
                    )
                }
            }
        }
    }
}
