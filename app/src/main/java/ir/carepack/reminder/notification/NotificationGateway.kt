package ir.carepack.reminder.notification

import ir.carepack.domain.reminder.ReminderNotification

interface NotificationGateway {

    fun post(
        notification: ReminderNotification,
    )

    fun cancel(
        occurrenceId: String,
    )

    fun cancelAll()
}
