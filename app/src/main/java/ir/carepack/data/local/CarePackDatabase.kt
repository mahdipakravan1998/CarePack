package ir.carepack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CareRecipientEntity::class,
        MedicationEntity::class,
        ScheduleSeriesEntity::class,
        ScheduleVersionEntity::class,
        ScheduleTimeEntity::class,
        OccurrenceEntity::class,
        CaregiverReportEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CarePackDatabase : RoomDatabase() {
    abstract fun careRecipientDao(): CareRecipientDao
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun reportingDao(): ReportingDao

    companion object {
        val invariantCallback =
            object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    installInvariantTriggers(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    installInvariantTriggers(db)
                }
            }

        private fun installInvariantTriggers(
            db: SupportSQLiteDatabase,
        ) {
            db.execSQL(
                "DROP TRIGGER IF EXISTS carepack_schedule_version_insert_guard",
            )
            db.execSQL(
                "DROP TRIGGER IF EXISTS carepack_schedule_version_update_guard",
            )
            INVARIANT_TRIGGERS.forEach(db::execSQL)
        }

        private val INVARIANT_TRIGGERS =
            listOf(
                """
                CREATE TRIGGER IF NOT EXISTS carepack_schedule_time_insert_guard
                BEFORE INSERT ON schedule_times
                WHEN NEW.minuteOfDay < 0 OR NEW.minuteOfDay > 1439
                BEGIN
                    SELECT RAISE(ABORT, 'carepack schedule time invariant');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER IF NOT EXISTS carepack_schedule_time_update_guard
                BEFORE UPDATE OF minuteOfDay ON schedule_times
                WHEN NEW.minuteOfDay < 0 OR NEW.minuteOfDay > 1439
                BEGIN
                    SELECT RAISE(ABORT, 'carepack schedule time invariant');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER IF NOT EXISTS carepack_occurrence_minute_insert_guard
                BEFORE INSERT ON occurrences
                WHEN NEW.minuteOfDay < 0 OR NEW.minuteOfDay > 1439
                BEGIN
                    SELECT RAISE(ABORT, 'carepack occurrence minute invariant');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER IF NOT EXISTS carepack_occurrence_minute_update_guard
                BEFORE UPDATE OF minuteOfDay ON occurrences
                WHEN NEW.minuteOfDay < 0 OR NEW.minuteOfDay > 1439
                BEGIN
                    SELECT RAISE(ABORT, 'carepack occurrence minute invariant');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER IF NOT EXISTS carepack_schedule_version_insert_guard
                BEFORE INSERT ON schedule_versions
                WHEN
                    NEW.weekdayMask <= 0 OR
                    NEW.weekdayMask > 127 OR
                    (NEW.startEpochDay IS NOT NULL AND NEW.endEpochDay IS NOT NULL AND NEW.startEpochDay > NEW.endEpochDay) OR
                    (NEW.effectiveUntilEpochMillis IS NOT NULL AND NEW.effectiveUntilEpochMillis < NEW.effectiveFromEpochMillis) OR
                    NOT (
                        (NEW.patternType = 'FIXED_TIMES' AND NEW.intervalHours IS NULL AND NEW.anchorMinuteOfDay IS NULL) OR
                        (NEW.patternType = 'EVERY_X_HOURS' AND NEW.intervalHours IN (6, 8, 12) AND NEW.anchorMinuteOfDay BETWEEN 0 AND 1439)
                    ) OR
                    (
                        NEW.effectiveUntilEpochMillis IS NULL AND
                        EXISTS (
                            SELECT 1
                            FROM schedule_versions existing
                            WHERE existing.scheduleSeriesId = NEW.scheduleSeriesId
                              AND existing.effectiveUntilEpochMillis IS NULL
                        )
                    )
                BEGIN
                    SELECT RAISE(ABORT, 'carepack schedule version invariant');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER IF NOT EXISTS carepack_schedule_version_update_guard
                BEFORE UPDATE ON schedule_versions
                WHEN
                    NEW.weekdayMask <= 0 OR
                    NEW.weekdayMask > 127 OR
                    (NEW.startEpochDay IS NOT NULL AND NEW.endEpochDay IS NOT NULL AND NEW.startEpochDay > NEW.endEpochDay) OR
                    (NEW.effectiveUntilEpochMillis IS NOT NULL AND NEW.effectiveUntilEpochMillis < NEW.effectiveFromEpochMillis) OR
                    NOT (
                        (NEW.patternType = 'FIXED_TIMES' AND NEW.intervalHours IS NULL AND NEW.anchorMinuteOfDay IS NULL) OR
                        (NEW.patternType = 'EVERY_X_HOURS' AND NEW.intervalHours IN (6, 8, 12) AND NEW.anchorMinuteOfDay BETWEEN 0 AND 1439)
                    ) OR
                    (
                        NEW.effectiveUntilEpochMillis IS NULL AND
                        EXISTS (
                            SELECT 1
                            FROM schedule_versions existing
                            WHERE existing.scheduleSeriesId = NEW.scheduleSeriesId
                              AND existing.effectiveUntilEpochMillis IS NULL
                              AND existing.id != NEW.id
                        )
                    )
                BEGIN
                    SELECT RAISE(ABORT, 'carepack schedule version invariant');
                END
                """.trimIndent(),
            )
    }
}
