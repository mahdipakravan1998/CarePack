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
    val reminderStatus: ReminderStatus? = null,
    val seniorMode: SeniorMode = SeniorMode.STANDARD,
    val snackbarMessage: String? = null,
    val undoChange: ReportChange? = null,
)

private data class TodayTransientState(
    val snackbarMessage: String? = null,
    val undoChange: ReportChange? = null,
)

private data class TodayUserState(
    val reminderPreferenceState: ReminderPreferenceState,
    val userExperienceState: UserExperiencePreferenceState,
    val reminderStatus: ReminderStatus?,
    val transient: TodayTransientState,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val todayQueryService: TodayQueryService,
    private val caregiverReportService: CaregiverReportService,
    private val reminderCoordinator: ReminderCoordinator,
    private val reminderPreferenceStore: ReminderPreferenceStore?,
    private val userExperiencePreferenceStore: UserExperiencePreferenceStore?,
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

    private val mutableReminderStatus =
        MutableStateFlow<ReminderStatus?>(
            null,
        )

    private val transientState =
        MutableStateFlow(
            TodayTransientState(),
        )

    private var undoJob: Job? =
        null

    private val reminderPreferences =
        reminderPreferenceStore?.state
            ?: flowOf(
                ReminderPreferenceState(),
            )

    private val userExperiencePreferences =
        userExperiencePreferenceStore?.state
            ?: flowOf(
                UserExperiencePreferenceState(),
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
                    localDate =
                        request.localDate,
                ),
                observeHistory(
                    localDate =
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

    private val userState =
        combine(
            reminderPreferences,
            userExperiencePreferences,
            mutableReminderStatus,
            transientState,
        ) {
                reminderPreferenceState,
                userExperienceState,
                reminderStatus,
                transient,
            ->
            TodayUserState(
                reminderPreferenceState =
                    reminderPreferenceState,
                userExperienceState =
                    userExperienceState,
                reminderStatus =
                    reminderStatus,
                transient =
                    transient,
            )
        }

    val state =
        combine(
            selectedSection,
            content,
            userState,
        ) {
                section,
                dateContent,
                userState,
            ->
            TodayUiState(
                localDate =
                    dateContent.localDate,
                selectedSection =
                    section,
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
                reminderStatus =
                    userState
                        .reminderStatus
                        ?.copy(
                            remindersEnabled =
                                userState
                                    .reminderPreferenceState
                                    .remindersEnabled,
                        ),
                seniorMode =
                    userState
                        .userExperienceState
                        .seniorMode,
                snackbarMessage =
                    userState
                        .transient
                        .snackbarMessage,
                undoChange =
                    userState
                        .transient
                        .undoChange,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted.Eagerly,
                initialValue =
                    TodayUiState(
                        localDate =
                            initialLocalDate,
                    ),
            )

    init {
        refresh()
    }

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

        refresh()
    }

    fun refresh() {
        refreshReminderStatus()
    }

    fun setReport(
        occurrenceId: String,
        state: CaregiverReportState,
    ) {
        require(occurrenceId.isNotBlank())

        viewModelScope.launch {
            try {
                when (
                    val outcome =
                        caregiverReportService.setReport(
                            occurrenceId =
                                occurrenceId,
                            newState =
                                state,
                        )
                ) {
                    is SetReportOutcome.Changed -> {
                        showReportChanged(
                            change =
                                outcome.change,
                        )
                    }

                    is SetReportOutcome.Unchanged -> {
                        showSnackbar(
                            message =
                                "این وضعیت قبلاً ثبت شده است.",
                        )
                    }

                    SetReportOutcome.CancelledOccurrenceRejected -> {
                        showSnackbar(
                            message =
                                "برای نوبت لغوشده نمی‌توان گزارش ثبت کرد.",
                        )
                    }

                    SetReportOutcome.OccurrenceNotFound -> {
                        showSnackbar(
                            message =
                                "نوبت پیدا نشد.",
                        )
                    }
                }
            } catch (
                cancellation:
                CancellationException,
            ) {
                throw cancellation
            } catch (_: Exception) {
                showSnackbar(
                    message =
                        "ثبت گزارش انجام نشد.",
                )
            }
        }
    }

    fun remindLater(
        occurrenceId: String,
    ) {
        require(occurrenceId.isNotBlank())

        viewModelScope.launch {
            try {
                when (
                    reminderCoordinator
                        .remindLater(
                            occurrenceId =
                                occurrenceId,
                        )
                ) {
                    is RemindLaterOutcome.Scheduled -> {
                        showSnackbar(
                            message =
                                "یادآوری دوباره ثبت شد.",
                        )
                    }

                    is RemindLaterOutcome.Ignored -> {
                        showSnackbar(
                            message =
                                "برای این نوبت امکان یادآوری دوباره وجود ندارد.",
                        )
                    }

                    RemindLaterOutcome.SchedulingFailed -> {
                        showSnackbar(
                            message =
                                "ثبت یادآوری دوباره انجام نشد.",
                        )
                    }
                }

                refreshReminderStatus()
            } catch (
                cancellation:
                CancellationException,
            ) {
                throw cancellation
            } catch (_: Exception) {
                showSnackbar(
                    message =
                        "ثبت یادآوری دوباره انجام نشد.",
                )
            }
        }
    }

    fun undoReportChange() {
        val change =
            transientState
                .value
                .undoChange
                ?: return

        viewModelScope.launch {
            try {
                when (
                    caregiverReportService
                        .restorePrevious(
                            change = change,
                        )
                ) {
                    is UndoReportOutcome.Restored -> {
                        undoJob?.cancel()

                        transientState.value =
                            TodayTransientState(
                                snackbarMessage =
                                    "تغییر گزارش برگردانده شد.",
                            )
                    }

                    UndoReportOutcome.NoLongerCurrent,
                    UndoReportOutcome.OccurrenceNotFound,
                        -> {
                        undoJob?.cancel()

                        transientState.value =
                            TodayTransientState(
                                snackbarMessage =
                                    "واگرد دیگر در دسترس نیست.",
                            )
                    }
                }
            } catch (
                cancellation:
                CancellationException,
            ) {
                throw cancellation
            } catch (_: Exception) {
                showSnackbar(
                    message =
                        "واگرد انجام نشد.",
                )
            }
        }
    }

    fun consumeSnackbar() {
        transientState.update {
            it.copy(
                snackbarMessage = null,
            )
        }
    }

    private fun refreshReminderStatus() {
        viewModelScope.launch {
            try {
                mutableReminderStatus.value =
                    reminderCoordinator
                        .currentStatus()
            } catch (
                cancellation:
                CancellationException,
            ) {
                throw cancellation
            } catch (_: Exception) {
                mutableReminderStatus.value =
                    null
            }
        }
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
            .onStart {
                emit(TodayLoad.Loading)
            }
            .catch { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }

                emit(
                    TodayLoad.Failed(
                        message =
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
            .onStart {
                emit(HistoryLoad.Loading)
            }
            .catch { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }

                emit(
                    HistoryLoad.Failed(
                        message =
                            "خواندن سابقه انجام نشد.",
                    ),
                )
            }

    private fun showReportChanged(
        change: ReportChange,
    ) {
        undoJob?.cancel()

        transientState.value =
            TodayTransientState(
                snackbarMessage =
                    "گزارش ثبت شد.",
                undoChange =
                    change,
            )

        undoJob =
            viewModelScope.launch {
                delay(
                    UNDO_WINDOW_MILLIS,
                )

                transientState.update {
                    it.copy(
                        undoChange = null,
                    )
                }
            }
    }

    private fun showSnackbar(
        message: String,
    ) {
        transientState.update {
            it.copy(
                snackbarMessage =
                    message,
            )
        }
    }

    override fun onCleared() {
        undoJob?.cancel()

        super.onCleared()
    }

    companion object {
        fun factory(
            todayQueryService: TodayQueryService,
            caregiverReportService: CaregiverReportService,
            carePlanService: CarePlanService,
            reminderPreferenceStore: ReminderPreferenceStore? = null,
            reminderCoordinator: ReminderCoordinator,
            userExperiencePreferenceStore: UserExperiencePreferenceStore? = null,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    @Suppress("UNUSED_VARIABLE")
                    val retainedCarePlanService =
                        carePlanService

                    TodayViewModel(
                        todayQueryService =
                            todayQueryService,
                        caregiverReportService =
                            caregiverReportService,
                        reminderCoordinator =
                            reminderCoordinator,
                        reminderPreferenceStore =
                            reminderPreferenceStore,
                        userExperiencePreferenceStore =
                            userExperiencePreferenceStore,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }

        private const val UNDO_WINDOW_MILLIS =
            8_000L
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
                            .padding(16.dp)
                            .carePackPoliteLiveRegion()
                            .testTag(
                                "today_snackbar",
                            ),
                    action = {
                        if (state.undoChange != null) {
                            TextButton(
                                onClick = onUndo,
                                modifier =
                                    Modifier.testTag(
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
                                    Modifier.testTag(
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

@Composable
private fun TodayHeader(
    localDate: LocalDate,
    seniorMode: SeniorMode,
    onOpenSettings: () -> Unit,
    onOpenTodayReport: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "today_header",
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp,
            ),
    ) {
        Text(
            text =
                if (seniorMode == SeniorMode.SIMPLE) {
                    stringResource(
                        R.string.today_simple_title,
                    )
                } else {
                    stringResource(
                        R.string.today_title,
                    )
                },
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
                localDate
                    .toJalaliDisplayText(),
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
                    "today_jalali_date",
                ),
        )

        OutlinedButton(
            onClick =
                onOpenTodayReport,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "today_open_report",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.carepack_settings_today_report,
                    ),
            )
        }

        OutlinedButton(
            onClick =
                onOpenSettings,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "today_open_settings",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.primary_nav_settings,
                    ),
            )
        }
    }
}

@Composable
private fun ReminderAwarenessCard(
    status: ReminderStatus?,
) {
    val message =
        when (status?.availability) {
            ReminderAvailability.NOTIFICATION_PERMISSION_REQUIRED -> {
                stringResource(
                    R.string.today_notification_unavailable_body,
                )
            }

            ReminderAvailability.APPROXIMATE -> {
                stringResource(
                    R.string.today_approximate_reminder_body,
                )
            }

            else -> null
        }

    if (message != null) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "today_reminder_awareness",
                    ),
        ) {
            Text(
                text = message,
                modifier =
                    Modifier.padding(
                        16.dp,
                    ),
            )
        }
    }
}

@Composable
private fun TodayTabs(
    selectedSection: TodaySection,
    onTodaySelected: () -> Unit,
    onHistorySelected: () -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex =
            selectedSection.ordinal,
        modifier =
            Modifier.testTag(
                "today_tabs",
            ),
    ) {
        Tab(
            selected =
                selectedSection ==
                        TodaySection.TODAY,
            onClick =
                onTodaySelected,
            text = {
                Text(
                    text =
                        stringResource(
                            R.string.today_title,
                        ),
                )
            },
            modifier =
                Modifier.testTag(
                    "today_tab_today",
                ),
        )

        Tab(
            selected =
                selectedSection ==
                        TodaySection.HISTORY,
            onClick =
                onHistorySelected,
            text = {
                Text(
                    text = "سابقه اخیر",
                )
            },
            modifier =
                Modifier.testTag(
                    "today_tab_history",
                ),
        )
    }
}

