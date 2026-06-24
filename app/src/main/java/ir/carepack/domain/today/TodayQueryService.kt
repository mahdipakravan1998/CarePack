package ir.carepack.domain.today

import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.TodayItem
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface TodayQueryService {
    fun observeToday(
        localDate: LocalDate,
    ): Flow<List<TodayItem>>

    fun observeOccurrence(
        occurrenceId: String,
    ): Flow<OccurrenceDetail?>
}