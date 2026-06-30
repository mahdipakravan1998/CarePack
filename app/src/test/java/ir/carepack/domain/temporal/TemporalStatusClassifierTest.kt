package ir.carepack.domain.temporal

import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalStatusClassifierTest {

    private val classifier =
        TemporalStatusClassifier()

    private val scheduledAt =
        Instant.parse(
            "2026-06-24T08:00:00Z",
        )

    @Test
    fun oneMillisecondBeforeScheduledTime_isUpcoming() {
        assertEquals(
            TemporalStatus.UPCOMING,
            classifier.classify(
                scheduledAt =
                    scheduledAt,
                now =
                    scheduledAt
                        .minusMillis(1),
            ),
        )
    }

    @Test
    fun exactlyAtScheduledTime_isDue() {
        assertEquals(
            TemporalStatus.DUE,
            classifier.classify(
                scheduledAt =
                    scheduledAt,
                now = scheduledAt,
            ),
        )
    }

    @Test
    fun oneMillisecondBeforePlus60Minutes_isDue() {
        assertEquals(
            TemporalStatus.DUE,
            classifier.classify(
                scheduledAt =
                    scheduledAt,
                now =
                    scheduledAt
                        .plusSeconds(
                            60L * 60L,
                        )
                        .minusMillis(1),
            ),
        )
    }

    @Test
    fun exactlyAtPlus60Minutes_isPast() {
        assertEquals(
            TemporalStatus.PAST,
            classifier.classify(
                scheduledAt =
                    scheduledAt,
                now =
                    scheduledAt
                        .plusSeconds(
                            60L * 60L,
                        ),
            ),
        )
    }

    @Test
    fun activePastNoReport_isOverdue() {
        assertTrue(
            classifier.isOverdue(
                lifecycle =
                    OccurrenceLifecycle
                        .ACTIVE,
                reportState = null,
                phase =
                    TemporalStatus.PAST,
            ),
        )
    }

    @Test
    fun explicitUnknown_isNotOverdue() {
        assertFalse(
            classifier.isOverdue(
                lifecycle =
                    OccurrenceLifecycle
                        .ACTIVE,
                reportState =
                    CaregiverReportState
                        .UNKNOWN,
                phase =
                    TemporalStatus.PAST,
            ),
        )
    }

    @Test
    fun givenPastOccurrence_isNotOverdue() {
        assertFalse(
            classifier.isOverdue(
                lifecycle =
                    OccurrenceLifecycle
                        .ACTIVE,
                reportState =
                    CaregiverReportState
                        .GIVEN,
                phase =
                    TemporalStatus.PAST,
            ),
        )
    }

    @Test
    fun cancelledPastNoReport_isNotOverdue() {
        assertFalse(
            classifier.isOverdue(
                lifecycle =
                    OccurrenceLifecycle
                        .CANCELLED,
                reportState = null,
                phase =
                    TemporalStatus.PAST,
            ),
        )
    }

    @Test
    fun activeDueNoReport_isNotOverdue() {
        assertFalse(
            classifier.isOverdue(
                lifecycle =
                    OccurrenceLifecycle
                        .ACTIVE,
                reportState = null,
                phase =
                    TemporalStatus.DUE,
            ),
        )
    }
}
