package ir.carepack.domain.report

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime


data class ReportDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(
            !endDate.isBefore(
                startDate,
            ),
        )
    }

    val dayCount: Int
        get() = (
                    endDate.toEpochDay() - startDate.toEpochDay() +
                            1L).toInt()

    operator fun contains(
        date: LocalDate,
    ): Boolean = !date.isBefore(startDate) &&
                !date.isAfter(endDate)
}


enum class RangeReportPeriod(
    val dayCount: Int,
) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    ;

    fun rangeEndingAt(
        today: LocalDate,
    ): ReportDateRange = ReportDateRange(
            startDate = today.minusDays(
                    dayCount.toLong() - 1L,
                ),
            endDate = today,
        )
}


enum class RangeOccurrenceReportState {
    GIVEN,
    NOT_GIVEN,
    UNKNOWN,
    NO_REPORT,
}


data class RangeOccurrenceEntry(
    val occurrenceId: String,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneIdSnapshot: String,
    val scheduledAt: Instant,
    val medicationName: String,
    val instruction: String,
    val medicationType: String,
    val dosageText: String,
    val doseUnit: String,
    val reportState: RangeOccurrenceReportState,
) {
    init {
        require(occurrenceId.isNotBlank())
        require(zoneIdSnapshot.isNotBlank())
        require(medicationName.isNotBlank())
    }
}


data class DayRangeSummary(
    val date: LocalDate,
    val totalOccurrenceCount: Int,
    val givenCount: Int,
    val notGivenCount: Int,
    val unknownCount: Int,
    val noReportCount: Int,
    val entries: List<RangeOccurrenceEntry>,
) {
    init {
        require(totalOccurrenceCount >= 0)
        require(givenCount >= 0)
        require(notGivenCount >= 0)
        require(unknownCount >= 0)
        require(noReportCount >= 0)
        require(
            totalOccurrenceCount == givenCount +
                    notGivenCount + unknownCount +
                    noReportCount,
        )
        require(
            entries.size == totalOccurrenceCount,
        )
    }
}


data class DateRangeSummary(
    val range: ReportDateRange,
    val totalOccurrenceCount: Int,
    val givenCount: Int,
    val notGivenCount: Int,
    val unknownCount: Int,
    val noReportCount: Int,
    val daySummaries: List<DayRangeSummary>,
) {
    init {
        require(totalOccurrenceCount >= 0)
        require(givenCount >= 0)
        require(notGivenCount >= 0)
        require(unknownCount >= 0)
        require(noReportCount >= 0)
        require(
            totalOccurrenceCount == givenCount +
                    notGivenCount + unknownCount +
                    noReportCount,
        )
        require(
            daySummaries.size == range.dayCount,
        )
        require(
            daySummaries.zipWithNext().all { pair ->
                    pair.first.date <
                            pair.second.date
                },
        )
    }

    val entries: List<RangeOccurrenceEntry>
        get() = daySummaries.flatMap(
                DayRangeSummary::entries,
            )

    fun summaryFor(
        date: LocalDate,
    ): DayRangeSummary? = daySummaries.firstOrNull {
            it.date == date
        }
}
