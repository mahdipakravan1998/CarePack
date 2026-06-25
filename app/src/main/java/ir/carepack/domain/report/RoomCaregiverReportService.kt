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

    override suspend fun recordGiven(
        occurrenceId: String,
    ): RecordGivenOutcome {
        return database.withTransaction {
            val occurrence =
                database
                    .occurrenceDao()
                    .getById(occurrenceId)

            when {
                occurrence == null -> {
                    RecordGivenOutcome.OccurrenceNotFound
                }

                occurrence.lifecycle !=
                        OccurrenceLifecycle.ACTIVE.name -> {
                    RecordGivenOutcome
                        .CancelledOccurrenceRejected
                }

                else -> {
                    recordActiveOccurrenceAsGiven(
                        occurrenceId = occurrenceId,
                    )
                }
            }
        }
    }

    private suspend fun recordActiveOccurrenceAsGiven(
        occurrenceId: String,
    ): RecordGivenOutcome {
        val reportDao =
            database.caregiverReportDao()

        val existingReport =
            reportDao.getByOccurrenceId(
                occurrenceId,
            )

        if (existingReport != null) {
            return existingReport.toExistingOutcome()
        }

        val nowEpochMillis =
            clock
                .instant()
                .toEpochMilli()

        val insertResult =
            reportDao.insertIgnoringConflict(
                CaregiverReportEntity(
                    occurrenceId = occurrenceId,
                    state =
                        CaregiverReportState
                            .GIVEN
                            .name,
                    recordedAtEpochMillis =
                        nowEpochMillis,
                    updatedAtEpochMillis =
                        nowEpochMillis,
                ),
            )

        if (insertResult != -1L) {
            return RecordGivenOutcome.Recorded(
                occurrenceId = occurrenceId,
            )
        }

        val persistedReport =
            checkNotNull(
                reportDao.getByOccurrenceId(
                    occurrenceId,
                ),
            )

        return persistedReport.toExistingOutcome()
    }
}

private fun CaregiverReportEntity.toExistingOutcome():
        RecordGivenOutcome {
    val existingState =
        CaregiverReportState.valueOf(state)

    return if (
        existingState ==
        CaregiverReportState.GIVEN
    ) {
        RecordGivenOutcome.Unchanged(
            occurrenceId = occurrenceId,
        )
    } else {
        RecordGivenOutcome.ExistingDifferentReport(
            occurrenceId = occurrenceId,
            existingState = existingState,
        )
    }
}
