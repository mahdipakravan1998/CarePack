package ir.carepack.domain.calendar

import java.time.LocalDate

@JvmInline
value class JalaliYear(
    val value: Int,
) {
    init {
        require(value in 1..3177)
    }
}

@JvmInline
value class JalaliMonth(
    val value: Int,
) {
    init {
        require(value in 1..12)
    }
}

@JvmInline
value class JalaliDayOfMonth(
    val value: Int,
) {
    init {
        require(value in 1..31)
    }
}

data class JalaliPresentationDate(
    val year: JalaliYear,
    val month: JalaliMonth,
    val dayOfMonth: JalaliDayOfMonth,
) {
    init {
        require(
            dayOfMonth.value <= lengthOfMonth(
                year = year.value,
                month = month.value,
            ),
        )
    }

    fun toLocalDate(): LocalDate =
        PersianCalendarMath.toGregorian(
            year = year.value,
            month = month.value,
            day = dayOfMonth.value,
        )

    fun formatNumeric(): String =
        buildString {
            append(year.value.toString().padStart(4, '0'))
            append('/')
            append(month.value.toString().padStart(2, '0'))
            append('/')
            append(dayOfMonth.value.toString().padStart(2, '0'))
        }

    companion object {
        fun from(
            localDate: LocalDate,
        ): JalaliPresentationDate {
            val date =
                PersianCalendarMath.fromGregorian(
                    localDate,
                )

            return JalaliPresentationDate(
                year =
                    JalaliYear(date.year),
                month =
                    JalaliMonth(date.month),
                dayOfMonth =
                    JalaliDayOfMonth(date.day),
            )
        }

        fun parseNumeric(
            rawValue: String,
        ): JalaliPresentationDate? {
            val match =
                NUMERIC_PATTERN.matchEntire(
                    rawValue.trim(),
                ) ?: return null

            val year =
                match.groupValues[1].toInt()

            val month =
                match.groupValues[2].toInt()

            val day =
                match.groupValues[3].toInt()

            return runCatching {
                JalaliPresentationDate(
                    year = JalaliYear(year),
                    month = JalaliMonth(month),
                    dayOfMonth =
                        JalaliDayOfMonth(day),
                )
            }.getOrNull()
        }

        fun lengthOfMonth(
            year: Int,
            month: Int,
        ): Int =
            when (month) {
                in 1..6 -> 31
                in 7..11 -> 30
                12 -> if (isLeapYear(year)) 30 else 29
                else -> error("Invalid Jalali month: $month")
            }

        fun isLeapYear(
            year: Int,
        ): Boolean =
            PersianCalendarMath.isLeapYear(year)

        private val NUMERIC_PATTERN =
            Regex("""^(\d{4})[/\-](\d{1,2})[/\-](\d{1,2})$""")
    }
}

private data class JalaliDateParts(
    val year: Int,
    val month: Int,
    val day: Int,
)

private object PersianCalendarMath {
    private val breaks =
        intArrayOf(
            -61,
            9,
            38,
            199,
            426,
            686,
            756,
            818,
            1111,
            1181,
            1210,
            1635,
            2060,
            2097,
            2192,
            2262,
            2324,
            2394,
            2456,
            3178,
        )

    fun fromGregorian(
        localDate: LocalDate,
    ): JalaliDateParts {
        val julianDayNumber =
            gregorianToJulianDayNumber(
                year = localDate.year,
                month = localDate.monthValue,
                day = localDate.dayOfMonth,
            )

        return julianDayNumberToJalali(
            julianDayNumber,
        )
    }

    fun toGregorian(
        year: Int,
        month: Int,
        day: Int,
    ): LocalDate {
        val julianDayNumber =
            jalaliToJulianDayNumber(
                year = year,
                month = month,
                day = day,
            )

        val parts =
            julianDayNumberToGregorian(
                julianDayNumber,
            )

        return LocalDate.of(
            parts.year,
            parts.month,
            parts.day,
        )
    }

    fun isLeapYear(
        year: Int,
    ): Boolean =
        jalaliCalendar(year).leap == 0

