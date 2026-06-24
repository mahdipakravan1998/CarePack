package ir.carepack.domain.today

import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.OccurrenceReadRow
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TodayItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTodayQueryService(
    private val database: CarePackDatabase,
) : TodayQueryService {

    override fun observeToday(
        localDate: LocalDate,
    ): Flow<List<TodayItem>> {
        return database
            .occurrenceDao()
            .observeForDate(localDate.toEpochDay())
            .map { rows ->
                rows.map(OccurrenceReadRow::toTodayItem)
            }
    }

    override fun observeOccurrence(
        occurrenceId: String,
    ): Flow<OccurrenceDetail?> {
        return database
            .occurrenceDao()
            .observeById(occurrenceId)
            .map { row ->
                row?.toOccurrenceDetail()
            }
    }
}

private fun OccurrenceReadRow.toTodayItem(): TodayItem {
    return TodayItem(
        occurrenceId = occurrenceId,
        localDate = LocalDate.ofEpochDay(localDateEpochDay),
        localTime = minuteOfDay.toLocalTime(),
        medicationName = medicationNameSnapshot,
        medicationInstruction = medicationInstructionSnapshot,
        lifecycle = OccurrenceLifecycle.valueOf(lifecycle),
        reportState = reportState?.let(
            CaregiverReportState::valueOf,
        ),
    )
}

private fun OccurrenceReadRow.toOccurrenceDetail():
        OccurrenceDetail {
    return OccurrenceDetail(
        occurrenceId = occurrenceId,
        localDate = LocalDate.ofEpochDay(localDateEpochDay),
        localTime = minuteOfDay.toLocalTime(),
        scheduledAt = Instant.ofEpochMilli(
            scheduledAtEpochMillis,
        ),
        medicationName = medicationNameSnapshot,
        medicationInstruction = medicationInstructionSnapshot,
        lifecycle = OccurrenceLifecycle.valueOf(lifecycle),
        reportState = reportState?.let(
            CaregiverReportState::valueOf,
        ),
    )
}

private fun Int.toLocalTime(): LocalTime {
    return LocalTime.of(
        this / MINUTES_PER_HOUR,
        this % MINUTES_PER_HOUR,
    )
}

private const val MINUTES_PER_HOUR = 60