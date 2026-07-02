package ir.carepack.feature.careplan

import ir.carepack.domain.calendar.JalaliPresentationDate
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanValidation
import ir.carepack.domain.careplan.ValidationResult
import ir.carepack.domain.careplan.errorsOrEmpty
import ir.carepack.domain.careplan.valueOrNull
import ir.carepack.domain.occurrence.DEFAULT_SCHEDULE_PREVIEW_DAY_COUNT
import ir.carepack.domain.occurrence.SchedulePreviewRequest
import ir.carepack.domain.occurrence.SchedulePreviewResolver
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.domain.schedule.SchedulePattern
import ir.carepack.domain.schedule.SchedulePatternRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

enum class ScheduleInputMode {
    FIXED_TIMES,
    EVERY_X_HOURS,
}

data class SchedulePreviewItem(
    val localDate: LocalDate,
    val dayOfWeek: DayOfWeek,
    val minuteOfDay: Int,
)

data class ScheduleFormUiState(
    val weekdays: Set<DayOfWeek>,
    val minutesOfDay: List<Int>,
    val timeDraft: String,
    val startDateText: String,
    val endDateText: String,
    val zoneId: String,
    val previewEffectiveFrom: Instant,
    val inputMode: ScheduleInputMode = ScheduleInputMode.FIXED_TIMES,
    val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
    val intervalAnchorDraft: String = DEFAULT_ANCHOR_TIME,
    val errors: Map<CarePlanField, String> = emptyMap(),
)

internal data class ParsedScheduleDates(
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val errors: Map<CarePlanField, String>,
)

internal fun ScheduleFormUiState.toggleWeekday(
    day: DayOfWeek,
): ScheduleFormUiState {
    val updatedWeekdays =
        weekdays.toMutableSet()

    if (!updatedWeekdays.add(day)) {
        updatedWeekdays.remove(day)
    }

    return copy(
        weekdays =
            updatedWeekdays,
        errors =
            errors -
                    CarePlanField.WEEKDAYS,
    )
}

internal fun ScheduleFormUiState.withInputMode(
    mode: ScheduleInputMode,
): ScheduleFormUiState =
    copy(
        inputMode = mode,
        errors =
            errors -
                    CarePlanField.TIMES,
    )

internal fun ScheduleFormUiState.withTimeDraft(
    value: String,
): ScheduleFormUiState =
    copy(
        timeDraft = value,
        errors =
            errors -
                    CarePlanField.TIMES,
    )

internal fun ScheduleFormUiState.addDraftTime():
        ScheduleFormUiState =
    when (
        val result =
            CarePlanValidation
                .validateScheduleTime(
                    rawValue =
                        timeDraft,
                    existingMinutesOfDay =
                        minutesOfDay,
                )
    ) {
        is ValidationResult.Valid -> {
            copy(
                minutesOfDay =
                    (
                            minutesOfDay +
                                    result.value
                            )
                        .distinct()
                        .sorted(),
                timeDraft = "",
                errors =
                    errors -
                            CarePlanField.TIMES,
            )
        }

        is ValidationResult.Invalid -> {
            copy(
                errors =
                    errors +
                            result
                                .errors
                                .toFieldErrors(),
            )
        }
    }

internal fun ScheduleFormUiState.removeTime(
    minuteOfDay: Int,
): ScheduleFormUiState =
    copy(
        minutesOfDay =
            minutesOfDay -
                    minuteOfDay,
        errors =
            errors -
                    CarePlanField.TIMES,
    )

internal fun ScheduleFormUiState.withIntervalHours(
    hours: Int,
): ScheduleFormUiState =
    copy(
        intervalHours = hours,
        errors =
            errors -
                    CarePlanField.TIMES,
    )

internal fun ScheduleFormUiState.withIntervalHoursDefault():
        ScheduleFormUiState =
    copy(
        intervalHours =
            if (
                intervalHours in
                SchedulePatternRules
                    .allowedIntervalHours
            ) {
                intervalHours
            } else {
                DEFAULT_INTERVAL_HOURS
            },
    )

