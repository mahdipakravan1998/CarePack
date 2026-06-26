package ir.carepack.domain.report

import ir.carepack.domain.model.CaregiverReportState

data class ReportChange(
    val occurrenceId: String,
    val previousState: CaregiverReportState?,
    val newState: CaregiverReportState,
    val changedAtEpochMillis: Long,
)

sealed interface SetReportOutcome {

    data class Changed(
        val change: ReportChange,
    ) : SetReportOutcome

    data class Unchanged(
        val occurrenceId: String,
        val state: CaregiverReportState,
    ) : SetReportOutcome

    data object OccurrenceNotFound :
        SetReportOutcome

    data object CancelledOccurrenceRejected :
        SetReportOutcome
}

sealed interface UndoReportOutcome {

    data class Restored(
        val occurrenceId: String,
        val restoredState:
        CaregiverReportState?,
    ) : UndoReportOutcome

    data object NoLongerCurrent :
        UndoReportOutcome

    data object OccurrenceNotFound :
        UndoReportOutcome
}

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

    data object OccurrenceNotFound :
        RecordGivenOutcome

    data object CancelledOccurrenceRejected :
        RecordGivenOutcome
}

interface CaregiverReportService {

    suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome

    suspend fun restorePrevious(
        change: ReportChange,
    ): UndoReportOutcome

    suspend fun recordGiven(
        occurrenceId: String,
    ): RecordGivenOutcome {
        return when (
            val outcome =
                setReport(
                    occurrenceId =
                        occurrenceId,
                    newState =
                        CaregiverReportState
                            .GIVEN,
                )
        ) {
            is SetReportOutcome.Changed -> {
                RecordGivenOutcome.Recorded(
                    occurrenceId =
                        outcome
                            .change
                            .occurrenceId,
                )
            }

            is SetReportOutcome.Unchanged -> {
                RecordGivenOutcome.Unchanged(
                    occurrenceId =
                        outcome.occurrenceId,
                )
            }

            SetReportOutcome
                .OccurrenceNotFound -> {
                RecordGivenOutcome
                    .OccurrenceNotFound
            }

            SetReportOutcome
                .CancelledOccurrenceRejected -> {
                RecordGivenOutcome
                    .CancelledOccurrenceRejected
            }
        }
    }
}