@Composable
private fun SimpleTodayCard(
    item: TodayItem,
    isPrimary: Boolean,
    onGiven: () -> Unit,
    onNotGiven: () -> Unit,
    onUnknown: () -> Unit,
    onRemindLater: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val canRecord =
        item.lifecycle ==
                OccurrenceLifecycle.ACTIVE

    val statusText =
        item.statusText()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "نوبت امروز ${item.medicationName}، ساعت ${item.localTime.toDisplayText()}، $statusText"
                }
                .testTag(
                    "simple_today_card_${item.occurrenceId}",
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(
                        20.dp,
                    )
                    .testTag(
                        "simple_today_card",
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            Text(
                text =
                    if (isPrimary) {
                        stringResource(
                            R.string.today_next_item,
                        )
                    } else {
                        "نوبت امروز"
                    },
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.carePackHeading(),
            )

            Text(
                text =
                    item
                        .localTime
                        .toDisplayText(),
                style =
                    MaterialTheme
                        .typography
                        .displaySmall
                        .copy(
                            textDirection =
                                TextDirection.Ltr,
                        ),
                modifier =
                    Modifier.testTag(
                        "simple_today_time",
                    ),
            )

            Text(
                text =
                    item.medicationName,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier.testTag(
                        "simple_today_medication_name",
                    ),
            )

            Text(
                text =
                    item.medicationInstruction,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.testTag(
                        "simple_today_instruction",
                    ),
            )

            item
                .recordingDetailsText()
                .takeIf(String::isNotBlank)
                ?.let { details ->
                    Text(
                        text =
                            details,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        modifier =
                            Modifier.testTag(
                                "simple_today_recording_details",
                            ),
                    )
                }

            Text(
                text =
                    statusText,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier
                        .carePackPoliteLiveRegion()
                        .testTag(
                            "simple_today_status",
                        ),
            )

            Button(
                onClick = onGiven,
                enabled = canRecord,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 64.dp,
                        )
                        .testTag(
                            "simple_today_given_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.record_given,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                )
            }

            Button(
                onClick =
                    onRemindLater,
                enabled =
                    canRecord &&
                            item.reportState == null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 64.dp,
                        )
                        .testTag(
                            "simple_today_remind_later_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.remind_later,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                )
            }

            Text(
                text =
                    stringResource(
                        R.string.today_secondary_actions,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleSmall,
                modifier =
                    Modifier.carePackHeading(),
            )

            OutlinedButton(
                onClick =
                    onNotGiven,
                enabled = canRecord,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(
                            minHeight = 56.dp,
                        )
                        .testTag(
                            "simple_today_not_given_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.record_not_given,
                        ),
                )
            }

            OutlinedButton(
                onClick =
                    onUnknown,
                enabled = canRecord,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(
                            minHeight = 56.dp,
                        )
                        .testTag(
                            "simple_today_unknown_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.record_unknown,
                        ),
                )
            }

            TextButton(
                onClick =
                    onOpenDetails,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(
                            minHeight = 56.dp,
                        )
                        .testTag(
                            "simple_today_details_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.detail_title,
                        ),
                )
            }
        }
    }
}

