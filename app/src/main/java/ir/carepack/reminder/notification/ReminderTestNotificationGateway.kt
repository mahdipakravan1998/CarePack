package ir.carepack.reminder.notification

import java.time.Instant

interface ReminderTestNotificationGateway {

    fun postTestReminder(
        scheduledAt: Instant,
    )

    fun cancelTestReminder()
}
