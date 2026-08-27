package ir.carepack.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.carepack.R
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.TodayItem
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.experience.carePackExperience



@Composable
fun TodayRoute(
    viewModel: TodayViewModel,
    onOpenCarePlan: () -> Unit,
    onOpenTodayReport: () -> Unit,
    onOpenOccurrence: (String) -> Unit,
) {
    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(
        lifecycleOwner,
        viewModel,
    ) {
        val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    viewModel.refresh()
                }
            }

        lifecycleOwner.lifecycle
            .addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle
                .removeObserver(observer)
        }
    }

    TodayScreen(
        state = state,
        onTodaySelected = viewModel::showToday,
        onHistorySelected = viewModel::showHistory,
        onRetry = viewModel::retry,
        onOpenCarePlan = onOpenCarePlan,
        onOpenTodayReport = onOpenTodayReport,
        onOpenOccurrence = onOpenOccurrence,
        onGiven = { occurrenceId ->
            viewModel.setReport(
                occurrenceId = occurrenceId,
                state = CaregiverReportState.GIVEN,
            )
        },
        onNotGiven = { occurrenceId ->
            viewModel.setReport(
                occurrenceId = occurrenceId,
                state = CaregiverReportState.NOT_GIVEN,
            )
        },
        onUnknown = { occurrenceId ->
            viewModel.setReport(
                occurrenceId = occurrenceId,
                state = CaregiverReportState.UNKNOWN,
            )
        },
        onRemindLater = viewModel::remindLater,
        onUndo = viewModel::undoReportChange,
        onSnackbarConsumed = viewModel::consumeSnackbar,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onTodaySelected: () -> Unit,
    onHistorySelected: () -> Unit,
    onRetry: () -> Unit,
    onOpenCarePlan: () -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onOpenSettings: () -> Unit = {},
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
    val experience = carePackExperience()

    Scaffold(
        modifier = modifier
                .fillMaxSize().testTag(
                    "today_screen",
                ),
    ) { contentPadding ->
        Box(
            modifier = Modifier
                    .fillMaxSize().padding(contentPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                        .fillMaxSize().navigationBarsPadding()
                        .testTag(
                            "today_content",
                        ),
                contentPadding = PaddingValues(
                        horizontal = experience.screenHorizontalPadding,
                        vertical = experience.screenVerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(
                        experience.itemSpacing,
                    ),
            ) {
                item {
                    TodayHeader(
                        localDate = state.localDate,
                        seniorMode = state.seniorMode,
                        onOpenTodayReport = onOpenTodayReport,
                    )
                }

                item {
                    ReminderAwarenessCard(
                        status = state.reminderStatus,
                    )
                }

                item {
                    TodayTabs(
                        selectedSection = state.selectedSection,
                        onTodaySelected = onTodaySelected,
                        onHistorySelected = onHistorySelected,
                    )
                }

                when (state.selectedSection) {
                    TodaySection.TODAY -> {
                        todayContent(
                            state = state,
                            onRetry = onRetry,
                            onOpenCarePlan = onOpenCarePlan,
                            onOpenOccurrence = onOpenOccurrence,
                            onGiven = onGiven,
                            onNotGiven = onNotGiven,
                            onUnknown = onUnknown,
                            onRemindLater = onRemindLater,
                        )
                    }

                    TodaySection.HISTORY -> {
                        historyContent(
                            state = state,
                            onRetry = onRetry,
                            onOpenOccurrence = onOpenOccurrence,
                        )
                    }
                }
            }

            if (state.snackbarMessage != null) {
                Snackbar(
                    modifier = Modifier
                            .align(
                                Alignment.BottomCenter,
                            ).navigationBarsPadding()
                            .padding(
                                experience.compactSpacing,
                            ).carePackPoliteLiveRegion()
                            .testTag(
                                "today_snackbar",
                            ),
                    action = {
                        if (state.undoChange != null) {
                            TextButton(
                                onClick = onUndo,
                                modifier = Modifier
                                        .carePackInteractiveControl().testTag(
                                            "today_undo_report",
                                        ),
                            ) {
                                Text(
                                    text = stringResource(
                                            R.string.undo,
                                        ),
                                )
                            }
                        } else {
                            TextButton(
                                onClick = onSnackbarConsumed,
                                modifier = Modifier
                                        .carePackInteractiveControl().testTag(
                                            "today_snackbar_dismiss",
                                        ),
                            ) {
                                Text(
                                    text = stringResource(
                                            R.string.dismiss_for_later,
                                        ),
                                )
                            }
                        }
                    },
                ) {
                    Text(
                        text = state.snackbarMessage,
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
                    testTag = "today_loading",
                )
            }
        }

        state.errorMessage != null -> {
            item {
                ErrorCard(
                    message = state.errorMessage,
                    onRetry = onRetry,
                    testTag = "today_error",
                )
            }
        }

        state.items.isEmpty() -> {
            item {
                TodayEmptyCard(
                    emptyState = state.emptyState,
                    onOpenCarePlan = onOpenCarePlan,
                    seniorMode = state.seniorMode,
                )
            }
        }

        state.seniorMode == SeniorMode.SIMPLE -> {
            val simpleItems = state.items
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
                items = simpleItems,
                key = {
                        _,
                        item ->
                    item.occurrenceId
                },
            ) { index, item ->
                SimpleTodayCard(
                    item = item,
                    isPrimary = index == 0,
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
                    testTag = "history_loading",
                )
            }
        }

        state.historyErrorMessage != null -> {
            item {
                ErrorCard(
                    message = state.historyErrorMessage,
                    onRetry = onRetry,
                    testTag = "history_error",
                )
            }
        }

        state.historyDays.isEmpty() -> {
            item {
                Card(
                    modifier = Modifier
                            .fillMaxWidth().testTag(
                                "history_empty",
                            ),
                ) {
                    Text(
                        text = "در هشت روز اخیر نوبتی برای نمایش وجود ندارد.",
                        modifier = Modifier.padding(
                                16.dp,
                            ),
                    )
                }
            }
        }

        else -> {
            state.historyDays.forEach { day ->
                item(
                    key = "history-day-${day.localDate}",
                ) {
                    Text(
                        text = day.localDate
                                .toJalaliDisplayText(),
                        style = MaterialTheme
                                .typography.titleMedium,
                        modifier = Modifier
                                .carePackHeading().padding(
                                    top = 8.dp,
                                ),
                    )
                }

                items(
                    items = day.items,
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
