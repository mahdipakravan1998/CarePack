package ir.carepack.domain.occurrence

import java.time.Instant
import java.time.LocalDate

data class GuaranteedOccurrence(
    val occurrenceId: String,
    val wasCreated: Boolean,
)

data class GenerationSummary(
    val occurrences:
    List<GuaranteedOccurrence>,
    val skippedCandidateCount: Int,
) {
    val insertedCount: Int
        get() =
            occurrences.count {
                it.wasCreated
            }

    val existingCount: Int
        get() =
            occurrences.count {
                !it.wasCreated
            }
}

interface OccurrenceGenerator {

    suspend fun guaranteeWindowForSchedule(
        scheduleVersionId: String,
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary

    suspend fun guaranteeWindowForAll(
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary
}