internal fun ScheduleFormUiState.withIntervalAnchorDraft(
    value: String,
): ScheduleFormUiState =
    copy(
        intervalAnchorDraft =
            value.keepHourMinuteDraftCharacters(),
        errors =
            errors -
                    CarePlanField.TIMES,
    )

internal fun ScheduleFormUiState.withStartDate(
    value: String,
): ScheduleFormUiState =
    copy(
        startDateText =
            value.keepDateDraftCharacters(),
        errors =
            errors -
                    CarePlanField.START_DATE,
    )

internal fun ScheduleFormUiState.withEndDate(
    value: String,
): ScheduleFormUiState =
    copy(
        endDateText =
            value.keepDateDraftCharacters(),
        errors =
            errors -
                    CarePlanField.END_DATE,
    )

internal fun ScheduleFormUiState.parseDates():
        ParsedScheduleDates {
    val result =
        CarePlanValidation
            .parseScheduleDates(
                rawStartDate =
                    startDateText,
                rawEndDate =
                    endDateText,
            )

    val value =
        result.valueOrNull()

    return ParsedScheduleDates(
        startDate =
            value?.startDate,
        endDate =
            value?.endDate,
        errors =
            result
                .errorsOrEmpty()
                .toFieldErrors(),
    )
}

internal fun ScheduleFormUiState.withValidationErrors(
    validationErrors:
    Map<CarePlanField, String>,
): ScheduleFormUiState =
    copy(
        errors =
            validationErrors
                .filterKeys {
                    it in SCHEDULE_FIELDS
                },
    )

internal fun ScheduleFormUiState.withDateErrors(
    dateErrors:
    Map<CarePlanField, String>,
): ScheduleFormUiState =
    copy(
        errors =
            errors +
                    dateErrors,
    )

internal fun ScheduleFormUiState.withPreviewEffectiveFrom(
    effectiveFrom: Instant,
): ScheduleFormUiState =
    copy(
        previewEffectiveFrom =
            effectiveFrom,
    )

internal fun ScheduleFormUiState.clearErrors():
        ScheduleFormUiState =
    copy(
        errors =
            emptyMap(),
    )

internal fun ScheduleFormUiState.effectiveMinutesOfDay():
        List<Int> =
    when (inputMode) {
        ScheduleInputMode.FIXED_TIMES -> {
            minutesOfDay
                .distinct()
                .sorted()
        }

        ScheduleInputMode.EVERY_X_HOURS -> {
            toSchedulePattern()
                .representativeMinutesOfDay
        }
    }

internal fun ScheduleFormUiState.toSchedulePattern():
        SchedulePattern =
    when (inputMode) {
        ScheduleInputMode.FIXED_TIMES -> {
            FixedTimeSchedule(
                minutesOfDay =
                    minutesOfDay,
            )
        }

        ScheduleInputMode.EVERY_X_HOURS -> {
            IntervalSchedule(
                intervalHours =
                    intervalHours,
                anchorMinuteOfDay =
                    intervalAnchorMinuteOrDefault(),
            )
        }
    }

internal fun ScheduleFormUiState.previewItems(
    anchorDate: LocalDate,
    dayCount: Int = PREVIEW_DAY_COUNT,
): List<SchedulePreviewItem> {
    val parsedDates =
        parseDates()

    if (
        parsedDates
            .errors
            .isNotEmpty()
    ) {
        return emptyList()
    }

    val pattern =
        toSchedulePattern()

    if (
        weekdays.isEmpty() ||
        pattern
            .representativeMinutesOfDay
            .isEmpty()
    ) {
        return emptyList()
    }

    return SchedulePreviewResolver()
        .resolve(
            SchedulePreviewRequest(
                weekdays =
                    weekdays,
                schedulePattern =
                    pattern,
                zoneId =
                    zoneId,
                effectiveFrom =
                    previewEffectiveFrom,
                startDate =
                    parsedDates.startDate,
                endDate =
                    parsedDates.endDate,
                anchorDate =
                    anchorDate,
                dayCount =
                    dayCount,
            ),
        )
        .map { occurrence ->
            SchedulePreviewItem(
                localDate =
                    occurrence.localDate,
                dayOfWeek =
                    occurrence.dayOfWeek,
                minuteOfDay =
                    occurrence.minuteOfDay,
            )
        }
}

