package ir.carepack.domain.reminder

import java.time.Instant

data class SnoozedReminder(
    val occurrenceId: String,
    val remindAt: Instant,
    val createdAt: Instant,
) {
    init {
        require(occurrenceId.isNotBlank())
        require(remindAt.isAfter(createdAt))
    }
}

sealed interface SnoozedReminderDecision {

    data class Schedule(
        val snoozedReminder: SnoozedReminder,
    ) : SnoozedReminderDecision

    data object Ignore :
        SnoozedReminderDecision
}

object SnoozedReminderPolicy {
    fun create(
        occurrenceId: String,
        now: Instant,
        remindAt: Instant,
        occurrenceAlreadyReported: Boolean,
        occurrenceActive: Boolean,
    ): SnoozedReminderDecision {
        if (
            occurrenceAlreadyReported ||
            !occurrenceActive ||
            !remindAt.isAfter(now)
        ) {
            return SnoozedReminderDecision.Ignore
        }

        return SnoozedReminderDecision.Schedule(
            SnoozedReminder(
                occurrenceId = occurrenceId,
                remindAt = remindAt,
                createdAt = now,
            ),
        )
    }
}
