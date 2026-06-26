package ir.carepack.feature.today

import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.today.TodayQueryService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val dispatcher =
        StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(
            dispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun crossingMidnight_rebindsTodayAndHistoryToNewDate() =
        runTest(dispatcher.scheduler) {
            val now =
                MutableStateFlow(
                    BEFORE_MIDNIGHT,
                )

            val queryService =
                RecordingTodayQueryService()

            val viewModel =
                TodayViewModel(
                    todayQueryService =
                        queryService,
                    clock =
                        Clock.fixed(
                            BEFORE_MIDNIGHT,
                            ZoneOffset.UTC,
                        ),
                    zoneProvider =
                        ZoneProvider {
                            ZoneOffset.UTC
                        },
                    now = now,
                )

            runCurrent()

            assertEquals(
                FIRST_DATE,
                viewModel.state.value.localDate,
            )

            assertEquals(
                listOf(
                    FIRST_DATE,
                ),
                queryService.todayDates,
            )

            assertEquals(
                listOf(
                    FIRST_DATE,
                ),
                queryService.historyDates,
            )

            now.value =
                AFTER_MIDNIGHT

            runCurrent()

            assertEquals(
                SECOND_DATE,
                viewModel.state.value.localDate,
            )

            assertEquals(
                listOf(
                    FIRST_DATE,
                    SECOND_DATE,
                ),
                queryService.todayDates,
            )

            assertEquals(
                listOf(
                    FIRST_DATE,
                    SECOND_DATE,
                ),
                queryService.historyDates,
            )
        }

    @Test
    fun tickingWithinSameDate_doesNotRestartDateQueries() =
        runTest(dispatcher.scheduler) {
            val now =
                MutableStateFlow(
                    BEFORE_MIDNIGHT,
                )

            val queryService =
                RecordingTodayQueryService()

            TodayViewModel(
                todayQueryService =
                    queryService,
                clock =
                    Clock.fixed(
                        BEFORE_MIDNIGHT,
                        ZoneOffset.UTC,
                    ),
                zoneProvider =
                    ZoneProvider {
                        ZoneOffset.UTC
                    },
                now = now,
            )

            runCurrent()

            now.value =
                BEFORE_MIDNIGHT.plusMillis(
                    500L,
                )

            runCurrent()

            assertEquals(
                listOf(
                    FIRST_DATE,
                ),
                queryService.todayDates,
            )

            assertEquals(
                listOf(
                    FIRST_DATE,
                ),
                queryService.historyDates,
            )
        }

    private companion object {

        val BEFORE_MIDNIGHT: Instant =
            Instant.parse(
                "2026-06-24T23:59:59Z",
            )

        val AFTER_MIDNIGHT: Instant =
            Instant.parse(
                "2026-06-25T00:00:00Z",
            )

        val FIRST_DATE: LocalDate =
            LocalDate.of(
                2026,
                6,
                24,
            )

        val SECOND_DATE: LocalDate =
            LocalDate.of(
                2026,
                6,
                25,
            )
    }
}

private class RecordingTodayQueryService :
    TodayQueryService {

    val todayDates =
        mutableListOf<LocalDate>()

    val historyDates =
        mutableListOf<LocalDate>()

    override fun observeToday(
        localDate: LocalDate,
    ): Flow<List<TodayItem>> {
        return flowOf(
            emptyList(),
        )
    }

    override fun observeToday(
        localDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<TodayModel> {
        todayDates +=
            localDate

        return flowOf(
            TodayModel(
                localDate = localDate,
                items = emptyList(),
                emptyState =
                    TodayEmptyState
                        .NO_MEDICATIONS,
            ),
        )
    }

    override fun observeOccurrence(
        occurrenceId: String,
    ): Flow<OccurrenceDetail?> {
        return flowOf(
            null,
        )
    }

    override fun observeOccurrence(
        occurrenceId: String,
        now: Flow<Instant>,
    ): Flow<OccurrenceDetail?> {
        return flowOf(
            null,
        )
    }

    override fun observeRecentHistory(
        anchorDate: LocalDate,
        now: Flow<Instant>,
    ): Flow<List<HistoryDay>> {
        historyDates +=
            anchorDate

        return flowOf(
            emptyList(),
        )
    }
}
