package ir.carepack.performance

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.service.RoomDateRangeSummaryService
import ir.carepack.data.service.RoomReminderScheduleSource
import ir.carepack.domain.report.ReportDateRange
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LongHistoryHotPathEvidenceTest {

    private lateinit var context: Context
    private lateinit var database: CarePackDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(
                context,
                CarePackDatabase::class.java,
            )
                .addCallback(CarePackDatabase.invariantCallback)
                .build()
        seedFourYearMixedHistory()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun actualReminderAndRangeHotPathsUseExpectedIndexesAndStayWithinDeviceAwareBudget() =
        runBlocking {
            val now = Instant.parse("2028-01-01T00:00:00Z")
            val range =
                ReportDateRange(
                    startDate = LocalDate.parse("2027-12-03"),
                    endDate = LocalDate.parse("2028-01-01"),
                )

            val reminderSource = RoomReminderScheduleSource(database)
            val rangeSource = RoomDateRangeSummaryService(database)

            val reminderPlan = explain(reminderSql(now.toEpochMilli()))
            val rangePlan =
                explain(
                    rangeSql(
                        range.startDate.toEpochDay(),
                        range.endDate.toEpochDay(),
                    ),
                )

            val reminderStarted = SystemClock.elapsedRealtimeNanos()
            repeat(20) {
                val targets = reminderSource.getNextEligibleTargets(now)
                assertFalse(targets.isEmpty())
            }
            val reminderMillis = elapsedMillis(reminderStarted)

            val rangeStarted = SystemClock.elapsedRealtimeNanos()
            repeat(20) {
                val summary = rangeSource.getSummary(range)
                assertTrue(summary.totalOccurrenceCount >= 0)
            }
            val rangeMillis = elapsedMillis(rangeStarted)

            val thresholdMillis =
                if (
                    Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                    Build.FINGERPRINT.contains("emulator", ignoreCase = true)
                ) {
                    10_000L
                } else {
                    5_000L
                }

            val evidence =
                buildString {
                    appendLine("dataset.medications=4")
                    appendLine("dataset.schedules=8")
                    appendLine("dataset.years=4")
                    appendLine("reminder.elapsedMillis=$reminderMillis")
                    appendLine("range.elapsedMillis=$rangeMillis")
                    appendLine("thresholdMillis=$thresholdMillis")
                    appendLine("reminder.plan:")
                    reminderPlan.forEach(::appendLine)
                    appendLine("range.plan:")
                    rangePlan.forEach(::appendLine)
                }

            val additionalTestOutputDir =
                InstrumentationRegistry.getArguments()
                    .getString("additionalTestOutputDir")
                    ?.let(::File)
                    ?: error(
                        "additionalTestOutputDir instrumentation argument is unavailable.",
                    )

            check(
                additionalTestOutputDir.isDirectory ||
                    additionalTestOutputDir.mkdirs(),
            ) {
                "Unable to create the additional test output directory."
            }

            File(
                additionalTestOutputDir,
                EVIDENCE_FILE,
            ).writeText(evidence)

            assertTrue(
                "Reminder per-series candidate lookup must be indexed. Plan=$reminderPlan",
                reminderPlan.any {
                    it.contains(
                        "index_schedule_versions_scheduleSeriesId",
                        ignoreCase = true,
                    )
                } &&
                    reminderPlan.any {
                        it.contains(
                            "index_occurrences_scheduleVersionId",
                            ignoreCase = true,
                        )
                    },
            )
            assertTrue(
                "Range query must use the local-day index. Plan=$rangePlan",
                rangePlan.any {
                    it.contains(
                        "index_occurrences_localEpochDay_lifecycle",
                        ignoreCase = true,
                    )
                },
            )
            assertTrue(
                "Actual reminder hot path exceeded $thresholdMillis ms: $reminderMillis ms",
                reminderMillis < thresholdMillis,
            )
            assertTrue(
                "Actual range hot path exceeded $thresholdMillis ms: $rangeMillis ms",
                rangeMillis < thresholdMillis,
            )
        }

    private fun elapsedMillis(startNanos: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L

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

    private fun reminderSql(nowEpochMillis: Long): String =
        """
        SELECT
            series.id AS scheduleSeriesId,
            occurrence.id AS occurrenceId,
            occurrence.localEpochDay AS localEpochDay,
            occurrence.minuteOfDay AS minuteOfDay,
            occurrence.zoneIdSnapshot AS zoneIdSnapshot,
            occurrence.scheduledAtEpochMillis AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot AS medicationNameSnapshot
        FROM schedule_series AS series
        INNER JOIN occurrences AS occurrence
            ON occurrence.id = (
                SELECT candidate.id
                FROM occurrences AS candidate
                INNER JOIN schedule_versions AS candidateVersion
                    ON candidateVersion.id = candidate.scheduleVersionId
                WHERE candidateVersion.scheduleSeriesId = series.id
                  AND candidate.lifecycle = 'ACTIVE'
                  AND candidate.scheduledAtEpochMillis > $nowEpochMillis
                  AND NOT EXISTS (
                      SELECT 1
                      FROM caregiver_reports AS candidateReport
                      WHERE candidateReport.occurrenceId = candidate.id
                  )
                ORDER BY
                    candidate.scheduledAtEpochMillis,
                    candidate.id
                LIMIT 1
            )
        INNER JOIN medications AS medication
            ON medication.id = occurrence.medicationId
        WHERE medication.stoppedAtEpochMillis IS NULL
          AND medication.archivedAtEpochMillis IS NULL
        ORDER BY
            series.id,
            occurrence.scheduledAtEpochMillis,
            occurrence.id
        """.trimIndent()

    private fun rangeSql(
        startEpochDay: Long,
        endEpochDay: Long,
    ): String =
        """
        SELECT
            occurrence.id AS occurrenceId,
            occurrence.localEpochDay AS localEpochDay,
            occurrence.minuteOfDay AS minuteOfDay,
            occurrence.zoneIdSnapshot AS zoneIdSnapshot,
            occurrence.scheduledAtEpochMillis AS scheduledAtEpochMillis,
            occurrence.medicationNameSnapshot AS medicationNameSnapshot,
            occurrence.instructionSnapshot AS instructionSnapshot,
            occurrence.medicationTypeSnapshot AS medicationTypeSnapshot,
            occurrence.dosageTextSnapshot AS dosageTextSnapshot,
            occurrence.doseUnitSnapshot AS doseUnitSnapshot,
            occurrence.lifecycle AS lifecycle,
            occurrence.cancellationReason AS cancellationReason,
            report.state AS reportState
        FROM occurrences AS occurrence
        LEFT JOIN caregiver_reports AS report
            ON report.occurrenceId = occurrence.id
        WHERE occurrence.localEpochDay BETWEEN $startEpochDay AND $endEpochDay
          AND occurrence.lifecycle != 'CANCELLED'
        ORDER BY
            occurrence.localEpochDay,
            occurrence.minuteOfDay,
            occurrence.id
        """.trimIndent()

    private fun seedFourYearMixedHistory() {
        val db = database.openHelper.writableDatabase
        val baseDate = LocalDate.parse("2026-01-01")
        val baseMillis = Instant.parse("2026-01-01T08:00:00Z").toEpochMilli()

        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT INTO care_recipients " +
                    "(id, singletonSlot, displayName, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES ('recipient-1', 1, 'test', ?, ?)",
                arrayOf(baseMillis, baseMillis),
            )

            repeat(4) { medicationIndex ->
                val medicationId = "medication-$medicationIndex"
                db.execSQL(
                    "INSERT INTO medications " +
                        "(id, careRecipientId, name, instructionText, medicationType, dosageText, doseUnit, " +
                        "createdAtEpochMillis, updatedAtEpochMillis, stoppedAtEpochMillis, archivedAtEpochMillis) " +
                        "VALUES (?, 'recipient-1', 'test', 'test', '', '', '', ?, ?, NULL, NULL)",
                    arrayOf(medicationId, baseMillis, baseMillis),
                )

                repeat(2) { scheduleIndex ->
                    val seriesId = "series-$medicationIndex-$scheduleIndex"
                    val versionId = "version-$medicationIndex-$scheduleIndex"
                    val minute = 8 * 60 + scheduleIndex * 8 * 60
                    db.execSQL(
                        "INSERT INTO schedule_series (id, medicationId, createdAtEpochMillis) VALUES (?, ?, ?)",
                        arrayOf(seriesId, medicationId, baseMillis),
                    )
                    db.execSQL(
                        "INSERT INTO schedule_versions " +
                            "(id, scheduleSeriesId, versionNumber, weekdayMask, startEpochDay, endEpochDay, " +
                            "zoneId, patternType, intervalHours, anchorMinuteOfDay, effectiveFromEpochMillis, " +
                            "effectiveUntilEpochMillis, createdAtEpochMillis, supersededReason) " +
                            "VALUES (?, ?, 1, 127, NULL, NULL, 'UTC', 'FIXED_TIMES', NULL, NULL, ?, NULL, ?, NULL)",
                        arrayOf(versionId, seriesId, baseMillis, baseMillis),
                    )
                    db.execSQL(
                        "INSERT INTO schedule_times (scheduleVersionId, minuteOfDay) VALUES (?, ?)",
                        arrayOf(versionId, minute),
                    )

                    repeat(1_461) { day ->
                        val occurrenceId = "occ-$medicationIndex-$scheduleIndex-$day"
                        val localDate = baseDate.plusDays(day.toLong())
                        val scheduledAt =
                            localDate.atTime(minute / 60, minute % 60)
                                .toInstant(ZoneOffset.UTC)
                                .toEpochMilli()
                        val cancelled = day % 11 == 0
                        db.execSQL(
                            "INSERT INTO occurrences " +
                                "(id, scheduleVersionId, medicationId, localEpochDay, minuteOfDay, zoneIdSnapshot, " +
                                "scheduledAtEpochMillis, medicationNameSnapshot, instructionSnapshot, " +
                                "medicationTypeSnapshot, dosageTextSnapshot, doseUnitSnapshot, lifecycle, " +
                                "cancelledAtEpochMillis, cancellationReason, createdAtEpochMillis) " +
                                "VALUES (?, ?, ?, ?, ?, 'UTC', ?, 'test', 'test', '', '', '', ?, ?, ?, ?)",
                            arrayOf(
                                occurrenceId,
                                versionId,
                                medicationId,
                                localDate.toEpochDay(),
                                minute,
                                scheduledAt,
                                if (cancelled) "CANCELLED" else "ACTIVE",
                                if (cancelled) scheduledAt else null,
                                if (cancelled) "SCHEDULE_UPDATED" else null,
                                baseMillis,
                            ),
                        )

                        if (!cancelled && day % 3 == 0) {
                            val state =
                                when (day % 9) {
                                    0 -> "GIVEN"
                                    3 -> "NOT_GIVEN"
                                    else -> "UNKNOWN"
                                }
                            db.execSQL(
                                "INSERT INTO caregiver_reports " +
                                    "(occurrenceId, state, recordedAtEpochMillis, updatedAtEpochMillis) " +
                                    "VALUES (?, ?, ?, ?)",
                                arrayOf(occurrenceId, state, scheduledAt, scheduledAt),
                            )
                        }
                    }
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val EVIDENCE_FILE = "carepack-performance-evidence.txt"
    }
}
