package ir.carepack.data.local

data class ScheduleDefinitionRow(
    val scheduleVersionId: String,
    val scheduleSeriesId: String,
    val medicationId: String,
    val weekdayMask: Int,
    val minuteOfDay: Int,
    val zoneId: String,
    val patternType: String,
    val intervalHours: Int?,
    val anchorMinuteOfDay: Int?,
    val effectiveFromEpochMillis: Long,
    val effectiveUntilEpochMillis: Long?,
    val startEpochDay: Long?,
    val endEpochDay: Long?,
    val medicationNameSnapshot: String,
    val instructionSnapshot: String,
)

data class OpenScheduleVersionRow(
    val scheduleVersionId: String,
    val scheduleSeriesId: String,
    val medicationId: String,
    val versionNumber: Int,
    val weekdayMask: Int,
    val zoneId: String,
    val patternType: String,
    val intervalHours: Int?,
    val anchorMinuteOfDay: Int?,
    val startEpochDay: Long?,
    val endEpochDay: Long?,
)

data class MedicationScheduleOverviewRow(
    val medicationId: String,
    val medicationName: String,
    val medicationInstruction: String,
    val medicationCreatedAtEpochMillis: Long,
    val medicationStoppedAtEpochMillis: Long?,
    val scheduleSeriesId: String?,
    val scheduleVersionId: String?,
    val scheduleVersionNumber: Int?,
    val weekdayMask: Int?,
    val zoneId: String?,
    val patternType: String?,
    val intervalHours: Int?,
    val anchorMinuteOfDay: Int?,
    val effectiveFromEpochMillis: Long?,
    val startEpochDay: Long?,
    val endEpochDay: Long?,
    val minuteOfDay: Int?,
)

data class ReminderTargetRow(
    val scheduleSeriesId: String,
    val occurrenceId: String,
    val localEpochDay: Long,
    val minuteOfDay: Int,
    val zoneIdSnapshot: String,
    val scheduledAtEpochMillis: Long,
    val medicationNameSnapshot: String,
)
