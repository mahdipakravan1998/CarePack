package ir.carepack.domain.report

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeReportDomainTest {

    @Test
    fun sevenDayPeriod_includesTodayAndPreviousSixDates() {
        val today =
            LocalDate.parse(
                "2026-06-24",
            )

        val range =
            RangeReportPeriod
                .SEVEN_DAYS
                .rangeEndingAt(today)

        assertEquals(
            LocalDate.parse(
                "2026-06-18",
            ),
            range.startDate,
        )
        assertEquals(today, range.endDate)
        assertEquals(7, range.dayCount)
        assertTrue(range.startDate in range)
        assertTrue(range.endDate in range)
    }

    @Test
    fun thirtyDayPeriod_includesTodayAndPreviousTwentyNineDates() {
        val today =
            LocalDate.parse(
                "2026-01-05",
            )

        val range =
            RangeReportPeriod
                .THIRTY_DAYS
                .rangeEndingAt(today)

        assertEquals(
            LocalDate.parse(
                "2025-12-07",
            ),
            range.startDate,
        )
        assertEquals(today, range.endDate)
        assertEquals(30, range.dayCount)
    }

    @Test(
        expected =
            IllegalArgumentException::class,
    )
    fun reversedRange_isRejected() {
        ReportDateRange(
            startDate =
                LocalDate.parse(
                    "2026-06-25",
                ),
            endDate =
                LocalDate.parse(
                    "2026-06-24",
                ),
        )
    }

    @Test
    fun summaryBuilder_distinguishesUnknownFromNoReport() {
        val range =
            ReportDateRange(
                startDate = REPORT_DATE,
                endDate = REPORT_DATE,
            )

        val summary =
            RangeSummaryBuilder.build(
                range = range,
                entries =
                    listOf(
                        entry(
                            id = "given",
                            state =
                                RangeOccurrenceReportState
                                    .GIVEN,
                            minuteOfDay = 8 * 60,
                        ),
                        entry(
                            id = "not-given",
                            state =
                                RangeOccurrenceReportState
                                    .NOT_GIVEN,
                            minuteOfDay = 9 * 60,
                        ),
                        entry(
                            id = "unknown",
                            state =
                                RangeOccurrenceReportState
                                    .UNKNOWN,
                            minuteOfDay = 10 * 60,
                        ),
                        entry(
                            id = "no-report",
                            state =
                                RangeOccurrenceReportState
                                    .NO_REPORT,
                            minuteOfDay = 11 * 60,
                        ),
                    ),
            )

        assertEquals(4, summary.totalOccurrenceCount)
        assertEquals(1, summary.givenCount)
        assertEquals(1, summary.notGivenCount)
        assertEquals(1, summary.unknownCount)
        assertEquals(1, summary.noReportCount)

        val day =
            checkNotNull(
                summary.summaryFor(REPORT_DATE),
            )

        assertEquals(1, day.unknownCount)
        assertEquals(1, day.noReportCount)
    }

    @Test
    fun summaryBuilder_ordersDaysAndEntriesDeterministically() {
        val range =
            ReportDateRange(
                startDate = REPORT_DATE,
                endDate =
                    REPORT_DATE.plusDays(2),
            )

        val summary =
            RangeSummaryBuilder.build(
                range = range,
                entries =
                    listOf(
                        entry(
                            id = "b",
                            date =
                                REPORT_DATE.plusDays(1),
                            minuteOfDay = 9 * 60,
                        ),
                        entry(
                            id = "z",
                            date = REPORT_DATE,
                            minuteOfDay = 8 * 60,
                        ),
                        entry(
                            id = "a",
                            date = REPORT_DATE,
                            minuteOfDay = 8 * 60,
                        ),
                        entry(
                            id = "c",
                            date = REPORT_DATE,
                            minuteOfDay = 7 * 60,
                        ),
                    ),
            )

        assertEquals(
            listOf(
                REPORT_DATE,
                REPORT_DATE.plusDays(1),
                REPORT_DATE.plusDays(2),
            ),
            summary.daySummaries.map {
                it.date
            },
        )

        assertEquals(
            listOf("c", "a", "z"),
            checkNotNull(
                summary.summaryFor(REPORT_DATE),
            ).entries.map {
                it.occurrenceId
            },
        )
    }

    @Test
    fun summaryBuilder_includesMultipleMedicationsAndSchedulesOnceEach() {
        val range =
            ReportDateRange(
                startDate = REPORT_DATE,
                endDate = REPORT_DATE,
            )

        val summary =
            RangeSummaryBuilder.build(
                range = range,
                entries =
                    listOf(
                        entry(
                            id = "medication-a-schedule-1",
                            medicationName = "داروی الف",
                            minuteOfDay = 8 * 60,
                        ),
                        entry(
                            id = "medication-a-schedule-2",
                            medicationName = "داروی الف",
                            minuteOfDay = 20 * 60,
                        ),
                        entry(
                            id = "medication-b-schedule-1",
                            medicationName = "داروی ب",
                            minuteOfDay = 12 * 60,
                        ),
                    ),
            )

        assertEquals(3, summary.totalOccurrenceCount)
        assertEquals(
            listOf(
                "medication-a-schedule-1",
                "medication-b-schedule-1",
                "medication-a-schedule-2",
            ),
            summary.entries.map {
                it.occurrenceId
            },
        )
    }

    @Test
    fun summaryBuilder_ignoresEntriesOutsideRequestedRange() {
        val range =
            ReportDateRange(
                startDate = REPORT_DATE,
                endDate = REPORT_DATE,
            )

        val summary =
            RangeSummaryBuilder.build(
                range = range,
                entries =
                    listOf(
                        entry(
                            id = "before",
                            date =
                                REPORT_DATE.minusDays(1),
                        ),
                        entry(
                            id = "inside",
                            date = REPORT_DATE,
                        ),
                        entry(
                            id = "after",
                            date =
                                REPORT_DATE.plusDays(1),
                        ),
                    ),
            )

        assertEquals(1, summary.totalOccurrenceCount)
        assertEquals(
            listOf("inside"),
            summary.entries.map {
                it.occurrenceId
            },
        )
    }

    @Test
    fun emptyRange_containsEveryDateWithZeroCounts() {
        val range =
            RangeReportPeriod
                .SEVEN_DAYS
                .rangeEndingAt(REPORT_DATE)

        val summary =
            RangeSummaryBuilder.build(
                range = range,
                entries = emptyList(),
            )

        assertEquals(0, summary.totalOccurrenceCount)
        assertEquals(7, summary.daySummaries.size)
        assertTrue(
            summary.daySummaries.all { day ->
                day.totalOccurrenceCount == 0 &&
                        day.entries.isEmpty()
            },
        )
        assertNull(
            summary.summaryFor(
                REPORT_DATE.plusDays(1),
            ),
        )
    }

    @Test
    fun rangeCrossingGregorianYear_remainsInclusiveAndOrdered() {
        val range =
            RangeReportPeriod
                .SEVEN_DAYS
                .rangeEndingAt(
                    LocalDate.parse(
                        "2026-01-03",
                    ),
                )

        val summary =
            RangeSummaryBuilder.build(
                range = range,
                entries =
                    listOf(
                        entry(
                            id = "year-end",
                            date =
                                LocalDate.parse(
                                    "2025-12-31",
                                ),
                        ),
                        entry(
                            id = "year-start",
                            date =
                                LocalDate.parse(
                                    "2026-01-01",
                                ),
                        ),
                    ),
            )

        assertEquals(7, summary.daySummaries.size)
        assertEquals(
            LocalDate.parse(
                "2025-12-28",
            ),
            summary.daySummaries.first().date,
        )
        assertEquals(
            LocalDate.parse(
                "2026-01-03",
            ),
            summary.daySummaries.last().date,
        )
        assertEquals(2, summary.totalOccurrenceCount)
    }

    private fun entry(
        id: String,
        date: LocalDate = REPORT_DATE,
        minuteOfDay: Int = 8 * 60,
        medicationName: String = "داروی آزمون",
        state: RangeOccurrenceReportState =
            RangeOccurrenceReportState.NO_REPORT,
    ): RangeOccurrenceEntry =
        RangeOccurrenceEntry(
            occurrenceId = id,
            localDate = date,
            localTime =
                LocalTime.of(
                    minuteOfDay / 60,
                    minuteOfDay % 60,
                ),
            zoneIdSnapshot = "Asia/Tehran",
            scheduledAt =
                Instant.parse(
                    "2026-06-24T04:30:00Z",
                ).plusSeconds(
                    minuteOfDay.toLong() * 60L,
                ),
            medicationName = medicationName,
            instruction = "بعد از غذا",
            medicationType = "قرص",
            dosageText = "یک",
            doseUnit = "عدد",
            reportState = state,
        )

    private companion object {
        val REPORT_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )
    }
}
