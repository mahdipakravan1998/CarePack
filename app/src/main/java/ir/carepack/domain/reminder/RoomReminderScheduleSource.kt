package ir.carepack.domain.reminder

import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.ReminderTargetRow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class RoomReminderScheduleSource(
    private val database: CarePackDatabase,
) : ReminderScheduleSource {

    override suspend fun getAllScheduleSeriesIds():
            Set<String> {
        return database
            .scheduleDao()
            .getAllSeriesIds()
            .toSet()
    }

    override suspend fun hasActiveSchedule():
            Boolean {
        return database
            .scheduleDao()
            .countActiveSeries() > 0
    }

    override suspend fun getNextEligibleTargets(
        now: Instant,
    ): List<ReminderTarget> {
        return database
            .occurrenceDao()
            .getNextReminderTargets(
                nowEpochMillis =
                    now.toEpochMilli(),
            )
            .map { row ->
                row.toDomain()
            }
    }

    override suspend fun getEligibleOccurrence(
        occurrenceId: String,
    ): ReminderTarget? {
        if (occurrenceId.isBlank()) {
            return null
        }

        return database
            .occurrenceDao()
            .getEligibleReminderOccurrence(
                occurrenceId =
                    occurrenceId,
            )
            ?.toDomain()
    }

    private fun ReminderTargetRow.toDomain():
            ReminderTarget {
        require(
            minuteOfDay in
                    MINUTE_OF_DAY_RANGE,
        )

        return ReminderTarget(
            alarmKey =
                AlarmKey.forScheduleSeries(
                    scheduleSeriesId =
                        scheduleSeriesId,
                ),
            occurrenceId =
                occurrenceId,
            scheduledAt =
                Instant.ofEpochMilli(
                    scheduledAtEpochMillis,
                ),
            localDate =
                LocalDate.ofEpochDay(
                    localDateEpochDay,
                ),
            localTime =
                LocalTime.of(
                    minuteOfDay /
                            MINUTES_PER_HOUR,
                    minuteOfDay %
                            MINUTES_PER_HOUR,
                ),
            zoneId =
                zoneId,
            medicationName =
                medicationNameSnapshot,
        )
    }

    private companion object {
        const val MINUTES_PER_HOUR =
            60

        const val MINUTES_PER_DAY =
            24 * MINUTES_PER_HOUR

        val MINUTE_OF_DAY_RANGE =
            0 until MINUTES_PER_DAY
    }
}
