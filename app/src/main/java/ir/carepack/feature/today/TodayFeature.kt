package ir.carepack.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.core.time.tickingNow
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.HistoryItem
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.feature.reporting.reportStateText
import ir.carepack.feature.reporting.temporalPhaseText
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.today.TodayQueryService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update

enum class TodaySection {
    TODAY,
    HISTORY,
}

data class TodayUiState(
    val localDate: LocalDate,
    val selectedSection: TodaySection =
        TodaySection.TODAY,
    val isLoading: Boolean = true,
    val items: List<TodayItem> =
        emptyList(),
    val emptyState: TodayEmptyState? = null,
    val errorMessage: String? = null,
    val isHistoryLoading: Boolean = true,
    val historyDays: List<HistoryDay> =
        emptyList(),
    val historyErrorMessage: String? = null,
)

class TodayViewModel(
    private val todayQueryService: TodayQueryService,
    clock: Clock,
    private val zoneProvider: ZoneProvider,
    now: Flow<Instant> =
        tickingNow(clock),
) : ViewModel() {

    private val initialLocalDate =
        clock
            .instant()
            .atZone(
                zoneProvider.currentZone(),
            )
            .toLocalDate()

    private val sharedNow =
        now.shareIn(
            scope = viewModelScope,
            started =
                SharingStarted.Eagerly,
            replay = 1,
        )

    private val currentLocalDate =
        sharedNow
            .map { instant ->
                instant
                    .atZone(
                        zoneProvider.currentZone(),
                    )
                    .toLocalDate()
            }
            .distinctUntilChanged()

    private val mutableState =
        MutableStateFlow(
            TodayUiState(
                localDate =
                    initialLocalDate,
            ),
        )

    val state =
        mutableState.asStateFlow()

    private var todayObservationJob: Job? =
        null

    private var historyObservationJob: Job? =
        null

    init {
        currentLocalDate
            .onEach { localDate ->
                onLocalDateChanged(
                    localDate = localDate,
                )
            }
            .launchIn(viewModelScope)
    }

    fun showToday() {
        mutableState.update { current ->
            current.copy(
                selectedSection =
                    TodaySection.TODAY,
            )
        }
    }

    fun showHistory() {
        mutableState.update { current ->
            current.copy(
                selectedSection =
                    TodaySection.HISTORY,
            )
        }
    }

    fun retry() {
        val localDate =
            mutableState.value.localDate

        observeToday(
            localDate = localDate,
        )

        observeHistory(
            localDate = localDate,
        )
    }

    private fun onLocalDateChanged(
        localDate: LocalDate,
    ) {
        mutableState.update { current ->
            current.copy(
                localDate = localDate,
            )
        }

        observeToday(
            localDate = localDate,
        )

        observeHistory(
            localDate = localDate,
        )
    }

    private fun observeToday(
        localDate: LocalDate,
    ) {
        todayObservationJob?.cancel()

        mutableState.update { current ->
            current.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        todayObservationJob =
            todayQueryService
                .observeToday(
                    localDate = localDate,
                    now = sharedNow,
                )
                .onEach { model ->
                    mutableState.update { current ->
                        if (
                            current.localDate !=
                            model.localDate
                        ) {
                            current
                        } else {
                            current.copy(
                                isLoading = false,
                                items = model.items,
                                emptyState =
                                    model.emptyState,
                                errorMessage = null,
                            )
                        }
                    }
                }
                .catch {
                    mutableState.update { current ->
                        if (
                            current.localDate !=
                            localDate
                        ) {
                            current
                        } else {
                            current.copy(
                                isLoading = false,
                                errorMessage =
                                    "خواندن اطلاعات امروز انجام نشد.",
                            )
                        }
                    }
                }
                .launchIn(viewModelScope)
    }

    private fun observeHistory(
        localDate: LocalDate,
    ) {
        historyObservationJob?.cancel()

        mutableState.update { current ->
            current.copy(
                isHistoryLoading = true,
                historyErrorMessage = null,
            )
        }

        historyObservationJob =
            todayQueryService
                .observeRecentHistory(
                    anchorDate = localDate,
                    now = sharedNow,
                )
                .onEach { historyDays ->
                    mutableState.update { current ->
                        if (
                            current.localDate !=
                            localDate
                        ) {
                            current
                        } else {
                            current.copy(
                                isHistoryLoading =
                                    false,
                                historyDays =
                                    historyDays,
                                historyErrorMessage =
                                    null,
                            )
                        }
                    }
                }
                .catch {
                    mutableState.update { current ->
                        if (
                            current.localDate !=
                            localDate
                        ) {
                            current
                        } else {
                            current.copy(
                                isHistoryLoading =
                                    false,
                                historyErrorMessage =
                                    "خواندن سابقه انجام نشد.",
                            )
                        }
                    }
                }
                .launchIn(viewModelScope)
    }

    companion object {

        fun factory(
            todayQueryService: TodayQueryService,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    TodayViewModel(
                        todayQueryService =
                            todayQueryService,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }
        }
    }
}

@Composable
fun TodayRoute(
    viewModel: TodayViewModel,
    onOccurrenceSelected: (String) -> Unit,
    onManageCarePlan: () -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    TodayScreen(
        state = state,
        onOccurrenceSelected =
            onOccurrenceSelected,
        onManageCarePlan =
            onManageCarePlan,
        onShowToday =
            viewModel::showToday,
        onShowHistory =
            viewModel::showHistory,
        onRetry =
            viewModel::retry,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onOccurrenceSelected: (String) -> Unit,
    onManageCarePlan: () -> Unit,
    modifier: Modifier = Modifier,
    onShowToday: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Scaffold(
        modifier =
            modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(
                        horizontal = 24.dp,
                        vertical = 16.dp,
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.today_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp),
            )

            Text(
                text =
                    state.localDate
                        .toString(),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                modifier =
                    Modifier.testTag(
                        "today_local_date",
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            OutlinedButton(
                onClick =
                    onManageCarePlan,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "manage_care_plan",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .manage_care_plan,
                        ),
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                SectionButton(
                    text =
                        stringResource(
                            R.string
                                .pr3_today_section,
                        ),
                    selected =
                        state.selectedSection ==
                                TodaySection.TODAY,
                    onClick =
                        onShowToday,
                    testTag =
                        "today_section",
                    modifier =
                        Modifier.weight(1f),
                )

                SectionButton(
                    text =
                        stringResource(
                            R.string
                                .pr3_history_section,
                        ),
                    selected =
                        state.selectedSection ==
                                TodaySection.HISTORY,
                    onClick =
                        onShowHistory,
                    testTag =
                        "history_section",
                    modifier =
                        Modifier.weight(1f),
                )
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp),
            )

            when (state.selectedSection) {
                TodaySection.TODAY -> {
                    TodayContent(
                        state = state,
                        onOccurrenceSelected =
                            onOccurrenceSelected,
                        onRetry = onRetry,
                        modifier =
                            Modifier.weight(1f),
                    )
                }

                TodaySection.HISTORY -> {
                    HistoryContent(
                        state = state,
                        onOccurrenceSelected =
                            onOccurrenceSelected,
                        onRetry = onRetry,
                        modifier =
                            Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier =
                modifier.testTag(
                    testTag,
                ),
        ) {
            Text(text = text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier =
                modifier.testTag(
                    testTag,
                ),
        ) {
            Text(text = text)
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onOccurrenceSelected: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> {
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .testTag(
                            "today_loading",
                        ),
            ) {
                CircularProgressIndicator()
            }
        }

        state.errorMessage != null -> {
            ErrorContent(
                message =
                    state.errorMessage,
                onRetry = onRetry,
                testTag =
                    "today_error",
                modifier = modifier,
            )
        }

        state.items.isEmpty() -> {
            TodayEmptyContent(
                emptyState =
                    state.emptyState,
                modifier = modifier,
            )
        }

        else -> {
            LazyColumn(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .testTag(
                            "today_list",
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp,
                    ),
            ) {
                items(
                    items = state.items,
                    key = { item ->
                        item.occurrenceId
                    },
                ) { item ->
                    TodayOccurrenceCard(
                        item = item,
                        onClick = {
                            onOccurrenceSelected(
                                item.occurrenceId,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayEmptyContent(
    emptyState: TodayEmptyState?,
    modifier: Modifier = Modifier,
) {
    val hasNoMedications =
        emptyState ==
                TodayEmptyState.NO_MEDICATIONS

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(
                    if (hasNoMedications) {
                        "today_empty_no_medications"
                    } else {
                        "today_empty_no_occurrences"
                    },
                ),
    ) {
        Text(
            text =
                stringResource(
                    if (hasNoMedications) {
                        R.string
                            .pr3_no_medications_title
                    } else {
                        R.string
                            .pr3_no_occurrences_title
                    },
                ),
            style =
                MaterialTheme
                    .typography
                    .titleLarge,
            modifier =
                Modifier.semantics {
                    heading()
                },
        )

        Spacer(
            modifier =
                Modifier.height(8.dp),
        )

        Text(
            text =
                stringResource(
                    if (hasNoMedications) {
                        R.string
                            .pr3_no_medications_body
                    } else {
                        R.string
                            .pr3_no_occurrences_body
                    },
                ),
        )
    }
}

@Composable
private fun HistoryContent(
    state: TodayUiState,
    onOccurrenceSelected: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isHistoryLoading -> {
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .testTag(
                            "history_loading",
                        ),
            ) {
                CircularProgressIndicator()
            }
        }

        state.historyErrorMessage != null -> {
            ErrorContent(
                message =
                    state.historyErrorMessage,
                onRetry = onRetry,
                testTag =
                    "history_error",
                modifier = modifier,
            )
        }

        state.historyDays.isEmpty() -> {
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .testTag(
                            "history_empty",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .pr3_history_empty_title,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    modifier =
                        Modifier.semantics {
                            heading()
                        },
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp),
                )

                Text(
                    text =
                        stringResource(
                            R.string
                                .pr3_history_empty_body,
                        ),
                )
            }
        }

        else -> {
            LazyColumn(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .testTag(
                            "history_list",
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        16.dp,
                    ),
            ) {
                items(
                    items =
                        state.historyDays,
                    key = { historyDay ->
                        historyDay.localDate
                    },
                ) { historyDay ->
                    HistoryDayContent(
                        historyDay =
                            historyDay,
                        onOccurrenceSelected =
                            onOccurrenceSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDayContent(
    historyDay: HistoryDay,
    onOccurrenceSelected: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Text(
            text =
                historyDay.localDate
                    .toString(),
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            modifier =
                Modifier
                    .testTag(
                        "history_date_" +
                                historyDay.localDate,
                    )
                    .semantics {
                        heading()
                    },
        )

        Spacer(
            modifier =
                Modifier.height(8.dp),
        )

        historyDay.items.forEach { item ->
            HistoryOccurrenceCard(
                item = item,
                onClick = {
                    onOccurrenceSelected(
                        item.occurrenceId,
                    )
                },
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )
        }
    }
}

@Composable
private fun TodayOccurrenceCard(
    item: TodayItem,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(
                    mergeDescendants = true,
                ) {}
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
                .testTag(
                    "today_item_" +
                            item.occurrenceId,
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(20.dp),
        ) {
            Text(
                text =
                    item.medicationName,
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    item.localTime.format(
                        HOUR_MINUTE_FORMATTER,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    temporalPhaseText(
                        item.temporalPhase,
                    ),
                modifier =
                    Modifier.testTag(
                        "today_phase_" +
                                item.occurrenceId,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp),
            )

            Text(
                text =
                    if (item.isOverdue) {
                        stringResource(
                            R.string
                                .pr3_recording_time_passed,
                        )
                    } else {
                        reportStateText(
                            item.reportState,
                        )
                    },
                color =
                    if (item.isOverdue) {
                        MaterialTheme
                            .colorScheme
                            .error
                    } else {
                        MaterialTheme
                            .colorScheme
                            .onSurface
                    },
                modifier =
                    Modifier.testTag(
                        "today_report_" +
                                item.occurrenceId,
                    ),
            )
        }
    }
}

@Composable
private fun HistoryOccurrenceCard(
    item: HistoryItem,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(
                    mergeDescendants = true,
                ) {}
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
                .testTag(
                    "history_item_" +
                            item.occurrenceId,
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
        ) {
            Text(
                text =
                    item.medicationName,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp),
            )

            Text(
                text =
                    item.localTime.format(
                        HOUR_MINUTE_FORMATTER,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp),
            )

            Text(
                text =
                    when {
                        item.lifecycle ==
                                OccurrenceLifecycle.CANCELLED -> {
                            stringResource(
                                R.string
                                    .pr3_cancelled_occurrence,
                            )
                        }

                        item.isOverdue -> {
                            stringResource(
                                R.string
                                    .pr3_recording_time_passed,
                            )
                        }

                        else -> {
                            reportStateText(
                                item.reportState,
                            )
                        }
                    },
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(testTag),
    ) {
        Text(
            text = message,
            color =
                MaterialTheme
                    .colorScheme
                    .error,
        )

        Spacer(
            modifier =
                Modifier.height(12.dp),
        )

        Button(
            onClick = onRetry,
            modifier =
                Modifier.testTag(
                    "${testTag}_retry",
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.pr3_retry,
                    ),
            )
        }
    }
}

private val HOUR_MINUTE_FORMATTER =
    DateTimeFormatter.ofPattern(
        "HH:mm",
    )
