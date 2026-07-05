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

    val alarmKey: AlarmKey
        get() =
            AlarmKey.forDelayedOccurrence(
                occurrenceId =
                    occurrenceId,
            )
}

enum class RemindLaterIgnoreReason {
    INVALID_DELAY,
    OCCURRENCE_NOT_ELIGIBLE,
}

sealed interface RemindLaterOutcome {

    data class Scheduled(
        val snoozedReminder: SnoozedReminder,
    ) : RemindLaterOutcome

    data class Ignored(
        val reason: RemindLaterIgnoreReason,
    ) : RemindLaterOutcome

    data object SchedulingFailed :
        RemindLaterOutcome
}

sealed interface SnoozedReminderDecision {

    data class Schedule(
        val snoozedReminder: SnoozedReminder,
    ) : SnoozedReminderDecision

    data class Ignore(
        val reason: RemindLaterIgnoreReason,
    ) : SnoozedReminderDecision
}

object SnoozedReminderPolicy {

    fun create(
        occurrenceId: String,
        now: Instant,
        remindAt: Instant,
        occurrenceAlreadyReported: Boolean,
        occurrenceActive: Boolean,
    ): SnoozedReminderDecision {
        if (!remindAt.isAfter(now)) {
            return SnoozedReminderDecision.Ignore(
                reason =
                    RemindLaterIgnoreReason
                        .INVALID_DELAY,
            )
        }

        if (
            occurrenceAlreadyReported ||
            !occurrenceActive
        ) {
            return SnoozedReminderDecision.Ignore(
                reason =
                    RemindLaterIgnoreReason
                        .OCCURRENCE_NOT_ELIGIBLE,
            )
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
