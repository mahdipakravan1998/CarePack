package ir.carepack.domain.report

import androidx.room.withTransaction
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.CaregiverReportEntity
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceLifecycle
import java.time.Clock

class RoomCaregiverReportService(
    private val database: CarePackDatabase,
    private val clock: Clock,
) : CaregiverReportService {

    override suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        return database.withTransaction {
            val reportingDao =
                database.reportingDao()

            val lifecycle =
                reportingDao.getOccurrenceLifecycle(
                    occurrenceId = occurrenceId,
                )

            if (lifecycle == null) {
                SetReportOutcome.OccurrenceNotFound
            } else if (
                lifecycle !=
                OccurrenceLifecycle.ACTIVE.name
            ) {
                SetReportOutcome.CancelledOccurrenceRejected
            } else {
                val existing =
                    reportingDao.getReport(
                        occurrenceId = occurrenceId,
                    )

                val previousState =
                    existing
                        ?.state
                        ?.let(
                            CaregiverReportState::valueOf,
                        )

                when {
                    previousState == newState -> {
                        SetReportOutcome.Unchanged(
                            occurrenceId = occurrenceId,
                            state = newState,
                        )
                    }

                    existing == null -> {
                        insertFirstReport(
                            occurrenceId = occurrenceId,
                            newState = newState,
                        )
                    }

                    else -> {
                        updateExistingReport(
                            existing = existing,
                            newState = newState,
                        )
                    }
                }
            }
        }
    }

    override suspend fun restorePrevious(
        change: ReportChange,
    ): UndoReportOutcome {
        return database.withTransaction {
            val reportingDao =
                database.reportingDao()

            val occurrenceLifecycle =
                reportingDao.getOccurrenceLifecycle(
                    occurrenceId =
                        change.occurrenceId,
                )

            if (occurrenceLifecycle == null) {
                UndoReportOutcome.OccurrenceNotFound
            } else if (
                occurrenceLifecycle !=
                OccurrenceLifecycle.ACTIVE.name
            ) {
                UndoReportOutcome.NoLongerCurrent
            } else {
                val current =
                    reportingDao.getReport(
                        occurrenceId =
                            change.occurrenceId,
                    )

                if (current == null) {
                    UndoReportOutcome.NoLongerCurrent
                } else {
                    val currentState =
                        CaregiverReportState.valueOf(
                            current.state,
                        )

                    val isCurrentChange =
                        currentState ==
                                change.newState &&
                                current.updatedAtEpochMillis ==
                                change.changedAtEpochMillis

                    if (!isCurrentChange) {
                        UndoReportOutcome.NoLongerCurrent
                    } else if (
                        change.previousState == null
                    ) {
                        restoreToNoReport(
                            change = change,
                        )
                    } else {
                        restoreToExplicitState(
                            change = change,
                            current =
                                current,
                        )
                    }
                }
            }
        }
    }

    private suspend fun restoreToNoReport(
        change: ReportChange,
    ): UndoReportOutcome {
        val deletedRows =
            database
                .reportingDao()
                .deleteReportIfCurrent(
                    occurrenceId =
                        change.occurrenceId,
                    expectedCurrentState =
                        change.newState.name,
                    expectedCurrentUpdatedAtEpochMillis =
                        change.changedAtEpochMillis,
                )

        return if (deletedRows == 1) {
            UndoReportOutcome.Restored(
                occurrenceId =
                    change.occurrenceId,
                restoredState = null,
            )
        } else {
            UndoReportOutcome.NoLongerCurrent
        }
    }

    private suspend fun restoreToExplicitState(
        change: ReportChange,
        current: CaregiverReportEntity,
    ): UndoReportOutcome {
        val previousState =
            checkNotNull(
                change.previousState,
            )

        val restoredAtEpochMillis =
            nextTimestamp(
                previousTimestamp =
                    current.updatedAtEpochMillis,
            )

        val restoredRows =
            database
                .reportingDao()
                .restorePreviousReportIfCurrent(
                    occurrenceId =
                        change.occurrenceId,
                    expectedCurrentState =
                        change.newState.name,
                    expectedCurrentUpdatedAtEpochMillis =
                        change.changedAtEpochMillis,
                    previousState =
                        previousState.name,
                    restoredAtEpochMillis =
                        restoredAtEpochMillis,
                )

        return if (restoredRows == 1) {
            UndoReportOutcome.Restored(
                occurrenceId =
                    change.occurrenceId,
                restoredState =
                    previousState,
            )
        } else {
            UndoReportOutcome.NoLongerCurrent
        }
    }

    private suspend fun insertFirstReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        val reportingDao =
            database.reportingDao()

        val changedAtEpochMillis =
            clock
                .instant()
                .toEpochMilli()

        val insertResult =
            reportingDao.insertReportIgnoringConflict(
                CaregiverReportEntity(
                    occurrenceId =
                        occurrenceId,
                    state =
                        newState.name,
                    recordedAtEpochMillis =
                        changedAtEpochMillis,
                    updatedAtEpochMillis =
                        changedAtEpochMillis,
                ),
            )

        if (insertResult != -1L) {
            return SetReportOutcome.Changed(
                change =
                    ReportChange(
                        occurrenceId =
                            occurrenceId,
                        previousState = null,
                        newState = newState,
                        changedAtEpochMillis =
                            changedAtEpochMillis,
                    ),
            )
        }

        val concurrentReport =
            checkNotNull(
                reportingDao.getReport(
                    occurrenceId =
                        occurrenceId,
                ),
            )

        val concurrentState =
            CaregiverReportState.valueOf(
                concurrentReport.state,
            )

        if (concurrentState == newState) {
            return SetReportOutcome.Unchanged(
                occurrenceId =
                    occurrenceId,
                state = newState,
            )
        }

        val retryTimestamp =
            nextTimestamp(
                previousTimestamp =
                    concurrentReport
                        .updatedAtEpochMillis,
            )

        val updatedRows =
            reportingDao.updateReport(
                occurrenceId =
                    occurrenceId,
                state =
                    newState.name,
                updatedAtEpochMillis =
                    retryTimestamp,
            )

        check(updatedRows == 1)

        return SetReportOutcome.Changed(
            change =
                ReportChange(
                    occurrenceId =
                        occurrenceId,
                    previousState =
                        concurrentState,
                    newState =
                        newState,
                    changedAtEpochMillis =
                        retryTimestamp,
                ),
        )
    }

    private suspend fun updateExistingReport(
        existing: CaregiverReportEntity,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        val previousState =
            CaregiverReportState.valueOf(
                existing.state,
            )

        val changedAtEpochMillis =
            nextTimestamp(
                previousTimestamp =
                    existing.updatedAtEpochMillis,
            )

        val updatedRows =
            database
                .reportingDao()
                .updateReport(
                    occurrenceId =
                        existing.occurrenceId,
                    state =
                        newState.name,
                    updatedAtEpochMillis =
                        changedAtEpochMillis,
                )

        check(updatedRows == 1)

        return SetReportOutcome.Changed(
            change =
                ReportChange(
                    occurrenceId =
                        existing.occurrenceId,
                    previousState =
                        previousState,
                    newState =
                        newState,
                    changedAtEpochMillis =
                        changedAtEpochMillis,
                ),
        )
    }

    private fun nextTimestamp(
        previousTimestamp: Long,
    ): Long {
        val currentTimestamp =
            clock
                .instant()
                .toEpochMilli()

        return if (
            currentTimestamp >
            previousTimestamp
        ) {
            currentTimestamp
        } else {
            check(
                previousTimestamp <
                        Long.MAX_VALUE,
            )

            previousTimestamp + 1L
        }
    }
}
