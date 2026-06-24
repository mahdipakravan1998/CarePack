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