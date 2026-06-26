package ir.carepack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CareRecipientDao {

    @Insert
    suspend fun insert(
        entity: CareRecipientEntity,
    )

    @Query(
        """
        SELECT *
        FROM care_recipients
        ORDER BY createdAtEpochMillis
        LIMIT 1
        """,
    )
    suspend fun getSingleton(): CareRecipientEntity?

    @Query(
        """
        SELECT *
        FROM care_recipients
        ORDER BY createdAtEpochMillis
        LIMIT 1
        """,
    )
    fun observeSingleton(): Flow<CareRecipientEntity?>

    @Query(
        """
        UPDATE care_recipients
        SET displayName = :displayName
        WHERE id = :recipientId
        """,
    )
    suspend fun updateDisplayName(
        recipientId: String,
        displayName: String,
    ): Int

    @Query(
        "SELECT COUNT(*) FROM care_recipients",
    )
    suspend fun count(): Int
}

@Dao
interface MedicationDao {

    @Insert
    suspend fun insert(
        entity: MedicationEntity,
    )

    @Query(
        """
        SELECT *
        FROM medications
        WHERE id = :medicationId
        LIMIT 1
        """,
    )
    suspend fun getById(
        medicationId: String,
    ): MedicationEntity?

    @Query(
        """
        UPDATE medications
        SET
            name = :name,
            instruction = :instruction
        WHERE id = :medicationId
          AND stoppedAtEpochMillis IS NULL
          AND archivedAtEpochMillis IS NULL
        """,
    )
    suspend fun updateText(
        medicationId: String,
        name: String,
        instruction: String,
    ): Int

    @Query(
        """
        UPDATE medications
        SET stoppedAtEpochMillis = :stoppedAtEpochMillis
        WHERE id = :medicationId
          AND stoppedAtEpochMillis IS NULL
          AND archivedAtEpochMillis IS NULL
        """,
    )
    suspend fun markStopped(
        medicationId: String,
        stoppedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE medications
        SET archivedAtEpochMillis = :archivedAtEpochMillis
        WHERE id = :medicationId
          AND stoppedAtEpochMillis IS NOT NULL
          AND archivedAtEpochMillis IS NULL
        """,
    )
    suspend fun markArchived(
        medicationId: String,
        archivedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        SELECT
            medication.id AS medicationId,
            medication.careRecipientId AS careRecipientId,
            medication.name AS medicationName,
            medication.instruction AS medicationInstruction,
            medication.createdAtEpochMillis
                AS medicationCreatedAtEpochMillis,
            medication.stoppedAtEpochMillis
                AS medicationStoppedAtEpochMillis,
            medication.archivedAtEpochMillis
                AS medicationArchivedAtEpochMillis,
            series.id AS scheduleSeriesId,
            version.id AS scheduleVersionId,
            version.versionNumber AS scheduleVersionNumber,
            version.weekdayMask AS weekdayMask,
            version.zoneId AS zoneId,
            version.effectiveFromEpochMillis
                AS effectiveFromEpochMillis,
            version.startDateEpochDay AS startDateEpochDay,
            version.endDateEpochDay AS endDateEpochDay,
            scheduleTime.minuteOfDay AS minuteOfDay
        FROM medications AS medication
        LEFT JOIN schedule_series AS series
            ON series.medicationId = medication.id
           AND series.stoppedAtEpochMillis IS NULL
        LEFT JOIN schedule_versions AS version
            ON version.seriesId = series.id
           AND version.effectiveUntilEpochMillis IS NULL
        LEFT JOIN schedule_times AS scheduleTime
            ON scheduleTime.scheduleVersionId = version.id
        WHERE medication.archivedAtEpochMillis IS NULL
        ORDER BY
            medication.createdAtEpochMillis,
            medication.id,
            scheduleTime.minuteOfDay
        """,
    )
    fun observeNonArchivedScheduleRows():
            Flow<List<MedicationScheduleOverviewRow>>

    @Query(
        """
        SELECT
            medication.id AS medicationId,
            medication.careRecipientId AS careRecipientId,
            medication.name AS medicationName,
            medication.instruction AS medicationInstruction,
            medication.createdAtEpochMillis
                AS medicationCreatedAtEpochMillis,
            medication.stoppedAtEpochMillis
                AS medicationStoppedAtEpochMillis,
            medication.archivedAtEpochMillis
                AS medicationArchivedAtEpochMillis,
            series.id AS scheduleSeriesId,
            version.id AS scheduleVersionId,
            version.versionNumber AS scheduleVersionNumber,
            version.weekdayMask AS weekdayMask,
            version.zoneId AS zoneId,
            version.effectiveFromEpochMillis
                AS effectiveFromEpochMillis,
            version.startDateEpochDay AS startDateEpochDay,
            version.endDateEpochDay AS endDateEpochDay,
            scheduleTime.minuteOfDay AS minuteOfDay
        FROM medications AS medication
        LEFT JOIN schedule_series AS series
            ON series.medicationId = medication.id
           AND series.stoppedAtEpochMillis IS NULL
        LEFT JOIN schedule_versions AS version
            ON version.seriesId = series.id
           AND version.effectiveUntilEpochMillis IS NULL
        LEFT JOIN schedule_times AS scheduleTime
            ON scheduleTime.scheduleVersionId = version.id
        WHERE medication.id = :medicationId
        ORDER BY scheduleTime.minuteOfDay
        """,
    )
    suspend fun getScheduleRowsForMedication(
        medicationId: String,
    ): List<MedicationScheduleOverviewRow>

