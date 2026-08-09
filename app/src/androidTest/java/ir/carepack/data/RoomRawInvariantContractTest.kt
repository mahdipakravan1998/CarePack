package ir.carepack.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.CareRecipientEntity
import ir.carepack.data.local.MedicationEntity
import ir.carepack.data.local.ScheduleSeriesEntity
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRawInvariantContractTest {

    private lateinit var context: Context
    private lateinit var database: CarePackDatabase

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(
                context,
                CarePackDatabase::class.java,
            )
                .addCallback(CarePackDatabase.invariantCallback)
                .build()
        seedParents()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun rawInvalidMinuteOfDayIsRejectedByDatabase() =
        runBlocking {
            insertValidOpenVersion("version-valid", 1)

            assertConstraintRejected {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO schedule_times(scheduleVersionId, minuteOfDay) VALUES(?, ?)",
                    arrayOf("version-valid", 1_440),
                )
            }
        }

    @Test
    fun rawPatternFieldMismatchIsRejectedByDatabase() =
        runBlocking {
            assertConstraintRejected {
                database.openHelper.writableDatabase.execSQL(
                    """
                    INSERT INTO schedule_versions(
                        id, scheduleSeriesId, versionNumber, weekdayMask,
                        startEpochDay, endEpochDay, zoneId, patternType,
                        intervalHours, anchorMinuteOfDay, effectiveFromEpochMillis,
                        effectiveUntilEpochMillis, createdAtEpochMillis, supersededReason
                    ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        "version-invalid-pattern",
                        "series-1",
                        1,
                        127,
                        null,
                        null,
                        "UTC",
                        "FIXED_TIMES",
                        8,
                        420,
                        1_000L,
                        null,
                        1_000L,
                        null,
                    ),
                )
            }
        }

    @Test
    fun rawSecondOpenVersionForSameSeriesIsRejectedByDatabase() =
        runBlocking {
            insertValidOpenVersion("version-open-1", 1)

            assertConstraintRejected {
                database.openHelper.writableDatabase.execSQL(
                    """
                    INSERT INTO schedule_versions(
                        id, scheduleSeriesId, versionNumber, weekdayMask,
                        startEpochDay, endEpochDay, zoneId, patternType,
                        intervalHours, anchorMinuteOfDay, effectiveFromEpochMillis,
                        effectiveUntilEpochMillis, createdAtEpochMillis, supersededReason
                    ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        "version-open-2",
                        "series-1",
                        2,
                        127,
                        null,
                        null,
                        "UTC",
                        "FIXED_TIMES",
                        null,
                        null,
                        2_000L,
                        null,
                        2_000L,
                        null,
                    ),
                )
            }
        }

    @Test
    fun rawZeroLengthClosedVersionIsAcceptedButInvertedRangeIsRejectedByDatabase() =
        runBlocking {
            insertValidOpenVersion("version-range", 1)

            database.openHelper.writableDatabase.execSQL(
                """
                UPDATE schedule_versions
                SET effectiveUntilEpochMillis = ?
                WHERE id = ?
                """.trimIndent(),
                arrayOf(
                    1_000L,
                    "version-range",
                ),
            )

            assertConstraintRejected {
                database.openHelper.writableDatabase.execSQL(
                    """
                    UPDATE schedule_versions
                    SET effectiveUntilEpochMillis = ?
                    WHERE id = ?
                    """.trimIndent(),
                    arrayOf(
                        999L,
                        "version-range",
                    ),
                )
            }
        }

    private suspend fun seedParents() {
        val now = Instant.parse("2026-08-08T12:00:00Z").toEpochMilli()
        database.careRecipientDao().insert(
            CareRecipientEntity(
                id = "recipient-1",
                singletonSlot = 1,
                displayName = "آزمون",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        database.medicationDao().insert(
            MedicationEntity(
                id = "medication-1",
                careRecipientId = "recipient-1",
                name = "داروی آزمون",
                instructionText = "دستور آزمون",
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
    }

    private fun insertValidOpenVersion(
        id: String,
        versionNumber: Int,
    ) {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO schedule_versions(
                id, scheduleSeriesId, versionNumber, weekdayMask,
                startEpochDay, endEpochDay, zoneId, patternType,
                intervalHours, anchorMinuteOfDay, effectiveFromEpochMillis,
                effectiveUntilEpochMillis, createdAtEpochMillis, supersededReason
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                id,
                "series-1",
                versionNumber,
                127,
                null,
                null,
                "UTC",
                "FIXED_TIMES",
                null,
                null,
                versionNumber * 1_000L,
                null,
                versionNumber * 1_000L,
                null,
            ),
        )
    }

    private fun assertConstraintRejected(
        operation: () -> Unit,
    ) {
        var rejected = false
        try {
            operation()
        } catch (_: SQLiteConstraintException) {
            rejected = true
        }
        assertTrue("Expected SQLite invariant rejection.", rejected)
    }
}
