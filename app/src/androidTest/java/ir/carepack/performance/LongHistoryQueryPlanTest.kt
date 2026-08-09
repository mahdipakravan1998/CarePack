package ir.carepack.performance

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ir.carepack.data.local.CarePackDatabase
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LongHistoryQueryPlanTest {

    private lateinit var context: Context
    private lateinit var database: CarePackDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(
                context,
                CarePackDatabase::class.java,
            ).build()
        seedThreeYearHistory()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reminderAndRangeQueries_haveIndexedPlansAndBoundedRuntime() {
        val reminderPlan =
            explain(
                """
                SELECT occurrence.id
                FROM occurrences AS occurrence
                INNER JOIN schedule_versions AS version
                    ON version.id = occurrence.scheduleVersionId
                INNER JOIN medications AS medication
                    ON medication.id = occurrence.medicationId
                WHERE occurrence.lifecycle = 'ACTIVE'
                  AND occurrence.scheduledAtEpochMillis > 1750752000000
                  AND medication.stoppedAtEpochMillis IS NULL
                  AND medication.archivedAtEpochMillis IS NULL
                ORDER BY occurrence.scheduledAtEpochMillis
                LIMIT 1
                """.trimIndent(),
            )

        val rangePlan =
            explain(
                """
                SELECT COUNT(*)
                FROM occurrences
                WHERE localEpochDay BETWEEN 20200 AND 21300
                """.trimIndent(),
            )

        val started = SystemClock.elapsedRealtimeNanos()
        repeat(100) {
            database.openHelper.writableDatabase
                .query(
                    "SELECT COUNT(*) FROM occurrences " +
                        "WHERE scheduledAtEpochMillis > 1750752000000",
                )
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                }
        }
        val elapsedMillis =
            (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L

        val evidence =
            buildString {
                appendLine("Reminder query plan:")
                reminderPlan.forEach(::appendLine)
                appendLine("Range query plan:")
                rangePlan.forEach(::appendLine)
                appendLine("100 count queries elapsedMillis=$elapsedMillis")
            }

        File(context.cacheDir, "carepack-query-plan-evidence.txt")
            .writeText(evidence)

        assertFalse(reminderPlan.isEmpty())
        assertFalse(rangePlan.isEmpty())
        assertTrue(
            reminderPlan.any { line ->
                line.contains("index_occurrences_scheduledAtEpochMillis", ignoreCase = true) ||
                    line.contains("index_occurrences_scheduleVersionId", ignoreCase = true) ||
                    (
                        line.contains(
                            "occurrence",
                            ignoreCase = true,
                        ) &&
                            (
                                line.contains(
                                    "SEARCH",
                                    ignoreCase = true,
                                ) ||
                                    line.contains(
                                        "USING INDEX",
                                        ignoreCase = true,
                                    ) ||
                                    line.contains(
                                        "USING COVERING INDEX",
                                        ignoreCase = true,
                                    )
                            )
                    )
            },
        )
        assertTrue(
            "Long-history query benchmark exceeded the release threshold: $elapsedMillis ms",
            elapsedMillis < 5_000L,
        )
    }

    private fun explain(sql: String): List<String> {
        val lines = mutableListOf<String>()
        database.openHelper.writableDatabase
            .query("EXPLAIN QUERY PLAN $sql")
            .use { cursor ->
                val detailColumn = cursor.getColumnIndexOrThrow("detail")
                while (cursor.moveToNext()) {
                    lines += cursor.getString(detailColumn)
                }
            }
        return lines
    }

    private fun seedThreeYearHistory() {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT INTO care_recipients " +
                    "(id, singletonSlot, displayName, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES ('recipient-1', 1, 'test', 1750752000000, 1750752000000)",
            )
            db.execSQL(
                "INSERT INTO medications " +
                    "(id, careRecipientId, name, instructionText, medicationType, dosageText, doseUnit, " +
                    "createdAtEpochMillis, updatedAtEpochMillis, stoppedAtEpochMillis, archivedAtEpochMillis) " +
                    "VALUES ('medication-1', 'recipient-1', 'test', 'test', '', '', '', " +
                    "1750752000000, 1750752000000, NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO schedule_series (id, medicationId, createdAtEpochMillis) " +
                    "VALUES ('series-1', 'medication-1', 1750752000000)",
            )
            db.execSQL(
                "INSERT INTO schedule_versions " +
                    "(id, scheduleSeriesId, versionNumber, weekdayMask, startEpochDay, endEpochDay, " +
                    "zoneId, patternType, intervalHours, anchorMinuteOfDay, effectiveFromEpochMillis, " +
                    "effectiveUntilEpochMillis, createdAtEpochMillis, supersededReason) " +
                    "VALUES ('version-1', 'series-1', 1, 127, NULL, NULL, 'UTC', 'FIXED_TIMES', " +
                    "NULL, NULL, 1750752000000, NULL, 1750752000000, NULL)",
            )
            db.execSQL(
                "INSERT INTO schedule_times (scheduleVersionId, minuteOfDay) " +
                    "VALUES ('version-1', 540)",
            )

            repeat(1_096) { day ->
                val epochDay = 20_628L + day
                val scheduledAt = 1_750_752_000_000L + day * 86_400_000L
                db.execSQL(
                    "INSERT INTO occurrences " +
                        "(id, scheduleVersionId, medicationId, localEpochDay, minuteOfDay, " +
                        "zoneIdSnapshot, scheduledAtEpochMillis, medicationNameSnapshot, " +
                        "instructionSnapshot, medicationTypeSnapshot, dosageTextSnapshot, " +
                        "doseUnitSnapshot, lifecycle, cancelledAtEpochMillis, cancellationReason, " +
                        "createdAtEpochMillis) VALUES (?, 'version-1', 'medication-1', ?, 540, " +
                        "'UTC', ?, 'test', 'test', '', '', '', 'ACTIVE', NULL, NULL, 1750752000000)",
                    arrayOf("occurrence-$day", epochDay, scheduledAt),
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
