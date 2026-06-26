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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private lateinit var targetContext: Context

    private var roomDatabase: CarePackDatabase? = null

    @Before
    fun setUp() {
        targetContext = ApplicationProvider.getApplicationContext()
        targetContext.deleteDatabase(TEST_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        roomDatabase?.close()
        roomDatabase = null

        targetContext.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun migration1To2_preservesExistingRows() {
        createVersionOneDatabase()

        val database =
            Room.databaseBuilder(
                targetContext,
                CarePackDatabase::class.java,
                TEST_DATABASE_NAME,
            )
                .addMigrations(
                    DatabaseMigrations.MIGRATION_1_2,
                )
                .allowMainThreadQueries()
                .build()

        roomDatabase = database

        /*
         * Accessing writableDatabase forces Room to open the database,
         * run MIGRATION_1_2 and validate the resulting version-2 schema.
         */
        val migratedDatabase = database.openHelper.writableDatabase

        assertEquals(
            2,
            readDatabaseVersion(migratedDatabase),
        )

        assertEquals(
            1,
            readRowCount(
                database = migratedDatabase,
                tableName = CARE_RECIPIENTS_TABLE,
            ),
        )

        assertTrue(
            columnExists(
                database = migratedDatabase,
                tableName = MEDICATIONS_TABLE,
                columnName = STOPPED_AT_COLUMN,
            ),
        )

        assertTrue(
            columnExists(
                database = migratedDatabase,
                tableName = MEDICATIONS_TABLE,
                columnName = ARCHIVED_AT_COLUMN,
            ),
        )

        assertTrue(
            columnExists(
                database = migratedDatabase,
                tableName = OCCURRENCES_TABLE,
                columnName = CANCELLED_AT_COLUMN,
            ),
        )

        assertTrue(
            columnExists(
                database = migratedDatabase,
                tableName = OCCURRENCES_TABLE,
                columnName = CANCELLATION_REASON_COLUMN,
            ),
        )
    }

    /**
     * Creates a real version-1 SQLite database from Room's exported 1.json
     * without using MigrationTestHelper.
     *
     * This avoids MigrationTestHelper's internal kotlinx.serialization path,
     * while still testing the real exported version-1 schema and the real
     * MIGRATION_1_2 implementation.
     */
    private fun createVersionOneDatabase() {
        val databaseFile =
            targetContext.getDatabasePath(TEST_DATABASE_NAME)

        databaseFile.parentFile?.mkdirs()

        val schemaJson = readVersionOneSchema()
        val schemaRoot = JSONObject(schemaJson)
        val databaseSchema =
            schemaRoot.getJSONObject(DATABASE_JSON_KEY)

        assertEquals(
            1,
            databaseSchema.getInt(VERSION_JSON_KEY),
        )

        SQLiteDatabase.openOrCreateDatabase(
            databaseFile,
            null,
        ).use { sqliteDatabase ->
            sqliteDatabase.beginTransaction()

            try {
                createEntities(
                    sqliteDatabase = sqliteDatabase,
                    databaseSchema = databaseSchema,
                )

                createViews(
                    sqliteDatabase = sqliteDatabase,
                    databaseSchema = databaseSchema,
                )

                executeSetupQueries(
                    sqliteDatabase = sqliteDatabase,
                    databaseSchema = databaseSchema,
                )

                insertSyntheticCareRecipient(sqliteDatabase)

                sqliteDatabase.version = 1
                sqliteDatabase.setTransactionSuccessful()
            } finally {
                sqliteDatabase.endTransaction()
            }
        }
    }

    private fun readVersionOneSchema(): String {
        val instrumentation =
            InstrumentationRegistry.getInstrumentation()

        return runCatching {
            instrumentation.context.assets
                .open(VERSION_ONE_SCHEMA_ASSET)
                .bufferedReader()
                .use { reader ->
                    reader.readText()
                }
        }.getOrElse {
            targetContext.assets
                .open(VERSION_ONE_SCHEMA_ASSET)
                .bufferedReader()
                .use { reader ->
                    reader.readText()
                }
        }
    }

    private fun createEntities(
        sqliteDatabase: SQLiteDatabase,
        databaseSchema: JSONObject,
    ) {
        val entities =
            databaseSchema.getJSONArray(ENTITIES_JSON_KEY)

        for (entityIndex in 0 until entities.length()) {
            val entity = entities.getJSONObject(entityIndex)
            val tableName =
                entity.getString(TABLE_NAME_JSON_KEY)

            executeSchemaSql(
                sqliteDatabase = sqliteDatabase,
                sql = entity.getString(CREATE_SQL_JSON_KEY),
                tableName = tableName,
            )

            val indices =
                entity.optJSONArray(INDICES_JSON_KEY)

            if (indices != null) {
                for (indexPosition in 0 until indices.length()) {
                    val index =
                        indices.getJSONObject(indexPosition)

                    executeSchemaSql(
                        sqliteDatabase = sqliteDatabase,
                        sql = index.getString(CREATE_SQL_JSON_KEY),
                        tableName = tableName,
                    )
                }
            }

            val contentSyncTriggers =
                entity.optJSONArray(
                    CONTENT_SYNC_TRIGGERS_JSON_KEY,
                )

            if (contentSyncTriggers != null) {
                for (
                triggerPosition in
                0 until contentSyncTriggers.length()
                ) {
                    executeSchemaSql(
                        sqliteDatabase = sqliteDatabase,
                        sql = contentSyncTriggers.getString(
                            triggerPosition,
                        ),
                        tableName = tableName,
                    )
                }
            }
        }
    }

    private fun createViews(
        sqliteDatabase: SQLiteDatabase,
        databaseSchema: JSONObject,
    ) {
        val views =
            databaseSchema.optJSONArray(VIEWS_JSON_KEY)
                ?: return

        for (viewIndex in 0 until views.length()) {
            val view = views.getJSONObject(viewIndex)

            sqliteDatabase.execSQL(
                view.getString(CREATE_SQL_JSON_KEY),
            )
        }
    }

    private fun executeSetupQueries(
        sqliteDatabase: SQLiteDatabase,
        databaseSchema: JSONObject,
    ) {
        val setupQueries =
            databaseSchema.optJSONArray(
                SETUP_QUERIES_JSON_KEY,
            ) ?: return

        for (
        queryIndex in
        0 until setupQueries.length()
        ) {
            sqliteDatabase.execSQL(
                setupQueries.getString(queryIndex),
            )
        }
    }

    private fun executeSchemaSql(
        sqliteDatabase: SQLiteDatabase,
        sql: String,
        tableName: String,
    ) {
        val resolvedSql =
            sql.replace(
                oldValue = TABLE_NAME_PLACEHOLDER,
                newValue = tableName,
            )

        sqliteDatabase.execSQL(resolvedSql)
    }

    private fun insertSyntheticCareRecipient(
        sqliteDatabase: SQLiteDatabase,
    ) {
        val columns =
            readTableColumns(
                sqliteDatabase = sqliteDatabase,
                tableName = CARE_RECIPIENTS_TABLE,
            )

        check(columns.isNotEmpty()) {
            "The $CARE_RECIPIENTS_TABLE table was not created from version 1 schema."
        }

        val values = ContentValues()

        columns.forEach { column ->
            val mustProvideValue =
                column.primaryKeyPosition > 0 ||
                        (
                                column.isNotNull &&
                                        column.defaultValue == null
                                )

            if (mustProvideValue) {
                putSyntheticValue(
                    values = values,
                    column = column,
                )
            }
        }

        val insertedRowId =
            sqliteDatabase.insertOrThrow(
                CARE_RECIPIENTS_TABLE,
                null,
                values,
            )

        check(insertedRowId != -1L) {
            "Could not insert the synthetic version-1 care recipient."
        }
    }

    private fun readTableColumns(
        sqliteDatabase: SQLiteDatabase,
        tableName: String,
    ): List<SqliteColumn> {
        val columns = mutableListOf<SqliteColumn>()

        sqliteDatabase.rawQuery(
            "PRAGMA table_info(${quoteIdentifier(tableName)})",
            null,
        ).use { cursor ->
            val nameIndex =
                cursor.getColumnIndexOrThrow("name")

            val typeIndex =
                cursor.getColumnIndexOrThrow("type")

            val notNullIndex =
                cursor.getColumnIndexOrThrow("notnull")

            val defaultValueIndex =
                cursor.getColumnIndexOrThrow("dflt_value")

            val primaryKeyIndex =
                cursor.getColumnIndexOrThrow("pk")

            while (cursor.moveToNext()) {
                columns +=
                    SqliteColumn(
                        name = cursor.getString(nameIndex),
                        declaredType =
                            cursor.getString(typeIndex).orEmpty(),
                        isNotNull =
                            cursor.getInt(notNullIndex) == 1,
                        defaultValue =
                            if (
                                cursor.isNull(
                                    defaultValueIndex,
                                )
                            ) {
                                null
                            } else {
                                cursor.getString(
                                    defaultValueIndex,
                                )
                            },
                        primaryKeyPosition =
                            cursor.getInt(primaryKeyIndex),
                    )
            }
        }

        return columns
    }

    private fun putSyntheticValue(
        values: ContentValues,
        column: SqliteColumn,
    ) {
        val normalizedType =
            column.declaredType.uppercase()

        when {
            normalizedType.contains("INT") -> {
                val value =
                    if (column.primaryKeyPosition > 0) {
                        SYNTHETIC_PRIMARY_KEY
                    } else {
                        SYNTHETIC_INTEGER_VALUE
                    }

                values.put(
                    column.name,
                    value,
                )
            }

            normalizedType.contains("CHAR") ||
                    normalizedType.contains("CLOB") ||
                    normalizedType.contains("TEXT") -> {
                values.put(
                    column.name,
                    "$SYNTHETIC_TEXT_PREFIX-${column.name}",
                )
            }

            normalizedType.contains("REAL") ||
                    normalizedType.contains("FLOA") ||
                    normalizedType.contains("DOUB") -> {
                values.put(
                    column.name,
                    SYNTHETIC_REAL_VALUE,
                )
            }

            normalizedType.contains("BLOB") ||
                    normalizedType.isBlank() -> {
                values.put(
                    column.name,
                    SYNTHETIC_BLOB_VALUE,
                )
            }

            else -> {
                values.put(
                    column.name,
                    SYNTHETIC_INTEGER_VALUE,
                )
            }
        }
    }

    private fun readDatabaseVersion(
        database: SupportSQLiteDatabase,
    ): Int {
        database.query(
            "PRAGMA user_version",
        ).use { cursor ->
            check(cursor.moveToFirst()) {
                "PRAGMA user_version returned no result."
            }

            return cursor.getInt(0)
        }
    }

    private fun readRowCount(
        database: SupportSQLiteDatabase,
        tableName: String,
    ): Int {
        database.query(
            "SELECT COUNT(*) FROM ${quoteIdentifier(tableName)}",
        ).use { cursor ->
            check(cursor.moveToFirst()) {
                "COUNT query returned no result for table $tableName."
            }

            return cursor.getInt(0)
        }
    }

    private fun columnExists(
        database: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
    ): Boolean {
        database.query(
            "PRAGMA table_info(${quoteIdentifier(tableName)})",
        ).use { cursor ->
            val nameIndex =
                cursor.getColumnIndexOrThrow("name")

            while (cursor.moveToNext()) {
                if (
                    cursor.getString(nameIndex) ==
                    columnName
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun quoteIdentifier(
        identifier: String,
    ): String {
        val escapedIdentifier =
            identifier.replace(
                oldValue = "`",
                newValue = "``",
            )

        return "`$escapedIdentifier`"
    }

    private data class SqliteColumn(
        val name: String,
        val declaredType: String,
        val isNotNull: Boolean,
        val defaultValue: String?,
        val primaryKeyPosition: Int,
    )

    private companion object {

        const val TEST_DATABASE_NAME =
            "carepack-migration-1-to-2-test.db"

        const val VERSION_ONE_SCHEMA_ASSET =
            "ir.carepack.data.local.CarePackDatabase/1.json"

        const val CARE_RECIPIENTS_TABLE =
            "care_recipients"

        const val MEDICATIONS_TABLE =
            "medications"

        const val OCCURRENCES_TABLE =
            "occurrences"

        const val STOPPED_AT_COLUMN =
            "stoppedAtEpochMillis"

        const val ARCHIVED_AT_COLUMN =
            "archivedAtEpochMillis"

        const val CANCELLED_AT_COLUMN =
            "cancelledAtEpochMillis"

        const val CANCELLATION_REASON_COLUMN =
            "cancellationReason"

        const val DATABASE_JSON_KEY =
            "database"

        const val VERSION_JSON_KEY =
            "version"

        const val ENTITIES_JSON_KEY =
            "entities"

        const val VIEWS_JSON_KEY =
            "views"

        const val TABLE_NAME_JSON_KEY =
            "tableName"

        const val CREATE_SQL_JSON_KEY =
            "createSql"

        const val INDICES_JSON_KEY =
            "indices"

        const val CONTENT_SYNC_TRIGGERS_JSON_KEY =
            "contentSyncTriggers"

        const val SETUP_QUERIES_JSON_KEY =
            "setupQueries"

        const val TABLE_NAME_PLACEHOLDER =
            "\${TABLE_NAME}"

        const val SYNTHETIC_PRIMARY_KEY =
            9_001L

        const val SYNTHETIC_INTEGER_VALUE =
            1L

        const val SYNTHETIC_REAL_VALUE =
            1.0

        const val SYNTHETIC_TEXT_PREFIX =
            "migration-test"

        val SYNTHETIC_BLOB_VALUE =
            byteArrayOf(
                1,
                2,
                3,
            )
    }
}
