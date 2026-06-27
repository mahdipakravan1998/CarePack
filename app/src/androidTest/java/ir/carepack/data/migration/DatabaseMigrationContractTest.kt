package ir.carepack.data.migration

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.DatabaseMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationContractTest {

    private lateinit var context: Context
    private var roomDatabase: CarePackDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        roomDatabase?.close()
        roomDatabase = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migration1To2_preservesGraphAddsNullableColumnsAndKeepsConstraints() {
        VersionOneDatabaseFixture(
            context = context,
            databaseName = DATABASE_NAME,
        ).create(insertData = ::insertVersionOneGraph)

        val database = Room.databaseBuilder(
            context,
            CarePackDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        roomDatabase = database
        val sqlite = database.openHelper.writableDatabase

        assertEquals(2, readInt(sqlite, "PRAGMA user_version"))
        assertVersionTwoColumns(sqlite)
        assertVersionOneGraph(sqlite)
        assertVersionTwoConstraints(sqlite)
    }

    private fun assertVersionTwoColumns(database: SupportSQLiteDatabase) {
        assertTrue(columnExists(database, "medications", "stoppedAtEpochMillis"))
        assertTrue(columnExists(database, "medications", "archivedAtEpochMillis"))
        assertTrue(columnExists(database, "occurrences", "cancelledAtEpochMillis"))
        assertTrue(columnExists(database, "occurrences", "cancellationReason"))
    }

    private fun assertVersionOneGraph(database: SupportSQLiteDatabase) {
        assertEquals(1, readInt(database, "SELECT COUNT(*) FROM care_recipients"))
        assertEquals(1, readInt(database, "SELECT COUNT(*) FROM medications"))
        assertEquals(1, readInt(database, "SELECT COUNT(*) FROM schedule_series"))
        assertEquals(1, readInt(database, "SELECT COUNT(*) FROM schedule_versions"))
        assertEquals(1, readInt(database, "SELECT COUNT(*) FROM schedule_times"))
        assertEquals(1, readInt(database, "SELECT COUNT(*) FROM occurrences"))
        assertEquals(1, readInt(database, "SELECT COUNT(*) FROM caregiver_reports"))

        assertEquals(
            RECIPIENT_ID,
            readText(database, "SELECT id FROM care_recipients LIMIT 1"),
        )
        assertEquals(
            "فرد نسخه یک",
            readText(
                database,
                "SELECT displayName FROM care_recipients WHERE id = '$RECIPIENT_ID'",
            ),
        )
        assertEquals(
            CREATED_AT,
            readLong(
                database,
                "SELECT createdAtEpochMillis FROM care_recipients WHERE id = '$RECIPIENT_ID'",
            ),
        )

        assertEquals(
            MEDICATION_ID,
            readText(database, "SELECT id FROM medications LIMIT 1"),
        )
        assertEquals(
            RECIPIENT_ID,
            readText(
                database,
                "SELECT careRecipientId FROM medications WHERE id = '$MEDICATION_ID'",
            ),
        )
        assertEquals(
            "داروی نسخه یک",
            readText(database, "SELECT name FROM medications WHERE id = '$MEDICATION_ID'"),
        )
        assertEquals(
            "دستور نسخه یک",
            readText(
                database,
                "SELECT instruction FROM medications WHERE id = '$MEDICATION_ID'",
            ),
        )
        assertEquals(
            CREATED_AT,
            readLong(
                database,
                "SELECT createdAtEpochMillis FROM medications WHERE id = '$MEDICATION_ID'",
            ),
        )
        assertNull(
            readNullableLong(
                database,
                "SELECT stoppedAtEpochMillis FROM medications WHERE id = '$MEDICATION_ID'",
            ),
        )
        assertNull(
            readNullableLong(
                database,
                "SELECT archivedAtEpochMillis FROM medications WHERE id = '$MEDICATION_ID'",
            ),
        )

        assertEquals(
            SCHEDULE_SERIES_ID,
            readText(database, "SELECT id FROM schedule_series LIMIT 1"),
        )
        assertEquals(
            MEDICATION_ID,
            readText(
                database,
                "SELECT medicationId FROM schedule_series WHERE id = '$SCHEDULE_SERIES_ID'",
            ),
        )
        assertEquals(
            CREATED_AT,
            readLong(
                database,
                "SELECT createdAtEpochMillis FROM schedule_series " +
                        "WHERE id = '$SCHEDULE_SERIES_ID'",
            ),
        )
        assertNull(
            readNullableLong(
                database,
                "SELECT stoppedAtEpochMillis FROM schedule_series " +
                        "WHERE id = '$SCHEDULE_SERIES_ID'",
            ),
        )

        assertEquals(
            SCHEDULE_VERSION_ID,
            readText(database, "SELECT id FROM schedule_versions LIMIT 1"),
        )
        assertEquals(
            SCHEDULE_SERIES_ID,
            readText(
                database,
                "SELECT seriesId FROM schedule_versions WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertEquals(
            MEDICATION_ID,
            readText(
                database,
                "SELECT medicationId FROM schedule_versions WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertEquals(
            1,
            readInt(
                database,
                "SELECT versionNumber FROM schedule_versions WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertEquals(
            WEEKDAY_MASK,
            readInt(
                database,
                "SELECT weekdayMask FROM schedule_versions WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertEquals(
            "Asia/Tehran",
            readText(
                database,
                "SELECT zoneId FROM schedule_versions WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertEquals(
            EFFECTIVE_FROM,
            readLong(
                database,
                "SELECT effectiveFromEpochMillis FROM schedule_versions " +
                        "WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertNull(
            readNullableLong(
                database,
                "SELECT effectiveUntilEpochMillis FROM schedule_versions " +
                        "WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertNull(
            readNullableLong(
                database,
                "SELECT startDateEpochDay FROM schedule_versions " +
                        "WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertNull(
            readNullableLong(
                database,
                "SELECT endDateEpochDay FROM schedule_versions " +
                        "WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertEquals(
            "داروی نسخه یک",
            readText(
                database,
                "SELECT medicationNameSnapshot FROM schedule_versions " +
                        "WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertEquals(
            "دستور نسخه یک",
            readText(
                database,
                "SELECT medicationInstructionSnapshot FROM schedule_versions " +
                        "WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertEquals(
            CREATED_AT,
            readLong(
                database,
                "SELECT createdAtEpochMillis FROM schedule_versions " +
                        "WHERE id = '$SCHEDULE_VERSION_ID'",
            ),
        )
        assertEquals(
            MINUTE_OF_DAY,
            readInt(
                database,
                "SELECT minuteOfDay FROM schedule_times " +
                        "WHERE scheduleVersionId = '$SCHEDULE_VERSION_ID'",
            ),
        )

        assertEquals(
            OCCURRENCE_ID,
            readText(database, "SELECT id FROM occurrences LIMIT 1"),
        )
        assertEquals(
            SCHEDULE_SERIES_ID,
            readText(
                database,
                "SELECT scheduleSeriesId FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            SCHEDULE_VERSION_ID,
            readText(
                database,
                "SELECT scheduleVersionId FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            MEDICATION_ID,
            readText(
                database,
                "SELECT medicationId FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            LOCAL_DATE_EPOCH_DAY,
            readLong(
                database,
                "SELECT localDateEpochDay FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            MINUTE_OF_DAY,
            readInt(
                database,
                "SELECT minuteOfDay FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            "Asia/Tehran",
            readText(database, "SELECT zoneId FROM occurrences WHERE id = '$OCCURRENCE_ID'"),
        )
        assertEquals(
            SCHEDULED_AT,
            readLong(
                database,
                "SELECT scheduledAtEpochMillis FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            "داروی نسخه یک",
            readText(
                database,
                "SELECT medicationNameSnapshot FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            "دستور نسخه یک",
            readText(
                database,
                "SELECT medicationInstructionSnapshot FROM occurrences " +
                        "WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            OCCURRENCE_LIFECYCLE,
            readText(
                database,
                "SELECT lifecycle FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            CREATED_AT,
            readLong(
                database,
                "SELECT createdAtEpochMillis FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertNull(
            readNullableLong(
                database,
                "SELECT cancelledAtEpochMillis FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )
        assertNull(
            readNullableText(
                database,
                "SELECT cancellationReason FROM occurrences WHERE id = '$OCCURRENCE_ID'",
            ),
        )

        assertEquals(
            "GIVEN",
            readText(
                database,
                "SELECT state FROM caregiver_reports WHERE occurrenceId = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            RECORDED_AT,
            readLong(
                database,
                "SELECT recordedAtEpochMillis FROM caregiver_reports " +
                        "WHERE occurrenceId = '$OCCURRENCE_ID'",
            ),
        )
        assertEquals(
            RECORDED_AT,
            readLong(
                database,
                "SELECT updatedAtEpochMillis FROM caregiver_reports " +
                        "WHERE occurrenceId = '$OCCURRENCE_ID'",
            ),
        )
    }

    private fun assertVersionTwoConstraints(database: SupportSQLiteDatabase) {
        assertConstraintRejected {
            database.execSQL(
                """
                INSERT INTO care_recipients (
                    id,
                    singletonSlot,
                    displayName,
                    createdAtEpochMillis
                ) VALUES (
                    'recipient-duplicate',
                    1,
                    'تکراری',
                    2
                )
                """.trimIndent(),
            )
        }

        assertConstraintRejected {
            database.execSQL(
                """
                INSERT INTO schedule_versions (
                    id,
                    seriesId,
                    medicationId,
                    versionNumber,
                    weekdayMask,
                    zoneId,
                    effectiveFromEpochMillis,
                    effectiveUntilEpochMillis,
                    startDateEpochDay,
                    endDateEpochDay,
                    medicationNameSnapshot,
                    medicationInstructionSnapshot,
                    createdAtEpochMillis
                ) VALUES (
                    'version-duplicate',
                    '$SCHEDULE_SERIES_ID',
                    '$MEDICATION_ID',
                    1,
                    $WEEKDAY_MASK,
                    'Asia/Tehran',
                    $EFFECTIVE_FROM,
                    NULL,
                    NULL,
                    NULL,
                    'داروی نسخه یک',
                    'دستور نسخه یک',
                    $CREATED_AT
                )
                """.trimIndent(),
            )
        }

        assertConstraintRejected {
            database.execSQL(
                """
                INSERT INTO occurrences (
                    id,
                    scheduleSeriesId,
                    scheduleVersionId,
                    medicationId,
                    localDateEpochDay,
                    minuteOfDay,
                    zoneId,
                    scheduledAtEpochMillis,
                    medicationNameSnapshot,
                    medicationInstructionSnapshot,
                    lifecycle,
                    createdAtEpochMillis,
                    cancelledAtEpochMillis,
                    cancellationReason
                ) VALUES (
                    'occurrence-duplicate',
                    '$SCHEDULE_SERIES_ID',
                    '$SCHEDULE_VERSION_ID',
                    '$MEDICATION_ID',
                    $LOCAL_DATE_EPOCH_DAY,
                    $MINUTE_OF_DAY,
                    'Asia/Tehran',
                    $SCHEDULED_AT,
                    'داروی نسخه یک',
                    'دستور نسخه یک',
                    '$OCCURRENCE_LIFECYCLE',
                    $CREATED_AT,
                    NULL,
                    NULL
                )
                """.trimIndent(),
            )
        }
    }

    private fun insertVersionOneGraph(database: SQLiteDatabase) {
        database.insertOrThrow(
            "care_recipients",
            null,
            ContentValues().apply {
                put("id", RECIPIENT_ID)
                put("singletonSlot", 1)
                put("displayName", "فرد نسخه یک")
                put("createdAtEpochMillis", CREATED_AT)
            },
        )
        database.insertOrThrow(
            "medications",
            null,
            ContentValues().apply {
                put("id", MEDICATION_ID)
                put("careRecipientId", RECIPIENT_ID)
                put("name", "داروی نسخه یک")
                put("instruction", "دستور نسخه یک")
                put("createdAtEpochMillis", CREATED_AT)
            },
        )
        database.insertOrThrow(
            "schedule_series",
            null,
            ContentValues().apply {
                put("id", SCHEDULE_SERIES_ID)
                put("medicationId", MEDICATION_ID)
                put("createdAtEpochMillis", CREATED_AT)
                putNull("stoppedAtEpochMillis")
            },
        )
        database.insertOrThrow(
            "schedule_versions",
            null,
            ContentValues().apply {
                put("id", SCHEDULE_VERSION_ID)
                put("seriesId", SCHEDULE_SERIES_ID)
                put("medicationId", MEDICATION_ID)
                put("versionNumber", 1)
                put("weekdayMask", WEEKDAY_MASK)
                put("zoneId", "Asia/Tehran")
                put("effectiveFromEpochMillis", EFFECTIVE_FROM)
                putNull("effectiveUntilEpochMillis")
                putNull("startDateEpochDay")
                putNull("endDateEpochDay")
                put("medicationNameSnapshot", "داروی نسخه یک")
                put("medicationInstructionSnapshot", "دستور نسخه یک")
                put("createdAtEpochMillis", CREATED_AT)
            },
        )
        database.insertOrThrow(
            "schedule_times",
            null,
            ContentValues().apply {
                put("scheduleVersionId", SCHEDULE_VERSION_ID)
                put("minuteOfDay", MINUTE_OF_DAY)
            },
        )
        database.insertOrThrow(
            "occurrences",
            null,
            ContentValues().apply {
                put("id", OCCURRENCE_ID)
                put("scheduleSeriesId", SCHEDULE_SERIES_ID)
                put("scheduleVersionId", SCHEDULE_VERSION_ID)
                put("medicationId", MEDICATION_ID)
                put("localDateEpochDay", LOCAL_DATE_EPOCH_DAY)
                put("minuteOfDay", MINUTE_OF_DAY)
                put("zoneId", "Asia/Tehran")
                put("scheduledAtEpochMillis", SCHEDULED_AT)
                put("medicationNameSnapshot", "داروی نسخه یک")
                put("medicationInstructionSnapshot", "دستور نسخه یک")
                put("lifecycle", OCCURRENCE_LIFECYCLE)
                put("createdAtEpochMillis", CREATED_AT)
            },
        )
        database.insertOrThrow(
            "caregiver_reports",
            null,
            ContentValues().apply {
                put("occurrenceId", OCCURRENCE_ID)
                put("state", "GIVEN")
                put("recordedAtEpochMillis", RECORDED_AT)
                put("updatedAtEpochMillis", RECORDED_AT)
            },
        )
    }

    private fun columnExists(
        database: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
    ): Boolean {
        database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) {
                    return true
                }
            }
        }
        return false
    }

    private fun readInt(database: SupportSQLiteDatabase, query: String): Int =
        database.query(query).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun readLong(database: SupportSQLiteDatabase, query: String): Long =
        database.query(query).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun readNullableLong(
        database: SupportSQLiteDatabase,
        query: String,
    ): Long? = database.query(query).use { cursor ->
        check(cursor.moveToFirst())
        if (cursor.isNull(0)) null else cursor.getLong(0)
    }

    private fun readText(database: SupportSQLiteDatabase, query: String): String =
        database.query(query).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun readNullableText(
        database: SupportSQLiteDatabase,
        query: String,
    ): String? = database.query(query).use { cursor ->
        check(cursor.moveToFirst())
        if (cursor.isNull(0)) null else cursor.getString(0)
    }

    private fun assertConstraintRejected(operation: () -> Unit) {
        var rejected = false
        try {
            operation()
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private companion object {
        const val DATABASE_NAME = "carepack-migration-contract.db"
        const val RECIPIENT_ID = "recipient-v1"
        const val MEDICATION_ID = "medication-v1"
        const val SCHEDULE_SERIES_ID = "series-v1"
        const val SCHEDULE_VERSION_ID = "version-v1"
        const val OCCURRENCE_ID = "occurrence-v1"
        const val OCCURRENCE_LIFECYCLE = "ACTIVE"
        const val CREATED_AT = 1_719_207_000_000L
        const val EFFECTIVE_FROM = 1_719_216_000_000L
        const val SCHEDULED_AT = 1_719_223_800_000L
        const val RECORDED_AT = 1_719_224_000_000L
        const val LOCAL_DATE_EPOCH_DAY = 19_898L
        const val MINUTE_OF_DAY = 720
        const val WEEKDAY_MASK = 4
    }
}