    private fun jalaliCalendar(
        year: Int,
    ): JalaliCalendarState {
        require(year >= breaks.first() && year < breaks.last())

        val gregorianYear =
            year + 621

        var leapJ =
            -14

        var previousBreak =
            breaks[0]

        var jump = 0

        for (index in 1 until breaks.size) {
            val currentBreak =
                breaks[index]

            jump = currentBreak - previousBreak

            if (year < currentBreak) {
                break
            }

            leapJ +=
                (jump / 33) * 8 +
                        ((jump % 33) / 4)

            previousBreak = currentBreak
        }

        var yearsSinceBreak =
            year - previousBreak

        leapJ +=
            (yearsSinceBreak / 33) * 8 +
                    (((yearsSinceBreak % 33) + 3) / 4)

        if (jump % 33 == 4 && jump - yearsSinceBreak == 4) {
            leapJ += 1
        }

        val leapG =
            gregorianYear / 4 -
                    ((gregorianYear / 100 + 1) * 3 / 4) -
                    150

        val march =
            20 + leapJ - leapG

        if (jump - yearsSinceBreak < 6) {
            yearsSinceBreak =
                yearsSinceBreak - jump +
                        ((jump + 4) / 33) * 33
        }

        var leap =
            ((yearsSinceBreak + 1) % 33 - 1) % 4

        if (leap == -1) {
            leap = 4
        }

        return JalaliCalendarState(
            leap = leap,
            gregorianYear = gregorianYear,
            march = march,
        )
    }

    private fun jalaliToJulianDayNumber(
        year: Int,
        month: Int,
        day: Int,
    ): Int {
        val calendar =
            jalaliCalendar(year)

        return gregorianToJulianDayNumber(
            year = calendar.gregorianYear,
            month = 3,
            day = calendar.march,
        ) +
                (month - 1) * 31 -
                ((month / 7) * (month - 7)) +
                day -
                1
    }

    private fun julianDayNumberToJalali(
        julianDayNumber: Int,
    ): JalaliDateParts {
        val gregorian =
            julianDayNumberToGregorian(
                julianDayNumber,
            )

        var year =
            gregorian.year - 621

        val calendar =
            jalaliCalendar(year)

        val dayOfYear =
            julianDayNumber -
                    gregorianToJulianDayNumber(
                        year = gregorian.year,
                        month = 3,
                        day = calendar.march,
                    )

        if (dayOfYear >= 0) {
            return if (dayOfYear <= 185) {
                JalaliDateParts(
                    year = year,
                    month = 1 + dayOfYear / 31,
                    day = dayOfYear % 31 + 1,
                )
            } else {
                val adjustedDay =
                    dayOfYear - 186

                JalaliDateParts(
                    year = year,
                    month = 7 + adjustedDay / 30,
                    day = adjustedDay % 30 + 1,
                )
            }
        }

        year -= 1

        val adjustedDay =
            dayOfYear + 179 +
                    if (calendar.leap == 1) 1 else 0

        return JalaliDateParts(
            year = year,
            month = 7 + adjustedDay / 30,
            day = adjustedDay % 30 + 1,
        )
    }

    private fun gregorianToJulianDayNumber(
        year: Int,
        month: Int,
        day: Int,
    ): Int {
        val adjustedYear =
            year +
                    4800 -
                    ((14 - month) / 12)

        val adjustedMonth =
            month +
                    12 * ((14 - month) / 12) -
                    3

        return day +
                ((153 * adjustedMonth + 2) / 5) +
                365 * adjustedYear +
                adjustedYear / 4 -
                adjustedYear / 100 +
                adjustedYear / 400 -
                32045
    }

    private fun julianDayNumberToGregorian(
        julianDayNumber: Int,
    ): JalaliDateParts {
        val a =
            julianDayNumber + 32044

        val b =
            (4 * a + 3) / 146097

        val c =
            a - (146097 * b) / 4

        val d =
            (4 * c + 3) / 1461

        val e =
            c - (1461 * d) / 4

        val m =
            (5 * e + 2) / 153

        val day =
            e - (153 * m + 2) / 5 + 1

        val month =
            m + 3 - 12 * (m / 10)

        val year =
            100 * b + d - 4800 + (m / 10)

        return JalaliDateParts(
            year = year,
            month = month,
            day = day,
        )
    }
}

private data class JalaliCalendarState(
    val leap: Int,
    val gregorianYear: Int,
    val march: Int,
)
