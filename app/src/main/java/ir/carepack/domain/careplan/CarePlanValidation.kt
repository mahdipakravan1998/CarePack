package ir.carepack.domain.careplan

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

internal sealed interface ValidationResult<out T> {

    data class Valid<T>(
        val value: T,
    ) : ValidationResult<T>

    data class Invalid(
        val errors: List<CarePlanValidationError>,
    ) : ValidationResult<Nothing>
}

internal data class ValidatedMedicationText(
    val name: String,
    val instruction: String,
)

internal data class ValidatedScheduleDefinition(
    val weekdayMask: Int,
    val minutesOfDay: List<Int>,
    val zoneId: ZoneId,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
)

internal object CarePlanValidation {

    fun validateRecipientName(
        rawValue: String,
    ): ValidationResult<String> {
        return validateRequiredBoundedText(
            rawValue = rawValue,
            field = CarePlanField.RECIPIENT_NAME,
            maximumLength =
                CarePlanLimits
                    .RECIPIENT_NAME_MAX_LENGTH,
            emptyMessage =
                "نام فرد تحت مراقبت نمی‌تواند خالی باشد.",
            tooLongMessage =
                "نام فرد تحت مراقبت نباید بیشتر از " +
                        "${CarePlanLimits.RECIPIENT_NAME_MAX_LENGTH} " +
                        "نویسه باشد.",
        )
    }

    fun validateMedicationText(
        rawName: String,
        rawInstruction: String,
    ): ValidationResult<ValidatedMedicationText> {
        val nameResult =
            validateRequiredBoundedText(
                rawValue = rawName,
                field =
                    CarePlanField.MEDICATION_NAME,
                maximumLength =
                    CarePlanLimits
                        .MEDICATION_NAME_MAX_LENGTH,
                emptyMessage =
                    "نام دارو نمی‌تواند خالی باشد.",
                tooLongMessage =
                    "نام دارو نباید بیشتر از " +
                            "${CarePlanLimits.MEDICATION_NAME_MAX_LENGTH} " +
                            "نویسه باشد.",
            )

        val instructionResult =
            validateRequiredBoundedText(
                rawValue = rawInstruction,
                field = CarePlanField.INSTRUCTION,
                maximumLength =
                    CarePlanLimits
                        .INSTRUCTION_MAX_LENGTH,
                emptyMessage =
                    "دستور مصرف یا توضیح مراقبت نمی‌تواند خالی باشد.",
                tooLongMessage =
                    "دستور مصرف نباید بیشتر از " +
                            "${CarePlanLimits.INSTRUCTION_MAX_LENGTH} " +
                            "نویسه باشد.",
            )

        val errors =
            nameResult.errorsOrEmpty() +
                    instructionResult.errorsOrEmpty()

        if (errors.isNotEmpty()) {
            return ValidationResult.Invalid(
                errors = errors,
            )
        }

        return ValidationResult.Valid(
            ValidatedMedicationText(
                name =
                    checkNotNull(
                        nameResult.valueOrNull(),
                    ),
                instruction =
                    checkNotNull(
                        instructionResult.valueOrNull(),
                    ),
            ),
        )
    }

    fun validateSchedule(
        weekdays: Set<DayOfWeek>,
        minutesOfDay: List<Int>,
        startDate: LocalDate?,
        endDate: LocalDate?,
        rawZoneId: String,
    ): ValidationResult<ValidatedScheduleDefinition> {
        val errors =
            mutableListOf<CarePlanValidationError>()

        if (weekdays.isEmpty()) {
            errors += CarePlanValidationError(
                field = CarePlanField.WEEKDAYS,
                message =
                    "حداقل یک روز هفته را انتخاب کنید.",
            )
        }

        if (minutesOfDay.isEmpty()) {
            errors += CarePlanValidationError(
                field = CarePlanField.TIMES,
                message =
                    "حداقل یک زمان را اضافه کنید.",
            )
        }

        if (
            minutesOfDay.any {
                it !in MINUTE_OF_DAY_RANGE
            }
        ) {
            errors += CarePlanValidationError(
                field = CarePlanField.TIMES,
                message =
                    "یک یا چند زمان معتبر نیست.",
            )
        }

        if (
            minutesOfDay
                .groupingBy { it }
                .eachCount()
                .any { (_, count) -> count > 1 }
        ) {
            errors += CarePlanValidationError(
                field = CarePlanField.TIMES,
                message =
                    "زمان‌های تکراری مجاز نیستند.",
            )
        }

        if (
            startDate != null &&
            endDate != null &&
            startDate.isAfter(endDate)
        ) {
            errors += CarePlanValidationError(
                field = CarePlanField.END_DATE,
                message =
                    "تاریخ پایان نمی‌تواند قبل از تاریخ شروع باشد.",
            )
        }

        val zoneId =
            runCatching {
                ZoneId.of(rawZoneId.trim())
            }.getOrNull()

        if (zoneId == null) {
            errors += CarePlanValidationError(
                field = CarePlanField.ZONE_ID,
                message =
                    "منطقه زمانی معتبر نیست.",
            )
        }

        if (errors.isNotEmpty()) {
            return ValidationResult.Invalid(
                errors = errors.distinct(),
            )
        }

        val weekdayMask =
            weekdays.fold(0) { mask, day ->
                mask or (
                        1 shl (
                                day.value - 1
                                )
                        )
            }

        return ValidationResult.Valid(
            ValidatedScheduleDefinition(
                weekdayMask = weekdayMask,
                minutesOfDay =
                    minutesOfDay
                        .distinct()
                        .sorted(),
                zoneId = checkNotNull(zoneId),
                startDate = startDate,
                endDate = endDate,
            ),
        )
    }

    fun isValidWeekdayMask(
        weekdayMask: Int,
    ): Boolean {
        return weekdayMask != 0 &&
                weekdayMask and
                VALID_WEEKDAY_MASK ==
                weekdayMask
    }

    private fun validateRequiredBoundedText(
        rawValue: String,
        field: CarePlanField,
        maximumLength: Int,
        emptyMessage: String,
        tooLongMessage: String,
    ): ValidationResult<String> {
        val normalized =
            rawValue.trim()

        if (normalized.isEmpty()) {
            return ValidationResult.Invalid(
                errors = listOf(
                    CarePlanValidationError(
                        field = field,
                        message = emptyMessage,
                    ),
                ),
            )
        }

        if (
            normalized.characterCount() >
            maximumLength
        ) {
            return ValidationResult.Invalid(
                errors = listOf(
                    CarePlanValidationError(
                        field = field,
                        message = tooLongMessage,
                    ),
                ),
            )
        }

        return ValidationResult.Valid(
            value = normalized,
        )
    }

    private fun String.characterCount():
            Int {
        return codePointCount(
            0,
            length,
        )
    }

    private val MINUTE_OF_DAY_RANGE =
        0 until MINUTES_PER_DAY

    private const val MINUTES_PER_DAY =
        24 * 60

    private const val VALID_WEEKDAY_MASK =
        0b1111111
}

internal fun <T> ValidationResult<T>
        .valueOrNull(): T? {
    return when (this) {
        is ValidationResult.Valid -> value
        is ValidationResult.Invalid -> null
    }
}

internal fun ValidationResult<*>
        .errorsOrEmpty():
        List<CarePlanValidationError> {
    return when (this) {
        is ValidationResult.Valid ->
            emptyList()

        is ValidationResult.Invalid ->
            errors
    }
}
