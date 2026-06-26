package ir.carepack.data.migration

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.DatabaseMigrations
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationContractTest {

    private lateinit var context:
            Context

    private var roomDatabase:
            CarePackDatabase? = null

    @Before
    fun setUp() {
        context =
            ApplicationProvider
                .getApplicationContext()

        context.deleteDatabase(
            DATABASE_NAME,
        )
    }

    @After
    fun tearDown() {
        roomDatabase?.close()
        roomDatabase = null

        context.deleteDatabase(
            DATABASE_NAME,
        )
    }

    @Test
    fun migration1To2_preservesCompleteTracerBulletGraphAndConstraints() {
        createVersionOneDatabase()

        val database =
            Room.databaseBuilder(
                context,
                CarePackDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(
                    DatabaseMigrations
                        .MIGRATION_1_2,
                )
                .allowMainThreadQueries()
                .build()

        roomDatabase = database

        val sqlite =
            database
                .openHelper
                .writableDatabase

        assertEquals(
            2,
            readUserVersion(sqlite),
        )

        assertEquals(
            RECIPIENT_ID,
            readText(
                sqlite,
                """
                SELECT id
                FROM care_recipients
                LIMIT 1
                """.trimIndent(),
            ),
        )

        assertEquals(
            MEDICATION_ID,
            readText(
                sqlite,
                """
                SELECT id
                FROM medications
                LIMIT 1
                """.trimIndent(),
            ),
        )

        assertEquals(
            "داروی نسخه یک",
            readText(
                sqlite,
                """
                SELECT name
                FROM medications
                WHERE id = '$MEDICATION_ID'
                """.trimIndent(),
            ),
        )

        assertNull(
            readNullableLong(
                sqlite,
                """
                SELECT stoppedAtEpochMillis
                FROM medications
                WHERE id = '$MEDICATION_ID'
                """.trimIndent(),
            ),
        )

        assertNull(
            readNullableLong(
                sqlite,
                """
                SELECT archivedAtEpochMillis
                FROM medications
                WHERE id = '$MEDICATION_ID'
                """.trimIndent(),
            ),
        )

        assertEquals(
            SCHEDULE_VERSION_ID,
            readText(
                sqlite,
                """
                SELECT id
                FROM schedule_versions
                LIMIT 1
                """.trimIndent(),
            ),
        )

        assertEquals(
            1,
            readInt(
                sqlite,
                """
                SELECT versionNumber
                FROM schedule_versions
                WHERE id = '$SCHEDULE_VERSION_ID'
                """.trimIndent(),
            ),
        )

        assertEquals(
            EFFECTIVE_FROM,
            readLong(
                sqlite,
                """
                SELECT effectiveFromEpochMillis
                FROM schedule_versions
                WHERE id = '$SCHEDULE_VERSION_ID'
                """.trimIndent(),
            ),
        )

        assertNull(
            readNullableLong(
                sqlite,
                """
                SELECT effectiveUntilEpochMillis
                FROM schedule_versions
                WHERE id = '$SCHEDULE_VERSION_ID'
                """.trimIndent(),
            ),
        )

        assertEquals(
            OCCURRENCE_ID,
            readText(
                sqlite,
                """
                SELECT id
                FROM occurrences
                LIMIT 1
                """.trimIndent(),
            ),
        )

        assertEquals(
            "داروی نسخه یک",
            readText(
                sqlite,
                """
                SELECT medicationNameSnapshot
                FROM occurrences
                WHERE id = '$OCCURRENCE_ID'
                """.trimIndent(),
            ),
        )

        assertEquals(
            "دستور نسخه یک",
            readText(
                sqlite,
                """
                SELECT medicationInstructionSnapshot
                FROM occurrences
                WHERE id = '$OCCURRENCE_ID'
                """.trimIndent(),
            ),
        )

        assertEquals(
            "ACTIVE",
            readText(
                sqlite,
                """
                SELECT lifecycle
                FROM occurrences
                WHERE id = '$OCCURRENCE_ID'
                """.trimIndent(),
            ),
        )

        assertNull(
            readNullableLong(
                sqlite,
                """
                SELECT cancelledAtEpochMillis
                FROM occurrences
                WHERE id = '$OCCURRENCE_ID'
                """.trimIndent(),
            ),
        )

        assertNull(
            readNullableText(
                sqlite,
                """
                SELECT cancellationReason
                FROM occurrences
                WHERE id = '$OCCURRENCE_ID'
                """.trimIndent(),
            ),
        )

        assertEquals(
            "GIVEN",
            readText(
                sqlite,
                """
                SELECT state
                FROM caregiver_reports
                WHERE occurrenceId = '$OCCURRENCE_ID'
                """.trimIndent(),
            ),
        )

        assertConstraintRejected {
            sqlite.execSQL(
                """
                INSERT INTO care_recipients (
                    id,
                    singletonSlot,
                    displayName,
                    createdAtEpochMillis
                )
                VALUES (
                    'recipient-duplicate',
                    1,
                    'تکراری',
                    2
                )
                """.trimIndent(),
            )
        }

        assertConstraintRejected {
            sqlite.execSQL(
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
                )
                VALUES (
                    'version-duplicate',
                    '$SCHEDULE_SERIES_ID',
                    '$MEDICATION_ID',
                    1,
                    4,
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
            sqlite.execSQL(
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
                )
                VALUES (
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
                    'ACTIVE',
                    $CREATED_AT,
                    NULL,
                    NULL
                )
                """.trimIndent(),
            )
        }
    }

    private fun createVersionOneDatabase() {
        val databaseFile =
            context.getDatabasePath(
                DATABASE_NAME,
            )

        databaseFile.parentFile?.mkdirs()

        val schema =
            JSONObject(
                readVersionOneSchema(),
            ).getJSONObject(
                DATABASE_KEY,
            )

        SQLiteDatabase.openOrCreateDatabase(
            databaseFile,
            null,
        ).use { sqlite ->
            sqlite.beginTransaction()

            try {
                createSchema(
                    sqlite = sqlite,
                    schema = schema,
                )

                insertVersionOneGraph(
                    sqlite,
                )

                sqlite.version = 1
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
        }
    }

    private fun readVersionOneSchema():
            String {
        val instrumentation =
            InstrumentationRegistry
                .getInstrumentation()

        return runCatching {
            instrumentation
                .context
                .assets
                .open(
                    VERSION_ONE_SCHEMA_ASSET,
                )
                .bufferedReader()
                .use {
                    it.readText()
                }
        }.getOrElse {
            context
                .assets
                .open(
                    VERSION_ONE_SCHEMA_ASSET,
                )
                .bufferedReader()
                .use {
                    it.readText()
                }
        }
    }

    private fun createSchema(
        sqlite: SQLiteDatabase,
        schema: JSONObject,
    ) {
        val entities =
            schema.getJSONArray(
                ENTITIES_KEY,
            )

        for (
        entityIndex in
        0 until entities.length()
        ) {
            val entity =
                entities.getJSONObject(
                    entityIndex,
                )

            val tableName =
                entity.getString(
                    TABLE_NAME_KEY,
                )

            executeSchemaSql(
                sqlite = sqlite,
                sql =
                    entity.getString(
                        CREATE_SQL_KEY,
                    ),
                tableName =
                    tableName,
            )

            val indices =
                entity.optJSONArray(
                    INDICES_KEY,
                )

            if (indices != null) {
                for (
                indexPosition in
                0 until indices.length()
                ) {
                    executeSchemaSql(
                        sqlite = sqlite,
                        sql =
                            indices
                                .getJSONObject(
                                    indexPosition,
                                )
                                .getString(
                                    CREATE_SQL_KEY,
                                ),
                        tableName =
                            tableName,
                    )
                }
            }
        }

        val setupQueries =
            schema.optJSONArray(
                SETUP_QUERIES_KEY,
            )

        if (setupQueries != null) {
            for (
            queryIndex in
            0 until setupQueries.length()
            ) {
                sqlite.execSQL(
                    setupQueries.getString(
                        queryIndex,
                    ),
                )
            }
        }
    }

    private fun executeSchemaSql(
        sqlite: SQLiteDatabase,
        sql: String,
        tableName: String,
    ) {
        sqlite.execSQL(
            sql.replace(
                oldValue =
                    TABLE_NAME_PLACEHOLDER,
                newValue =
                    tableName,
            ),
        )
    }

    private fun insertVersionOneGraph(
        sqlite: SQLiteDatabase,
    ) {
        sqlite.insertOrThrow(
            "care_recipients",
            null,
            ContentValues().apply {
                put("id", RECIPIENT_ID)
                put("singletonSlot", 1)
                put(
                    "displayName",
                    "فرد نسخه یک",
                )
                put(
                    "createdAtEpochMillis",
                    CREATED_AT,
                )
            },
        )

        sqlite.insertOrThrow(
            "medications",
            null,
            ContentValues().apply {
                put("id", MEDICATION_ID)
                put(
                    "careRecipientId",
                    RECIPIENT_ID,
                )
                put(
                    "name",
                    "داروی نسخه یک",
                )
                put(
                    "instruction",
                    "دستور نسخه یک",
                )
                put(
                    "createdAtEpochMillis",
                    CREATED_AT,
                )
            },
        )

        sqlite.insertOrThrow(
            "schedule_series",
            null,
            ContentValues().apply {
                put(
                    "id",
                    SCHEDULE_SERIES_ID,
                )
                put(
                    "medicationId",
                    MEDICATION_ID,
                )
                put(
                    "createdAtEpochMillis",
                    CREATED_AT,
                )
                putNull(
                    "stoppedAtEpochMillis",
                )
            },
        )

        sqlite.insertOrThrow(
            "schedule_versions",
            null,
            ContentValues().apply {
                put(
                    "id",
                    SCHEDULE_VERSION_ID,
                )
                put(
                    "seriesId",
                    SCHEDULE_SERIES_ID,
                )
                put(
                    "medicationId",
                    MEDICATION_ID,
                )
                put("versionNumber", 1)
                put("weekdayMask", 4)
                put(
                    "zoneId",
                    "Asia/Tehran",
                )
                put(
                    "effectiveFromEpochMillis",
                    EFFECTIVE_FROM,
                )
                putNull(
                    "effectiveUntilEpochMillis",
                )
                putNull(
                    "startDateEpochDay",
                )
                putNull(
                    "endDateEpochDay",
                )
                put(
                    "medicationNameSnapshot",
                    "داروی نسخه یک",
                )
                put(
                    "medicationInstructionSnapshot",
                    "دستور نسخه یک",
                )
                put(
                    "createdAtEpochMillis",
                    CREATED_AT,
                )
            },
        )

        sqlite.insertOrThrow(
            "schedule_times",
            null,
            ContentValues().apply {
                put(
                    "scheduleVersionId",
                    SCHEDULE_VERSION_ID,
                )
                put(
                    "minuteOfDay",
                    MINUTE_OF_DAY,
                )
            },
        )

        sqlite.insertOrThrow(
            "occurrences",
            null,
            ContentValues().apply {
                put(
                    "id",
                    OCCURRENCE_ID,
                )
                put(
                    "scheduleSeriesId",
                    SCHEDULE_SERIES_ID,
                )
                put(
                    "scheduleVersionId",
                    SCHEDULE_VERSION_ID,
                )
                put(
                    "medicationId",
                    MEDICATION_ID,
                )
                put(
                    "localDateEpochDay",
                    LOCAL_DATE_EPOCH_DAY,
                )
                put(
                    "minuteOfDay",
                    MINUTE_OF_DAY,
                )
                put(
                    "zoneId",
                    "Asia/Tehran",
                )
                put(
                    "scheduledAtEpochMillis",
                    SCHEDULED_AT,
                )
                put(
                    "medicationNameSnapshot",
                    "داروی نسخه یک",
                )
                put(
                    "medicationInstructionSnapshot",
                    "دستور نسخه یک",
                )
                put(
                    "lifecycle",
                    "ACTIVE",
                )
                put(
                    "createdAtEpochMillis",
                    CREATED_AT,
                )
            },
        )

        sqlite.insertOrThrow(
            "caregiver_reports",
            null,
            ContentValues().apply {
                put(
                    "occurrenceId",
                    OCCURRENCE_ID,
                )
                put(
                    "state",
                    "GIVEN",
                )
                put(
                    "recordedAtEpochMillis",
                    RECORDED_AT,
                )
                put(
                    "updatedAtEpochMillis",
                    RECORDED_AT,
                )
            },
        )
    }

    private fun readUserVersion(
        database: SupportSQLiteDatabase,
    ): Int {
        return readInt(
            database,
            "PRAGMA user_version",
        )
    }

    private fun readInt(
        database: SupportSQLiteDatabase,
        query: String,
    ): Int {
        database.query(query).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun readLong(
        database: SupportSQLiteDatabase,
        query: String,
    ): Long {
        database.query(query).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getLong(0)
        }
    }

    private fun readNullableLong(
        database: SupportSQLiteDatabase,
        query: String,
    ): Long? {
        database.query(query).use { cursor ->
            check(cursor.moveToFirst())

            return if (cursor.isNull(0)) {
                null
            } else {
                cursor.getLong(0)
            }
        }
    }

    private fun readText(
        database: SupportSQLiteDatabase,
        query: String,
    ): String {
        database.query(query).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getString(0)
        }
    }

    private fun readNullableText(
        database: SupportSQLiteDatabase,
        query: String,
    ): String? {
        database.query(query).use { cursor ->
            check(cursor.moveToFirst())

            return if (cursor.isNull(0)) {
                null
            } else {
                cursor.getString(0)
            }
        }
    }

    private fun assertConstraintRejected(
        operation: () -> Unit,
    ) {
        var rejected = false

        try {
            operation()
        } catch (_: Exception) {
            rejected = true
        }

        assertTrue(rejected)
    }

    private companion object {
        const val DATABASE_NAME =
            "carepack-migration-contract.db"

        const val VERSION_ONE_SCHEMA_ASSET =
            "ir.carepack.data.local.CarePackDatabase/1.json"

        const val DATABASE_KEY =
            "database"

        const val ENTITIES_KEY =
            "entities"

        const val TABLE_NAME_KEY =
            "tableName"

        const val CREATE_SQL_KEY =
            "createSql"

        const val INDICES_KEY =
            "indices"

        const val SETUP_QUERIES_KEY =
            "setupQueries"

        const val TABLE_NAME_PLACEHOLDER =
            "\${TABLE_NAME}"

        const val RECIPIENT_ID =
            "recipient-v1"

        const val MEDICATION_ID =
            "medication-v1"

        const val SCHEDULE_SERIES_ID =
            "series-v1"

        const val SCHEDULE_VERSION_ID =
            "version-v1"

        const val OCCURRENCE_ID =
            "occurrence-v1"

        const val CREATED_AT =
            1_719_207_000_000L

        const val EFFECTIVE_FROM =
            1_719_216_000_000L

        const val SCHEDULED_AT =
            1_719_223_800_000L

        const val RECORDED_AT =
            1_719_224_000_000L

        const val LOCAL_DATE_EPOCH_DAY =
            19_898L

        const val MINUTE_OF_DAY =
            720
    }
}
