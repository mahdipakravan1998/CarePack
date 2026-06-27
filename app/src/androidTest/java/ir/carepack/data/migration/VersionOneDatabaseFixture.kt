package ir.carepack.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject

internal class VersionOneDatabaseFixture(
    private val context: Context,
    private val databaseName: String,
) {
    fun create(
        insertData: (SQLiteDatabase) -> Unit,
    ) {
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()

        val schema = JSONObject(readSchema()).getJSONObject(DATABASE_KEY)
        check(schema.getInt(VERSION_KEY) == VERSION_ONE)

        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.beginTransaction()
            try {
                createEntities(database, schema)
                createViews(database, schema)
                executeSetupQueries(database, schema)
                insertData(database)
                database.version = VERSION_ONE
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    private fun readSchema(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        return runCatching {
            instrumentation.context.assets
                .open(VERSION_ONE_SCHEMA_ASSET)
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse {
            context.assets
                .open(VERSION_ONE_SCHEMA_ASSET)
                .bufferedReader()
                .use { it.readText() }
        }
    }

    private fun createEntities(
        database: SQLiteDatabase,
        schema: JSONObject,
    ) {
        val entities = schema.getJSONArray(ENTITIES_KEY)

        for (entityIndex in 0 until entities.length()) {
            val entity = entities.getJSONObject(entityIndex)
            val tableName = entity.getString(TABLE_NAME_KEY)

            executeSchemaSql(
                database = database,
                sql = entity.getString(CREATE_SQL_KEY),
                tableName = tableName,
            )

            entity.optJSONArray(INDICES_KEY)?.let { indices ->
                for (indexPosition in 0 until indices.length()) {
                    executeSchemaSql(
                        database = database,
                        sql = indices
                            .getJSONObject(indexPosition)
                            .getString(CREATE_SQL_KEY),
                        tableName = tableName,
                    )
                }
            }

            entity.optJSONArray(CONTENT_SYNC_TRIGGERS_KEY)?.let { triggers ->
                for (triggerPosition in 0 until triggers.length()) {
                    executeSchemaSql(
                        database = database,
                        sql = triggers.getString(triggerPosition),
                        tableName = tableName,
                    )
                }
            }
        }
    }

    private fun createViews(
        database: SQLiteDatabase,
        schema: JSONObject,
    ) {
        schema.optJSONArray(VIEWS_KEY)?.let { views ->
            for (viewIndex in 0 until views.length()) {
                database.execSQL(
                    views.getJSONObject(viewIndex).getString(CREATE_SQL_KEY),
                )
            }
        }
    }

    private fun executeSetupQueries(
        database: SQLiteDatabase,
        schema: JSONObject,
    ) {
        schema.optJSONArray(SETUP_QUERIES_KEY)?.let { queries ->
            for (queryIndex in 0 until queries.length()) {
                database.execSQL(queries.getString(queryIndex))
            }
        }
    }

    private fun executeSchemaSql(
        database: SQLiteDatabase,
        sql: String,
        tableName: String,
    ) {
        database.execSQL(
            sql.replace(
                oldValue = TABLE_NAME_PLACEHOLDER,
                newValue = tableName,
            ),
        )
    }

    private companion object {
        const val VERSION_ONE = 1
        const val VERSION_ONE_SCHEMA_ASSET =
            "ir.carepack.data.local.CarePackDatabase/1.json"
        const val DATABASE_KEY = "database"
        const val VERSION_KEY = "version"
        const val ENTITIES_KEY = "entities"
        const val VIEWS_KEY = "views"
        const val TABLE_NAME_KEY = "tableName"
        const val CREATE_SQL_KEY = "createSql"
        const val INDICES_KEY = "indices"
        const val CONTENT_SYNC_TRIGGERS_KEY = "contentSyncTriggers"
        const val SETUP_QUERIES_KEY = "setupQueries"
        const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"
    }
}
