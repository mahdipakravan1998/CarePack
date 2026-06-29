package ir.carepack.reminder.navigation

import android.content.Intent
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.reminder.notification.ReminderNotificationContract

class NotificationNavigationValidator(
    private val database: CarePackDatabase,
) {

    suspend fun validatedOccurrenceId(
        intent: Intent?,
    ): String? {
        val occurrenceId =
            ReminderNotificationContract
                .extractOccurrenceId(
                    intent = intent,
                )
                ?: return null

        val occurrence =
            database
                .occurrenceDao()
                .getById(
                    occurrenceId =
                        occurrenceId,
                )
                ?: return null

        return occurrence.id
    }
}
