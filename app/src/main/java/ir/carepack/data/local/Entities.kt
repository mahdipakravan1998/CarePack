package ir.carepack.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    @PrimaryKey
    val id: String,
    val singletonSlot: Int,
    val displayName: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
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
        Index(value = ["archivedAtEpochMillis"]),
    ],
)
data class MedicationEntity(
    @PrimaryKey
    val id: String,
    val careRecipientId: String,
    val name: String,
    val instructionText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val stoppedAtEpochMillis: Long?,
    val archivedAtEpochMillis: Long?,
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
    @PrimaryKey
    val id: String,
    val medicationId: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "schedule_versions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleSeriesId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["scheduleSeriesId"]),
        Index(value = ["effectiveUntilEpochMillis"]),
        Index(
            value = [
                "scheduleSeriesId",
                "versionNumber",
            ],
            unique = true,
        ),
    ],
)
data class ScheduleVersionEntity(
    @PrimaryKey
    val id: String,
    val scheduleSeriesId: String,
    val versionNumber: Int,
    val weekdayMask: Int,
    val startEpochDay: Long?,
    val endEpochDay: Long?,
    val zoneId: String,
    val patternType: String,
    val intervalHours: Int?,
    val anchorMinuteOfDay: Int?,
    val effectiveFromEpochMillis: Long,
    val effectiveUntilEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val supersededReason: String?,
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
        Index(value = ["scheduleVersionId"]),
        Index(value = ["medicationId"]),
        Index(value = ["scheduledAtEpochMillis"]),
        Index(
            value = [
                "localEpochDay",
                "lifecycle",
            ],
        ),
        Index(
            value = [
                "scheduleVersionId",
                "localEpochDay",
                "minuteOfDay",
            ],
            unique = true,
        ),
    ],
)
data class OccurrenceEntity(
    @PrimaryKey
    val id: String,
    val scheduleVersionId: String,
    val medicationId: String,
    val localEpochDay: Long,
    val minuteOfDay: Int,
    val zoneIdSnapshot: String,
    val scheduledAtEpochMillis: Long,
    val medicationNameSnapshot: String,
    val instructionSnapshot: String,
    val lifecycle: String,
    val cancelledAtEpochMillis: Long?,
    val cancellationReason: String?,
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
    @PrimaryKey
    val occurrenceId: String,
    val state: String,
    val recordedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
