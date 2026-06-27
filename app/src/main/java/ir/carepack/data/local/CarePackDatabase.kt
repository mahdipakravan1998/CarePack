package ir.carepack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

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
    version = 2,
    exportSchema = true,
)
abstract class CarePackDatabase : RoomDatabase() {
    abstract fun careRecipientDao(): CareRecipientDao
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun reportingDao(): ReportingDao
}
