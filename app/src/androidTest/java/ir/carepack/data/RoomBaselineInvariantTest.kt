package ir.carepack.data



import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.CareRecipientEntity
import ir.carepack.data.local.MedicationEntity
import ir.carepack.data.local.OccurrenceEntity
import ir.carepack.data.local.ScheduleSeriesEntity
import ir.carepack.data.local.ScheduleTimeEntity
import ir.carepack.data.local.ScheduleVersionEntity
import ir.carepack.domain.model.OccurrenceLifecycle
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBaselineInvariantTest {

    private lateinit var context: Context
    private lateinit var database: CarePackDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun exportedVersionOneSchema_createsAndCleanOpens() {
        createVersionOneDatabaseFromCommittedSchema()

        database =
            Room.databaseBuilder(
                context,
                CarePackDatabase::class.java,
                DATABASE_NAME,
            )
                .addCallback(
                    CarePackDatabase.invariantCallback,
                )
                .build()

        val writableDatabase = database.openHelper.writableDatabase
        assertNotNull(writableDatabase)
        assertEquals(1, writableDatabase.version)
    }

    private fun createVersionOneDatabaseFromCommittedSchema() {
        val schemaText =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
                .open(
                    "ir.carepack.data.local.CarePackDatabase/1.json",
                )
                .bufferedReader()
                .use { reader ->
                    reader.readText()
                }

        val databaseJson =
            org.json.JSONObject(
                schemaText,
            ).getJSONObject(
                "database",
            )

        assertEquals(
            1,
            databaseJson.getInt(
                "version",
            ),
        )

        val configuration =
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                .builder(
                    context,
                )
                .name(
                    DATABASE_NAME,
                )
                .callback(
                    object :
                        androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(
                            1,
                        ) {
                        override fun onCreate(
                            db:
                            androidx.sqlite.db.SupportSQLiteDatabase,
                        ) {
                            val entities =
                                databaseJson.getJSONArray(
                                    "entities",
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
                                        "tableName",
                                    )

                                db.execSQL(
                                    entity.getString(
                                        "createSql",
                                    ).replace(
                                        "\${TABLE_NAME}",
                                        tableName,
                                    ),
                                )

                                val indices =
                                    entity.optJSONArray(
                                        "indices",
                                    )

                                if (indices != null) {
                                    for (
                                        index in
                                        0 until indices.length()
                                    ) {
                                        db.execSQL(
                                            indices
                                                .getJSONObject(
                                                    index,
                                                )
                                                .getString(
                                                    "createSql",
                                                )
                                                .replace(
                                                    "\${TABLE_NAME}",
                                                    tableName,
                                                ),
                                        )
                                    }
                                }
                            }

                            val views =
                                databaseJson.optJSONArray(
                                    "views",
                                )

                            if (views != null) {
                                for (
                                    viewIndex in
                                    0 until views.length()
                                ) {
                                    db.execSQL(
                                        views
                                            .getJSONObject(
                                                viewIndex,
                                            )
                                            .getString(
                                                "createSql",
                                            ),
                                    )
                                }
                            }

                            val setupQueries =
                                databaseJson.getJSONArray(
                                    "setupQueries",
                                )

                            for (
                                queryIndex in
                                0 until setupQueries.length()
                            ) {
                                db.execSQL(
                                    setupQueries.getString(
                                        queryIndex,
                                    ),
                                )
                            }
                        }

                        override fun onUpgrade(
                            db:
                            androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build()

        val helper =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    configuration,
                )

        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    @Test
    fun logicalOccurrenceUniqueIndex_rejectsDuplicateScheduleDateAndMinute() =
        kotlinx.coroutines.runBlocking {
            database =
                Room.inMemoryDatabaseBuilder(
                    context,
                    CarePackDatabase::class.java,
                ).build()

            val now = Instant.parse("2026-06-24T08:00:00Z").toEpochMilli()

            database.careRecipientDao().insert(
                CareRecipientEntity(
                    id = "recipient-1",
                    singletonSlot = 1,
                    displayName = "فرد آزمون",
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            database.medicationDao().insert(
                MedicationEntity(
                    id = "medication-1",
                    careRecipientId = "recipient-1",
                    name = "داروی آزمون",
                    instructionText = "دستور",
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    stoppedAtEpochMillis = null,
                    archivedAtEpochMillis = null,
                ),
            )
            database.scheduleDao().insertSeries(
                ScheduleSeriesEntity(
                    id = "series-1",
                    medicationId = "medication-1",
                    createdAtEpochMillis = now,
                ),
            )
            database.scheduleDao().insertVersion(
                ScheduleVersionEntity(
                    id = "version-1",
                    scheduleSeriesId = "series-1",
                    versionNumber = 1,
                    weekdayMask = 127,
                    startEpochDay = null,
                    endEpochDay = null,
                    zoneId = "UTC",
                    patternType = "FIXED_TIMES",
                    intervalHours = null,
                    anchorMinuteOfDay = null,
                    effectiveFromEpochMillis = now,
                    effectiveUntilEpochMillis = null,
                    createdAtEpochMillis = now,
                    supersededReason = null,
                ),
            )
            database.scheduleDao().insertTimes(
                listOf(
                    ScheduleTimeEntity(
                        scheduleVersionId = "version-1",
                        minuteOfDay = 9 * 60,
                    ),
                ),
            )

            val first = occurrence("occurrence-1", now)
            val duplicate = occurrence("occurrence-2", now)

            val firstResult =
                database.occurrenceDao()
                    .insertIgnoringLogicalConflict(first)
            val duplicateResult =
                database.occurrenceDao()
                    .insertIgnoringLogicalConflict(duplicate)

            assertTrueInserted(firstResult)
            assertEquals(-1L, duplicateResult)
            assertEquals(1, database.occurrenceDao().count())
        }

    private fun occurrence(
        id: String,
        now: Long,
    ): OccurrenceEntity =
        OccurrenceEntity(
            id = id,
            scheduleVersionId = "version-1",
            medicationId = "medication-1",
            localEpochDay = 20_628L,
            minuteOfDay = 9 * 60,
            zoneIdSnapshot = "UTC",
            scheduledAtEpochMillis = now + 3_600_000L,
            medicationNameSnapshot = "داروی آزمون",
            instructionSnapshot = "دستور",
            lifecycle = OccurrenceLifecycle.ACTIVE.name,
            cancelledAtEpochMillis = null,
            cancellationReason = null,
            createdAtEpochMillis = now,
        )

    private fun assertTrueInserted(rowId: Long) {
        org.junit.Assert.assertTrue(rowId >= 0L)
    }

    private companion object {
        const val DATABASE_NAME = "carepack-room-baseline-invariant.db"
    }
}
