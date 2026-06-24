package ir.carepack.domain.occurrence

import java.time.Instant
import java.time.LocalDate

data class GuaranteedOccurrence(
    val occurrenceId: String,
    val wasCreated: Boolean,
)

data class GenerationSummary(
    val occurrences: List<GuaranteedOccurrence>,
    val skippedCandidateCount: Int,
)

interface OccurrenceGenerator {
    suspend fun guaranteeForSchedule(
        scheduleVersionId: String,
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary

    suspend fun guaranteeForEffectiveSchedules(
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary
}