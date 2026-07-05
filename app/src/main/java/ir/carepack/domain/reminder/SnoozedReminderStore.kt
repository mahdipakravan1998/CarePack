package ir.carepack.domain.reminder

import kotlinx.coroutines.flow.Flow

interface SnoozedReminderStore {

    val reminders: Flow<List<SnoozedReminder>>

    suspend fun upsert(
        reminder: SnoozedReminder,
    )

    suspend fun delete(
        occurrenceId: String,
    )

    suspend fun clear()
}
