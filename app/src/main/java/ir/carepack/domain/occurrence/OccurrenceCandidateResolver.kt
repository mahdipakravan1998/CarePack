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
)

class OccurrenceCandidateResolver {
    fun resolve(
        definition: ScheduleDefinition,
        anchorDate: LocalDate,
    ): OccurrenceCandidate? =
        resolveAll(
            definition =
                definition,
            anchorDate =
                anchorDate,
        ).singleOrNull {
            it.minuteOfDay ==
                    definition.minuteOfDay
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

        val zoneId =
            ZoneId.of(
                definition.zoneId,
            )

        return minutesFor(
            schedulePattern =
                definition.schedulePattern,
            fallbackMinuteOfDay =
                definition.minuteOfDay,
        )
            .mapNotNull { minuteOfDay ->
                candidateForMinute(
                    definition =
                        definition,
                    anchorDate =
                        anchorDate,
                    minuteOfDay =
                        minuteOfDay,
                    zoneId =
                        zoneId,
                )
            }
    }

    private fun candidateForMinute(
        definition: ScheduleDefinition,
        anchorDate: LocalDate,
        minuteOfDay: Int,
        zoneId: ZoneId,
    ): OccurrenceCandidate? {
        val localTime =
            LocalTime.ofSecondOfDay(
                minuteOfDay.toLong() * 60L,
            )

        val scheduledAt =
            LocalDateTime
                .of(
                    anchorDate,
                    localTime,
                )
                .atZone(
                    zoneId,
                )
                .toInstant()

        if (scheduledAt.isBefore(definition.effectiveFrom)) {
            return null
        }

        val effectiveUntil =
            definition.effectiveUntil

        if (
            effectiveUntil != null &&
            !scheduledAt.isBefore(effectiveUntil)
        ) {
            return null
        }

        return OccurrenceCandidate(
            localDate = anchorDate,
            minuteOfDay = minuteOfDay,
            zoneId = definition.zoneId,
            scheduledAt = scheduledAt,
        )
    }

    private fun minutesFor(
        schedulePattern: SchedulePattern,
        fallbackMinuteOfDay: Int,
    ): List<Int> =
        when (schedulePattern) {
            is FixedTimeSchedule ->
                schedulePattern
                    .representativeMinutesOfDay
                    .ifEmpty {
                        listOf(
                            fallbackMinuteOfDay,
                        )
                    }

            is IntervalSchedule ->
                schedulePattern
                    .representativeMinutesOfDay
        }

    private fun isScheduledWeekday(
        weekdayMask: Int,
        date: LocalDate,
    ): Boolean {
        val dayBit =
            1 shl (date.dayOfWeek.value - 1)

        return weekdayMask and dayBit != 0
    }
}
