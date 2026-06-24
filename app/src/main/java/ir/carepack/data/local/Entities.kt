package ir.carepack.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "care_recipients",
    indices = [
        Index(
            value = ["singletonSlot"],
            unique = true,
        ),
    ],
)
data class CareRecipientEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val singletonSlot: Int,
    val displayName: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = CareRecipientEntity::class,
            parentColumns = ["id"],
            childColumns = ["careRecipientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["careRecipientId"]),
    ],
)
data class MedicationEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val careRecipientId: String,
    val name: String,
    val instruction: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "schedule_series",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["medicationId"]),
    ],
)
data class ScheduleSeriesEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val medicationId: String,
    val createdAtEpochMillis: Long,
    val stoppedAtEpochMillis: Long?,
)

@Entity(
    tableName = "schedule_versions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["medicationId"]),
        Index(
            value = ["seriesId", "versionNumber"],
            unique = true,
        ),
    ],
)
data class ScheduleVersionEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val seriesId: String,
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

@Entity(
    tableName = "schedule_times",
    primaryKeys = [
        "scheduleVersionId",
        "minuteOfDay",
    ],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["scheduleVersionId"]),
    ],
)
data class ScheduleTimeEntity(
    val scheduleVersionId: String,
    val minuteOfDay: Int,
)

@Entity(
    tableName = "occurrences",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleSeriesId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ScheduleVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["scheduleSeriesId"]),
        Index(value = ["scheduleVersionId"]),
        Index(value = ["medicationId"]),
        Index(value = ["localDateEpochDay"]),
        Index(
            value = [
                "scheduleVersionId",
                "localDateEpochDay",
                "minuteOfDay",
            ],
            unique = true,
        ),
    ],
)
data class OccurrenceEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val scheduleSeriesId: String,
    val scheduleVersionId: String,
    val medicationId: String,
    val localDateEpochDay: Long,
    val minuteOfDay: Int,
    val zoneId: String,
    val scheduledAtEpochMillis: Long,
    val medicationNameSnapshot: String,
    val medicationInstructionSnapshot: String,
    val lifecycle: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "caregiver_reports",
    foreignKeys = [
        ForeignKey(
            entity = OccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class CaregiverReportEntity(
    @androidx.room.PrimaryKey
    val occurrenceId: String,
    val state: String,
    val recordedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)