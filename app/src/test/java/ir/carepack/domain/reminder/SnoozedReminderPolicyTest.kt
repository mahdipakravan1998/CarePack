package ir.carepack.domain.reminder

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozedReminderPolicyTest {

    @Test
    fun validRequest_createsDelayedReminderWithoutReportState() {
        val now =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        val decision =
            SnoozedReminderPolicy.create(
                occurrenceId =
                    OCCURRENCE_ID,
                now = now,
                remindAt =
                    now.plusSeconds(
                        600,
                    ),
                occurrenceAlreadyReported =
                    false,
                occurrenceActive =
                    true,
            )

        assertTrue(
            decision is
                    SnoozedReminderDecision
                    .Schedule,
        )

        val reminder =
            (
                    decision as
                            SnoozedReminderDecision
                            .Schedule
                    ).snoozedReminder

        assertEquals(
            OCCURRENCE_ID,
            reminder.occurrenceId,
        )

        assertEquals(
            now.plusSeconds(
                600,
            ),
            reminder.remindAt,
        )

        assertEquals(
            now,
            reminder.createdAt,
        )

        assertEquals(
            AlarmKey
                .forDelayedOccurrence(
                    OCCURRENCE_ID,
                )
                .stableToken,
            reminder
                .alarmKey
                .stableToken,
        )
    }

    @Test
    fun reportedOccurrence_isRejected() {
        val now =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        val decision =
            SnoozedReminderPolicy.create(
                occurrenceId =
                    OCCURRENCE_ID,
                now = now,
                remindAt =
                    now.plusSeconds(
                        600,
                    ),
                occurrenceAlreadyReported =
                    true,
                occurrenceActive =
                    true,
            )

        assertEquals(
            SnoozedReminderDecision.Ignore(
                reason =
                    RemindLaterIgnoreReason
                        .OCCURRENCE_NOT_ELIGIBLE,
            ),
            decision,
        )
    }

    @Test
    fun inactiveOccurrence_isRejected() {
        val now =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        val decision =
            SnoozedReminderPolicy.create(
                occurrenceId =
                    OCCURRENCE_ID,
                now = now,
                remindAt =
                    now.plusSeconds(
                        600,
                    ),
                occurrenceAlreadyReported =
                    false,
                occurrenceActive =
                    false,
            )

        assertEquals(
            SnoozedReminderDecision.Ignore(
                reason =
                    RemindLaterIgnoreReason
                        .OCCURRENCE_NOT_ELIGIBLE,
            ),
            decision,
        )
    }

    @Test
    fun nonFutureReminderTime_isRejected() {
        val now =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        val decision =
            SnoozedReminderPolicy.create(
                occurrenceId =
                    OCCURRENCE_ID,
                now = now,
                remindAt = now,
                occurrenceAlreadyReported =
                    false,
                occurrenceActive =
                    true,
            )

        assertEquals(
            SnoozedReminderDecision.Ignore(
                reason =
                    RemindLaterIgnoreReason
                        .INVALID_DELAY,
            ),
            decision,
        )
    }

    private companion object {
        const val OCCURRENCE_ID =
            "occurrence-1"
    }
}
