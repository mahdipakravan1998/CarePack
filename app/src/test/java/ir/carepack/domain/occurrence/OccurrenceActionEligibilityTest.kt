package ir.carepack.domain.occurrence

import ir.carepack.domain.model.OccurrenceLifecycle
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceActionEligibilityTest {

    private val scheduledAt = Instant.parse("2026-08-27T08:00:00Z")

    @Test
    fun reportMutation_hasAnIndependentScheduledTimeBoundary() {
        assertFalse(
            ReportMutationEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                scheduledAt = scheduledAt,
                now = scheduledAt.minusSeconds(1),
            ),
        )
        assertTrue(
            ReportMutationEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                scheduledAt = scheduledAt,
                now = scheduledAt,
            ),
        )
        assertTrue(
            ReportMutationEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                scheduledAt = scheduledAt,
                now = scheduledAt.plusSeconds(86_400),
            ),
        )
        assertFalse(
            ReportMutationEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.CANCELLED,
                scheduledAt = scheduledAt,
                now = scheduledAt.plusSeconds(1),
            ),
        )
    }

    @Test
    fun remindLater_requiresUnreportedDueOccurrenceOnSnapshotLocalDay() {
        val zoneId = "Asia/Tehran"
        val localDay = LocalDate.of(2026, 8, 27).toEpochDay()
        val at235959 = Instant.parse("2026-08-27T20:29:59Z")
        val atMidnight = Instant.parse("2026-08-27T20:30:00Z")

        assertFalse(allowedReminder(now = scheduledAt.minusSeconds(1)))
        assertTrue(allowedReminder(now = scheduledAt))
        assertTrue(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                hasCaregiverReport = false,
                scheduledAt = scheduledAt,
                occurrenceLocalEpochDay = localDay,
                zoneIdSnapshot = zoneId,
                now = at235959,
            ),
        )
        assertFalse(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                hasCaregiverReport = false,
                scheduledAt = scheduledAt,
                occurrenceLocalEpochDay = localDay,
                zoneIdSnapshot = zoneId,
                now = atMidnight,
            ),
        )
        assertFalse(allowedReminder(now = scheduledAt, hasReport = true))
        assertFalse(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.CANCELLED,
                hasCaregiverReport = false,
                scheduledAt = scheduledAt,
                occurrenceLocalEpochDay = localDay,
                zoneIdSnapshot = zoneId,
                now = scheduledAt,
            ),
        )
    }

    @Test
    fun remindLater_usesSnapshotZoneRatherThanDeviceZone() {
        val instant = Instant.parse("2026-08-27T20:45:00Z")
        val tehranDay = LocalDate.of(2026, 8, 28).toEpochDay()
        val losAngelesDay = LocalDate.of(2026, 8, 27).toEpochDay()

        assertTrue(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                hasCaregiverReport = false,
                scheduledAt = scheduledAt,
                occurrenceLocalEpochDay = tehranDay,
                zoneIdSnapshot = "Asia/Tehran",
                now = instant,
            ),
        )
        assertTrue(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                hasCaregiverReport = false,
                scheduledAt = scheduledAt,
                occurrenceLocalEpochDay = losAngelesDay,
                zoneIdSnapshot = "America/Los_Angeles",
                now = instant,
            ),
        )
        assertFalse(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                hasCaregiverReport = false,
                scheduledAt = scheduledAt,
                occurrenceLocalEpochDay = losAngelesDay,
                zoneIdSnapshot = "Asia/Tehran",
                now = instant,
            ),
        )
    }

    @Test
    fun remindLater_isStableAcrossDstGapAndOverlapDates() {
        assertTrue(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                hasCaregiverReport = false,
                scheduledAt = Instant.parse("2026-03-08T06:30:00Z"),
                occurrenceLocalEpochDay = LocalDate.of(2026, 3, 8).toEpochDay(),
                zoneIdSnapshot = "America/New_York",
                now = Instant.parse("2026-03-08T07:30:00Z"),
            ),
        )
        assertTrue(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                hasCaregiverReport = false,
                scheduledAt = Instant.parse("2026-11-01T05:15:00Z"),
                occurrenceLocalEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
                zoneIdSnapshot = "America/New_York",
                now = Instant.parse("2026-11-01T06:45:00Z"),
            ),
        )
        assertFalse(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                hasCaregiverReport = false,
                scheduledAt = scheduledAt,
                occurrenceLocalEpochDay = LocalDate.of(2026, 8, 27).toEpochDay(),
                zoneIdSnapshot = "Invalid/Zone",
                now = scheduledAt,
            ),
        )
    }

    @Test
    fun remindLaterEligibility_isIndependentOfNotificationDelivery() {
        val now = scheduledAt.plusSeconds(60)

        assertTrue(
            RemindLaterEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                hasCaregiverReport = false,
                scheduledAt = scheduledAt,
                occurrenceLocalEpochDay = LocalDate.of(2026, 8, 27).toEpochDay(),
                zoneIdSnapshot = "Asia/Tehran",
                now = now,
            ),
        )
        assertTrue(
            ReportMutationEligibility.isAllowed(
                lifecycle = OccurrenceLifecycle.ACTIVE,
                scheduledAt = scheduledAt,
                now = now,
            ),
        )
    }

    private fun allowedReminder(
        now: Instant,
        hasReport: Boolean = false,
    ): Boolean = RemindLaterEligibility.isAllowed(
        lifecycle = OccurrenceLifecycle.ACTIVE,
        hasCaregiverReport = hasReport,
        scheduledAt = scheduledAt,
        occurrenceLocalEpochDay = LocalDate.of(2026, 8, 27).toEpochDay(),
        zoneIdSnapshot = "Asia/Tehran",
        now = now,
    )
}
