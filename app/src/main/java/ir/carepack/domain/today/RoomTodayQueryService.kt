package ir.carepack.domain.today

import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.ReportingOccurrenceRow
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.HistoryItem
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalPhase
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.temporal.TemporalClassifier
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomTodayQueryService(
    private val database: CarePackDatabase,
    private val temporalClassifier: TemporalClassifier = TemporalClassifier(),
) : TodayQueryService {
    override fun observeToday(
        localDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<TodayModel> {
        val reportingDao = database.reportingDao()

        return combine(
            reportingDao.observeMedicationCount(),
            reportingDao.observeToday(localDate.toEpochDay()),
            now,
        ) { medicationCount, rows, currentInstant ->
            val items = rows.map { row ->
                row.toMappedOccurrence(currentInstant, temporalClassifier).toTodayItem()
            }

            TodayModel(
                localDate = localDate,
                items = items,
                emptyState = when {
                    items.isNotEmpty() -> null
                    medicationCount == 0 -> TodayEmptyState.NO_MEDICATIONS
                    else -> TodayEmptyState.NO_OCCURRENCES
                },
            )
        }
    }

    override fun observeOccurrence(
        occurrenceId: String,
        now: Flow<Instant>,
    ): Flow<OccurrenceDetail?> = combine(
        database.reportingDao().observeOccurrence(occurrenceId),
        now,
    ) { row, currentInstant ->
        row?.toMappedOccurrence(currentInstant, temporalClassifier)?.toOccurrenceDetail()
    }

    override fun observeRecentHistory(
        anchorDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<List<HistoryDay>> {
        val startDate = anchorDate.minusDays(HISTORY_PREVIOUS_DAYS)

        return combine(
            database.reportingDao().observeHistory(
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = anchorDate.toEpochDay(),
            ),
            now,
        ) { rows, currentInstant ->
            rows
                .map { row ->
                    row.toMappedOccurrence(currentInstant, temporalClassifier).toHistoryItem()
                }
                .groupBy(HistoryItem::localDate)
                .entries
                .sortedByDescending { entry -> entry.key }
                .map { (localDate, items) ->
                    HistoryDay(
                        localDate = localDate,
                        items = items.sortedWith(
                            compareBy<HistoryItem>(HistoryItem::scheduledAt)
                                .thenBy(HistoryItem::occurrenceId),
                        ),
                    )
                }
        }
    }

    private companion object {
        const val HISTORY_PREVIOUS_DAYS = 7L
    }
}

private data class MappedOccurrence(
    val occurrenceId: String,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val scheduledAt: Instant,
    val medicationName: String,
    val medicationInstruction: String,
    val lifecycle: OccurrenceLifecycle,
    val reportState: CaregiverReportState?,
    val zoneId: String,
    val temporalPhase: TemporalPhase,
    val isOverdue: Boolean,
    val cancellationReason: OccurrenceCancellationReason?,
)

private fun ReportingOccurrenceRow.toMappedOccurrence(
    now: Instant,
    classifier: TemporalClassifier,
): MappedOccurrence {
    val mappedLifecycle = OccurrenceLifecycle.valueOf(lifecycle)
    val mappedReportState = reportState?.let(CaregiverReportState::valueOf)
    val scheduledAt = Instant.ofEpochMilli(scheduledAtEpochMillis)
    val phase = classifier.classify(scheduledAt = scheduledAt, now = now)

    return MappedOccurrence(
        occurrenceId = occurrenceId,
        localDate = LocalDate.ofEpochDay(localDateEpochDay),
        localTime = minuteOfDay.toLocalTime(),
        scheduledAt = scheduledAt,
        medicationName = medicationNameSnapshot,
        medicationInstruction = medicationInstructionSnapshot,
        lifecycle = mappedLifecycle,
        reportState = mappedReportState,
        zoneId = zoneId,
        temporalPhase = phase,
        isOverdue = classifier.isOverdue(
            lifecycle = mappedLifecycle,
            reportState = mappedReportState,
            phase = phase,
        ),
        cancellationReason = cancellationReason?.let { storedValue ->
            runCatching { OccurrenceCancellationReason.valueOf(storedValue) }.getOrNull()
        },
    )
}

private fun MappedOccurrence.toTodayItem() = TodayItem(
    occurrenceId = occurrenceId,
    localDate = localDate,
    localTime = localTime,
    medicationName = medicationName,
    medicationInstruction = medicationInstruction,
    lifecycle = lifecycle,
    reportState = reportState,
    scheduledAt = scheduledAt,
    temporalPhase = temporalPhase,
    isOverdue = isOverdue,
)

private fun MappedOccurrence.toOccurrenceDetail() = OccurrenceDetail(
    occurrenceId = occurrenceId,
    localDate = localDate,
    localTime = localTime,
    scheduledAt = scheduledAt,
    medicationName = medicationName,
    medicationInstruction = medicationInstruction,
    lifecycle = lifecycle,
    reportState = reportState,
    zoneId = zoneId,
    temporalPhase = temporalPhase,
    isOverdue = isOverdue,
    cancellationReason = cancellationReason,
)

private fun MappedOccurrence.toHistoryItem() = HistoryItem(
    occurrenceId = occurrenceId,
    localDate = localDate,
    localTime = localTime,
    scheduledAt = scheduledAt,
    medicationName = medicationName,
    medicationInstruction = medicationInstruction,
    lifecycle = lifecycle,
    reportState = reportState,
    temporalPhase = temporalPhase,
    isOverdue = isOverdue,
)

private fun Int.toLocalTime(): LocalTime {
    require(this in 0 until MINUTES_PER_DAY)
    return LocalTime.of(this / MINUTES_PER_HOUR, this % MINUTES_PER_HOUR)
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
