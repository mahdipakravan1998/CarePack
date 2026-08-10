package ir.carepack.core.time

import java.time.LocalTime

internal fun Int.requireLocalTime(): LocalTime {
    require(this in 0 until MINUTES_PER_DAY)
    return LocalTime.of(this / MINUTES_PER_HOUR, this % MINUTES_PER_HOUR)
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
