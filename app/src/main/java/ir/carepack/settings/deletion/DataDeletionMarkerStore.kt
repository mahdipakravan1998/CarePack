package ir.carepack.settings.deletion

import kotlinx.coroutines.flow.Flow

enum class DataDeletionMarkerStage {
    PLATFORM_CLEANUP_PENDING,
    DOMAIN_DATA_PENDING,
    PREFERENCES_PENDING,
    TEMPORARY_DATA_PENDING,
    FINAL_PLATFORM_VERIFICATION_PENDING,
    COMPLETION_PENDING,
}

data class DataDeletionMarker(
    val version: Int,
    val operationId: String,
    val stage: DataDeletionMarkerStage,
    val startedAtEpochMillis: Long,
    val checksum: String,
) {
    init {
        require(version > 0)
        require(operationId.isNotBlank())
        require(startedAtEpochMillis >= 0L)
        require(checksum.isNotBlank())
    }

    fun withStage(
        newStage: DataDeletionMarkerStage,
    ): DataDeletionMarker = copy(
            stage = newStage,
            checksum = checksumFor(
                version = version,
                operationId = operationId,
                stage = newStage,
                startedAtEpochMillis = startedAtEpochMillis,
            ),
        )

    fun hasValidChecksum(): Boolean = checksum ==
            checksumFor(
                version = version,
                operationId = operationId,
                stage = stage,
                startedAtEpochMillis = startedAtEpochMillis,
            )

    companion object {
        const val CURRENT_VERSION = 1

        fun create(
            operationId: String,
            stage: DataDeletionMarkerStage,
            startedAtEpochMillis: Long,
        ): DataDeletionMarker {
            val version = CURRENT_VERSION

            return DataDeletionMarker(
                version = version,
                operationId = operationId,
                stage = stage,
                startedAtEpochMillis = startedAtEpochMillis,
                checksum = checksumFor(
                    version = version,
                    operationId = operationId,
                    stage = stage,
                    startedAtEpochMillis = startedAtEpochMillis,
                ),
            )
        }

        private fun checksumFor(
            version: Int,
            operationId: String,
            stage: DataDeletionMarkerStage,
            startedAtEpochMillis: Long,
        ): String = DeletionMarkerChecksum.sha256(
                listOf(
                    version.toString(),
                    operationId,
                    stage.name,
                    startedAtEpochMillis.toString(),
                ),
            )
    }
}

sealed interface DataDeletionMarkerReadResult {
    data object Absent : DataDeletionMarkerReadResult

    data class Valid(
        val marker: DataDeletionMarker,
    ) : DataDeletionMarkerReadResult

    data class Corrupted(
        val reason: DeletionMarkerCorruptionReason,
    ) : DataDeletionMarkerReadResult
}

interface DataDeletionMarkerStore {
    val state: Flow<DataDeletionMarkerReadResult>

    suspend fun save(
        marker: DataDeletionMarker,
    )

    suspend fun updateStage(
        operationId: String,
        stage: DataDeletionMarkerStage,
    )

    suspend fun clear(
        operationId: String,
    )
}
