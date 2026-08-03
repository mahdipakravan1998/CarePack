package ir.carepack.data.local

data class MedicationDeletionPreviewRow(
    val medicationId: String,
    val medicationName: String,
    val medicationUpdatedAtEpochMillis: Long,
    val scheduleSeriesCount: Int,
    val scheduleVersionCount: Int,
    val scheduleTimeCount: Int,
    val occurrenceCount: Int,
    val caregiverReportCount: Int,
)
