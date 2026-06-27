package ir.carepack.feature.careplan

import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanValidation
import ir.carepack.domain.careplan.ValidationResult
import ir.carepack.domain.careplan.errorsOrEmpty
import ir.carepack.domain.careplan.valueOrNull
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
    val errors: Map<CarePlanField, String> = emptyMap(),
)

internal data class ParsedScheduleDates(
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val errors: Map<CarePlanField, String>,
)

internal fun ScheduleFormUiState.toggleWeekday(day: DayOfWeek): ScheduleFormUiState {
    val updatedWeekdays = weekdays.toMutableSet()
    if (!updatedWeekdays.add(day)) {
        updatedWeekdays.remove(day)
    }
    return copy(
        weekdays = updatedWeekdays,
        errors = errors - CarePlanField.WEEKDAYS,
    )
}

internal fun ScheduleFormUiState.withTimeDraft(value: String): ScheduleFormUiState = copy(
    timeDraft = value,
    errors = errors - CarePlanField.TIMES,
)

internal fun ScheduleFormUiState.addDraftTime(): ScheduleFormUiState = when (
    val result = CarePlanValidation.validateScheduleTime(
        rawValue = timeDraft,
        existingMinutesOfDay = minutesOfDay,
    )
) {
    is ValidationResult.Valid -> copy(
        minutesOfDay = (minutesOfDay + result.value).sorted(),
        timeDraft = "",
        errors = errors - CarePlanField.TIMES,
    )

    is ValidationResult.Invalid -> copy(
        errors = errors + result.errors.toFieldErrors(),
    )
}

internal fun ScheduleFormUiState.removeTime(minuteOfDay: Int): ScheduleFormUiState = copy(
    minutesOfDay = minutesOfDay - minuteOfDay,
    errors = errors - CarePlanField.TIMES,
)

internal fun ScheduleFormUiState.withStartDate(value: String): ScheduleFormUiState = copy(
    startDateText = value,
    errors = errors - CarePlanField.START_DATE,
)

internal fun ScheduleFormUiState.withEndDate(value: String): ScheduleFormUiState = copy(
    endDateText = value,
    errors = errors - CarePlanField.END_DATE,
)

internal fun ScheduleFormUiState.parseDates(): ParsedScheduleDates {
    val result = CarePlanValidation.parseScheduleDates(
        rawStartDate = startDateText,
        rawEndDate = endDateText,
    )
    val value = result.valueOrNull()

    return ParsedScheduleDates(
        startDate = value?.startDate,
        endDate = value?.endDate,
        errors = result.errorsOrEmpty().toFieldErrors(),
    )
}

internal fun ScheduleFormUiState.withValidationErrors(
    validationErrors: Map<CarePlanField, String>,
): ScheduleFormUiState = copy(
    errors = validationErrors.filterKeys { it in SCHEDULE_FIELDS },
)

internal fun ScheduleFormUiState.withDateErrors(
    dateErrors: Map<CarePlanField, String>,
): ScheduleFormUiState = copy(errors = errors + dateErrors)

internal fun ScheduleFormUiState.clearErrors(): ScheduleFormUiState = copy(errors = emptyMap())

internal fun LocalTime.toMinuteOfDay(): Int = hour * MINUTES_PER_HOUR + minute

internal fun Int.toHourMinuteText(): String {
    require(this in 0 until MINUTES_PER_DAY)
    return String.format(
        Locale.ROOT,
        "%02d:%02d",
        this / MINUTES_PER_HOUR,
        this % MINUTES_PER_HOUR,
    )
}

private val SCHEDULE_FIELDS = setOf(
    CarePlanField.WEEKDAYS,
    CarePlanField.TIMES,
    CarePlanField.START_DATE,
    CarePlanField.END_DATE,
    CarePlanField.ZONE_ID,
)

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
