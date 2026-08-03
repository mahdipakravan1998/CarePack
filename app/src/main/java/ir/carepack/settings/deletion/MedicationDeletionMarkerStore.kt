package ir.carepack.settings.deletion

import kotlinx.coroutines.flow.Flow

data class MedicationDeletionPreview(
    val medicationId: String,
    val medicationName: String,
    val medicationUpdatedAtEpochMillis: Long,
    val scheduleSeriesCount: Int,
    val scheduleVersionCount: Int,
    val scheduleTimeCount: Int,
    val occurrenceCount: Int,
    val caregiverReportCount: Int,
) {
    init {
        require(medicationId.isNotBlank())
        require(medicationName.isNotBlank())
        require(scheduleSeriesCount >= 0)
        require(scheduleVersionCount >= 0)
        require(scheduleTimeCount >= 0)
        require(occurrenceCount >= 0)
        require(caregiverReportCount >= 0)
    }
}

enum class MedicationDeletionMarkerStage {
    PLATFORM_CLEANUP_PENDING,
    DATABASE_DELETED,
    ABORTED_CHANGED_PREVIEW,
}

data class MedicationDeletionMarker(
    val expectedPreview: MedicationDeletionPreview,
    val scheduleSeriesIds: Set<String>,
    val stage: MedicationDeletionMarkerStage,
    val startedAtEpochMillis: Long,
) {
    init {
        require(
            scheduleSeriesIds.none {
                it.isBlank()
            },
        )
    }
}

interface MedicationDeletionMarkerStore {

    val marker: Flow<MedicationDeletionMarker?>

    suspend fun save(
        marker: MedicationDeletionMarker,
    )

    suspend fun updateStage(
        medicationId: String,
        stage: MedicationDeletionMarkerStage,
    )

    suspend fun clear(
        medicationId: String,
    )
}