internal fun Int.toHourMinuteText():
        String =
    SchedulePatternRules
        .localTimeFrom(
            this,
        )
        .toHourMinuteText()

internal fun LocalTime.toMinuteOfDay():
        Int =
    SchedulePatternRules
        .minuteOfDayFrom(
            this,
        )

internal fun LocalTime.toHourMinuteText():
        String =
    "%02d:%02d".format(
        Locale.ROOT,
        hour,
        minute,
    )

internal fun LocalDate.toJalaliDateText():
        String =
    JalaliPresentationDate
        .from(
            this,
        )
        .formatNumeric()

private fun ScheduleFormUiState.intervalAnchorMinuteOrDefault():
        Int =
    parseHourMinuteDraft(
        intervalAnchorDraft,
    ) ?: DEFAULT_ANCHOR_MINUTE

private fun parseHourMinuteDraft(
    rawValue: String,
): Int? {
    val normalized =
        rawValue
            .trim()
            .normalizePersianDigits()
            .replace(
                oldChar = '：',
                newChar = ':',
            )

    val match =
        HOUR_MINUTE_PATTERN
            .matchEntire(
                normalized,
            ) ?: return null

    val hour =
        match.groupValues[1].toInt()

    val minute =
        match.groupValues[2].toInt()

    return hour * MINUTES_PER_HOUR + minute
}

private fun String.keepHourMinuteDraftCharacters():
        String =
    filter { character ->
        character.isDigit() ||
                character == ':' ||
                character == '：'
    }
        .replace(
            oldChar = '：',
            newChar = ':',
        )
        .take(
            MAX_TIME_DRAFT_LENGTH,
        )

private fun String.keepDateDraftCharacters():
        String =
    filter { character ->
        character.isDigit() ||
                character == '/' ||
                character == '-'
    }
        .take(
            MAX_DATE_DRAFT_LENGTH,
        )

private fun String.normalizePersianDigits():
        String =
    map { character ->
        when (character) {
            '۰' -> '0'
            '۱' -> '1'
            '۲' -> '2'
            '۳' -> '3'
            '۴' -> '4'
            '۵' -> '5'
            '۶' -> '6'
            '۷' -> '7'
            '۸' -> '8'
            '۹' -> '9'
            '٠' -> '0'
            '١' -> '1'
            '٢' -> '2'
            '٣' -> '3'
            '٤' -> '4'
            '٥' -> '5'
            '٦' -> '6'
            '٧' -> '7'
            '٨' -> '8'
            '٩' -> '9'
            else -> character
        }
    }
        .joinToString(
            separator = "",
        )

private val SCHEDULE_FIELDS =
    setOf(
        CarePlanField.WEEKDAYS,
        CarePlanField.TIMES,
        CarePlanField.START_DATE,
        CarePlanField.END_DATE,
        CarePlanField.ZONE_ID,
    )

private val HOUR_MINUTE_PATTERN =
    Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

private const val MINUTES_PER_HOUR = 60
private const val DEFAULT_INTERVAL_HOURS = 8
private const val DEFAULT_ANCHOR_TIME = "08:00"
private const val DEFAULT_ANCHOR_MINUTE = 8 * 60
private const val PREVIEW_DAY_COUNT = DEFAULT_SCHEDULE_PREVIEW_DAY_COUNT
private const val MAX_TIME_DRAFT_LENGTH = 5
private const val MAX_DATE_DRAFT_LENGTH = 10
