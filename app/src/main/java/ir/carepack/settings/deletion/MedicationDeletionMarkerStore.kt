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
    DATABASE_DELETE_PENDING,
    DATABASE_DELETED,
    FINAL_RECONCILIATION_PENDING,
}

enum class DeletionMarkerCorruptionReason {
    STORAGE_READ_FAILURE,
    PARTIAL_MARKER,
    UNKNOWN_VERSION,
    INVALID_STAGE,
    INVALID_VALUE,
    CHECKSUM_MISMATCH,
}

data class MedicationDeletionMarker(
    val version: Int,
    val expectedPreview: MedicationDeletionPreview,
    val scheduleSeriesIds: Set<String>,
    val occurrenceIds: Set<String>,
    val stage: MedicationDeletionMarkerStage,
    val startedAtEpochMillis: Long,
    val checksum: String,
) {
    init {
        require(version > 0)
        require(scheduleSeriesIds.none(String::isBlank))
        require(occurrenceIds.none(String::isBlank))
        require(startedAtEpochMillis >= 0L)
        require(checksum.isNotBlank())
    }

    fun withStage(
        newStage: MedicationDeletionMarkerStage,
    ): MedicationDeletionMarker = copy(
            stage = newStage,
            checksum = checksumFor(
                version = version,
                expectedPreview = expectedPreview,
                scheduleSeriesIds = scheduleSeriesIds,
                occurrenceIds = occurrenceIds,
                stage = newStage,
                startedAtEpochMillis = startedAtEpochMillis,
            ),
        )

    fun hasValidChecksum(): Boolean = checksum ==
            checksumFor(
                version = version,
                expectedPreview = expectedPreview,
                scheduleSeriesIds = scheduleSeriesIds,
                occurrenceIds = occurrenceIds,
                stage = stage,
                startedAtEpochMillis = startedAtEpochMillis,
            )

    companion object {
        const val CURRENT_VERSION = 1

        fun create(
            expectedPreview: MedicationDeletionPreview,
            scheduleSeriesIds: Set<String>,
            occurrenceIds: Set<String>,
            stage: MedicationDeletionMarkerStage,
            startedAtEpochMillis: Long,
        ): MedicationDeletionMarker {
            val version = CURRENT_VERSION

            return MedicationDeletionMarker(
                version = version,
                expectedPreview = expectedPreview,
                scheduleSeriesIds = scheduleSeriesIds,
                occurrenceIds = occurrenceIds,
                stage = stage,
                startedAtEpochMillis = startedAtEpochMillis,
                checksum = checksumFor(
                    version = version,
                    expectedPreview = expectedPreview,
                    scheduleSeriesIds = scheduleSeriesIds,
                    occurrenceIds = occurrenceIds,
                    stage = stage,
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
            )
        }

        private fun checksumFor(
            version: Int,
            expectedPreview: MedicationDeletionPreview,
            scheduleSeriesIds: Set<String>,
            occurrenceIds: Set<String>,
            stage: MedicationDeletionMarkerStage,
            startedAtEpochMillis: Long,
        ): String = DeletionMarkerChecksum.sha256(
                listOf(
                    version.toString(),
                    expectedPreview.medicationId,
                    expectedPreview.medicationName,
                    expectedPreview.medicationUpdatedAtEpochMillis.toString(),
                    expectedPreview.scheduleSeriesCount.toString(),
                    expectedPreview.scheduleVersionCount.toString(),
                    expectedPreview.scheduleTimeCount.toString(),
                    expectedPreview.occurrenceCount.toString(),
                    expectedPreview.caregiverReportCount.toString(),
                    scheduleSeriesIds.sorted().joinToString("\u001e"),
                    occurrenceIds.sorted().joinToString("\u001e"),
                    stage.name,
                    startedAtEpochMillis.toString(),
                ),
            )
    }
}

sealed interface MedicationDeletionMarkerReadResult {
    data object Absent : MedicationDeletionMarkerReadResult

    data class Valid(
        val marker: MedicationDeletionMarker,
    ) : MedicationDeletionMarkerReadResult

    data class Corrupted(
        val reason: DeletionMarkerCorruptionReason,
    ) : MedicationDeletionMarkerReadResult
}

interface MedicationDeletionMarkerStore {
    val state: Flow<MedicationDeletionMarkerReadResult>

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
