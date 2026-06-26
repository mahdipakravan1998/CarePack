package ir.carepack.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {

            override fun migrate(
                db: SupportSQLiteDatabase,
            ) {
                db.execSQL(
                    """
                    ALTER TABLE medications
                    ADD COLUMN stoppedAtEpochMillis INTEGER
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    ALTER TABLE medications
                    ADD COLUMN archivedAtEpochMillis INTEGER
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    ALTER TABLE occurrences
                    ADD COLUMN cancelledAtEpochMillis INTEGER
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    ALTER TABLE occurrences
                    ADD COLUMN cancellationReason TEXT
                    """.trimIndent(),
                )
            }
        }
}