@Composable
private fun TodayItemCard(
    item: TodayItem,
    onOpen: () -> Unit,
    onGiven: () -> Unit,
    onNotGiven: () -> Unit,
    onUnknown: () -> Unit,
    onRemindLater: () -> Unit,
) {
    val canRecord =
        item.lifecycle ==
                OccurrenceLifecycle.ACTIVE

    val statusText =
        item.statusText()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = onOpen,
                )
                .semantics {
                    contentDescription =
                        "${item.medicationName}، ساعت ${item.localTime.toDisplayText()}، $statusText"
                }
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
                    12.dp,
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
                        item.medicationName,
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    modifier =
                        Modifier.weight(1f),
                )

                Text(
                    text =
                        item
                            .localTime
                            .toDisplayText(),
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                            .copy(
                                textDirection =
                                    TextDirection.Ltr,
                            ),
                )
            }

            Text(
                text =
                    item.medicationInstruction,
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            item
                .recordingDetailsText()
                .takeIf(String::isNotBlank)
                ?.let { details ->
                    Text(
                        text =
                            details,
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                        modifier =
                            Modifier.testTag(
                                "today_recording_details_${item.occurrenceId}",
                            ),
                    )
                }

            Text(
                text =
                    statusText,
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                modifier =
                    Modifier.carePackPoliteLiveRegion(),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                Button(
                    onClick = onGiven,
                    enabled = canRecord,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(
                                "today_given_${item.occurrenceId}",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.record_given,
                            ),
                    )
                }

                OutlinedButton(
                    onClick =
                        onRemindLater,
                    enabled =
                        canRecord &&
                                item.reportState == null,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(
                                "today_remind_later_${item.occurrenceId}",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.remind_later,
                            ),
                    )
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                OutlinedButton(
                    onClick =
                        onNotGiven,
                    enabled = canRecord,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(
                                "today_not_given_${item.occurrenceId}",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.record_not_given,
                            ),
                    )
                }

                OutlinedButton(
                    onClick =
                        onUnknown,
                    enabled = canRecord,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(
                                "today_unknown_${item.occurrenceId}",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.record_unknown,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTodayItemCard(
    item: TodayItem,
    onOpen: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = onOpen,
                )
                .testTag(
                    "today_compact_item_${item.occurrenceId}",
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
                    "${item.localTime.toDisplayText()} — ${item.medicationName}",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
                        .copy(
                            textDirection =
                                TextDirection.ContentOrLtr,
                        ),
            )

            Text(
                text =
                    item.statusText(),
            )

            item
                .recordingDetailsText()
                .takeIf(String::isNotBlank)
                ?.let { details ->
                    Text(
                        text =
                            details,
                    )
                }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: HistoryItem,
    onOpen: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = onOpen,
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
                    "${item.localTime.toDisplayText()} — ${item.medicationName}",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
                        .copy(
                            textDirection =
                                TextDirection.ContentOrLtr,
                        ),
            )

            Text(
                text =
                    item.statusText(),
            )

            item
                .recordingDetailsText()
                .takeIf(String::isNotBlank)
                ?.let { details ->
                    Text(
                        text =
                            details,
                    )
                }
        }
    }
}

