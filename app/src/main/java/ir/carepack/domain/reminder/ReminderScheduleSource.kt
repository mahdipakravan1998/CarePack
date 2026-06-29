package ir.carepack.domain.reminder

import java.time.Instant

interface ReminderScheduleSource {

    suspend fun getAllScheduleSeriesIds():
            Set<String>

    suspend fun hasActiveSchedule():
            Boolean

    suspend fun getNextEligibleTargets(
        now: Instant,
    ): List<ReminderTarget>

    suspend fun getEligibleOccurrence(
        occurrenceId: String,
    ): ReminderTarget?
}
