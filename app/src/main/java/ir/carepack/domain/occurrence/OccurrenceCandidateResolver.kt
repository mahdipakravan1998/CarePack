package ir.carepack.domain.occurrence

import ir.carepack.domain.model.ScheduleDefinition
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

        val zoneId = ZoneId.of(definition.zoneId)

        val localTime = LocalTime.ofSecondOfDay(
            definition.minuteOfDay.toLong() * 60L,
        )

        val scheduledAt = LocalDateTime
            .of(anchorDate, localTime)
            .atZone(zoneId)
            .toInstant()

        if (scheduledAt.isBefore(definition.effectiveFrom)) {
            return null
        }

        val effectiveUntil = definition.effectiveUntil

        if (
            effectiveUntil != null &&
            !scheduledAt.isBefore(effectiveUntil)
        ) {
            return null
        }

        return OccurrenceCandidate(
            localDate = anchorDate,
            minuteOfDay = definition.minuteOfDay,
            zoneId = definition.zoneId,
            scheduledAt = scheduledAt,
        )
    }

    private fun isScheduledWeekday(
        weekdayMask: Int,
        date: LocalDate,
    ): Boolean {
        val dayBit = 1 shl (date.dayOfWeek.value - 1)
        return weekdayMask and dayBit != 0
    }
}