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

@Composable
fun CalendarRoute(
    summaryService:
    DateRangeSummaryService,
    userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    clock: Clock,
    zoneProvider: ZoneProvider,
    onOpenOccurrence: (String) -> Unit,
    onOpenRangeReport: () -> Unit,
) {
    val viewModel:
            CalendarViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory =
                CalendarViewModel.factory(
                    summaryService =
                        summaryService,
                    userExperiencePreferenceStore =
                        userExperiencePreferenceStore,
                    clock = clock,
                    zoneProvider =
                        zoneProvider,
                ),
        )

    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalCarePackExperience provides
                CarePackExperience.forMode(
                    state.seniorMode,
                ),
    ) {
        CalendarScreen(
            state = state,
            onPreviousMonth =
                viewModel::showPreviousMonth,
            onNextMonth =
                viewModel::showNextMonth,
            onToday =
                viewModel::showToday,
            onDateSelected =
                viewModel::selectDate,
            onOpenOccurrence =
                onOpenOccurrence,
            onOpenRangeReport =
                onOpenRangeReport,
            onRetry =
                viewModel::refresh,
        )
    }
}

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onOpenOccurrence: (String) -> Unit,
    onOpenRangeReport: () -> Unit,
    onRetry: () -> Unit,
) {
    val experience =
        LocalCarePackExperience.current

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    "calendar_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        contentPadding,
                    )
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal =
                            experience
                                .screenHorizontalPadding,
                        vertical =
                            experience
                                .screenVerticalPadding,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    experience.sectionSpacing,
                ),
        ) {
            CalendarTitleSection(
                onOpenRangeReport =
                    onOpenRangeReport,
            )

            CalendarMonthControls(
                displayedMonth =
                    state.displayedMonth,
                onPreviousMonth =
                    onPreviousMonth,
                onNextMonth =
                    onNextMonth,
                onToday = onToday,
            )

            CalendarMonthContent(
                state = state,
                onDateSelected =
                    onDateSelected,
            )

            SelectedDaySection(
                state = state,
                onOpenOccurrence =
                    onOpenOccurrence,
                onRetry = onRetry,
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .calendar_history_hint,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                modifier =
                    Modifier.testTag(
                        "calendar_history_hint",
                    ),
            )
        }
    }
}

@Composable
private fun CalendarTitleSection(
    onOpenRangeReport: () -> Unit,
) {
    val experience =
        LocalCarePackExperience.current

    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                experience.itemSpacing,
            ),
    ) {
        Text(
            text =
                stringResource(
                    R.string.calendar_title,
                ),
            style =
                MaterialTheme
                    .typography
                    .headlineMedium,
            modifier =
                Modifier
                    .carePackHeading()
                    .testTag(
                        "calendar_title",
                    ),
        )

        Text(
            text =
                stringResource(
                    R.string
                        .calendar_description,
                ),
            style =
                MaterialTheme
                    .typography
                    .bodyLarge,
        )

        OutlinedButton(
            onClick = onOpenRangeReport,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .carePackPrimaryAction()
                    .testTag(
                        "calendar_open_range_report",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .calendar_open_range_report,
                    ),
            )
        }
    }
}

@Composable
private fun CalendarMonthControls(
    displayedMonth: JalaliYearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
) {
    val experience =
        LocalCarePackExperience.current

    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                experience.compactSpacing,
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onPreviousMonth,
                modifier =
                    Modifier
                        .carePackInteractiveControl()
                        .testTag(
                            "calendar_previous_month",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .calendar_previous_month,
                        ),
                )
            }

            Text(
                text =
                    PersianDateText.formatMonthYear(
                        year =
                            displayedMonth.year,
                        month =
                            displayedMonth.month,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .carePackHeading()
                        .testTag(
                            "calendar_month_title",
                        ),
            )

            TextButton(
                onClick = onNextMonth,
                modifier =
                    Modifier
                        .carePackInteractiveControl()
                        .testTag(
                            "calendar_next_month",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .calendar_next_month,
                        ),
                )
            }
        }

        OutlinedButton(
            onClick = onToday,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .carePackInteractiveControl()
                    .testTag(
                        "calendar_today",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.calendar_today,
                    ),
            )
        }
    }
}

