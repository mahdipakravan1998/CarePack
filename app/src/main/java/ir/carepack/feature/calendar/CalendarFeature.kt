package ir.carepack.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.report.DateRangeSummaryService
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import java.time.Clock
import java.time.LocalDate




@Composable
fun CalendarRoute(
    summaryService: DateRangeSummaryService,
    userExperiencePreferenceStore: UserExperiencePreferenceStore,
    clock: Clock,
    zoneProvider: ZoneProvider,
    onOpenOccurrence: (String) -> Unit,
    onOpenRangeReport: () -> Unit,
) {
    val viewModel: CalendarViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = CalendarViewModel.factory(
                    summaryService = summaryService,
                    userExperiencePreferenceStore = userExperiencePreferenceStore,
                    clock = clock,
                    zoneProvider = zoneProvider,
                ),
        )

    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalCarePackExperience provides
                CarePackExperience.forMode(
                    state.seniorMode,
                ),
    ) {
        CalendarScreen(
            state = state,
            onPreviousMonth = viewModel::showPreviousMonth,
            onNextMonth = viewModel::showNextMonth,
            onToday = viewModel::showToday,
            onDateSelected = viewModel::selectDate,
            onOpenOccurrence = onOpenOccurrence,
            onOpenRangeReport = onOpenRangeReport,
            onRetry = viewModel::refresh,
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
    val experience = LocalCarePackExperience.current

    Scaffold(
        modifier = Modifier
                .fillMaxSize().testTag(
                    "calendar_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                    .fillMaxSize().padding(
                        contentPadding,
                    ).imePadding()
                    .navigationBarsPadding().verticalScroll(
                        rememberScrollState(),
                    ).padding(
                        horizontal = experience
                                .screenHorizontalPadding,
                        vertical = experience
                                .screenVerticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.sectionSpacing,
                ),
        ) {
            CalendarTitleSection(
                onOpenRangeReport = onOpenRangeReport,
            )

            CalendarMonthControls(
                displayedMonth = state.displayedMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onToday = onToday,
            )

            CalendarMonthContent(
                state = state,
                onDateSelected = onDateSelected,
            )

            SelectedDaySection(
                state = state,
                onOpenOccurrence = onOpenOccurrence,
                onRetry = onRetry,
            )

            Text(
                text = stringResource(
                        R.string.calendar_history_hint,
                    ),
                style = MaterialTheme
                        .typography.bodySmall,
                modifier = Modifier.testTag(
                        "calendar_history_hint",
                    ),
            )
        }
    }
}
