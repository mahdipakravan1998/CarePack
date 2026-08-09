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
