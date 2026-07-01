package ir.carepack.domain.schedule

import java.time.LocalTime

sealed interface SchedulePattern {
    val representativeMinutesOfDay: List<Int>
}

data class FixedTimeSchedule(
    val minutesOfDay: List<Int>,
) : SchedulePattern {
    override val representativeMinutesOfDay: List<Int> =
        minutesOfDay
            .distinct()
            .sorted()
}

data class IntervalSchedule(
    val intervalHours: Int,
    val anchorMinuteOfDay: Int,
) : SchedulePattern {
    override val representativeMinutesOfDay: List<Int> =
        if (
            intervalHours in ALLOWED_INTERVAL_HOURS &&
            anchorMinuteOfDay in MINUTE_OF_DAY_RANGE
        ) {
            intervalMinutesWithinDayInternal(
                intervalHours = intervalHours,
                anchorMinuteOfDay = anchorMinuteOfDay,
            )
        } else {
            emptyList()
        }
}

object SchedulePatternRules {
    val allowedIntervalHours: Set<Int> =
        ALLOWED_INTERVAL_HOURS

    fun isValidMinuteOfDay(
        minuteOfDay: Int,
    ): Boolean =
        minuteOfDay in MINUTE_OF_DAY_RANGE

    fun isAllowedIntervalHours(
        intervalHours: Int,
    ): Boolean =
        intervalHours in ALLOWED_INTERVAL_HOURS

    fun minuteOfDayFrom(
        localTime: LocalTime,
    ): Int =
        localTime.hour * MINUTES_PER_HOUR +
                localTime.minute

    fun localTimeFrom(
        minuteOfDay: Int,
    ): LocalTime {
        require(isValidMinuteOfDay(minuteOfDay))

        return LocalTime.of(
            minuteOfDay / MINUTES_PER_HOUR,
            minuteOfDay % MINUTES_PER_HOUR,
        )
    }

    fun intervalMinutesWithinDay(
        intervalHours: Int,
        anchorMinuteOfDay: Int,
    ): List<Int> {
        require(isAllowedIntervalHours(intervalHours))
        require(isValidMinuteOfDay(anchorMinuteOfDay))

        return intervalMinutesWithinDayInternal(
            intervalHours = intervalHours,
            anchorMinuteOfDay = anchorMinuteOfDay,
        )
    }
}

private fun intervalMinutesWithinDayInternal(
    intervalHours: Int,
    anchorMinuteOfDay: Int,
): List<Int> {
    val intervalMinutes =
        intervalHours * MINUTES_PER_HOUR

    return (0 until MINUTES_PER_DAY)
        .filter { minuteOfDay ->
            positiveModulo(
                value = minuteOfDay - anchorMinuteOfDay,
                divisor = intervalMinutes,
            ) == 0
        }
}

private fun positiveModulo(
    value: Int,
    divisor: Int,
): Int =
    ((value % divisor) + divisor) % divisor

private val ALLOWED_INTERVAL_HOURS =
    setOf(
        6,
        8,
        12,
    )

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private val MINUTE_OF_DAY_RANGE = 0 until MINUTES_PER_DAY