@Composable
private fun TodayEmptyCard(
    emptyState: TodayEmptyState?,
    onOpenCarePlan: () -> Unit,
    seniorMode: SeniorMode,
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
            Text(
                text =
                    if (seniorMode == SeniorMode.SIMPLE) {
                        stringResource(
                            R.string.today_simple_empty_title,
                        )
                    } else {
                        when (emptyState) {
                            TodayEmptyState.NO_MEDICATIONS -> {
                                stringResource(
                                    R.string.today_simple_empty_body,
                                )
                            }

                            TodayEmptyState.NO_OCCURRENCES,
                            null,
                                -> {
                                stringResource(
                                    R.string.today_empty_title,
                                )
                            }
                        }
                    },
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.carePackHeading(),
            )

            Text(
                text =
                    when (emptyState) {
                        TodayEmptyState.NO_MEDICATIONS -> {
                            stringResource(
                                R.string.today_simple_empty_body,
                            )
                        }

                        TodayEmptyState.NO_OCCURRENCES,
                        null,
                            -> {
                            stringResource(
                                R.string.today_empty_body,
                            )
                        }
                    },
            )

            Button(
                onClick =
                    onOpenCarePlan,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "today_empty_open_care_plan",
                        ),
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
private fun LoadingCard(
    testTag: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(testTag),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally,
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
    testTag: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(testTag),
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "${testTag}_retry",
                        ),
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

private fun simplePriority(
    item: TodayItem,
): Int {
    if (item.reportState == null) {
        return when (item.temporalStatus) {
            TemporalStatus.DUE -> 0
            TemporalStatus.PAST -> 1
            TemporalStatus.UPCOMING -> 2
        }
    }

    return 3
}

private fun TodayItem.recordingDetailsText():
        String =
    medicationRecordingDetailsText(
        medicationType =
            medicationType,
        dosageText =
            dosageText,
        doseUnit =
            doseUnit,
    )

private fun HistoryItem.recordingDetailsText():
        String =
    medicationRecordingDetailsText(
        medicationType =
            medicationType,
        dosageText =
            dosageText,
        doseUnit =
            doseUnit,
    )

private fun medicationRecordingDetailsText(
    medicationType: String,
    dosageText: String,
    doseUnit: String,
): String =
    buildList {
        medicationType
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                add(
                    "نوع: $value",
                )
            }

        dosageText
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                add(
                    "مقدار ثبت‌شده: $value",
                )
            }

        doseUnit
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                add(
                    "واحد: $value",
                )
            }
    }.joinToString(
        separator = "، ",
    )

