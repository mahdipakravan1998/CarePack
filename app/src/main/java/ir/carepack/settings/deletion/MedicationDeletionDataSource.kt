package ir.carepack.settings.deletion

data class MedicationDeletionGraph(
    val preview: MedicationDeletionPreview,
    val scheduleSeriesIds: List<String>,
    val occurrenceIds: List<String>,
) {
    init {
        require(
            scheduleSeriesIds.none {
                it.isBlank()
            },
        )

        require(
            occurrenceIds.none {
                it.isBlank()
            },
        )
    }
}

data class MedicationDeletionCounts(
    val caregiverReportCount: Int,
    val occurrenceCount: Int,
    val scheduleTimeCount: Int,
    val scheduleVersionCount: Int,
    val scheduleSeriesCount: Int,
    val medicationCount: Int,
) {
    init {
        require(caregiverReportCount >= 0)
        require(occurrenceCount >= 0)
        require(scheduleTimeCount >= 0)
        require(scheduleVersionCount >= 0)
        require(scheduleSeriesCount >= 0)
        require(medicationCount >= 0)
    }
}

sealed interface MedicationGraphDeletionResult {

    data class Deleted(
        val counts: MedicationDeletionCounts,
    ) : MedicationGraphDeletionResult

    data object NotFound :
        MedicationGraphDeletionResult

    data class ChangedSincePreview(
        val latestPreview:
        MedicationDeletionPreview,
    ) : MedicationGraphDeletionResult
}

interface MedicationDeletionDataSource {

    suspend fun loadPreview(
        medicationId: String,
    ): MedicationDeletionPreview?

    suspend fun loadGraph(
        medicationId: String,
    ): MedicationDeletionGraph?

    suspend fun deleteGraph(
        medicationId: String,
        expectedPreview:
        MedicationDeletionPreview?,
    ): MedicationGraphDeletionResult
}
