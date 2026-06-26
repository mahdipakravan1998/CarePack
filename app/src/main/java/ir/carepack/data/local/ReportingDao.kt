package ir.carepack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ReportingOccurrenceRow(
    val occurrenceId: String,
    val localDateEpochDay: Long,
    val minuteOfDay: Int,
    val zoneId: String,
    val scheduledAtEpochMillis: Long,
    val medicationNameSnapshot: String,
    val medicationInstructionSnapshot: String,
    val lifecycle: String,
    val cancelledAtEpochMillis: Long?,
    val cancellationReason: String?,
    val reportState: String?,
)

@Dao
interface ReportingDao {

    @Query(
        """
        SELECT COUNT(*)
        FROM medications
        """,
    )
    fun observeMedicationCount(): Flow<Int>

    @Query(
        """
        SELECT
            occurrence.id AS occurrenceId,
            occurrence.localDateEpochDay
                AS localDateEpochDay,
            occurrence.minuteOfDay
                AS minuteOfDay,
            occurrence.zoneId
                AS zoneId,
            occurrence.scheduledAtEpochMillis
                AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot
                AS medicationNameSnapshot,
            occurrence.medicationInstructionSnapshot
                AS medicationInstructionSnapshot,
            occurrence.lifecycle
                AS lifecycle,
            occurrence.cancelledAtEpochMillis
                AS cancelledAtEpochMillis,
            occurrence.cancellationReason
                AS cancellationReason,
            report.state
                AS reportState
        FROM occurrences AS occurrence
        LEFT JOIN caregiver_reports AS report
            ON report.occurrenceId = occurrence.id
        WHERE occurrence.localDateEpochDay
                = :localDateEpochDay
          AND occurrence.lifecycle != 'CANCELLED'
        ORDER BY
            occurrence.scheduledAtEpochMillis,
            occurrence.id
        """,
    )
    fun observeToday(
        localDateEpochDay: Long,
    ): Flow<List<ReportingOccurrenceRow>>

    @Query(
        """
        SELECT
            occurrence.id AS occurrenceId,
            occurrence.localDateEpochDay
                AS localDateEpochDay,
            occurrence.minuteOfDay
                AS minuteOfDay,
            occurrence.zoneId
                AS zoneId,
            occurrence.scheduledAtEpochMillis
                AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot
                AS medicationNameSnapshot,
            occurrence.medicationInstructionSnapshot
                AS medicationInstructionSnapshot,
            occurrence.lifecycle
                AS lifecycle,
            occurrence.cancelledAtEpochMillis
                AS cancelledAtEpochMillis,
            occurrence.cancellationReason
                AS cancellationReason,
            report.state
                AS reportState
        FROM occurrences AS occurrence
        LEFT JOIN caregiver_reports AS report
            ON report.occurrenceId = occurrence.id
        WHERE occurrence.localDateEpochDay
                BETWEEN :startEpochDay
                AND :endEpochDay
        ORDER BY
            occurrence.localDateEpochDay DESC,
            occurrence.scheduledAtEpochMillis,
            occurrence.id
        """,
    )
    fun observeHistory(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<ReportingOccurrenceRow>>

    @Query(
        """
        SELECT
            occurrence.id AS occurrenceId,
            occurrence.localDateEpochDay
                AS localDateEpochDay,
            occurrence.minuteOfDay
                AS minuteOfDay,
            occurrence.zoneId
                AS zoneId,
            occurrence.scheduledAtEpochMillis
                AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot
                AS medicationNameSnapshot,
            occurrence.medicationInstructionSnapshot
                AS medicationInstructionSnapshot,
            occurrence.lifecycle
                AS lifecycle,
            occurrence.cancelledAtEpochMillis
                AS cancelledAtEpochMillis,
            occurrence.cancellationReason
                AS cancellationReason,
            report.state
                AS reportState
        FROM occurrences AS occurrence
        LEFT JOIN caregiver_reports AS report
            ON report.occurrenceId = occurrence.id
        WHERE occurrence.id = :occurrenceId
        LIMIT 1
        """,
    )
    fun observeOccurrence(
        occurrenceId: String,
    ): Flow<ReportingOccurrenceRow?>

    @Query(
        """
        SELECT lifecycle
        FROM occurrences
        WHERE id = :occurrenceId
        LIMIT 1
        """,
    )
    suspend fun getOccurrenceLifecycle(
        occurrenceId: String,
    ): String?

    @Query(
        """
        SELECT *
        FROM caregiver_reports
        WHERE occurrenceId = :occurrenceId
        LIMIT 1
        """,
    )
    suspend fun getReport(
        occurrenceId: String,
    ): CaregiverReportEntity?

    @Insert(
        onConflict = OnConflictStrategy.IGNORE,
    )
    suspend fun insertReportIgnoringConflict(
        entity: CaregiverReportEntity,
    ): Long

    @Query(
        """
        UPDATE caregiver_reports
        SET
            state = :state,
            updatedAtEpochMillis =
                :updatedAtEpochMillis
        WHERE occurrenceId = :occurrenceId
        """,
    )
    suspend fun updateReport(
        occurrenceId: String,
        state: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE caregiver_reports
        SET
            state = :previousState,
            updatedAtEpochMillis =
                :restoredAtEpochMillis
        WHERE occurrenceId = :occurrenceId
          AND state = :expectedCurrentState
          AND updatedAtEpochMillis =
                :expectedCurrentUpdatedAtEpochMillis
        """,
    )
    suspend fun restorePreviousReportIfCurrent(
        occurrenceId: String,
        expectedCurrentState: String,
        expectedCurrentUpdatedAtEpochMillis: Long,
        previousState: String,
        restoredAtEpochMillis: Long,
    ): Int

    @Query(
        """
        DELETE FROM caregiver_reports
        WHERE occurrenceId = :occurrenceId
          AND state = :expectedCurrentState
          AND updatedAtEpochMillis =
                :expectedCurrentUpdatedAtEpochMillis
        """,
    )
    suspend fun deleteReportIfCurrent(
        occurrenceId: String,
        expectedCurrentState: String,
        expectedCurrentUpdatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM caregiver_reports
        WHERE occurrenceId = :occurrenceId
        """,
    )
    suspend fun countReportsForOccurrence(
        occurrenceId: String,
    ): Int
}