    @Query(
        "SELECT COUNT(*) FROM medications",
    )
    suspend fun count(): Int
}

@Dao
interface ScheduleDao {

    @Insert
    suspend fun insertSeries(
        entity: ScheduleSeriesEntity,
    )

    @Insert
    suspend fun insertVersion(
        entity: ScheduleVersionEntity,
    )

    @Insert
    suspend fun insertTimes(
        entities: List<ScheduleTimeEntity>,
    )

    @Query(
        """
        SELECT
            version.id AS scheduleVersionId,
            version.seriesId AS scheduleSeriesId,
            version.medicationId AS medicationId,
            version.weekdayMask AS weekdayMask,
            scheduleTime.minuteOfDay AS minuteOfDay,
            version.zoneId AS zoneId,
            version.effectiveFromEpochMillis
                AS effectiveFromEpochMillis,
            version.effectiveUntilEpochMillis
                AS effectiveUntilEpochMillis,
            version.startDateEpochDay AS startDateEpochDay,
            version.endDateEpochDay AS endDateEpochDay,
            version.medicationNameSnapshot
                AS medicationNameSnapshot,
            version.medicationInstructionSnapshot
                AS medicationInstructionSnapshot
        FROM schedule_versions AS version
        INNER JOIN schedule_times AS scheduleTime
            ON scheduleTime.scheduleVersionId = version.id
        WHERE version.id = :scheduleVersionId
        ORDER BY scheduleTime.minuteOfDay
        """,
    )
    suspend fun getDefinitionsForVersion(
        scheduleVersionId: String,
    ): List<ScheduleDefinitionRow>

    @Query(
        """
        SELECT DISTINCT version.id
        FROM schedule_versions AS version
        INNER JOIN medications AS medication
            ON medication.id = version.medicationId
        WHERE medication.archivedAtEpochMillis IS NULL
          AND version.effectiveFromEpochMillis
                < :windowEndExclusiveEpochMillis
          AND (
                version.effectiveUntilEpochMillis IS NULL
                OR version.effectiveUntilEpochMillis
                    > :windowStartEpochMillis
          )
        ORDER BY version.createdAtEpochMillis, version.id
        """,
    )
    suspend fun getGenerationVersionIds(
        windowStartEpochMillis: Long,
        windowEndExclusiveEpochMillis: Long,
    ): List<String>

    @Query(
        """
        SELECT
            version.id AS scheduleVersionId,
            version.seriesId AS scheduleSeriesId,
            version.medicationId AS medicationId,
            version.versionNumber AS versionNumber,
            version.weekdayMask AS weekdayMask,
            version.zoneId AS zoneId,
            version.effectiveFromEpochMillis
                AS effectiveFromEpochMillis,
            version.effectiveUntilEpochMillis
                AS effectiveUntilEpochMillis,
            version.startDateEpochDay AS startDateEpochDay,
            version.endDateEpochDay AS endDateEpochDay,
            version.medicationNameSnapshot
                AS medicationNameSnapshot,
            version.medicationInstructionSnapshot
                AS medicationInstructionSnapshot,
            version.createdAtEpochMillis AS createdAtEpochMillis
        FROM schedule_versions AS version
        INNER JOIN schedule_series AS series
            ON series.id = version.seriesId
        WHERE version.medicationId = :medicationId
          AND version.effectiveUntilEpochMillis IS NULL
          AND series.stoppedAtEpochMillis IS NULL
        ORDER BY version.versionNumber
        """,
    )
    suspend fun getOpenVersionsForMedication(
        medicationId: String,
    ): List<OpenScheduleVersionRow>

    @Query(
        """
        SELECT minuteOfDay
        FROM schedule_times
        WHERE scheduleVersionId = :scheduleVersionId
        ORDER BY minuteOfDay
        """,
    )
    suspend fun getTimesForVersion(
        scheduleVersionId: String,
    ): List<Int>

