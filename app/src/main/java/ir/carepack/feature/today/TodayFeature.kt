package ir.carepack.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.core.time.tickingNow
import ir.carepack.domain.calendar.JalaliPresentationDate
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.HistoryItem
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalStatus
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.reminder.RemindLaterOutcome
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch



@Composable
fun TodayRoute(
    viewModel: TodayViewModel,
    onOpenCarePlan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTodayReport: () -> Unit,
    onOpenOccurrence: (String) -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    val lifecycleOwner =
        LocalLifecycleOwner.current

    DisposableEffect(
        lifecycleOwner,
        viewModel,
    ) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    viewModel.refresh()
                }
            }

        lifecycleOwner
            .lifecycle
            .addObserver(observer)

        onDispose {
            lifecycleOwner
                .lifecycle
                .removeObserver(observer)
        }
    }

    TodayScreen(
        state = state,
        onTodaySelected =
            viewModel::showToday,
        onHistorySelected =
            viewModel::showHistory,
        onRetry =
            viewModel::retry,
        onOpenCarePlan =
            onOpenCarePlan,
        onOpenSettings =
            onOpenSettings,
        onOpenTodayReport =
            onOpenTodayReport,
        onOpenOccurrence =
            onOpenOccurrence,
        onGiven = { occurrenceId ->
            viewModel.setReport(
                occurrenceId =
                    occurrenceId,
                state =
                    CaregiverReportState.GIVEN,
            )
        },
        onNotGiven = { occurrenceId ->
            viewModel.setReport(
                occurrenceId =
                    occurrenceId,
                state =
                    CaregiverReportState.NOT_GIVEN,
            )
        },
        onUnknown = { occurrenceId ->
            viewModel.setReport(
                occurrenceId =
                    occurrenceId,
                state =
                    CaregiverReportState.UNKNOWN,
            )
        },
        onRemindLater =
            viewModel::remindLater,
        onUndo =
            viewModel::undoReportChange,
        onSnackbarConsumed =
            viewModel::consumeSnackbar,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onTodaySelected: () -> Unit,
    onHistorySelected: () -> Unit,
    onRetry: () -> Unit,
    onOpenCarePlan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTodayReport: () -> Unit = {},
    onOpenOccurrence: (String) -> Unit,
    onGiven: (String) -> Unit = {},
    onNotGiven: (String) -> Unit = {},
    onUnknown: (String) -> Unit = {},
    onRemindLater: (String) -> Unit = {},
    onUndo: () -> Unit = {},
    onSnackbarConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val experience =
        carePackExperience()

    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "today_screen",
                ),
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .testTag(
                            "today_content",
                        ),
                contentPadding =
                    PaddingValues(
                        horizontal =
                            experience.screenHorizontalPadding,
                        vertical =
                            experience.screenVerticalPadding,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        experience.itemSpacing,
                    ),
            ) {
                item {
                    TodayHeader(
                        localDate =
                            state.localDate,
                        seniorMode =
                            state.seniorMode,
                        onOpenSettings =
                            onOpenSettings,
                        onOpenTodayReport =
                            onOpenTodayReport,
                    )
                }

                item {
                    ReminderAwarenessCard(
                        status =
                            state.reminderStatus,
                    )
                }

                item {
                    TodayTabs(
                        selectedSection =
                            state.selectedSection,
                        onTodaySelected =
                            onTodaySelected,
                        onHistorySelected =
                            onHistorySelected,
                    )
                }

                when (state.selectedSection) {
                    TodaySection.TODAY -> {
                        todayContent(
                            state = state,
                            onRetry = onRetry,
                            onOpenCarePlan =
                                onOpenCarePlan,
                            onOpenOccurrence =
                                onOpenOccurrence,
                            onGiven = onGiven,
                            onNotGiven =
                                onNotGiven,
                            onUnknown =
                                onUnknown,
                            onRemindLater =
                                onRemindLater,
                        )
                    }

                    TodaySection.HISTORY -> {
                        historyContent(
                            state = state,
                            onRetry = onRetry,
                            onOpenOccurrence =
                                onOpenOccurrence,
                        )
                    }
                }
            }

            if (state.snackbarMessage != null) {
                Snackbar(
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomCenter,
                            )
                            .navigationBarsPadding()
                            .padding(
                                experience.compactSpacing,
                            )
                            .carePackPoliteLiveRegion()
                            .testTag(
                                "today_snackbar",
                            ),
                    action = {
                        if (state.undoChange != null) {
                            TextButton(
                                onClick = onUndo,
                                modifier =
                                    Modifier
                                        .carePackInteractiveControl()
                                        .testTag(
                                            "today_undo_report",
                                        ),
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.undo,
                                        ),
                                )
                            }
                        } else {
                            TextButton(
                                onClick =
                                    onSnackbarConsumed,
                                modifier =
                                    Modifier
                                        .carePackInteractiveControl()
                                        .testTag(
                                            "today_snackbar_dismiss",
                                        ),
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.dismiss_for_later,
                                        ),
                                )
                            }
                        }
                    },
                ) {
                    Text(
                        text =
                            state.snackbarMessage,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.todayContent(
    state: TodayUiState,
    onRetry: () -> Unit,
    onOpenCarePlan: () -> Unit,
    onOpenOccurrence: (String) -> Unit,
    onGiven: (String) -> Unit,
    onNotGiven: (String) -> Unit,
    onUnknown: (String) -> Unit,
    onRemindLater: (String) -> Unit,
) {
    when {
        state.isLoading -> {
            item {
                LoadingCard(
                    testTag =
                        "today_loading",
                )
            }
        }

        state.errorMessage != null -> {
            item {
                ErrorCard(
                    message =
                        state.errorMessage,
                    onRetry = onRetry,
                    testTag =
                        "today_error",
                )
            }
        }

        state.items.isEmpty() -> {
            item {
                TodayEmptyCard(
                    emptyState =
                        state.emptyState,
                    onOpenCarePlan =
                        onOpenCarePlan,
                    seniorMode =
                        state.seniorMode,
                )
            }
        }

        state.seniorMode == SeniorMode.SIMPLE -> {
            val simpleItems =
                state.items
                    .sortedWith(
                        compareBy<TodayItem> {
                            simplePriority(it)
                        }.thenBy {
                            it.scheduledAt
                        }.thenBy {
                            it.occurrenceId
                        },
                    )

            itemsIndexed(
                items =
                    simpleItems,
                key = {
                        _,
                        item ->
                    item.occurrenceId
                },
            ) { index, item ->
                SimpleTodayCard(
                    item = item,
                    isPrimary =
                        index == 0,
                    onGiven = {
                        onGiven(
                            item.occurrenceId,
                        )
                    },
                    onNotGiven = {
                        onNotGiven(
                            item.occurrenceId,
                        )
                    },
                    onUnknown = {
                        onUnknown(
                            item.occurrenceId,
                        )
                    },
                    onRemindLater = {
                        onRemindLater(
                            item.occurrenceId,
                        )
                    },
                    onOpenDetails = {
                        onOpenOccurrence(
                            item.occurrenceId,
                        )
                    },
                )
            }
        }

        else -> {
            items(
                items = state.items,
                key = {
                    it.occurrenceId
                },
            ) { item ->
                TodayItemCard(
                    item = item,
                    onOpen = {
                        onOpenOccurrence(
                            item.occurrenceId,
                        )
                    },
                    onGiven = {
                        onGiven(
                            item.occurrenceId,
                        )
                    },
                    onNotGiven = {
                        onNotGiven(
                            item.occurrenceId,
                        )
                    },
                    onUnknown = {
                        onUnknown(
                            item.occurrenceId,
                        )
                    },
                    onRemindLater = {
                        onRemindLater(
                            item.occurrenceId,
                        )
                    },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyContent(
    state: TodayUiState,
    onRetry: () -> Unit,
    onOpenOccurrence: (String) -> Unit,
) {
    when {
        state.isHistoryLoading -> {
            item {
                LoadingCard(
                    testTag =
                        "history_loading",
                )
            }
        }

        state.historyErrorMessage != null -> {
            item {
                ErrorCard(
                    message =
                        state.historyErrorMessage,
                    onRetry = onRetry,
                    testTag =
                        "history_error",
                )
            }
        }

        state.historyDays.isEmpty() -> {
            item {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                "history_empty",
                            ),
                ) {
                    Text(
                        text =
                            "در هشت روز اخیر نوبتی برای نمایش وجود ندارد.",
                        modifier =
                            Modifier.padding(
                                16.dp,
                            ),
                    )
                }
            }
        }

        else -> {
            state.historyDays.forEach { day ->
                item(
                    key =
                        "history-day-${day.localDate}",
                ) {
                    Text(
                        text =
                            day.localDate
                                .toJalaliDisplayText(),
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        modifier =
                            Modifier
                                .carePackHeading()
                                .padding(
                                    top = 8.dp,
                                ),
                    )
                }

                items(
                    items =
                        day.items,
                    key = {
                        it.occurrenceId
                    },
                ) { item ->
                    HistoryItemCard(
                        item = item,
                        onOpen = {
                            onOpenOccurrence(
                                item.occurrenceId,
                            )
                        },
                    )
                }
            }
        }
    }
}
