package ir.carepack.domain.report

import ir.carepack.domain.model.CaregiverReportState

data class ReportChange(
    val occurrenceId: String,
    val previousState: CaregiverReportState?,
    val newState: CaregiverReportState,
    val changedAtEpochMillis: Long,
)

sealed interface SetReportOutcome {
    data class Changed(val change: ReportChange) : SetReportOutcome

    data class Unchanged(
        val occurrenceId: String,
        val state: CaregiverReportState,
    ) : SetReportOutcome

    data object OccurrenceNotFound : SetReportOutcome
    data object CancelledOccurrenceRejected : SetReportOutcome
}

sealed interface UndoReportOutcome {
    data class Restored(
        val occurrenceId: String,
        val restoredState: CaregiverReportState?,
    ) : UndoReportOutcome

    data object NoLongerCurrent : UndoReportOutcome
    data object OccurrenceNotFound : UndoReportOutcome
}

interface CaregiverReportService {
    suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome

    suspend fun restorePrevious(change: ReportChange): UndoReportOutcome
}