    @Query(
        """
        UPDATE schedule_versions
        SET effectiveUntilEpochMillis = :effectiveUntilEpochMillis
        WHERE id = :scheduleVersionId
          AND effectiveUntilEpochMillis IS NULL
        """,
    )
    suspend fun closeVersion(
        scheduleVersionId: String,
        effectiveUntilEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE schedule_series
        SET stoppedAtEpochMillis = :stoppedAtEpochMillis
        WHERE medicationId = :medicationId
          AND stoppedAtEpochMillis IS NULL
        """,
    )
    suspend fun stopOpenSeriesForMedication(
        medicationId: String,
        stoppedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM schedule_versions
        WHERE seriesId = :seriesId
          AND effectiveFromEpochMillis <= :instantEpochMillis
          AND (
                effectiveUntilEpochMillis IS NULL
                OR effectiveUntilEpochMillis
                    > :instantEpochMillis
          )
        """,
    )
    suspend fun countVersionsActiveAt(
        seriesId: String,
        instantEpochMillis: Long,
    ): Int

    @Query(
        "SELECT COUNT(*) FROM schedule_series",
    )
    suspend fun countSeries(): Int

    @Query(
        "SELECT COUNT(*) FROM schedule_versions",
    )
    suspend fun countVersions(): Int

    @Query(
        "SELECT COUNT(*) FROM schedule_times",
    )
    suspend fun countTimes(): Int
}

@Dao
interface OccurrenceDao {

    @Insert(
        onConflict = OnConflictStrategy.IGNORE,
    )
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
        SELECT *
        FROM occurrences
        WHERE medicationId = :medicationId
        ORDER BY
            scheduledAtEpochMillis,
            scheduleVersionId,
            id
        """,
    )
    suspend fun getForMedication(
        medicationId: String,
    ): List<OccurrenceEntity>

    @Query(
        """
        SELECT *
        FROM occurrences
        WHERE scheduleVersionId = :scheduleVersionId
        ORDER BY scheduledAtEpochMillis, id
        """,
    )
    suspend fun getForVersion(
        scheduleVersionId: String,
    ): List<OccurrenceEntity>

    @Query(
        """
        UPDATE occurrences
        SET
            lifecycle = 'CANCELLED',
            cancelledAtEpochMillis = :cancelledAtEpochMillis,
            cancellationReason = :cancellationReason
        WHERE scheduleVersionId = :scheduleVersionId
          AND scheduledAtEpochMillis > :nowEpochMillis
          AND lifecycle = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1
              FROM caregiver_reports AS report
              WHERE report.occurrenceId = occurrences.id
          )
        """,
    )
    suspend fun cancelFutureUnreportedForVersion(
        scheduleVersionId: String,
        nowEpochMillis: Long,
        cancelledAtEpochMillis: Long,
        cancellationReason: String,
    ): Int

    @Query(
        """
        UPDATE occurrences
        SET
            lifecycle = 'CANCELLED',
            cancelledAtEpochMillis = :cancelledAtEpochMillis,
            cancellationReason = :cancellationReason
        WHERE medicationId = :medicationId
          AND scheduledAtEpochMillis > :nowEpochMillis
          AND lifecycle = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1
              FROM caregiver_reports AS report
              WHERE report.occurrenceId = occurrences.id
          )
        """,
    )
    suspend fun cancelFutureUnreportedForMedication(
        medicationId: String,
        nowEpochMillis: Long,
        cancelledAtEpochMillis: Long,
        cancellationReason: String,
    ): Int

    @Query(
        """
        SELECT
            occurrence.id AS occurrenceId,
            occurrence.localDateEpochDay
                AS localDateEpochDay,
            occurrence.minuteOfDay AS minuteOfDay,
            occurrence.scheduledAtEpochMillis
                AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot
                AS medicationNameSnapshot,
            occurrence.medicationInstructionSnapshot
                AS medicationInstructionSnapshot,
            occurrence.lifecycle AS lifecycle,
            report.state AS reportState
        FROM occurrences AS occurrence
        LEFT JOIN caregiver_reports AS report
            ON report.occurrenceId = occurrence.id
        WHERE occurrence.localDateEpochDay
                = :localDateEpochDay
          AND occurrence.lifecycle != 'CANCELLED'
        ORDER BY
            occurrence.minuteOfDay,
            occurrence.id
        """,
    )
    fun observeForDate(
        localDateEpochDay: Long,
    ): Flow<List<OccurrenceReadRow>>

    @Query(
        """
        SELECT
            occurrence.id AS occurrenceId,
            occurrence.localDateEpochDay
                AS localDateEpochDay,
            occurrence.minuteOfDay AS minuteOfDay,
            occurrence.scheduledAtEpochMillis
                AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot
                AS medicationNameSnapshot,
            occurrence.medicationInstructionSnapshot
                AS medicationInstructionSnapshot,
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

    @Query(
        """
        SELECT COUNT(*)
        FROM occurrences
        WHERE localDateEpochDay
            BETWEEN :startEpochDay AND :endEpochDay
        """,
    )
    suspend fun countBetweenDates(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Int

    @Query(
        "SELECT COUNT(*) FROM occurrences",
    )
    suspend fun count(): Int
}

@Dao
interface CaregiverReportDao {

    @Insert(
        onConflict = OnConflictStrategy.IGNORE,
    )
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

    @Query(
        "SELECT COUNT(*) FROM caregiver_reports",
    )
    suspend fun count(): Int
}