@Composable
private fun CalendarMonthContent(
    state: CalendarUiState,
    onDateSelected: (LocalDate) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackTraversalGroup()
                .testTag(
                    "calendar_month_grid",
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                4.dp,
            ),
    ) {
        CalendarWeekdayHeader(
            weekdayOrder =
                state
                    .monthModel
                    .weekdayOrder,
        )

        state
            .monthModel
            .weeks
            .forEach { week ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            4.dp,
                        ),
                ) {
                    week.forEach { cell ->
                        CalendarDayCell(
                            cell = cell,
                            summary =
                                state.summary
                                    ?.summaryFor(
                                        cell.localDate,
                                    ),
                            onClick = {
                                onDateSelected(
                                    cell.localDate,
                                )
                            },
                            modifier =
                                Modifier.weight(1f),
                        )
                    }
                }
            }

        if (state.isLoading) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 8.dp,
                        )
                        .carePackPoliteLiveRegion()
                        .testTag(
                            "calendar_loading",
                        ),
                horizontalArrangement =
                    Arrangement.Center,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.size(
                            24.dp,
                        ),
                )

                Text(
                    text =
                        stringResource(
                            R.string
                                .calendar_loading,
                        ),
                    modifier =
                        Modifier.padding(
                            start = 12.dp,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CalendarWeekdayHeader(
    weekdayOrder: List<DayOfWeek>,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        weekdayOrder.forEach { dayOfWeek ->
            Text(
                text =
                    PersianDateText.shortWeekdayName(
                        dayOfWeek,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .labelLarge,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(
                            vertical = 4.dp,
                        ),
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    cell: JalaliMonthCell,
    summary: DayRangeSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val experience =
        LocalCarePackExperience.current

    val total =
        summary
            ?.totalOccurrenceCount
            ?: 0

    val description =
        calendarDayDescription(
            cell = cell,
            summary = summary,
        )

    val border =
        when {
            cell.isSelected ->
                BorderStroke(
                    width = 2.dp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,
                )

            cell.isToday ->
                BorderStroke(
                    width = 1.dp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .outline,
                )

            else -> null
        }

    Surface(
        color =
            if (cell.isSelected) {
                MaterialTheme
                    .colorScheme
                    .primaryContainer
            } else {
                MaterialTheme
                    .colorScheme
                    .surface
            },
        border = border,
        shape =
            MaterialTheme
                .shapes
                .medium,
        modifier =
            modifier
                .sizeIn(
                    minHeight =
                        experience
                            .calendarCellMinHeight,
                )
                .alpha(
                    if (
                        cell
                            .belongsToDisplayedMonth
                    ) {
                        1f
                    } else {
                        0.5f
                    },
                )
                .selectable(
                    selected =
                        cell.isSelected,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription =
                        description
                    selected =
                        cell.isSelected
                }
                .testTag(
                    "calendar_day_${cell.localDate.toEpochDay()}",
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min =
                            experience
                                .calendarCellMinHeight,
                    )
                    .padding(
                        horizontal = 2.dp,
                        vertical = 6.dp,
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center,
        ) {
            Text(
                text =
                    cell
                        .jalaliDate
                        .dayOfMonth
                        .value
                        .toString()
                        .toPersianDigits(),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                textAlign = TextAlign.Center,
            )

            if (total > 0) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .calendar_day_total_compact,
                            total
                                .toString()
                                .toPersianDigits(),
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    maxLines = 1,
                    textAlign =
                        TextAlign.Center,
                    modifier =
                        Modifier.testTag(
                            "calendar_day_count_${cell.localDate.toEpochDay()}",
                        ),
                )

                CalendarCompactStatus(
                    summary =
                        checkNotNull(summary),
                )
            }
        }
    }
}

@Composable
private fun CalendarCompactStatus(
    summary: DayRangeSummary,
) {
    val text =
        buildList {
            if (summary.givenCount > 0) {
                add(
                    "✓" +
                            summary.givenCount
                                .toString()
                                .toPersianDigits(),
                )
            }

            if (summary.notGivenCount > 0) {
                add(
                    "×" +
                            summary.notGivenCount
                                .toString()
                                .toPersianDigits(),
                )
            }

            if (summary.unknownCount > 0) {
                add(
                    "؟" +
                            summary.unknownCount
                                .toString()
                                .toPersianDigits(),
                )
            }

            if (summary.noReportCount > 0) {
                add(
                    "ـ" +
                            summary.noReportCount
                                .toString()
                                .toPersianDigits(),
                )
            }
        }.joinToString(
            separator = " ",
        )

    if (text.isNotBlank()) {
        Text(
            text = text,
            style =
                MaterialTheme
                    .typography
                    .labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun calendarDayDescription(
    cell: JalaliMonthCell,
    summary: DayRangeSummary?,
): String {
    val total =
        summary
            ?.totalOccurrenceCount
            ?: 0

    return buildString {
        append(
            stringResource(
                R.string
                    .calendar_day_semantics,
                PersianDateText.formatFull(
                    cell.localDate,
                ),
                total
                    .toString()
                    .toPersianDigits(),
                (summary?.givenCount ?: 0)
                    .toString()
                    .toPersianDigits(),
                (summary?.notGivenCount ?: 0)
                    .toString()
                    .toPersianDigits(),
                (summary?.unknownCount ?: 0)
                    .toString()
                    .toPersianDigits(),
                (summary?.noReportCount ?: 0)
                    .toString()
                    .toPersianDigits(),
            ),
        )

        if (cell.isToday) {
            append("، ")
            append(
                stringResource(
                    R.string
                        .calendar_today_description,
                ),
            )
        }

        if (cell.isSelected) {
            append("، ")
            append(
                stringResource(
                    R.string
                        .calendar_selected_description,
                ),
            )
        }

        if (
            !cell
                .belongsToDisplayedMonth
        ) {
            append("، ")
            append(
                stringResource(
                    R.string
                        .calendar_adjacent_month_description,
                ),
            )
        }
    }
}

@Composable
private fun SelectedDaySection(
    state: CalendarUiState,
    onOpenOccurrence: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val experience =
        LocalCarePackExperience.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackTraversalGroup()
                .testTag(
                    "calendar_selected_day",
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                experience.itemSpacing,
            ),
    ) {
        Text(
            text =
                stringResource(
                    R.string
                        .calendar_selected_day_title,
                    PersianDateText.formatFull(
                        state.selectedDate,
                    ),
                ),
            style =
                MaterialTheme
                    .typography
                    .titleLarge,
            modifier =
                Modifier
                    .carePackHeading()
                    .testTag(
                        "calendar_selected_day_title",
                    ),
        )

        when {
            state.failure != null -> {
                CalendarError(
                    failure =
                        state.failure,
                    onRetry = onRetry,
                )
            }

            state.isLoading -> {
                Text(
                    text =
                        stringResource(
                            R.string
                                .calendar_selected_day_loading,
                        ),
                    modifier =
                        Modifier
                            .carePackPoliteLiveRegion()
                            .testTag(
                                "calendar_selected_day_loading",
                            ),
                )
            }

            state.selectedDaySummary == null ||
                    state.selectedDaySummary
                        .totalOccurrenceCount == 0 -> {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                "calendar_empty_day",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .calendar_empty_day,
                            ),
                        modifier =
                            Modifier.padding(
                                16.dp,
                            ),
                    )
                }
            }

            else -> {
                state
                    .selectedDaySummary
                    .entries
                    .forEach { entry ->
                        CalendarOccurrenceCard(
                            entry = entry,
                            onOpen = {
                                onOpenOccurrence(
                                    entry.occurrenceId,
                                )
                            },
                        )
                    }
            }
        }
    }
}

@Composable
private fun CalendarError(
    failure: CalendarFailure,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(
                    "calendar_error",
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text =
                when (failure) {
                    CalendarFailure.LOAD_FAILED ->
                        stringResource(
                            R.string
                                .calendar_error,
                        )
                },
            color =
                MaterialTheme
                    .colorScheme
                    .error,
        )

        Button(
            onClick = onRetry,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .carePackPrimaryAction()
                    .testTag(
                        "calendar_retry",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.retry_action,
                    ),
            )
        }
    }
}

