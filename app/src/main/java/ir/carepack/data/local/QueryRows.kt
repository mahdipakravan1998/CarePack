package ir.carepack.data.local

data class ScheduleDefinitionRow(
    val scheduleVersionId: String,
    val scheduleSeriesId: String,
    val medicationId: String,
    val weekdayMask: Int,
    val minuteOfDay: Int,
    val zoneId: String,
    val effectiveFromEpochMillis: Long,
    val effectiveUntilEpochMillis: Long?,
    val startDateEpochDay: Long?,
    val endDateEpochDay: Long?,
    val medicationNameSnapshot: String,
    val medicationInstructionSnapshot: String,
)

data class OpenScheduleVersionRow(
    val scheduleVersionId: String,
    val scheduleSeriesId: String,
    val medicationId: String,
    val versionNumber: Int,
    val weekdayMask: Int,
    val zoneId: String,
    val effectiveFromEpochMillis: Long,
    val effectiveUntilEpochMillis: Long?,
    val startDateEpochDay: Long?,
    val endDateEpochDay: Long?,
    val medicationNameSnapshot: String,
    val medicationInstructionSnapshot: String,
    val createdAtEpochMillis: Long,
)

data class MedicationScheduleOverviewRow(
    val medicationId: String,
    val careRecipientId: String,
    val medicationName: String,
    val medicationInstruction: String,
    val medicationCreatedAtEpochMillis: Long,
    val medicationStoppedAtEpochMillis: Long?,
    val medicationArchivedAtEpochMillis: Long?,
    val scheduleSeriesId: String?,
    val scheduleVersionId: String?,
    val scheduleVersionNumber: Int?,
    val weekdayMask: Int?,
    val zoneId: String?,
    val effectiveFromEpochMillis: Long?,
    val startDateEpochDay: Long?,
    val endDateEpochDay: Long?,
    val minuteOfDay: Int?,
)

data class OccurrenceReadRow(
    val occurrenceId: String,
    val localDateEpochDay: Long,
    val minuteOfDay: Int,
    val scheduledAtEpochMillis: Long,
    val medicationNameSnapshot: String,
    val medicationInstructionSnapshot: String,
    val lifecycle: String,
    val reportState: String?,
)
