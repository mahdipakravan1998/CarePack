package ir.carepack.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.HistoryItem
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.TimezoneWarning
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.feature.reporting.reportStateText
import ir.carepack.feature.reporting.temporalPhaseText
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
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
    val selectedSection:
    TodaySection = TodaySection.TODAY,
    val isLoading: Boolean = true,
    val items: List<TodayItem> =
        emptyList(),
    val emptyState: TodayEmptyState? =
        null,
    val errorMessage: String? = null,
    val isHistoryLoading: Boolean =
        true,
    val historyDays: List<HistoryDay> =
        emptyList(),
    val historyErrorMessage: String? =
        null,
    val reminderStatus: ReminderStatus? =
        null,
    val timezoneWarning: TimezoneWarning? =
        null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val todayQueryService:
    TodayQueryService,
    clock: Clock,
    private val zoneProvider:
    ZoneProvider,
    private val reminderCoordinator:
    ReminderCoordinator? = null,
    private val reminderPreferenceStore:
    ReminderPreferenceStore? = null,
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

    private val reminderPreferences =
        reminderPreferenceStore
            ?.state
            ?: flowOf(
                ReminderPreferenceState(),
            )

    private val dateRequests =
        combine(
            sharedNow
                .map { instant ->
                    instant
                        .atZone(
                            zoneProvider
                                .currentZone(),
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
        dateRequests
            .flatMapLatest { request ->
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

    val state =
        combine(
            selectedSection,
            content,
            reminderPreferences,
            mutableReminderStatus,
        ) {
                section,
                dateContent,
                preferenceState,
                reminderStatus ->
            TodayUiState(
                localDate =
                    dateContent.localDate,
                selectedSection = section,
                isLoading =
                    dateContent.today is
                            TodayLoad.Loading,
                items =
                    (
                            dateContent.today as?
                                    TodayLoad.Loaded
                            )
                        ?.model
                        ?.items
                        .orEmpty(),
                emptyState =
                    (
                            dateContent.today as?
                                    TodayLoad.Loaded
                            )
                        ?.model
                        ?.emptyState,
                errorMessage =
                    (
                            dateContent.today as?
                                    TodayLoad.Failed
                            )
                        ?.message,
                isHistoryLoading =
                    dateContent.history is
                            HistoryLoad.Loading,
                historyDays =
                    (
                            dateContent.history as?
                                    HistoryLoad.Loaded
                            )
                        ?.days
                        .orEmpty(),
                historyErrorMessage =
                    (
                            dateContent.history as?
                                    HistoryLoad.Failed
                            )
                        ?.message,
                reminderStatus =
                    reminderStatus
                        ?.copy(
                            remindersEnabled =
                                preferenceState
                                    .remindersEnabled,
                        ),
                timezoneWarning =
                    preferenceState
                        .timezoneWarning,
            )
        }.stateIn(
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
        refreshReminderStatus()
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

        refreshReminderStatus()
    }

    fun refreshReminderStatus() {
        val coordinator =
            reminderCoordinator
                ?: return

        viewModelScope.launch {
            try {
                mutableReminderStatus.value =
                    coordinator
                        .currentStatus()
            } catch (
                cancellation:
                CancellationException,
            ) {
                throw cancellation
            } catch (_: Exception) {
                Unit
            }
        }
    }

    fun dismissTimezoneWarning() {
        val preferenceStore =
            reminderPreferenceStore
                ?: return

        viewModelScope.launch {
            try {
                preferenceStore
                    .dismissTimezoneWarning()
            } catch (
                cancellation:
                CancellationException,
            ) {
                throw cancellation
            } catch (_: Exception) {
                Unit
            }
        }
    }

    private fun observeToday(
        localDate: LocalDate,
    ): Flow<TodayLoad> {
        return todayQueryService
            .observeToday(
                localDate = localDate,
                now = sharedNow,
            )
            .map<TodayModel, TodayLoad> {
                    model ->
                TodayLoad.Loaded(
                    model = model,
                )
            }
            .onStart {
                emit(
                    TodayLoad.Loading,
                )
            }
            .catch {
                emit(
                    TodayLoad.Failed(
                        message =
                            "خواندن اطلاعات امروز انجام نشد.",
                    ),
                )
            }
    }

    private fun observeHistory(
        localDate: LocalDate,
    ): Flow<HistoryLoad> {
        return todayQueryService
            .observeRecentHistory(
                anchorDate = localDate,
                now = sharedNow,
            )
            .map<List<HistoryDay>, HistoryLoad> {
                    days ->
                HistoryLoad.Loaded(
                    days = days,
                )
            }
            .onStart {
                emit(
                    HistoryLoad.Loading,
                )
            }
            .catch {
                emit(
                    HistoryLoad.Failed(
                        message =
                            "خواندن سابقه انجام نشد.",
                    ),
                )
            }
    }

    companion object {
        fun factory(
            todayQueryService:
            TodayQueryService,
            reminderCoordinator:
            ReminderCoordinator,
            reminderPreferenceStore:
            ReminderPreferenceStore,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    TodayViewModel(
                        todayQueryService =
                            todayQueryService,
                        reminderCoordinator =
                            reminderCoordinator,
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
    onOccurrenceSelected: (String) -> Unit,
    onManageCarePlan: () -> Unit,
    onReminderSettings: () -> Unit,
    onReviewSchedules: () -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    val lifecycleOwner =
        LocalLifecycleOwner.current

    DisposableEffect(
        lifecycleOwner,
    ) {
        val observer =
            LifecycleEventObserver {
                    _, event ->
                if (
                    event ==
                    Lifecycle.Event.ON_RESUME
                ) {
                    viewModel
                        .refreshReminderStatus()
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
        onOccurrenceSelected =
            onOccurrenceSelected,
        onManageCarePlan =
            onManageCarePlan,
        onReminderSettings =
            onReminderSettings,
        onReviewSchedules =
            onReviewSchedules,
        onDismissTimezoneWarning =
            viewModel::dismissTimezoneWarning,
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
    onReminderSettings: () -> Unit = {},
    onReviewSchedules: () -> Unit = {},
    onDismissTimezoneWarning: () -> Unit = {},
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
                    state.localDate.toString(),
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
                        onManageCarePlan,
                    modifier =
                        Modifier
                            .weight(1f)
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

                OutlinedButton(
                    onClick =
                        onReminderSettings,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(
                                "open_reminder_settings",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .open_reminder_settings,
                            ),
                    )
                }
            }

            state.timezoneWarning?.let {
                    warning ->
                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp,
                        ),
                )

                TimezoneWarningCard(
                    warning = warning,
                    onReviewSchedules =
                        onReviewSchedules,
                    onDismiss =
                        onDismissTimezoneWarning,
                )
            }

            ReminderAvailabilityBanner(
                status =
                    state.reminderStatus,
                onOpenSettings =
                    onReminderSettings,
            )

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
                    onClick = onShowToday,
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
                    onClick = onShowHistory,
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
private fun TimezoneWarningCard(
    warning: TimezoneWarning,
    onReviewSchedules: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "timezone_warning",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .timezone_warning_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .timezone_warning_body,
                        warning
                            .previousZoneId,
                        warning
                            .currentZoneId,
                    ),
                modifier =
                    Modifier.testTag(
                        "timezone_warning_body",
                    ),
            )

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
                Button(
                    onClick =
                        onReviewSchedules,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(
                                "timezone_warning_review",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .review_schedules,
                            ),
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(
                                "timezone_warning_dismiss",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .dismiss_for_later,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderAvailabilityBanner(
    status: ReminderStatus?,
    onOpenSettings: () -> Unit,
) {
    if (
        status == null ||
        !status.remindersEnabled
    ) {
        return
    }

    val banner =
        when (status.availability) {
            ReminderAvailability
                .NOTIFICATION_PERMISSION_REQUIRED -> {
                ReminderBannerContent(
                    titleResource =
                        R.string
                            .today_notification_unavailable_title,
                    bodyResource =
                        R.string
                            .today_notification_unavailable_body,
                    testTag =
                        "today_notification_unavailable",
                )
            }

            ReminderAvailability.APPROXIMATE -> {
                ReminderBannerContent(
                    titleResource =
                        R.string
                            .today_approximate_reminder_title,
                    bodyResource =
                        R.string
                            .today_approximate_reminder_body,
                    testTag =
                        "today_approximate_reminder",
                )
            }

            ReminderAvailability.DISABLED,
            ReminderAvailability.NO_ACTIVE_SCHEDULE,
            ReminderAvailability.EXACT -> {
                null
            }
        } ?: return

    Spacer(
        modifier =
            Modifier.height(12.dp),
    )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    banner.testTag,
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        banner.titleResource,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    stringResource(
                        banner.bodyResource,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            OutlinedButton(
                onClick =
                    onOpenSettings,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "today_reminder_status_settings",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .open_reminder_settings,
                        ),
                )
            }
        }
    }
}

private data class ReminderBannerContent(
    val titleResource: Int,
    val bodyResource: Int,
    val testTag: String,
)

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
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier =
                modifier.testTag(
                    testTag,
                ),
        ) {
            Text(text)
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
                    key =
                        TodayItem::occurrenceId,
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
                TodayEmptyState
                    .NO_MEDICATIONS

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
                    items = state.historyDays,
                    key =
                        HistoryDay::localDate,
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
                historyDay
                    .localDate
                    .toString(),
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            modifier =
                Modifier
                    .testTag(
                        "history_date_${historyDay.localDate}",
                    )
                    .semantics {
                        heading()
                    },
        )

        Spacer(
            modifier =
                Modifier.height(8.dp),
        )

        historyDay.items.forEach {
                item ->
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
    OccurrenceCardSurface(
        testTag =
            "today_item_${item.occurrenceId}",
        contentPadding = 20.dp,
        onClick = onClick,
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
                    "today_phase_${item.occurrenceId}",
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
                    "today_report_${item.occurrenceId}",
                ),
        )
    }
}

@Composable
private fun HistoryOccurrenceCard(
    item: HistoryItem,
    onClick: () -> Unit,
) {
    OccurrenceCardSurface(
        testTag =
            "history_item_${item.occurrenceId}",
        contentPadding = 16.dp,
        onClick = onClick,
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
                            OccurrenceLifecycle
                                .CANCELLED -> {
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

@Composable
private fun OccurrenceCardSurface(
    testTag: String,
    contentPadding: Dp,
    onClick: () -> Unit,
    content:
    @Composable ColumnScope.() -> Unit,
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
                .testTag(testTag),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    contentPadding,
                ),
            content = content,
        )
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
