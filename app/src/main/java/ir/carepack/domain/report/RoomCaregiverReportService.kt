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
                    val existingReport =
                        database
                            .caregiverReportDao()
                            .getByOccurrenceId(
                                occurrenceId,
                            )

                    if (existingReport != null) {
                        val existingState =
                            CaregiverReportState.valueOf(
                                existingReport.state,
                            )

                        if (
                            existingState ==
                            CaregiverReportState.GIVEN
                        ) {
                            RecordGivenOutcome.Unchanged(
                                occurrenceId =
                                    occurrenceId,
                            )
                        } else {
                            RecordGivenOutcome
                                .ExistingDifferentReport(
                                    occurrenceId =
                                        occurrenceId,
                                    existingState =
                                        existingState,
                                )
                        }
                    } else {
                        val nowEpochMillis =
                            clock
                                .instant()
                                .toEpochMilli()

                        database
                            .caregiverReportDao()
                            .insert(
                                CaregiverReportEntity(
                                    occurrenceId =
                                        occurrenceId,
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

                        RecordGivenOutcome.Recorded(
                            occurrenceId = occurrenceId,
                        )
                    }
                }
            }
        }
    }
}
