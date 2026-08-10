package ir.carepack.data.service

import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.domain.schedule.SchedulePattern

internal data class PersistedSchedulePattern(
    val patternType: String,
    val intervalHours: Int?,
    val anchorMinuteOfDay: Int?,
)

/** The single translation boundary between schedule domain values and Room columns. */
internal object SchedulePatternPersistenceCodec {

    private const val FIXED_TIMES = "FIXED_TIMES"
    private const val EVERY_X_HOURS = "EVERY_X_HOURS"

    fun encode(pattern: SchedulePattern): PersistedSchedulePattern = when (pattern) {
            is FixedTimeSchedule ->
                PersistedSchedulePattern(
                    patternType = FIXED_TIMES,
                    intervalHours = null,
                    anchorMinuteOfDay = null,
                )

            is IntervalSchedule ->
                PersistedSchedulePattern(
                    patternType = EVERY_X_HOURS,
                    intervalHours = pattern.intervalHours,
                    anchorMinuteOfDay = pattern.anchorMinuteOfDay,
                )
        }

    fun decode(
        patternType: String,
        intervalHours: Int?,
        anchorMinuteOfDay: Int?,
        fixedMinutesOfDay: List<Int>,
    ): SchedulePattern = when (patternType) {
            EVERY_X_HOURS ->
                IntervalSchedule(
                    intervalHours = checkNotNull(intervalHours),
                    anchorMinuteOfDay = checkNotNull(anchorMinuteOfDay),
                )

            else -> FixedTimeSchedule(minutesOfDay = fixedMinutesOfDay)
        }
}
