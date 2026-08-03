package ir.carepack.domain.report

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface DateRangeSummaryService {

    fun observeSummary(
        range: ReportDateRange,
    ): Flow<DateRangeSummary>

    suspend fun getSummary(
        range: ReportDateRange,
    ): DateRangeSummary
}

object RangeSummaryBuilder {

    fun build(
        range: ReportDateRange,
        entries: List<RangeOccurrenceEntry>,
    ): DateRangeSummary {
        val orderedEntries =
            entries.asSequence()
                .filter { entry ->
                    entry.localDate in range
                }
                .sortedWith(
                    compareBy<RangeOccurrenceEntry>(
                        RangeOccurrenceEntry::localDate,
                    ).thenBy(
                        RangeOccurrenceEntry::localTime,
                    ).thenBy(
                        RangeOccurrenceEntry::occurrenceId,
                    ),
                )
                .toList()

        val entriesByDate =
            orderedEntries.groupBy(
                RangeOccurrenceEntry::localDate,
            )

        val daySummaries =
            (0 until range.dayCount)
                .map { dayOffset ->
                    val date =
                        range.startDate.plusDays(
                            dayOffset.toLong(),
                        )

                    val dayEntries =
                        entriesByDate[date]
                            .orEmpty()

                    DayRangeSummary(
                        date = date,
                        totalOccurrenceCount =
                            dayEntries.size,
                        givenCount =
                            dayEntries.count {
                                it.reportState ==
                                        RangeOccurrenceReportState
                                            .GIVEN
                            },
                        notGivenCount =
                            dayEntries.count {
                                it.reportState ==
                                        RangeOccurrenceReportState
                                            .NOT_GIVEN
                            },
                        unknownCount =
                            dayEntries.count {
                                it.reportState ==
                                        RangeOccurrenceReportState
                                            .UNKNOWN
                            },
                        noReportCount =
                            dayEntries.count {
                                it.reportState ==
                                        RangeOccurrenceReportState
                                            .NO_REPORT
                            },
                        entries = dayEntries,
                    )
                }

        return DateRangeSummary(
            range = range,
            totalOccurrenceCount =
                orderedEntries.size,
            givenCount =
                orderedEntries.count {
                    it.reportState ==
                            RangeOccurrenceReportState
                                .GIVEN
                },
            notGivenCount =
                orderedEntries.count {
                    it.reportState ==
                            RangeOccurrenceReportState
                                .NOT_GIVEN
                },
            unknownCount =
                orderedEntries.count {
                    it.reportState ==
                            RangeOccurrenceReportState
                                .UNKNOWN
                },
            noReportCount =
                orderedEntries.count {
                    it.reportState ==
                            RangeOccurrenceReportState
                                .NO_REPORT
                },
            daySummaries =
                daySummaries,
        )
    }
}
