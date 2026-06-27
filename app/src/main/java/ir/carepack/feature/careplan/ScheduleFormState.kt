package ir.carepack.feature.careplan

import ir.carepack.domain.careplan.CarePlanField
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

data class ScheduleFormUiState(
    val weekdays: Set<DayOfWeek>,
    val minutesOfDay: List<Int>,
    val timeDraft: String,
    val startDateText: String,
    val endDateText: String,
    val zoneId: String,
)

internal sealed interface AddScheduleTimeResult {
    data class Updated(val state: ScheduleFormUiState) : AddScheduleTimeResult
    data class Invalid(val message: String) : AddScheduleTimeResult
}

internal data class ParsedScheduleDates(
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val errors: Map<CarePlanField, String>,
)

internal fun ScheduleFormUiState.toggleWeekday(day: DayOfWeek): ScheduleFormUiState {
    val updated = weekdays.toMutableSet()
    if (!updated.add(day)) {
        updated.remove(day)
    }
    return copy(weekdays = updated)
}

internal fun ScheduleFormUiState.withTimeDraft(value: String): ScheduleFormUiState =
    copy(timeDraft = value)

internal fun ScheduleFormUiState.addDraftTime(): AddScheduleTimeResult {
    val minuteOfDay = parseHourMinute(timeDraft)
        ?: return AddScheduleTimeResult.Invalid(INVALID_TIME_MESSAGE)

    if (minuteOfDay in minutesOfDay) {
        return AddScheduleTimeResult.Invalid(DUPLICATE_TIME_MESSAGE)
    }

    return AddScheduleTimeResult.Updated(
        copy(
            minutesOfDay = (minutesOfDay + minuteOfDay).sorted(),
            timeDraft = "",
        ),
    )
}

internal fun ScheduleFormUiState.removeTime(minuteOfDay: Int): ScheduleFormUiState =
    copy(minutesOfDay = minutesOfDay - minuteOfDay)

internal fun ScheduleFormUiState.withStartDate(value: String): ScheduleFormUiState =
    copy(startDateText = value)

internal fun ScheduleFormUiState.withEndDate(value: String): ScheduleFormUiState =
    copy(endDateText = value)

internal fun ScheduleFormUiState.parseDates(): ParsedScheduleDates {
    val startDate = parseOptionalDate(startDateText)
    val endDate = parseOptionalDate(endDateText)
    val errors = buildMap {
        if (startDateText.isNotBlank() && startDate == null) {
            put(CarePlanField.START_DATE, INVALID_START_DATE_MESSAGE)
        }
        if (endDateText.isNotBlank() && endDate == null) {
            put(CarePlanField.END_DATE, INVALID_END_DATE_MESSAGE)
        }
    }
    return ParsedScheduleDates(startDate, endDate, errors)
}

internal fun LocalTime.toMinuteOfDay(): Int = hour * MINUTES_PER_HOUR + minute

internal fun parseHourMinute(value: String): Int? {
    val match = HOUR_MINUTE_REGEX.matchEntire(value.trim()) ?: return null
    return match.groupValues[1].toInt() * MINUTES_PER_HOUR + match.groupValues[2].toInt()
}

internal fun parseOptionalDate(value: String): LocalDate? {
    val normalized = value.trim()
    if (normalized.isEmpty()) {
        return null
    }
    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}

internal fun Int.toHourMinuteText(): String {
    require(this in 0 until MINUTES_PER_DAY)
    return String.format(
        Locale.ROOT,
        "%02d:%02d",
        this / MINUTES_PER_HOUR,
        this % MINUTES_PER_HOUR,
    )
}

private val HOUR_MINUTE_REGEX = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val INVALID_TIME_MESSAGE =
    "زمان باید به شکل معتبر ۲۴ ساعته مانند ۱۴:۳۰ باشد."
private const val DUPLICATE_TIME_MESSAGE = "این زمان قبلاً اضافه شده است."
private const val INVALID_START_DATE_MESSAGE = "تاریخ شروع باید به شکل YYYY-MM-DD باشد."
private const val INVALID_END_DATE_MESSAGE = "تاریخ پایان باید به شکل YYYY-MM-DD باشد."
