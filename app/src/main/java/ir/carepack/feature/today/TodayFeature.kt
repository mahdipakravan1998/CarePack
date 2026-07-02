package ir.carepack.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.core.time.tickingNow
import ir.carepack.domain.calendar.JalaliPresentationDate
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.HistoryItem
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class TodaySection {
    TODAY,
    HISTORY,
}

data class TodayUiState(
    val localDate: LocalDate,
    val selectedSection: TodaySection = TodaySection.TODAY,
    val isLoading: Boolean = true,
    val items: List<TodayItem> = emptyList(),
    val emptyState: TodayEmptyState? = null,
    val errorMessage: String? = null,
    val isHistoryLoading: Boolean = true,
    val historyDays: List<HistoryDay> = emptyList(),
    val historyErrorMessage: String? = null,
    val remindersEnabled: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val todayQueryService: TodayQueryService,
    private val reminderPreferenceStore: ReminderPreferenceStore?,
    clock: Clock,
    private val zoneProvider: ZoneProvider,
    now: Flow<Instant> = tickingNow(clock),
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
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    private val selectedSection =
        MutableStateFlow(
            TodaySection.TODAY,
        )

    private val retryVersion =
        MutableStateFlow(0L)

    private val reminderPreferences =
        reminderPreferenceStore?.state
            ?: flowOf(
                ReminderPreferenceState(),
            )

    private val dateRequests =
        combine(
            sharedNow
                .map { instant ->
                    instant
                        .atZone(
                            zoneProvider.currentZone(),
                        )
                        .toLocalDate()
                }
                .distinctUntilChanged(),
            retryVersion,
        ) { localDate, retry ->
            DateRequest(
                localDate = localDate,
                retryVersion = retry,
            )
        }

    private val content =
        dateRequests.flatMapLatest { request ->
            combine(
                observeToday(
                    request.localDate,
                ),
                observeHistory(
                    request.localDate,
                ),
            ) { today, history ->
                DateContent(
                    localDate =
                        request.localDate,
                    today = today,
                    history = history,
                )
            }
        }

    val state =
        combine(
            selectedSection,
            content,
            reminderPreferences,
        ) { section, dateContent, reminderState ->
            TodayUiState(
                localDate =
                    dateContent.localDate,
                selectedSection = section,
                isLoading =
                    dateContent.today is TodayLoad.Loading,
                items =
                    (dateContent.today as? TodayLoad.Loaded)
                        ?.model
                        ?.items
                        .orEmpty(),
                emptyState =
                    (dateContent.today as? TodayLoad.Loaded)
                        ?.model
                        ?.emptyState,
                errorMessage =
                    (dateContent.today as? TodayLoad.Failed)
                        ?.message,
                isHistoryLoading =
                    dateContent.history is HistoryLoad.Loading,
                historyDays =
                    (dateContent.history as? HistoryLoad.Loaded)
                        ?.days
                        .orEmpty(),
                historyErrorMessage =
                    (dateContent.history as? HistoryLoad.Failed)
                        ?.message,
                remindersEnabled =
                    reminderState.remindersEnabled,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    TodayUiState(
                        localDate =
                            initialLocalDate,
                    ),
            )

    fun showToday() {
        selectedSection.value =
            TodaySection.TODAY
    }

    fun showHistory() {
        selectedSection.value =
            TodaySection.HISTORY
    }

    fun retry() {
        retryVersion.update {
            it + 1L
        }
    }

    fun refresh() {
        retry()
    }

    private fun observeToday(
        localDate: LocalDate,
    ): Flow<TodayLoad> =
        todayQueryService
            .observeToday(
                localDate = localDate,
                now = sharedNow,
            )
            .map<TodayModel, TodayLoad> {
                TodayLoad.Loaded(it)
            }
            .catch {
                emit(
                    TodayLoad.Failed(
                        "خواندن امروز انجام نشد.",
                    ),
                )
            }

    private fun observeHistory(
        localDate: LocalDate,
    ): Flow<HistoryLoad> =
        todayQueryService
            .observeRecentHistory(
                anchorDate = localDate,
                now = sharedNow,
            )
            .map<List<HistoryDay>, HistoryLoad> {
                HistoryLoad.Loaded(it)
            }
            .catch {
                emit(
                    HistoryLoad.Failed(
                        "خواندن سابقه انجام نشد.",
                    ),
                )
            }

    companion object {

        fun factory(
            todayQueryService: TodayQueryService,
            caregiverReportService: CaregiverReportService,
            carePlanService: CarePlanService,
            reminderPreferenceStore: ReminderPreferenceStore? = null,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    @Suppress("UNUSED_VARIABLE")
                    val ignoredCaregiverReportService =
                        caregiverReportService

                    @Suppress("UNUSED_VARIABLE")
                    val ignoredCarePlanService =
                        carePlanService

                    TodayViewModel(
                        todayQueryService =
                            todayQueryService,
                        reminderPreferenceStore =
                            reminderPreferenceStore,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }
    }
}

private data class DateRequest(
    val localDate: LocalDate,
    val retryVersion: Long,
)

private data class DateContent(
    val localDate: LocalDate,
    val today: TodayLoad,
    val history: HistoryLoad,
)

private sealed interface TodayLoad {
    data object Loading : TodayLoad

    data class Loaded(
        val model: TodayModel,
    ) : TodayLoad

    data class Failed(
        val message: String,
    ) : TodayLoad
}

private sealed interface HistoryLoad {
    data object Loading : HistoryLoad

    data class Loaded(
        val days: List<HistoryDay>,
    ) : HistoryLoad

    data class Failed(
        val message: String,
    ) : HistoryLoad
}

@Composable
fun TodayRoute(
    viewModel: TodayViewModel,
    onOpenCarePlan: () -> Unit,
    onOpenSettings: () -> Unit,
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
        onOpenOccurrence =
            onOpenOccurrence,
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
    onOpenOccurrence: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "today_screen",
                ),
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .navigationBarsPadding()
                    .testTag(
                        "today_content",
                    ),
            contentPadding =
                PaddingValues(
                    horizontal = 20.dp,
                    vertical = 16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            item {
                TodayHeader(
                    localDate = state.localDate,
                    onOpenSettings =
                        onOpenSettings,
                )
            }

            item {
                TodaySectionSwitcher(
                    selectedSection =
                        state.selectedSection,
                    onTodaySelected =
                        onTodaySelected,
                    onHistorySelected =
                        onHistorySelected,
                )
            }

            if (state.selectedSection == TodaySection.TODAY) {
                todayContent(
                    state = state,
                    onRetry = onRetry,
                    onOpenCarePlan =
                        onOpenCarePlan,
                    onOpenOccurrence =
                        onOpenOccurrence,
                )
            } else {
                historyContent(
                    state = state,
                    onRetry = onRetry,
                    onOpenOccurrence =
                        onOpenOccurrence,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.todayContent(
    state: TodayUiState,
    onRetry: () -> Unit,
    onOpenCarePlan: () -> Unit,
    onOpenOccurrence: (String) -> Unit,
) {
    when {
        state.isLoading -> {
            item {
                LoadingCard()
            }
        }

        state.errorMessage != null -> {
            item {
                ErrorCard(
                    message =
                        state.errorMessage,
                    onRetry = onRetry,
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
                    onClick = {
                        onOpenOccurrence(
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
                LoadingCard()
            }
        }

        state.historyErrorMessage != null -> {
            item {
                ErrorCard(
                    message =
                        state.historyErrorMessage,
                    onRetry = onRetry,
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
                    Column(
                        modifier =
                            Modifier.padding(
                                16.dp,
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp,
                            ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.history_empty_title,
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                        )

                        Text(
                            text =
                                stringResource(
                                    R.string.history_empty_body,
                                ),
                        )
                    }
                }
            }
        }

        else -> {
            state.historyDays.forEach { day ->
                item {
                    Text(
                        text =
                            JalaliPresentationDate
                                .from(day.localDate)
                                .formatNumeric(),
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        modifier =
                            Modifier.testTag(
                                "history_day_${day.localDate}",
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
                        onClick = {
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

@Composable
private fun TodayHeader(
    localDate: LocalDate,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    4.dp,
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
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "today_title",
                        ),
            )

            Text(
                text =
                    JalaliPresentationDate
                        .from(localDate)
                        .formatNumeric(),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
                        .copy(
                            textDirection =
                                TextDirection.Ltr,
                        ),
                modifier =
                    Modifier.testTag(
                        "today_date",
                    ),
            )
        }

        TextButton(
            onClick =
                onOpenSettings,
            modifier =
                Modifier.testTag(
                    "today_settings",
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.carepack_settings_title,
                    ),
            )
        }
    }
}

@Composable
private fun TodaySectionSwitcher(
    selectedSection: TodaySection,
    onTodaySelected: () -> Unit,
    onHistorySelected: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                8.dp,
            ),
    ) {
        Button(
            onClick =
                onTodaySelected,
            enabled =
                selectedSection !=
                        TodaySection.TODAY,
            modifier =
                Modifier.weight(1f),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.today_section,
                    ),
            )
        }

        Button(
            onClick =
                onHistorySelected,
            enabled =
                selectedSection !=
                        TodaySection.HISTORY,
            modifier =
                Modifier.weight(1f),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.history_section,
                    ),
            )
        }
    }
}

@Composable
private fun TodayItemCard(
    item: TodayItem,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                )
                .testTag(
                    "today_item_${item.occurrenceId}",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    item.medicationName,
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
            )

            Text(
                text =
                    item.localTime
                        .toHourMinuteText(),
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall
                        .copy(
                            textDirection =
                                TextDirection.Ltr,
                        ),
            )

            Text(
                text =
                    item.medicationInstruction,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
            )

            Text(
                text =
                    reportStateText(
                        item.reportState,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .labelLarge,
            )

            if (item.isOverdue) {
                Text(
                    text =
                        stringResource(
                            R.string.recording_time_passed,
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                    modifier =
                        Modifier.carePackPoliteLiveRegion(),
                )
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: HistoryItem,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                )
                .testTag(
                    "history_item_${item.occurrenceId}",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    item.medicationName,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
            )

            Text(
                text =
                    item.localTime
                        .toHourMinuteText(),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
                        .copy(
                            textDirection =
                                TextDirection.Ltr,
                        ),
            )

            Text(
                text =
                    reportStateText(
                        item.reportState,
                    ),
            )
        }
    }
}

@Composable
private fun TodayEmptyCard(
    emptyState: TodayEmptyState?,
    onOpenCarePlan: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "today_empty",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            val title =
                when (emptyState) {
                    TodayEmptyState.NO_MEDICATIONS ->
                        stringResource(
                            R.string.today_no_medications_title,
                        )

                    TodayEmptyState.NO_OCCURRENCES ->
                        stringResource(
                            R.string.today_no_occurrences_title,
                        )

                    null ->
                        stringResource(
                            R.string.today_empty_title,
                        )
                }

            val body =
                when (emptyState) {
                    TodayEmptyState.NO_MEDICATIONS ->
                        stringResource(
                            R.string.today_no_medications_body,
                        )

                    TodayEmptyState.NO_OCCURRENCES ->
                        stringResource(
                            R.string.today_no_occurrences_body,
                        )

                    null ->
                        stringResource(
                            R.string.today_empty_body,
                        )
                }

            Text(
                text = title,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
            )

            Text(
                text = body,
            )

            OutlinedButton(
                onClick =
                    onOpenCarePlan,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.manage_care_plan,
                        ),
                )
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "today_loading",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    24.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            CircularProgressIndicator()

            Text(
                text =
                    stringResource(
                        R.string.loading,
                    ),
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "today_error",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            Text(
                text = message,
                color =
                    MaterialTheme
                        .colorScheme
                        .error,
            )

            Button(
                onClick = onRetry,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.retry,
                        ),
                )
            }
        }
    }
}

@Composable
private fun reportStateText(
    state: CaregiverReportState?,
): String =
    when (state) {
        CaregiverReportState.GIVEN ->
            stringResource(
                R.string.report_given,
            )

        CaregiverReportState.NOT_GIVEN ->
            stringResource(
                R.string.report_not_given,
            )

        CaregiverReportState.UNKNOWN ->
            stringResource(
                R.string.report_unknown,
            )

        null ->
            stringResource(
                R.string.report_no_report,
            )
    }

private fun LocalTime.toHourMinuteText(): String =
    "%02d:%02d".format(
        hour,
        minute,
    )
