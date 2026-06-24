package ir.carepack.domain.report

import ir.carepack.domain.model.CaregiverReportState

sealed interface RecordGivenOutcome {
    data class Recorded(
        val occurrenceId: String,
    ) : RecordGivenOutcome

    data class Unchanged(
        val occurrenceId: String,
    ) : RecordGivenOutcome

    data class ExistingDifferentReport(
        val occurrenceId: String,
        val existingState: CaregiverReportState,
    ) : RecordGivenOutcome

    data object OccurrenceNotFound : RecordGivenOutcome

    data object CancelledOccurrenceRejected : RecordGivenOutcome
}

interface CaregiverReportService {
    suspend fun recordGiven(
        occurrenceId: String,
    ): RecordGivenOutcome
}