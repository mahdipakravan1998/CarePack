package ir.carepack.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.calendar.FirstDayOfWeekPolicy
import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.calendar.JalaliMonthCell
import ir.carepack.domain.calendar.JalaliMonthModel
import ir.carepack.domain.calendar.JalaliMonthModelFactory
import ir.carepack.domain.calendar.JalaliYearMonth
import ir.carepack.domain.calendar.PersianDateText
import ir.carepack.domain.calendar.toPersianDigits
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.report.DateRangeSummary
import ir.carepack.domain.report.DateRangeSummaryService
import ir.carepack.domain.report.DayRangeSummary
import ir.carepack.domain.report.RangeOccurrenceEntry
import ir.carepack.domain.report.RangeOccurrenceReportState
import ir.carepack.domain.report.ReportDateRange
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.accessibility.carePackTraversalGroup
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    private val summaryService:
    DateRangeSummaryService,
    private val userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    private val initialToday =
        LocalDate.now(
            clock.withZone(
                zoneProvider.currentZone(),
            ),
        )

    private val initialFirstDayOfWeek =
        FirstDayOfWeekPolicy.resolve(
            preference =
                FirstDayOfWeekPreference
                    .SYSTEM_DEFAULT,
            zoneId =
                zoneProvider.currentZone(),
            locale = locale,
        )

    private val initialMonth =
        JalaliYearMonth.from(
            initialToday,
        )

    private val mutableState =
        MutableStateFlow(
            CalendarUiState(
                today = initialToday,
                selectedDate = initialToday,
                displayedMonth = initialMonth,
                firstDayOfWeek =
                    initialFirstDayOfWeek,
                monthModel =
                    JalaliMonthModelFactory.create(
                        displayedMonth = initialMonth,
                        today = initialToday,
                        selectedDate = initialToday,
                        firstDayOfWeek =
                            initialFirstDayOfWeek,
                    ),
            ),
        )

    val state =
        mutableState.asStateFlow()

    private var summaryObservationJob:
            Job? = null

    init {
        observeExperiencePreferences()
    }

    fun showPreviousMonth() {
        val current =
            mutableState.value

        updateDisplayedMonth(
            current
                .displayedMonth
                .previous(),
        )
    }

    fun showNextMonth() {
        val current =
            mutableState.value

        updateDisplayedMonth(
            current
                .displayedMonth
                .next(),
        )
    }

    fun showToday() {
        val today =
            LocalDate.now(
                clock.withZone(
                    zoneProvider.currentZone(),
                ),
            )

        val displayedMonth =
            JalaliYearMonth.from(
                today,
            )

        mutableState.update { current ->
            current.copy(
                today = today,
                selectedDate = today,
                displayedMonth =
                    displayedMonth,
            ).rebuildMonthModel()
        }

        restartSummaryObservation()
    }

    fun selectDate(
        date: LocalDate,
    ) {
        val selectedMonth =
            JalaliYearMonth.from(
                date,
            )

        val monthChanged =
            selectedMonth !=
                    mutableState
                        .value
                        .displayedMonth

        mutableState.update { current ->
            val next =
                current.copy(
                    selectedDate = date,
                    displayedMonth =
                        if (monthChanged) {
                            selectedMonth
                        } else {
                            current.displayedMonth
                        },
                ).rebuildMonthModel()

            next.copy(
                selectedDaySummary =
                    next.summary
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
            userExperiencePreferenceStore
                .state
                .collectLatest { preferenceState ->
                    val resolvedFirstDay =
                        FirstDayOfWeekPolicy.resolve(
                            preference =
                                preferenceState
                                    .firstDayOfWeekPreference,
                            zoneId =
                                zoneProvider.currentZone(),
                            locale = locale,
                        )

                    val firstDayChanged =
                        resolvedFirstDay !=
                                mutableState
                                    .value
                                    .firstDayOfWeek

                    mutableState.update { current ->
                        current.copy(
                            firstDayOfWeek =
                                resolvedFirstDay,
                            seniorMode =
                                preferenceState
                                    .seniorMode,
                        ).rebuildMonthModel()
                    }

                    if (
                        firstDayChanged ||
                        summaryObservationJob == null
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
            val selectedDate =
                if (
                    JalaliYearMonth.from(
                        current.today,
                    ) == displayedMonth
                ) {
                    current.today
                } else {
                    displayedMonth
                        .firstLocalDate()
                }

            current.copy(
                displayedMonth =
                    displayedMonth,
                selectedDate =
                    selectedDate,
            ).rebuildMonthModel()
        }

        restartSummaryObservation()
    }

    private fun restartSummaryObservation() {
        summaryObservationJob?.cancel()

        val snapshot =
            mutableState
                .value
                .rebuildMonthModel()

        mutableState.value =
            snapshot.copy(
                summary = null,
                selectedDaySummary = null,
                isLoading = true,
                failure = null,
            )

        val range =
            ReportDateRange(
                startDate =
                    snapshot
                        .monthModel
                        .firstVisibleDate,
                endDate =
                    snapshot
                        .monthModel
                        .lastVisibleDate,
            )

        summaryObservationJob =
            viewModelScope.launch {
                try {
                    summaryService
                        .observeSummary(
                            range,
                        )
                        .collectLatest { summary ->
                            mutableState.update { current ->
                                if (
                                    current.monthModel
                                        .firstVisibleDate !=
                                    range.startDate ||
                                    current.monthModel
                                        .lastVisibleDate !=
                                    range.endDate
                                ) {
                                    current
                                } else {
                                    current.copy(
                                        summary = summary,
                                        selectedDaySummary =
                                            summary.summaryFor(
                                                current
                                                    .selectedDate,
                                            ),
                                        isLoading = false,
                                        failure = null,
                                    )
                                }
                            }
                        }
                } catch (
                    cancellationException:
                    CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            failure =
                                CalendarFailure
                                    .LOAD_FAILED,
                        )
                    }
                }
            }
    }

    private fun CalendarUiState.rebuildMonthModel():
            CalendarUiState =
        copy(
            monthModel =
                JalaliMonthModelFactory.create(
                    displayedMonth =
                        displayedMonth,
                    today = today,
                    selectedDate =
                        selectedDate,
                    firstDayOfWeek =
                        firstDayOfWeek,
                ),
        )

    companion object {
        fun factory(
            summaryService:
            DateRangeSummaryService,
            userExperiencePreferenceStore:
            UserExperiencePreferenceStore,
            clock: Clock,
            zoneProvider: ZoneProvider,
            locale: Locale = Locale.getDefault(),
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CalendarViewModel(
                        summaryService =
                            summaryService,
                        userExperiencePreferenceStore =
                            userExperiencePreferenceStore,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                        locale = locale,
                    )
                }
            }
    }
}
