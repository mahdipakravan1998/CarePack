package ir.carepack.domain.today

import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.ReportingOccurrenceRow
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.HistoryItem
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.temporal.TemporalClassifier
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomTodayQueryService(
    private val database: CarePackDatabase,
    private val clock: Clock =
        Clock.systemUTC(),
    private val temporalClassifier:
    TemporalClassifier =
        TemporalClassifier(),
) : TodayQueryService {

    override fun observeToday(
        localDate: LocalDate,
    ): Flow<List<TodayItem>> {
        return observeToday(
            localDate = localDate,
            now = flowOf(
                clock.instant(),
            ),
        ).map { model ->
            model.items
        }
    }

    override fun observeToday(
        localDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<TodayModel> {
        val reportingDao =
            database.reportingDao()

        return combine(
            reportingDao
                .observeMedicationCount(),
            reportingDao
                .observeToday(
                    localDateEpochDay =
                        localDate.toEpochDay(),
                ),
            now,
        ) {
                medicationCount,
                rows,
                currentInstant,
            ->
            val items =
                rows.map { row ->
                    row.toTodayItem(
                        now = currentInstant,
                        classifier =
                            temporalClassifier,
                    )
                }

            TodayModel(
                localDate = localDate,
                items = items,
                emptyState =
                    when {
                        items.isNotEmpty() -> {
                            null
                        }

                        medicationCount == 0 -> {
                            TodayEmptyState
                                .NO_MEDICATIONS
                        }

                        else -> {
                            TodayEmptyState
                                .NO_OCCURRENCES
                        }
                    },
            )
        }
    }

    override fun observeOccurrence(
        occurrenceId: String,
    ): Flow<OccurrenceDetail?> {
        return observeOccurrence(
            occurrenceId =
                occurrenceId,
            now = flowOf(
                clock.instant(),
            ),
        )
    }

    override fun observeOccurrence(
        occurrenceId: String,
        now: Flow<Instant>,
    ): Flow<OccurrenceDetail?> {
        return combine(
            database
                .reportingDao()
                .observeOccurrence(
                    occurrenceId =
                        occurrenceId,
                ),
            now,
        ) { row, currentInstant ->
            row?.toOccurrenceDetail(
                now = currentInstant,
                classifier =
                    temporalClassifier,
            )
        }
    }

    override fun observeRecentHistory(
        anchorDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<List<HistoryDay>> {
        val startDate =
            anchorDate.minusDays(
                HISTORY_PREVIOUS_DAYS,
            )

        return combine(
            database
                .reportingDao()
                .observeHistory(
                    startEpochDay =
                        startDate.toEpochDay(),
                    endEpochDay =
                        anchorDate.toEpochDay(),
                ),
            now,
        ) { rows, currentInstant ->
            rows
                .map { row ->
                    row.toHistoryItem(
                        now = currentInstant,
                        classifier =
                            temporalClassifier,
                    )
                }
                .groupBy { historyItem ->
                    historyItem.localDate
                }
                .entries
                .sortedByDescending { entry ->
                    entry.key
                }
                .map { entry ->
                    HistoryDay(
                        localDate =
                            entry.key,
                        items =
                            entry.value
                                .sortedWith(
                                    compareBy<
                                            HistoryItem,
                                            > { item ->
                                        item.scheduledAt
                                    }.thenBy { item ->
                                        item.occurrenceId
                                    },
                                ),
                    )
                }
        }
    }

    private companion object {
        const val HISTORY_PREVIOUS_DAYS =
            7L
    }
}

private fun ReportingOccurrenceRow.toTodayItem(
    now: Instant,
    classifier: TemporalClassifier,
): TodayItem {
    val mappedLifecycle =
        OccurrenceLifecycle.valueOf(
            lifecycle,
        )

    val mappedReportState =
        reportState?.let(
            CaregiverReportState::valueOf,
        )

    val scheduledAt =
        Instant.ofEpochMilli(
            scheduledAtEpochMillis,
        )

    val phase =
        classifier.classify(
            scheduledAt = scheduledAt,
            now = now,
        )

    return TodayItem(
        occurrenceId =
            occurrenceId,
        localDate =
            LocalDate.ofEpochDay(
                localDateEpochDay,
            ),
        localTime =
            minuteOfDay.toLocalTime(),
        medicationName =
            medicationNameSnapshot,
        medicationInstruction =
            medicationInstructionSnapshot,
        lifecycle =
            mappedLifecycle,
        reportState =
            mappedReportState,
        scheduledAt =
            scheduledAt,
        temporalPhase =
            phase,
        isOverdue =
            classifier.isOverdue(
                lifecycle =
                    mappedLifecycle,
                reportState =
                    mappedReportState,
                phase = phase,
            ),
    )
}

private fun ReportingOccurrenceRow.toOccurrenceDetail(
    now: Instant,
    classifier: TemporalClassifier,
): OccurrenceDetail {
    val mappedLifecycle =
        OccurrenceLifecycle.valueOf(
            lifecycle,
        )

    val mappedReportState =
        reportState?.let(
            CaregiverReportState::valueOf,
        )

    val scheduledAt =
        Instant.ofEpochMilli(
            scheduledAtEpochMillis,
        )

    val phase =
        classifier.classify(
            scheduledAt = scheduledAt,
            now = now,
        )

    return OccurrenceDetail(
        occurrenceId =
            occurrenceId,
        localDate =
            LocalDate.ofEpochDay(
                localDateEpochDay,
            ),
        localTime =
            minuteOfDay.toLocalTime(),
        scheduledAt =
            scheduledAt,
        medicationName =
            medicationNameSnapshot,
        medicationInstruction =
            medicationInstructionSnapshot,
        lifecycle =
            mappedLifecycle,
        reportState =
            mappedReportState,
        zoneId =
            zoneId,
        temporalPhase =
            phase,
        isOverdue =
            classifier.isOverdue(
                lifecycle =
                    mappedLifecycle,
                reportState =
                    mappedReportState,
                phase = phase,
            ),
        cancellationReason =
            cancellationReason
                ?.let { storedValue ->
                    runCatching {
                        OccurrenceCancellationReason
                            .valueOf(
                                storedValue,
                            )
                    }.getOrNull()
                },
    )
}

private fun ReportingOccurrenceRow.toHistoryItem(
    now: Instant,
    classifier: TemporalClassifier,
): HistoryItem {
    val mappedLifecycle =
        OccurrenceLifecycle.valueOf(
            lifecycle,
        )

    val mappedReportState =
        reportState?.let(
            CaregiverReportState::valueOf,
        )

    val scheduledAt =
        Instant.ofEpochMilli(
            scheduledAtEpochMillis,
        )

    val phase =
        classifier.classify(
            scheduledAt = scheduledAt,
            now = now,
        )

    return HistoryItem(
        occurrenceId =
            occurrenceId,
        localDate =
            LocalDate.ofEpochDay(
                localDateEpochDay,
            ),
        localTime =
            minuteOfDay.toLocalTime(),
        scheduledAt =
            scheduledAt,
        medicationName =
            medicationNameSnapshot,
        medicationInstruction =
            medicationInstructionSnapshot,
        lifecycle =
            mappedLifecycle,
        reportState =
            mappedReportState,
        temporalPhase =
            phase,
        isOverdue =
            classifier.isOverdue(
                lifecycle =
                    mappedLifecycle,
                reportState =
                    mappedReportState,
                phase = phase,
            ),
    )
}

private fun Int.toLocalTime(): LocalTime {
    require(
        this in 0 until MINUTES_PER_DAY,
    )

    return LocalTime.of(
        this / MINUTES_PER_HOUR,
        this % MINUTES_PER_HOUR,
    )
}

private const val MINUTES_PER_HOUR =
    60

private const val MINUTES_PER_DAY =
    24 * MINUTES_PER_HOUR
