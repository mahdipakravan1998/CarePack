package ir.carepack.domain.temporal

import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalPhase
import java.time.Duration
import java.time.Instant

class TemporalClassifier {

    fun classify(
        scheduledAt: Instant,
        now: Instant,
    ): TemporalPhase {
        if (now.isBefore(scheduledAt)) {
            return TemporalPhase.UPCOMING
        }

        val dueUntil =
            scheduledAt.plus(DUE_DURATION)

        return if (now.isBefore(dueUntil)) {
            TemporalPhase.DUE
        } else {
            TemporalPhase.PAST
        }
    }

    fun isOverdue(
        lifecycle: OccurrenceLifecycle,
        reportState: CaregiverReportState?,
        phase: TemporalPhase,
    ): Boolean {
        return lifecycle ==
                OccurrenceLifecycle.ACTIVE &&
                phase == TemporalPhase.PAST &&
                reportState == null
    }

    private companion object {
        val DUE_DURATION:
                Duration =
            Duration.ofMinutes(60)
    }
}
