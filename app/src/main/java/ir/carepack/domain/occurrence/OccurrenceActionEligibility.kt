package ir.carepack.domain.occurrence

import ir.carepack.domain.model.OccurrenceLifecycle
import java.time.Instant
import java.time.ZoneId

object ReportMutationEligibility {

    fun isAllowed(
        lifecycle: OccurrenceLifecycle,
        scheduledAt: Instant,
        now: Instant,
    ): Boolean = lifecycle == OccurrenceLifecycle.ACTIVE &&
            !now.isBefore(scheduledAt)
}

object RemindLaterEligibility {

    fun isAllowed(
        lifecycle: OccurrenceLifecycle,
        hasCaregiverReport: Boolean,
        scheduledAt: Instant,
        occurrenceLocalEpochDay: Long,
        zoneIdSnapshot: String,
        now: Instant,
    ): Boolean {
        if (
            lifecycle != OccurrenceLifecycle.ACTIVE ||
            hasCaregiverReport ||
            now.isBefore(scheduledAt)
        ) {
            return false
        }

        val snapshotZone = runCatching {
            ZoneId.of(zoneIdSnapshot)
        }.getOrNull() ?: return false

        return now.atZone(snapshotZone)
            .toLocalDate()
            .toEpochDay() == occurrenceLocalEpochDay
    }
}