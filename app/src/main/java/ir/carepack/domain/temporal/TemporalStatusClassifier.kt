package ir.carepack.domain.temporal

import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalStatus
import java.time.Duration
import java.time.Instant

class TemporalStatusClassifier {

    fun classify(
        scheduledAt: Instant,
        now: Instant,
    ): TemporalStatus {
        if (now.isBefore(scheduledAt)) {
            return TemporalStatus.UPCOMING
        }

        val dueUntil =
            scheduledAt.plus(DUE_DURATION)

        return if (now.isBefore(dueUntil)) {
            TemporalStatus.DUE
        } else {
            TemporalStatus.PAST
        }
    }

    fun isOverdue(
        lifecycle: OccurrenceLifecycle,
        reportState: CaregiverReportState?,
        phase: TemporalStatus,
    ): Boolean {
        return lifecycle ==
                OccurrenceLifecycle.ACTIVE &&
                phase == TemporalStatus.PAST &&
                reportState == null
    }

    private companion object {
        val DUE_DURATION:
                Duration =
            Duration.ofMinutes(60)
    }
}
