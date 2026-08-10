package ir.carepack.settings.deletion

import androidx.room.withTransaction
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.MedicationDeletionPreviewRow

class RoomMedicationDeletionDataSource(
    private val database: CarePackDatabase,
) : MedicationDeletionDataSource {

    override suspend fun loadPreview(
        medicationId: String,
    ): MedicationDeletionPreview? {
        val trimmedMedicationId = medicationId.trim()

        require(trimmedMedicationId.isNotBlank())

        return database.medicationDao()
            .getDeletionPreview(
                medicationId = trimmedMedicationId,
            )?.toDomain()
    }

    override suspend fun loadGraph(
        medicationId: String,
    ): MedicationDeletionGraph? {
        val trimmedMedicationId = medicationId.trim()

        require(trimmedMedicationId.isNotBlank())

        return database.withTransaction {
            val dao = database.medicationDao()

            val preview = dao
                    .getDeletionPreview(
                        medicationId = trimmedMedicationId,
                    )?.toDomain()
                    ?: return@withTransaction null

            MedicationDeletionGraph(
                preview = preview,
                scheduleSeriesIds = dao
                        .getDeletionScheduleSeriesIds(
                            medicationId = trimmedMedicationId,
                        ).distinct(),
                occurrenceIds = dao
                        .getDeletionOccurrenceIds(
                            medicationId = trimmedMedicationId,
                        ).distinct(),
            )
        }
    }

    override suspend fun deleteGraph(
        medicationId: String,
        expectedPreview: MedicationDeletionPreview?,
    ): MedicationGraphDeletionResult {
        val trimmedMedicationId = medicationId.trim()

        require(trimmedMedicationId.isNotBlank())

        return database.withTransaction {
            val dao = database.medicationDao()

            val currentPreview = dao
                    .getDeletionPreview(
                        medicationId = trimmedMedicationId,
                    )?.toDomain()
                    ?: return@withTransaction (
                            MedicationGraphDeletionResult.NotFound
                            )

            if (
                expectedPreview != null && expectedPreview != currentPreview
            ) {
                return@withTransaction (
                        MedicationGraphDeletionResult.ChangedSincePreview(
                                latestPreview = currentPreview,
                            ))
            }

            val deletedReports = dao
                    .deleteReportsOwnedByMedication(
                        medicationId = trimmedMedicationId,
                    )

            check(
                deletedReports == currentPreview
                            .caregiverReportCount,
            )

            val deletedOccurrences = dao
                    .deleteOccurrencesOwnedByMedication(
                        medicationId = trimmedMedicationId,
                    )

            check(
                deletedOccurrences == currentPreview
                            .occurrenceCount,
            )

            val deletedTimes = dao
                    .deleteScheduleTimesOwnedByMedication(
                        medicationId = trimmedMedicationId,
                    )

            check(
                deletedTimes == currentPreview
                            .scheduleTimeCount,
            )

            val deletedVersions = dao
                    .deleteScheduleVersionsOwnedByMedication(
                        medicationId = trimmedMedicationId,
                    )

            check(
                deletedVersions == currentPreview
                            .scheduleVersionCount,
            )

            val deletedSeries = dao
                    .deleteScheduleSeriesOwnedByMedication(
                        medicationId = trimmedMedicationId,
                    )

            check(
                deletedSeries == currentPreview
                            .scheduleSeriesCount,
            )

            val deletedMedication = dao
                    .deleteMedicationById(
                        medicationId = trimmedMedicationId,
                    )

            check(
                deletedMedication == 1,
            )

            check(
                dao.getDeletionPreview(
                    medicationId = trimmedMedicationId,
                ) == null,
            )

            MedicationGraphDeletionResult.Deleted(
                    counts = MedicationDeletionCounts(
                            caregiverReportCount = deletedReports,
                            occurrenceCount = deletedOccurrences,
                            scheduleTimeCount = deletedTimes,
                            scheduleVersionCount = deletedVersions,
                            scheduleSeriesCount = deletedSeries,
                            medicationCount = deletedMedication,
                        ),
                )
        }
    }

    private fun MedicationDeletionPreviewRow.toDomain(): MedicationDeletionPreview =
        MedicationDeletionPreview(
            medicationId = medicationId,
            medicationName = medicationName,
            medicationUpdatedAtEpochMillis = medicationUpdatedAtEpochMillis,
            scheduleSeriesCount = scheduleSeriesCount,
            scheduleVersionCount = scheduleVersionCount,
            scheduleTimeCount = scheduleTimeCount,
            occurrenceCount = occurrenceCount,
            caregiverReportCount = caregiverReportCount,
        )
}
