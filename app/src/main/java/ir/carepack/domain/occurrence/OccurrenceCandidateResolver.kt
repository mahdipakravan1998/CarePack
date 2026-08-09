package ir.carepack.domain.occurrence

import ir.carepack.domain.model.ScheduleDefinition
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.domain.schedule.SchedulePattern
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class OccurrenceCandidate(
    val localDate: LocalDate,
    val minuteOfDay: Int,
    val zoneId: String,
    val scheduledAt: Instant,
    val resolutionKind: LocalDateTimeResolutionKind,
)

class OccurrenceCandidateResolver(
    private val localDateTimeResolver:
        CarePackLocalDateTimeResolver =
        CarePackLocalDateTimeResolver(),
) {

    fun resolve(
        definition: ScheduleDefinition,
        anchorDate: LocalDate,
    ): OccurrenceCandidate? {
        if (!isScheduledWeekday(definition.weekdayMask, anchorDate)) {
            return null
        }

        if (
            definition.startDate != null &&
            anchorDate.isBefore(definition.startDate)
        ) {
            return null
        }

        if (
            definition.endDate != null &&
            anchorDate.isAfter(definition.endDate)
        ) {
            return null
        }

        val requestedMinuteOfDay =
            definition.minuteOfDay

        val eligibleMinutes =
            minutesFor(
                schedulePattern = definition.schedulePattern,
                fallbackMinuteOfDay = requestedMinuteOfDay,
                anchorDate = anchorDate,
                startDate = definition.startDate,
            )

        if (requestedMinuteOfDay !in eligibleMinutes) {
            return null
        }

        return candidateForMinute(
            definition = definition,
            anchorDate = anchorDate,
            minuteOfDay = requestedMinuteOfDay,
            zoneId = ZoneId.of(definition.zoneId),
        )
    }

    fun resolveAll(
        definition: ScheduleDefinition,
        anchorDate: LocalDate,
    ): List<OccurrenceCandidate> {
        if (!isScheduledWeekday(definition.weekdayMask, anchorDate)) {
            return emptyList()
        }

        if (
            definition.startDate != null &&
            anchorDate.isBefore(definition.startDate)
        ) {
            return emptyList()
        }

        if (
            definition.endDate != null &&
            anchorDate.isAfter(definition.endDate)
        ) {
            return emptyList()
        }

        val zoneId = ZoneId.of(definition.zoneId)

        return minutesFor(
            schedulePattern = definition.schedulePattern,
            fallbackMinuteOfDay = definition.minuteOfDay,
            anchorDate = anchorDate,
            startDate = definition.startDate,
        ).mapNotNull { minuteOfDay ->
            candidateForMinute(
                definition = definition,
                anchorDate = anchorDate,
                minuteOfDay = minuteOfDay,
                zoneId = zoneId,
            )
        }
    }

    private fun candidateForMinute(
        definition: ScheduleDefinition,
        anchorDate: LocalDate,
        minuteOfDay: Int,
        zoneId: ZoneId,
    ): OccurrenceCandidate? {
        if (minuteOfDay !in 0 until MINUTES_PER_DAY) {
            return null
        }

        val localTime =
            LocalTime.ofSecondOfDay(
                minuteOfDay.toLong() *
                    SECONDS_PER_MINUTE,
            )

        val resolution =
            localDateTimeResolver.resolve(
                localDateTime =
                    LocalDateTime.of(
                        anchorDate,
                        localTime,
                    ),
                zoneId = zoneId,
            )

        if (
            resolution.instant
                .isBefore(definition.effectiveFrom)
        ) {
            return null
        }

        val effectiveUntil = definition.effectiveUntil

        if (
            effectiveUntil != null &&
            !resolution.instant.isBefore(effectiveUntil)
        ) {
            return null
        }

        val resolvedLocalTime =
            resolution.resolvedLocalDateTime.toLocalTime()

        return OccurrenceCandidate(
            localDate =
                resolution.resolvedLocalDateTime.toLocalDate(),
            minuteOfDay =
                resolvedLocalTime.hour * MINUTES_PER_HOUR +
                    resolvedLocalTime.minute,
            zoneId = definition.zoneId,
            scheduledAt = resolution.instant,
            resolutionKind = resolution.kind,
        )
    }

    private fun minutesFor(
        schedulePattern: SchedulePattern,
        fallbackMinuteOfDay: Int,
        anchorDate: LocalDate,
        startDate: LocalDate?,
    ): List<Int> {
        val minutes =
            when (schedulePattern) {
                is FixedTimeSchedule ->
                    schedulePattern
                        .representativeMinutesOfDay
                        .ifEmpty {
                            listOf(fallbackMinuteOfDay)
                        }

                is IntervalSchedule ->
                    schedulePattern
                        .representativeMinutesOfDay
            }
                .filter { minuteOfDay ->
                    minuteOfDay in 0 until MINUTES_PER_DAY
                }
                .distinct()
                .sorted()

        return when (schedulePattern) {
            is FixedTimeSchedule -> minutes
            is IntervalSchedule ->
                if (
                    startDate != null &&
                    anchorDate == startDate
                ) {
                    minutes.filter { minuteOfDay ->
                        minuteOfDay >=
                            schedulePattern.anchorMinuteOfDay
                    }
                } else {
                    minutes
                }
        }
    }

    private fun isScheduledWeekday(
        weekdayMask: Int,
        date: LocalDate,
    ): Boolean {
        val dayBit =
            1 shl (date.dayOfWeek.value - 1)

        return weekdayMask and dayBit != 0
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}
