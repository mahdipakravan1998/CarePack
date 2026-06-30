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
        SET
            displayName = :displayName,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :recipientId
        """,
    )
    suspend fun updateDisplayName(
        recipientId: String,
        displayName: String,
        updatedAtEpochMillis: Long,
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
            instructionText = :instructionText,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :medicationId
          AND stoppedAtEpochMillis IS NULL
          AND archivedAtEpochMillis IS NULL
        """,
    )
    suspend fun updateText(
        medicationId: String,
        name: String,
        instructionText: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE medications
        SET
            stoppedAtEpochMillis = :stoppedAtEpochMillis,
            updatedAtEpochMillis = :stoppedAtEpochMillis
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
        SET
            archivedAtEpochMillis = :archivedAtEpochMillis,
            updatedAtEpochMillis = :archivedAtEpochMillis
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
            medication.name AS medicationName,
            medication.instructionText AS medicationInstruction,
            medication.createdAtEpochMillis
                AS medicationCreatedAtEpochMillis,
            medication.stoppedAtEpochMillis
                AS medicationStoppedAtEpochMillis,
            series.id AS scheduleSeriesId,
            version.id AS scheduleVersionId,
            version.versionNumber AS scheduleVersionNumber,
            version.weekdayMask AS weekdayMask,
            version.zoneId AS zoneId,
            version.effectiveFromEpochMillis
                AS effectiveFromEpochMillis,
            version.startEpochDay AS startEpochDay,
            version.endEpochDay AS endEpochDay,
            scheduleTime.minuteOfDay AS minuteOfDay
        FROM medications AS medication
        LEFT JOIN schedule_series AS series
            ON series.medicationId = medication.id
        LEFT JOIN schedule_versions AS version
            ON version.scheduleSeriesId = series.id
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
            medication.name AS medicationName,
            medication.instructionText AS medicationInstruction,
            medication.createdAtEpochMillis
                AS medicationCreatedAtEpochMillis,
            medication.stoppedAtEpochMillis
                AS medicationStoppedAtEpochMillis,
            series.id AS scheduleSeriesId,
            version.id AS scheduleVersionId,
            version.versionNumber AS scheduleVersionNumber,
            version.weekdayMask AS weekdayMask,
            version.zoneId AS zoneId,
            version.effectiveFromEpochMillis
                AS effectiveFromEpochMillis,
            version.startEpochDay AS startEpochDay,
            version.endEpochDay AS endEpochDay,
            scheduleTime.minuteOfDay AS minuteOfDay
        FROM medications AS medication
        LEFT JOIN schedule_series AS series
            ON series.medicationId = medication.id
        LEFT JOIN schedule_versions AS version
            ON version.scheduleSeriesId = series.id
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
            version.scheduleSeriesId AS scheduleSeriesId,
            series.medicationId AS medicationId,
            version.weekdayMask AS weekdayMask,
            scheduleTime.minuteOfDay AS minuteOfDay,
            version.zoneId AS zoneId,
            version.effectiveFromEpochMillis
                AS effectiveFromEpochMillis,
            version.effectiveUntilEpochMillis
                AS effectiveUntilEpochMillis,
            version.startEpochDay AS startEpochDay,
            version.endEpochDay AS endEpochDay,
            medication.name AS medicationNameSnapshot,
            medication.instructionText AS instructionSnapshot
        FROM schedule_versions AS version
        INNER JOIN schedule_series AS series
            ON series.id = version.scheduleSeriesId
        INNER JOIN medications AS medication
            ON medication.id = series.medicationId
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
        INNER JOIN schedule_series AS series
            ON series.id = version.scheduleSeriesId
        INNER JOIN medications AS medication
            ON medication.id = series.medicationId
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
            version.scheduleSeriesId AS scheduleSeriesId,
            series.medicationId AS medicationId,
            version.versionNumber AS versionNumber,
            version.weekdayMask AS weekdayMask,
            version.zoneId AS zoneId,
            version.startEpochDay AS startEpochDay,
            version.endEpochDay AS endEpochDay
        FROM schedule_versions AS version
        INNER JOIN schedule_series AS series
            ON series.id = version.scheduleSeriesId
        INNER JOIN medications AS medication
            ON medication.id = series.medicationId
        WHERE series.medicationId = :medicationId
          AND version.effectiveUntilEpochMillis IS NULL
          AND medication.stoppedAtEpochMillis IS NULL
          AND medication.archivedAtEpochMillis IS NULL
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
        SET
            effectiveUntilEpochMillis = :effectiveUntilEpochMillis,
            supersededReason = :supersededReason
        WHERE id = :scheduleVersionId
          AND effectiveUntilEpochMillis IS NULL
        """,
    )
    suspend fun closeVersion(
        scheduleVersionId: String,
        effectiveUntilEpochMillis: Long,
        supersededReason: String,
    ): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM schedule_versions
        WHERE scheduleSeriesId = :scheduleSeriesId
          AND effectiveFromEpochMillis <= :instantEpochMillis
          AND (
                effectiveUntilEpochMillis IS NULL
                OR effectiveUntilEpochMillis
                    > :instantEpochMillis
          )
        """,
    )
    suspend fun countVersionsActiveAt(
        scheduleSeriesId: String,
        instantEpochMillis: Long,
    ): Int

    @Query(
        """
        SELECT id
        FROM schedule_series
        ORDER BY createdAtEpochMillis, id
        """,
    )
    suspend fun getAllSeriesIds():
            List<String>

    @Query(
        """
        SELECT COUNT(DISTINCT series.id)
        FROM schedule_series AS series
        INNER JOIN medications AS medication
            ON medication.id = series.medicationId
        INNER JOIN schedule_versions AS version
            ON version.scheduleSeriesId = series.id
        WHERE medication.stoppedAtEpochMillis IS NULL
          AND medication.archivedAtEpochMillis IS NULL
          AND version.effectiveUntilEpochMillis IS NULL
        """,
    )
    suspend fun countActiveSeries(): Int

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
          AND localEpochDay = :localEpochDay
          AND minuteOfDay = :minuteOfDay
        LIMIT 1
        """,
    )
    suspend fun getByLogicalKey(
        scheduleVersionId: String,
        localEpochDay: Long,
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
        SELECT
            version.scheduleSeriesId AS scheduleSeriesId,
            occurrence.id AS occurrenceId,
            occurrence.localEpochDay AS localEpochDay,
            occurrence.minuteOfDay AS minuteOfDay,
            occurrence.zoneIdSnapshot AS zoneIdSnapshot,
            occurrence.scheduledAtEpochMillis
                AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot
                AS medicationNameSnapshot
        FROM occurrences AS occurrence
        INNER JOIN schedule_versions AS version
            ON version.id = occurrence.scheduleVersionId
        INNER JOIN schedule_series AS series
            ON series.id = version.scheduleSeriesId
        INNER JOIN medications AS medication
            ON medication.id = occurrence.medicationId
        WHERE occurrence.lifecycle = 'ACTIVE'
          AND occurrence.scheduledAtEpochMillis > :nowEpochMillis
          AND medication.stoppedAtEpochMillis IS NULL
          AND medication.archivedAtEpochMillis IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM caregiver_reports AS report
              WHERE report.occurrenceId = occurrence.id
          )
          AND NOT EXISTS (
              SELECT 1
              FROM occurrences AS earlierOccurrence
              INNER JOIN schedule_versions AS earlierVersion
                  ON earlierVersion.id = earlierOccurrence.scheduleVersionId
              WHERE earlierVersion.scheduleSeriesId =
                    version.scheduleSeriesId
                AND earlierOccurrence.lifecycle = 'ACTIVE'
                AND earlierOccurrence.scheduledAtEpochMillis >
                    :nowEpochMillis
                AND NOT EXISTS (
                    SELECT 1
                    FROM caregiver_reports AS earlierReport
                    WHERE earlierReport.occurrenceId =
                        earlierOccurrence.id
                )
                AND (
                    earlierOccurrence.scheduledAtEpochMillis <
                        occurrence.scheduledAtEpochMillis
                    OR (
                        earlierOccurrence.scheduledAtEpochMillis =
                            occurrence.scheduledAtEpochMillis
                        AND earlierOccurrence.id <
                            occurrence.id
                    )
                )
          )
        ORDER BY
            version.scheduleSeriesId,
            occurrence.scheduledAtEpochMillis,
            occurrence.id
        """,
    )
    suspend fun getNextReminderTargets(
        nowEpochMillis: Long,
    ): List<ReminderTargetRow>

    @Query(
        """
        SELECT
            version.scheduleSeriesId AS scheduleSeriesId,
            occurrence.id AS occurrenceId,
            occurrence.localEpochDay AS localEpochDay,
            occurrence.minuteOfDay AS minuteOfDay,
            occurrence.zoneIdSnapshot AS zoneIdSnapshot,
            occurrence.scheduledAtEpochMillis
                AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot
                AS medicationNameSnapshot
        FROM occurrences AS occurrence
        INNER JOIN schedule_versions AS version
            ON version.id = occurrence.scheduleVersionId
        INNER JOIN schedule_series AS series
            ON series.id = version.scheduleSeriesId
        INNER JOIN medications AS medication
            ON medication.id = occurrence.medicationId
        WHERE occurrence.id = :occurrenceId
          AND occurrence.lifecycle = 'ACTIVE'
          AND medication.stoppedAtEpochMillis IS NULL
          AND medication.archivedAtEpochMillis IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM caregiver_reports AS report
              WHERE report.occurrenceId = occurrence.id
          )
        LIMIT 1
        """,
    )
    suspend fun getEligibleReminderOccurrence(
        occurrenceId: String,
    ): ReminderTargetRow?

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
        SELECT COUNT(*)
        FROM occurrences
        WHERE localEpochDay
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
