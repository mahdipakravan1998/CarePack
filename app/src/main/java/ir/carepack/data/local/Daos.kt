package ir.carepack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CareRecipientDao {
    @Insert
    suspend fun insert(entity: CareRecipientEntity)

    @Query(
        """
        SELECT *
        FROM care_recipients
        ORDER BY createdAtEpochMillis
        LIMIT 1
        """,
    )
    suspend fun getSingleton(): CareRecipientEntity?

    @Query("SELECT COUNT(*) FROM care_recipients")
    suspend fun count(): Int
}

@Dao
interface MedicationDao {
    @Insert
    suspend fun insert(entity: MedicationEntity)

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun count(): Int
}

@Dao
interface ScheduleDao {
    @Insert
    suspend fun insertSeries(entity: ScheduleSeriesEntity)

    @Insert
    suspend fun insertVersion(entity: ScheduleVersionEntity)

    @Insert
    suspend fun insertTime(entity: ScheduleTimeEntity)

    @Query(
        """
        SELECT
            version.id AS scheduleVersionId,
            version.seriesId AS scheduleSeriesId,
            version.medicationId AS medicationId,
            version.weekdayMask AS weekdayMask,
            time.minuteOfDay AS minuteOfDay,
            version.zoneId AS zoneId,
            version.effectiveFromEpochMillis AS effectiveFromEpochMillis,
            version.effectiveUntilEpochMillis AS effectiveUntilEpochMillis,
            version.startDateEpochDay AS startDateEpochDay,
            version.endDateEpochDay AS endDateEpochDay,
            version.medicationNameSnapshot AS medicationNameSnapshot,
            version.medicationInstructionSnapshot AS medicationInstructionSnapshot
        FROM schedule_versions AS version
        INNER JOIN schedule_times AS time
            ON time.scheduleVersionId = version.id
        WHERE version.id = :scheduleVersionId
        ORDER BY time.minuteOfDay
        """,
    )
    suspend fun getDefinitionsForVersion(
        scheduleVersionId: String,
    ): List<ScheduleDefinitionRow>

    @Query(
        """
        SELECT id
        FROM schedule_versions
        WHERE effectiveFromEpochMillis <= :nowEpochMillis
          AND (
              effectiveUntilEpochMillis IS NULL
              OR effectiveUntilEpochMillis > :nowEpochMillis
          )
        ORDER BY createdAtEpochMillis
        """,
    )
    suspend fun getEffectiveVersionIds(
        nowEpochMillis: Long,
    ): List<String>

    @Query("SELECT COUNT(*) FROM schedule_series")
    suspend fun countSeries(): Int

    @Query("SELECT COUNT(*) FROM schedule_versions")
    suspend fun countVersions(): Int

    @Query("SELECT COUNT(*) FROM schedule_times")
    suspend fun countTimes(): Int
}

@Dao
interface OccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringLogicalConflict(
        entity: OccurrenceEntity,
    ): Long

    @Query(
        """
        SELECT *
        FROM occurrences
        WHERE scheduleVersionId = :scheduleVersionId
          AND localDateEpochDay = :localDateEpochDay
          AND minuteOfDay = :minuteOfDay
        LIMIT 1
        """,
    )
    suspend fun getByLogicalKey(
        scheduleVersionId: String,
        localDateEpochDay: Long,
        minuteOfDay: Int,
    ): OccurrenceEntity?

    @Query(
        """
        SELECT *
        FROM occurrences
        WHERE id = :occurrenceId
        LIMIT 1
        """,
    )
    suspend fun getById(
        occurrenceId: String,
    ): OccurrenceEntity?

    @Query(
        """
        SELECT
            occurrence.id AS occurrenceId,
            occurrence.localDateEpochDay AS localDateEpochDay,
            occurrence.minuteOfDay AS minuteOfDay,
            occurrence.scheduledAtEpochMillis AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot AS medicationNameSnapshot,
            occurrence.medicationInstructionSnapshot AS medicationInstructionSnapshot,
            occurrence.lifecycle AS lifecycle,
            report.state AS reportState
        FROM occurrences AS occurrence
        LEFT JOIN caregiver_reports AS report
            ON report.occurrenceId = occurrence.id
        WHERE occurrence.localDateEpochDay = :localDateEpochDay
          AND occurrence.lifecycle != 'CANCELLED'
        ORDER BY occurrence.minuteOfDay, occurrence.id
        """,
    )
    fun observeForDate(
        localDateEpochDay: Long,
    ): Flow<List<OccurrenceReadRow>>

    @Query(
        """
        SELECT
            occurrence.id AS occurrenceId,
            occurrence.localDateEpochDay AS localDateEpochDay,
            occurrence.minuteOfDay AS minuteOfDay,
            occurrence.scheduledAtEpochMillis AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot AS medicationNameSnapshot,
            occurrence.medicationInstructionSnapshot AS medicationInstructionSnapshot,
            occurrence.lifecycle AS lifecycle,
            report.state AS reportState
        FROM occurrences AS occurrence
        LEFT JOIN caregiver_reports AS report
            ON report.occurrenceId = occurrence.id
        WHERE occurrence.id = :occurrenceId
        LIMIT 1
        """,
    )
    fun observeById(
        occurrenceId: String,
    ): Flow<OccurrenceReadRow?>

    @Query("SELECT COUNT(*) FROM occurrences")
    suspend fun count(): Int
}

@Dao
interface CaregiverReportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflict(
        entity: CaregiverReportEntity,
    ): Long

    @Query(
        """
        SELECT *
        FROM caregiver_reports
        WHERE occurrenceId = :occurrenceId
        LIMIT 1
        """,
    )
    suspend fun getByOccurrenceId(
        occurrenceId: String,
    ): CaregiverReportEntity?

    @Query("SELECT COUNT(*) FROM caregiver_reports")
    suspend fun count(): Int
}
