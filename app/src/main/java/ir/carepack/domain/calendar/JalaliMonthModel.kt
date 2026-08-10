package ir.carepack.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate


data class JalaliYearMonth(
    val year: Int,
    val month: Int,
) {
    init {
        require(year in 1..3177)
        require(month in 1..12)
    }

    val monthName: String
        get() = PersianDateText.monthName(
                month,
            )

    val lengthOfMonth: Int
        get() = JalaliPresentationDate
                .lengthOfMonth(
                    year = year,
                    month = month,
                )

    fun firstLocalDate(): LocalDate = JalaliPresentationDate(
            year = JalaliYear(year),
            month = JalaliMonth(month),
            dayOfMonth = JalaliDayOfMonth(1),
        ).toLocalDate()

    fun localDateAt(
        dayOfMonth: Int,
    ): LocalDate = JalaliPresentationDate(
            year = JalaliYear(year),
            month = JalaliMonth(month),
            dayOfMonth = JalaliDayOfMonth(
                    dayOfMonth,
                ),
        ).toLocalDate()

    fun previous(): JalaliYearMonth = if (month == 1) {
            JalaliYearMonth(
                year = year - 1,
                month = 12,
            )
        } else {
            copy(
                month = month - 1,
            )
        }

    fun next(): JalaliYearMonth = if (month == 12) {
            JalaliYearMonth(
                year = year + 1,
                month = 1,
            )
        } else {
            copy(
                month = month + 1,
            )
        }

    companion object {
        fun from(
            localDate: LocalDate,
        ): JalaliYearMonth {
            val jalaliDate = JalaliPresentationDate
                    .from(localDate)

            return JalaliYearMonth(
                year = jalaliDate
                        .year.value,
                month = jalaliDate
                        .month.value,
            )
        }
    }
}


data class JalaliMonthCell(
    val localDate: LocalDate,
    val jalaliDate: JalaliPresentationDate,
    val belongsToDisplayedMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
)


data class JalaliMonthModel(
    val displayedMonth: JalaliYearMonth,
    val firstDayOfWeek: DayOfWeek,
    val weekdayOrder: List<DayOfWeek>,
    val firstVisibleDate: LocalDate,
    val lastVisibleDate: LocalDate,
    val weeks: List<List<JalaliMonthCell>>,
) {
    init {
        require(weekdayOrder.size == DAYS_PER_WEEK)
        require(weeks.isNotEmpty())
        require(
            weeks.all { week ->
                week.size == DAYS_PER_WEEK
            },
        )
    }

    val cells: List<JalaliMonthCell>
        get() = weeks.flatten()

    companion object {
        private const val DAYS_PER_WEEK = 7
    }
}

object JalaliMonthModelFactory {

    fun create(
        displayedMonth: JalaliYearMonth,
        today: LocalDate,
        selectedDate: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): JalaliMonthModel {
        val firstMonthDate = displayedMonth.firstLocalDate()

        val leadingDayCount = dayDistance(
                first = firstDayOfWeek,
                second = firstMonthDate
                        .dayOfWeek,
            )

        val firstVisibleDate = firstMonthDate.minusDays(
                leadingDayCount.toLong(),
            )

        val usedCellCount = leadingDayCount +
                    displayedMonth.lengthOfMonth

        val rowCount = (
                    usedCellCount + DAYS_PER_WEEK - 1
                    ) / DAYS_PER_WEEK

        val totalCellCount = rowCount * DAYS_PER_WEEK

        val cells = (0 until totalCellCount)
                .map { index ->
                    val localDate = firstVisibleDate.plusDays(
                            index.toLong(),
                        )

                    val jalaliDate = JalaliPresentationDate
                            .from(localDate)

                    JalaliMonthCell(
                        localDate = localDate,
                        jalaliDate = jalaliDate,
                        belongsToDisplayedMonth = jalaliDate.year.value ==
                                    displayedMonth.year && jalaliDate.month.value ==
                                    displayedMonth.month,
                        isToday = localDate == today,
                        isSelected = localDate == selectedDate,
                    )
                }

        return JalaliMonthModel(
            displayedMonth = displayedMonth,
            firstDayOfWeek = firstDayOfWeek,
            weekdayOrder = weekdayOrder(
                    firstDayOfWeek,
                ),
            firstVisibleDate = firstVisibleDate,
            lastVisibleDate = cells.last().localDate,
            weeks = cells.chunked(
                    DAYS_PER_WEEK,
                ),
        )
    }

    private fun weekdayOrder(
        firstDayOfWeek: DayOfWeek,
    ): List<DayOfWeek> = (0 until DAYS_PER_WEEK)
            .map { offset ->
                DayOfWeek.of(
                    (
                            firstDayOfWeek.value - 1 + offset
                            ) % DAYS_PER_WEEK + 1,
                )
            }

    private fun dayDistance(
        first: DayOfWeek,
        second: DayOfWeek,
    ): Int = (
                second.value - first.value +
                        DAYS_PER_WEEK) % DAYS_PER_WEEK

    private const val DAYS_PER_WEEK = 7
}
