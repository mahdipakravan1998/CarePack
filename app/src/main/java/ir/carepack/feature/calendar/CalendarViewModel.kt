package ir.carepack.feature.calendar

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.calendar.FirstDayOfWeekPolicy
import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.calendar.JalaliMonthModel
import ir.carepack.domain.calendar.JalaliMonthModelFactory
import ir.carepack.domain.calendar.JalaliYearMonth
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.report.DateRangeSummary
import ir.carepack.domain.report.DateRangeSummaryService
import ir.carepack.domain.report.DayRangeSummary
import ir.carepack.domain.report.ReportDateRange
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch



enum class CalendarFailure {
    LOAD_FAILED,
}


data class CalendarUiState(
    val today: LocalDate,
    val selectedDate: LocalDate,
    val displayedMonth: JalaliYearMonth,
    val firstDayOfWeek: DayOfWeek,
    val monthModel: JalaliMonthModel,
    val summary: DateRangeSummary? = null,
    val selectedDaySummary: DayRangeSummary? = null,
    val seniorMode: SeniorMode = SeniorMode.STANDARD,
    val isLoading: Boolean = true,
    val failure: CalendarFailure? = null,
)

class CalendarViewModel(
    private val summaryService: DateRangeSummaryService,
    private val userExperiencePreferenceStore: UserExperiencePreferenceStore,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    private val initialToday = LocalDate.now(
            clock.withZone(
                zoneProvider.currentZone(),
            ),
        )

    private val initialFirstDayOfWeek = FirstDayOfWeekPolicy.resolve(
            preference = FirstDayOfWeekPreference
                    .SYSTEM_DEFAULT,
            zoneId = zoneProvider.currentZone(),
            locale = locale,
        )

    private val initialMonth = JalaliYearMonth.from(
            initialToday,
        )

    private val mutableState = MutableStateFlow(
            CalendarUiState(
                today = initialToday,
                selectedDate = initialToday,
                displayedMonth = initialMonth,
                firstDayOfWeek = initialFirstDayOfWeek,
                monthModel = JalaliMonthModelFactory.create(
                        displayedMonth = initialMonth,
                        today = initialToday,
                        selectedDate = initialToday,
                        firstDayOfWeek = initialFirstDayOfWeek,
                    ),
            ),
        )

    val state = mutableState.asStateFlow()

    private var summaryObservationJob: Job? = null

    init {
        observeExperiencePreferences()
    }

    fun showPreviousMonth() {
        val current = mutableState.value

        updateDisplayedMonth(
            current.displayedMonth
                .previous(),
        )
    }

    fun showNextMonth() {
        val current = mutableState.value

        updateDisplayedMonth(
            current.displayedMonth
                .next(),
        )
    }

    fun showToday() {
        val today = LocalDate.now(
                clock.withZone(
                    zoneProvider.currentZone(),
                ),
            )

        val displayedMonth = JalaliYearMonth.from(
                today,
            )

        mutableState.update { current ->
            current.copy(
                today = today,
                selectedDate = today,
                displayedMonth = displayedMonth,
            ).rebuildMonthModel()
        }

        restartSummaryObservation()
    }

    fun selectDate(
        date: LocalDate,
    ) {
        val selectedMonth = JalaliYearMonth.from(
                date,
            )

        val monthChanged = selectedMonth !=
                    mutableState.value
                        .displayedMonth

        mutableState.update { current ->
            val next = current.copy(
                    selectedDate = date,
                    displayedMonth = if (monthChanged) {
                            selectedMonth
                        } else {
                            current.displayedMonth
                        },
                ).rebuildMonthModel()

            next.copy(
                selectedDaySummary = next.summary
                        ?.summaryFor(
                            date,
                        ),
            )
        }

        if (monthChanged) {
            restartSummaryObservation()
        }
    }

    fun refresh() {
        restartSummaryObservation()
    }

    private fun observeExperiencePreferences() {
        viewModelScope.launch {
            userExperiencePreferenceStore.state
                .collectLatest { preferenceState ->
                    val resolvedFirstDay = FirstDayOfWeekPolicy.resolve(
                            preference = preferenceState
                                    .firstDayOfWeekPreference,
                            zoneId = zoneProvider.currentZone(),
                            locale = locale,
                        )

                    val firstDayChanged = resolvedFirstDay !=
                                mutableState.value
                                    .firstDayOfWeek

                    mutableState.update { current ->
                        current.copy(
                            firstDayOfWeek = resolvedFirstDay,
                            seniorMode = preferenceState
                                    .seniorMode,
                        ).rebuildMonthModel()
                    }

                    if (
                        firstDayChanged || summaryObservationJob == null
                    ) {
                        restartSummaryObservation()
                    }
                }
        }
    }

    private fun updateDisplayedMonth(
        displayedMonth: JalaliYearMonth,
    ) {
        mutableState.update { current ->
            val selectedDate = if (
                    JalaliYearMonth.from(
                        current.today,
                    ) == displayedMonth) {
                    current.today
                } else {
                    displayedMonth.firstLocalDate()
                }

            current.copy(
                displayedMonth = displayedMonth,
                selectedDate = selectedDate,
            ).rebuildMonthModel()
        }

        restartSummaryObservation()
    }

    private fun restartSummaryObservation() {
        summaryObservationJob?.cancel()

        val snapshot = mutableState
                .value.rebuildMonthModel()

        mutableState.value = snapshot.copy(
                summary = null,
                selectedDaySummary = null,
                isLoading = true,
                failure = null,
            )

        val range = ReportDateRange(
                startDate = snapshot
                        .monthModel.firstVisibleDate,
                endDate = snapshot
                        .monthModel.lastVisibleDate,
            )

        summaryObservationJob = viewModelScope.launch {
                try {
                    summaryService.observeSummary(
                            range,
                        ).collectLatest { summary ->
                            mutableState.update { current ->
                                if (
                                    current.monthModel.firstVisibleDate !=
                                    range.startDate || current.monthModel
                                        .lastVisibleDate != range.endDate
                                ) {
                                    current
                                } else {
                                    current.copy(
                                        summary = summary,
                                        selectedDaySummary = summary.summaryFor(
                                                current.selectedDate,
                                            ),
                                        isLoading = false,
                                        failure = null,
                                    )
                                }
                            }
                        }
                } catch (
                    cancellationException: CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            failure = CalendarFailure
                                    .LOAD_FAILED,
                        )
                    }
                }
            }
    }

    private fun CalendarUiState.rebuildMonthModel(): CalendarUiState =
        copy(
            monthModel = JalaliMonthModelFactory.create(
                    displayedMonth = displayedMonth,
                    today = today,
                    selectedDate = selectedDate,
                    firstDayOfWeek = firstDayOfWeek,
                ),
        )

    companion object {
        fun factory(
            summaryService: DateRangeSummaryService,
            userExperiencePreferenceStore: UserExperiencePreferenceStore,
            clock: Clock,
            zoneProvider: ZoneProvider,
            locale: Locale = Locale.getDefault(),
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    CalendarViewModel(
                        summaryService = summaryService,
                        userExperiencePreferenceStore = userExperiencePreferenceStore,
                        clock = clock,
                        zoneProvider = zoneProvider,
                        locale = locale,
                    )
            }
    }
}
