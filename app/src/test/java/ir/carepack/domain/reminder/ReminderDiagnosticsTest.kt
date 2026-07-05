package ir.carepack.domain.reminder

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReminderDiagnosticsTest {

    @Test
    fun identifierTokensAreStableAndDoNotExposeRawIdentifier() {
        val rawIdentifier =
            "occurrence-sensitive-12345"

        val firstToken =
            ReminderDiagnosticTokens
                .forIdentifier(
                    rawIdentifier,
                )

        val secondToken =
            ReminderDiagnosticTokens
                .forIdentifier(
                    rawIdentifier,
                )

        assertNotNull(
            firstToken,
        )

        assertEquals(
            firstToken,
            secondToken,
        )

        assertNotEquals(
            rawIdentifier,
            firstToken,
        )

        assertFalse(
            rawIdentifier.contains(
                checkNotNull(
                    firstToken,
                ),
            ),
        )
    }

    @Test
    fun recordHelperStoresOnlyNonSensitiveTokensAndSafeOutcome() {
        val sink =
            ReminderDiagnosticsRecordingSink()

        val rawOccurrenceId =
            "occurrence-with-raw-sensitive-id"

        sink.recordReminderDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .ALARM_REGISTERED,
            clock =
                Clock.fixed(
                    Instant.parse(
                        "2026-06-24T08:00:00Z",
                    ),
                    ZoneOffset.UTC,
                ),
            occurrenceId =
                rawOccurrenceId,
            alarmKey =
                AlarmKey.forScheduleSeries(
                    scheduleSeriesId =
                        "schedule-sensitive-id",
                ),
            availability =
                ReminderAvailability.EXACT,
            deliveryMode =
                ReminderDeliveryMode.EXACT,
            outcome =
                "registered\nwith-newline",
        )

        val event =
            sink.events.single()

        assertEquals(
            ReminderDiagnosticEventType
                .ALARM_REGISTERED,
            event.type,
        )

        assertEquals(
            Instant
                .parse(
                    "2026-06-24T08:00:00Z",
                )
                .toEpochMilli(),
            event.occurredAtEpochMillis,
        )

        assertEquals(
            ReminderAvailability.EXACT,
            event.availability,
        )

        assertEquals(
            ReminderDeliveryMode.EXACT,
            event.deliveryMode,
        )

        assertFalse(
            event.toString().contains(
                rawOccurrenceId,
            ),
        )

        assertFalse(
            event.toString().contains(
                "schedule-sensitive-id",
            ),
        )

        assertFalse(
            checkNotNull(
                event.outcome,
            ).contains('\n'),
        )
    }

    @Test
    fun diagnosticEventDoesNotNeedSensitiveMedicationRecipientInstructionOrReportText() {
        val sink =
            ReminderDiagnosticsRecordingSink()

        val medicationName =
            "داروی محرمانه"

        val instructionText =
            "دستور محرمانه"

        val recipientName =
            "نام فرد محرمانه"

        val reportText =
            "گزارش محرمانه"

        sink.recordReminderDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .NOTIFICATION_POST_ATTEMPTED,
            clock =
                Clock.fixed(
                    Instant.parse(
                        "2026-06-24T08:00:00Z",
                    ),
                    ZoneOffset.UTC,
                ),
            occurrenceId =
                "occurrence-sensitive",
            outcome =
                "attempted",
        )

        val payload =
            sink
                .events
                .single()
                .toString()

        assertFalse(
            payload.contains(
                medicationName,
            ),
        )

        assertFalse(
            payload.contains(
                instructionText,
            ),
        )

        assertFalse(
            payload.contains(
                recipientName,
            ),
        )

        assertFalse(
            payload.contains(
                reportText,
            ),
        )
    }

    @Test
    fun noOpSinkAcceptsEventsWithoutSideEffects() {
        NoOpReminderDiagnosticSink.record(
            ReminderDiagnosticEvent(
                type =
                    ReminderDiagnosticEventType
                        .REMINDERS_DISABLED,
                occurredAtEpochMillis =
                    1L,
            ),
        )
    }
}

private class ReminderDiagnosticsRecordingSink :
    ReminderDiagnosticSink {

    val events =
        mutableListOf<ReminderDiagnosticEvent>()

    override fun record(
        event: ReminderDiagnosticEvent,
    ) {
        events += event
    }
}
