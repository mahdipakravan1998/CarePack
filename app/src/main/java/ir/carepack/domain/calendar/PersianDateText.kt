package ir.carepack.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate

object PersianDateText {

    fun formatNumeric(
        localDate: LocalDate,
    ): String =
        JalaliPresentationDate
            .from(localDate)
            .formatNumeric()
            .toPersianDigits()

    fun formatMonthYear(
        year: Int,
        month: Int,
    ): String =
        "${monthName(month)} ${year.toString().toPersianDigits()}"

    fun formatFull(
        localDate: LocalDate,
    ): String {
        val jalaliDate =
            JalaliPresentationDate
                .from(localDate)

        return buildString {
            append(
                weekdayName(
                    localDate.dayOfWeek,
                ),
            )
            append("، ")
            append(
                jalaliDate
                    .dayOfMonth
                    .value
                    .toString()
                    .toPersianDigits(),
            )
            append(' ')
            append(
                monthName(
                    jalaliDate
                        .month
                        .value,
                ),
            )
            append(' ')
            append(
                jalaliDate
                    .year
                    .value
                    .toString()
                    .toPersianDigits(),
            )
        }
    }

    fun monthName(
        month: Int,
    ): String =
        PERSIAN_MONTH_NAMES[
            month - 1
        ]

    fun weekdayName(
        dayOfWeek: DayOfWeek,
    ): String =
        when (dayOfWeek) {
            DayOfWeek.SATURDAY ->
                "شنبه"

            DayOfWeek.SUNDAY ->
                "یکشنبه"

            DayOfWeek.MONDAY ->
                "دوشنبه"

            DayOfWeek.TUESDAY ->
                "سه‌شنبه"

            DayOfWeek.WEDNESDAY ->
                "چهارشنبه"

            DayOfWeek.THURSDAY ->
                "پنجشنبه"

            DayOfWeek.FRIDAY ->
                "جمعه"
        }

    fun shortWeekdayName(
        dayOfWeek: DayOfWeek,
    ): String =
        when (dayOfWeek) {
            DayOfWeek.SATURDAY ->
                "ش"

            DayOfWeek.SUNDAY ->
                "ی"

            DayOfWeek.MONDAY ->
                "د"

            DayOfWeek.TUESDAY ->
                "س"

            DayOfWeek.WEDNESDAY ->
                "چ"

            DayOfWeek.THURSDAY ->
                "پ"

            DayOfWeek.FRIDAY ->
                "ج"
        }

    private val PERSIAN_MONTH_NAMES =
        listOf(
            "فروردین",
            "اردیبهشت",
            "خرداد",
            "تیر",
            "مرداد",
            "شهریور",
            "مهر",
            "آبان",
            "آذر",
            "دی",
            "بهمن",
            "اسفند",
        )
}

fun String.toPersianDigits(): String =
    map { character ->
        when (character) {
            '0' -> '۰'
            '1' -> '۱'
            '2' -> '۲'
            '3' -> '۳'
            '4' -> '۴'
            '5' -> '۵'
            '6' -> '۶'
            '7' -> '۷'
            '8' -> '۸'
            '9' -> '۹'
            else -> character
        }
    }.joinToString(
        separator = "",
    )
