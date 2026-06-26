package ir.carepack.domain.today

import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.model.TodayModel
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface TodayQueryService {

    fun observeToday(
        localDate: LocalDate,
    ): Flow<List<TodayItem>>

    fun observeToday(
        localDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<TodayModel>

    fun observeOccurrence(
        occurrenceId: String,
    ): Flow<OccurrenceDetail?>

    fun observeOccurrence(
        occurrenceId: String,
        now: Flow<Instant>,
    ): Flow<OccurrenceDetail?>

    fun observeRecentHistory(
        anchorDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<List<HistoryDay>>
}