@Composable
private fun TodayItem.statusText():
        String {
    return when {
        lifecycle ==
                OccurrenceLifecycle.CANCELLED -> {
            stringResource(
                R.string.today_item_cancelled,
            )
        }

        reportState ==
                CaregiverReportState.GIVEN -> {
            stringResource(
                R.string.today_item_recorded_given,
            )
        }

        reportState ==
                CaregiverReportState.NOT_GIVEN -> {
            stringResource(
                R.string.today_item_recorded_not_given,
            )
        }

        reportState ==
                CaregiverReportState.UNKNOWN -> {
            stringResource(
                R.string.today_item_recorded_unknown,
            )
        }

        temporalStatus ==
                TemporalStatus.UPCOMING -> {
            stringResource(
                R.string.today_item_upcoming,
            )
        }

        temporalStatus ==
                TemporalStatus.DUE -> {
            stringResource(
                R.string.today_item_due,
            )
        }

        else -> {
            stringResource(
                R.string.today_item_recording_passed,
            )
        }
    }
}

@Composable
private fun HistoryItem.statusText():
        String {
    return when {
        lifecycle ==
                OccurrenceLifecycle.CANCELLED -> {
            stringResource(
                R.string.today_item_cancelled,
            )
        }

        reportState ==
                CaregiverReportState.GIVEN -> {
            stringResource(
                R.string.today_item_recorded_given,
            )
        }

        reportState ==
                CaregiverReportState.NOT_GIVEN -> {
            stringResource(
                R.string.today_item_recorded_not_given,
            )
        }

        reportState ==
                CaregiverReportState.UNKNOWN -> {
            stringResource(
                R.string.today_item_recorded_unknown,
            )
        }

        temporalStatus ==
                TemporalStatus.UPCOMING -> {
            stringResource(
                R.string.today_item_upcoming,
            )
        }

        temporalStatus ==
                TemporalStatus.DUE -> {
            stringResource(
                R.string.today_item_due,
            )
        }

        else -> {
            stringResource(
                R.string.today_item_recording_passed,
            )
        }
    }
}

private fun LocalDate.toJalaliDisplayText():
        String {
    return JalaliPresentationDate
        .from(this)
        .formatNumeric()
}

private fun LocalTime.toDisplayText():
        String {
    return format(
        HOUR_MINUTE_FORMATTER,
    )
}

private val HOUR_MINUTE_FORMATTER:
        DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "HH:mm",
    )