@Composable
private fun CalendarOccurrenceCard(
    entry: RangeOccurrenceEntry,
    onOpen: () -> Unit,
) {
    val experience =
        LocalCarePackExperience.current

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "calendar_occurrence_${entry.occurrenceId}",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    experience.compactSpacing,
                ),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        entry.medicationName,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    modifier =
                        Modifier.weight(1f),
                )

                Text(
                    text =
                        entry
                            .localTime
                            .format(
                                HOUR_MINUTE_FORMATTER,
                            )
                            .toPersianDigits(),
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                            .copy(
                                textDirection =
                                    TextDirection.Ltr,
                            ),
                    modifier =
                        Modifier.testTag(
                            "calendar_occurrence_time_${entry.occurrenceId}",
                        ),
                )
            }

            Text(
                text =
                    reportStateLabel(
                        entry.reportState,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                modifier =
                    Modifier.testTag(
                        "calendar_occurrence_state_${entry.occurrenceId}",
                    ),
            )

            if (entry.instruction.isNotBlank()) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .calendar_entry_instruction,
                            entry.instruction,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )
            }

            val recordingDetails =
                recordingDetails(
                    entry,
                )

            if (recordingDetails.isNotBlank()) {
                Text(
                    text = recordingDetails,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )
            }

            OutlinedButton(
                onClick = onOpen,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .carePackInteractiveControl()
                        .testTag(
                            "calendar_open_occurrence_${entry.occurrenceId}",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .calendar_open_occurrence,
                        ),
                )
            }
        }
    }
}

@Composable
private fun reportStateLabel(
    state: RangeOccurrenceReportState,
): String =
    when (state) {
        RangeOccurrenceReportState.GIVEN ->
            stringResource(
                R.string.calendar_report_given,
            )

        RangeOccurrenceReportState.NOT_GIVEN ->
            stringResource(
                R.string.calendar_report_not_given,
            )

        RangeOccurrenceReportState.UNKNOWN ->
            stringResource(
                R.string.calendar_report_unknown,
            )

        RangeOccurrenceReportState.NO_REPORT ->
            stringResource(
                R.string.calendar_report_no_report,
            )
    }

@Composable
private fun recordingDetails(
    entry: RangeOccurrenceEntry,
): String =
    buildList {
        entry.medicationType
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                add(
                    stringResource(
                        R.string
                            .calendar_entry_medication_type,
                        value,
                    ),
                )
            }

        entry.dosageText
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                add(
                    stringResource(
                        R.string
                            .calendar_entry_dosage,
                        value,
                    ),
                )
            }

        entry.doseUnit
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                add(
                    stringResource(
                        R.string
                            .calendar_entry_dose_unit,
                        value,
                    ),
                )
            }
    }.joinToString(
        separator = "، ",
    )

private val HOUR_MINUTE_FORMATTER =
    DateTimeFormatter.ofPattern(
        "HH:mm",
    )
