package ir.carepack.domain.report

import androidx.room.withTransaction
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.CaregiverReportEntity
import ir.carepack.data.local.ReportingDao
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceLifecycle
import java.time.Clock

class RoomCaregiverReportService(
    private val database: CarePackDatabase,
    private val clock: Clock,
) : CaregiverReportService {
    private val reportingDao: ReportingDao = database.reportingDao()

    override suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome = database.withTransaction {
        when (reportingDao.getOccurrenceLifecycle(occurrenceId)) {
            null -> SetReportOutcome.OccurrenceNotFound
            OccurrenceLifecycle.ACTIVE.name -> setActiveOccurrenceReport(occurrenceId, newState)
            else -> SetReportOutcome.CancelledOccurrenceRejected
        }
    }

    override suspend fun restorePrevious(change: ReportChange): UndoReportOutcome =
        database.withTransaction {
            when (reportingDao.getOccurrenceLifecycle(change.occurrenceId)) {
                null -> UndoReportOutcome.OccurrenceNotFound
                OccurrenceLifecycle.ACTIVE.name -> restoreActiveOccurrenceReport(change)
                else -> UndoReportOutcome.NoLongerCurrent
            }
        }

    private suspend fun setActiveOccurrenceReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        val existing = reportingDao.getReport(occurrenceId)
        val previousState = existing?.state?.let(CaregiverReportState::valueOf)

        return when {
            previousState == newState -> SetReportOutcome.Unchanged(occurrenceId, newState)
            existing == null -> insertFirstReport(occurrenceId, newState)
            else -> updateExistingReport(existing, newState)
        }
    }

    private suspend fun restoreActiveOccurrenceReport(change: ReportChange): UndoReportOutcome {
        val current = reportingDao.getReport(change.occurrenceId)
            ?: return UndoReportOutcome.NoLongerCurrent

        val isCurrentChange =
            CaregiverReportState.valueOf(current.state) == change.newState &&
                    current.updatedAtEpochMillis == change.changedAtEpochMillis

        if (!isCurrentChange) {
            return UndoReportOutcome.NoLongerCurrent
        }

        return change.previousState?.let { previousState ->
            restoreToExplicitState(change, current, previousState)
        } ?: restoreToNoReport(change)
    }

    private suspend fun restoreToNoReport(change: ReportChange): UndoReportOutcome {
        val deletedRows = reportingDao.deleteReportIfCurrent(
            occurrenceId = change.occurrenceId,
            expectedCurrentState = change.newState.name,
            expectedCurrentUpdatedAtEpochMillis = change.changedAtEpochMillis,
        )

        return if (deletedRows == 1) {
            UndoReportOutcome.Restored(change.occurrenceId, restoredState = null)
        } else {
            UndoReportOutcome.NoLongerCurrent
        }
    }

    private suspend fun restoreToExplicitState(
        change: ReportChange,
        current: CaregiverReportEntity,
        previousState: CaregiverReportState,
    ): UndoReportOutcome {
        val restoredRows = reportingDao.restorePreviousReportIfCurrent(
            occurrenceId = change.occurrenceId,
            expectedCurrentState = change.newState.name,
            expectedCurrentUpdatedAtEpochMillis = change.changedAtEpochMillis,
            previousState = previousState.name,
            restoredAtEpochMillis = nextTimestamp(current.updatedAtEpochMillis),
        )

        return if (restoredRows == 1) {
            UndoReportOutcome.Restored(change.occurrenceId, previousState)
        } else {
            UndoReportOutcome.NoLongerCurrent
        }
    }

    private suspend fun insertFirstReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        val changedAtEpochMillis = clock.instant().toEpochMilli()
        val insertResult = reportingDao.insertReportIgnoringConflict(
            CaregiverReportEntity(
                occurrenceId = occurrenceId,
                state = newState.name,
                recordedAtEpochMillis = changedAtEpochMillis,
                updatedAtEpochMillis = changedAtEpochMillis,
            ),
        )

        if (insertResult != INSERT_CONFLICT) {
            return changed(
                occurrenceId = occurrenceId,
                previousState = null,
                newState = newState,
                changedAtEpochMillis = changedAtEpochMillis,
            )
        }

        val concurrentReport = checkNotNull(reportingDao.getReport(occurrenceId))
        val concurrentState = CaregiverReportState.valueOf(concurrentReport.state)

        if (concurrentState == newState) {
            return SetReportOutcome.Unchanged(occurrenceId, newState)
        }

        val retryTimestamp = nextTimestamp(concurrentReport.updatedAtEpochMillis)
        check(
            reportingDao.updateReport(
                occurrenceId = occurrenceId,
                state = newState.name,
                updatedAtEpochMillis = retryTimestamp,
            ) == 1,
        )

        return changed(
            occurrenceId = occurrenceId,
            previousState = concurrentState,
            newState = newState,
            changedAtEpochMillis = retryTimestamp,
        )
    }

    private suspend fun updateExistingReport(
        existing: CaregiverReportEntity,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        val previousState = CaregiverReportState.valueOf(existing.state)
        val changedAtEpochMillis = nextTimestamp(existing.updatedAtEpochMillis)

        check(
            reportingDao.updateReport(
                occurrenceId = existing.occurrenceId,
                state = newState.name,
                updatedAtEpochMillis = changedAtEpochMillis,
            ) == 1,
        )

        return changed(
            occurrenceId = existing.occurrenceId,
            previousState = previousState,
            newState = newState,
            changedAtEpochMillis = changedAtEpochMillis,
        )
    }

    private fun changed(
        occurrenceId: String,
        previousState: CaregiverReportState?,
        newState: CaregiverReportState,
        changedAtEpochMillis: Long,
    ) = SetReportOutcome.Changed(
        ReportChange(
            occurrenceId = occurrenceId,
            previousState = previousState,
            newState = newState,
            changedAtEpochMillis = changedAtEpochMillis,
        ),
    )

    private fun nextTimestamp(previousTimestamp: Long): Long {
        val currentTimestamp = clock.instant().toEpochMilli()
        if (currentTimestamp > previousTimestamp) {
            return currentTimestamp
        }

        check(previousTimestamp < Long.MAX_VALUE)
        return previousTimestamp + 1L
    }

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}
