package ir.carepack.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import ir.carepack.feature.reporting.temporalStatusText
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
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
    val selectedSection: TodaySection = TodaySection.TODAY,
    val isLoading: Boolean = true,
    val items: List<TodayItem> = emptyList(),
    val emptyState: TodayEmptyState? = null,
    val errorMessage: String? = null,
    val isHistoryLoading: Boolean = true,
    val historyDays: List<HistoryDay> = emptyList(),
    val historyErrorMessage: String? = null,
    val reminderStatus: ReminderStatus? = null,
    val timezoneWarning: TimezoneWarning? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val todayQueryService: TodayQueryService,
    clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val reminderCoordinator: ReminderCoordinator? = null,
    private val reminderPreferenceStore: ReminderPreferenceStore? = null,
    now: Flow<Instant> = tickingNow(clock),
) : ViewModel() {

    private val initialLocalDate = clock.instant()
        .atZone(zoneProvider.currentZone())
        .toLocalDate()

    private val sharedNow = now.shareIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        replay = 1,
    )

    private val selectedSection = MutableStateFlow(TodaySection.TODAY)
    private val retryVersion = MutableStateFlow(0L)
    private val mutableReminderStatus = MutableStateFlow<ReminderStatus?>(null)

    private val reminderPreferences = reminderPreferenceStore?.state
        ?: flowOf(ReminderPreferenceState())

    private val dateRequests = combine(
        sharedNow
            .map { instant ->
                instant.atZone(zoneProvider.currentZone()).toLocalDate()
            }
            .distinctUntilChanged(),
        retryVersion,
    ) { localDate, retry ->
        DateRequest(
            localDate = localDate,
            retryVersion = retry,
        )
    }

    private val content = dateRequests.flatMapLatest { request ->
        combine(
            observeToday(request.localDate),
            observeHistory(request.localDate),
        ) { today, history ->
            DateContent(
                localDate = request.localDate,
                today = today,
                history = history,
            )
        }
    }

    val state = combine(
        selectedSection,
        content,
        reminderPreferences,
        mutableReminderStatus,
    ) { section, dateContent, preferenceState, reminderStatus ->
        TodayUiState(
            localDate = dateContent.localDate,
            selectedSection = section,
            isLoading = dateContent.today is TodayLoad.Loading,
            items = (dateContent.today as? TodayLoad.Loaded)
                ?.model
                ?.items
                .orEmpty(),
            emptyState = (dateContent.today as? TodayLoad.Loaded)
                ?.model
                ?.emptyState,
            errorMessage = (dateContent.today as? TodayLoad.Failed)
                ?.message,
            isHistoryLoading = dateContent.history is HistoryLoad.Loading,
            historyDays = (dateContent.history as? HistoryLoad.Loaded)
                ?.days
                .orEmpty(),
            historyErrorMessage = (dateContent.history as? HistoryLoad.Failed)
                ?.message,
            reminderStatus = reminderStatus?.copy(
                remindersEnabled = preferenceState.remindersEnabled,
            ),
            timezoneWarning = preferenceState.timezoneWarning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TodayUiState(
            localDate = initialLocalDate,
        ),
    )

    init {
        refreshReminderStatus()
    }

    fun showToday() {
        selectedSection.value = TodaySection.TODAY
    }

    fun showHistory() {
        selectedSection.value = TodaySection.HISTORY
    }

    fun retry() {
        retryVersion.update { current ->
            current + 1L
        }

        refreshReminderStatus()
    }

    fun refreshReminderStatus() {
        val coordinator = reminderCoordinator ?: return

        viewModelScope.launch {
            try {
                mutableReminderStatus.value =
                    coordinator.currentStatus()
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

    fun dismissTimezoneWarning() {
        val preferenceStore = reminderPreferenceStore ?: return

        viewModelScope.launch {
            try {
                preferenceStore.dismissTimezoneWarning()
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
    ): Flow<TodayLoad> =
        todayQueryService
            .observeToday(
                localDate = localDate,
                now = sharedNow,
            )
            .map<TodayModel, TodayLoad> { model ->
                TodayLoad.Loaded(model)
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
                            "دریافت نوبت‌های امروز انجام نشد.",
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
            .map<List<HistoryDay>, HistoryLoad> { days ->
                HistoryLoad.Loaded(days)
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
                            "دریافت سابقه اخیر انجام نشد.",
                    ),
                )
            }

    companion object {

        fun factory(
            todayQueryService: TodayQueryService,
            reminderCoordinator: ReminderCoordinator? = null,
            reminderPreferenceStore: ReminderPreferenceStore? = null,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TodayViewModel(
                        todayQueryService =
                            todayQueryService,
                        reminderCoordinator =
                            reminderCoordinator,
                        reminderPreferenceStore =
                            reminderPreferenceStore,
                        clock = clock,
                        zoneProvider = zoneProvider,
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

    data object Loading :
        TodayLoad

    data class Loaded(
        val model: TodayModel,
    ) : TodayLoad

    data class Failed(
        val message: String,
    ) : TodayLoad
}

private sealed interface HistoryLoad {

    data object Loading :
        HistoryLoad

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
    onOpenTodayReport: () -> Unit,
    onOpenSettings: () -> Unit,
    onReminderSettings: () -> Unit,
    onReviewSchedules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel
        .state
        .collectAsStateWithLifecycle()

    val lifecycleOwner =
        LocalLifecycleOwner.current

    DisposableEffect(
        lifecycleOwner,
        viewModel,
    ) {
        val observer =
            LifecycleEventObserver {
                    _,
                    event,
                ->
                if (
                    event ==
                    Lifecycle.Event.ON_START
                ) {
                    viewModel.refreshReminderStatus()
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
        onOccurrenceSelected =
            onOccurrenceSelected,
        onManageCarePlan =
            onManageCarePlan,
        onOpenTodayReport =
            onOpenTodayReport,
        onOpenSettings =
            onOpenSettings,
        onReminderSettings =
            onReminderSettings,
        onReviewSchedules =
            onReviewSchedules,
        onDismissTimezoneWarning =
            viewModel::dismissTimezoneWarning,
        modifier = modifier,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onTodaySelected: () -> Unit,
    onHistorySelected: () -> Unit,
    onRetry: () -> Unit,
    onOccurrenceSelected: (String) -> Unit,
    onManageCarePlan: () -> Unit,
    onOpenTodayReport: () -> Unit,
    onOpenSettings: () -> Unit,
    onReminderSettings: () -> Unit,
    onReviewSchedules: () -> Unit,
    onDismissTimezoneWarning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("today_screen"),
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .navigationBarsPadding()
                    .testTag("today_content"),
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp,
                    vertical = 20.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(16.dp),
        ) {
            item(
                key = "today-header",
            ) {
                Header(
                    localDate = state.localDate,
                    onOpenSettings =
                        onOpenSettings,
                )
            }

            item(
                key = "today-primary-actions",
            ) {
                PrimaryActions(
                    onOpenTodayReport =
                        onOpenTodayReport,
                    onManageCarePlan =
                        onManageCarePlan,
                    onReminderSettings =
                        onReminderSettings,
                )
            }

            state.timezoneWarning?.let { warning ->
                item(
                    key = "timezone-warning",
                ) {
                    TimezoneWarningCard(
                        warning = warning,
                        onReviewSchedules =
                            onReviewSchedules,
                        onDismiss =
                            onDismissTimezoneWarning,
                    )
                }
            }

            state.reminderStatus?.let { status ->
                val availability =
                    status.availability

                if (
                    status.remindersEnabled &&
                    availability !=
                    ReminderAvailability.EXACT
                ) {
                    item(
                        key = "reminder-availability",
                    ) {
                        ReminderAvailabilityBanner(
                            availability =
                                availability,
                            onReminderSettings =
                                onReminderSettings,
                        )
                    }
                }
            }

            item(
                key = "today-section-switcher",
            ) {
                SectionSwitcher(
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
                    when {
                        state.isLoading -> {
                            item(
                                key = "today-loading",
                            ) {
                                LoadingContent()
                            }
                        }

                        state.errorMessage != null -> {
                            item(
                                key = "today-error",
                            ) {
                                ErrorContent(
                                    message =
                                        state.errorMessage,
                                    onRetry = onRetry,
                                )
                            }
                        }

                        state.items.isEmpty() -> {
                            item(
                                key = "today-empty",
                            ) {
                                TodayEmptyContent(
                                    emptyState =
                                        state.emptyState,
                                    onManageCarePlan =
                                        onManageCarePlan,
                                )
                            }
                        }

                        else -> {
                            items(
                                items = state.items,
                                key = {
                                    "today-${it.occurrenceId}"
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

                TodaySection.HISTORY -> {
                    when {
                        state.isHistoryLoading -> {
                            item(
                                key = "history-loading",
                            ) {
                                LoadingContent()
                            }
                        }

                        state.historyErrorMessage != null -> {
                            item(
                                key = "history-error",
                            ) {
                                ErrorContent(
                                    message =
                                        state
                                            .historyErrorMessage,
                                    onRetry = onRetry,
                                )
                            }
                        }

                        state.historyDays.isEmpty() -> {
                            item(
                                key = "history-empty",
                            ) {
                                HistoryEmptyContent()
                            }
                        }

                        else -> {
                            state.historyDays.forEach { day ->
                                item(
                                    key =
                                        "history-day-" +
                                                day.localDate,
                                ) {
                                    HistoryDayContent(
                                        day = day,
                                        onOccurrenceSelected =
                                            onOccurrenceSelected,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    localDate: LocalDate,
    onOpenSettings: () -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                stringResource(
                    R.string.today_title,
                ),
            style =
                MaterialTheme.typography.headlineMedium,
            modifier =
                Modifier
                    .carePackHeading()
                    .testTag("today_title"),
        )

        Text(
            text =
                DATE_FORMATTER.format(localDate),
            style =
                MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.testTag("today_date"),
        )

        OutlinedButton(
            onClick =
                onOpenSettings,
            modifier =
                Modifier.testTag(
                    "today_settings",
                ),
        ) {
            Text(
                text = "تنظیمات",
            )
        }
    }
}

@Composable
private fun PrimaryActions(
    onOpenTodayReport: () -> Unit,
    onManageCarePlan: () -> Unit,
    onReminderSettings: () -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onOpenTodayReport,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "today_open_report",
                    ),
        ) {
            Text(
                text = "پیش‌نمایش گزارش امروز",
            )
        }

        OutlinedButton(
            onClick = onManageCarePlan,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "today_manage_care_plan",
                    ),
        ) {
            Text(
                text = "مدیریت داروها",
            )
        }

        TextButton(
            onClick = onReminderSettings,
            modifier =
                Modifier.testTag(
                    "today_reminder_settings",
                ),
        ) {
            Text(
                text = "تنظیمات یادآوری",
            )
        }
    }
}

@Composable
private fun SectionSwitcher(
    selectedSection: TodaySection,
    onTodaySelected: () -> Unit,
    onHistorySelected: () -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
    ) {
        SectionButton(
            selected =
                selectedSection ==
                        TodaySection.TODAY,
            label =
                stringResource(
                    R.string.today_section,
                ),
            testTag =
                "today_section_button",
            onClick =
                onTodaySelected,
        )

        SectionButton(
            selected =
                selectedSection ==
                        TodaySection.HISTORY,
            label =
                stringResource(
                    R.string.history_section,
                ),
            testTag =
                "history_section_button",
            onClick =
                onHistorySelected,
        )
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
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    "منطقه زمانی دستگاه تغییر کرده است.",
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "timezone_warning_title",
                        ),
            )

            Text(
                text =
                    "برنامه‌های قبلی همچنان با منطقه زمانی ذخیره‌شده خودشان محاسبه می‌شوند. " +
                            "برای استفاده از منطقه زمانی جدید، برنامه دارو را بررسی و ویرایش کنید.",
                style =
                    MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.testTag(
                        "timezone_warning_body",
                    ),
            )

            Text(
                text =
                    "قبلی: ${warning.previousZoneId}، فعلی: ${warning.currentZoneId}",
                style =
                    MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier.testTag(
                        "timezone_warning_zones",
                    ),
            )

            Button(
                onClick = onReviewSchedules,
                modifier =
                    Modifier.testTag(
                        "timezone_warning_review",
                    ),
            ) {
                Text(
                    text = "بررسی برنامه‌ها",
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier =
                    Modifier.testTag(
                        "timezone_warning_dismiss",
                    ),
            ) {
                Text(
                    text = "بعداً",
                )
            }
        }
    }
}

@Composable
private fun ReminderAvailabilityBanner(
    availability: ReminderAvailability,
    onReminderSettings: () -> Unit,
) {
    val content =
        when (availability) {
            ReminderAvailability.EXACT -> {
                null
            }

            ReminderAvailability.APPROXIMATE -> {
                ReminderBannerContent(
                    title =
                        stringResource(
                            R.string
                                .today_approximate_reminder_title,
                        ),
                    body =
                        stringResource(
                            R.string
                                .today_approximate_reminder_body,
                        ),
                    testTag =
                        "today_approximate_reminder",
                )
            }

            ReminderAvailability.NOTIFICATION_PERMISSION_REQUIRED -> {
                ReminderBannerContent(
                    title =
                        "اعلان‌ها فعال نیستند.",
                    body =
                        "بدون اجازه اعلان، یادآوری‌ها نمایش داده نمی‌شوند؛ اما ثبت اطلاعات همچنان قابل استفاده است.",
                    testTag =
                        "today_notification_missing",
                )
            }

            ReminderAvailability.NO_ACTIVE_SCHEDULE -> {
                ReminderBannerContent(
                    title =
                        "برنامه فعالی برای یادآوری وجود ندارد.",
                    body =
                        "پس از ثبت دارو و برنامه فعال، یادآوری‌ها قابل زمان‌بندی هستند.",
                    testTag =
                        "today_no_active_schedule",
                )
            }

            ReminderAvailability.DISABLED -> {
                ReminderBannerContent(
                    title =
                        "یادآوری‌ها خاموش هستند.",
                    body =
                        "برای دریافت اعلان محلی، یادآوری‌ها را از تنظیمات فعال کنید.",
                    testTag =
                        "today_reminders_disabled",
                )
            }
        } ?: return

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(content.testTag),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = content.title,
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.carePackHeading(),
            )

            Text(
                text = content.body,
                style =
                    MaterialTheme.typography.bodyMedium,
            )

            TextButton(
                onClick = onReminderSettings,
                modifier =
                    Modifier.testTag(
                        "${content.testTag}_settings",
                    ),
            ) {
                Text(
                    text = "تنظیمات یادآوری",
                )
            }
        }
    }
}

private data class ReminderBannerContent(
    val title: String,
    val body: String,
    val testTag: String,
)

@Composable
private fun SectionButton(
    selected: Boolean,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val description =
        if (selected) {
            "$label، انتخاب شده"
        } else {
            "$label، انتخاب نشده"
        }

    OutlinedButton(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        description
                }
                .testTag(testTag),
    ) {
        Text(
            text = label,
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TodayEmptyContent(
    emptyState: TodayEmptyState?,
    onManageCarePlan: () -> Unit,
) {
    val title =
        when (emptyState) {
            TodayEmptyState.NO_MEDICATIONS,
            null,
                -> {
                stringResource(
                    R.string
                        .today_no_medications_title
                )
            }

            TodayEmptyState.NO_OCCURRENCES -> {
                stringResource(
                    R.string
                        .today_no_occurrences_title
                )
            }
        }

    val body =
        when (emptyState) {
            TodayEmptyState.NO_MEDICATIONS,
            null,
                -> {
                stringResource(
                    R.string
                        .today_no_medications_body
                )
            }

            TodayEmptyState.NO_OCCURRENCES -> {
                stringResource(
                    R.string
                        .today_no_occurrences_body
                )
            }
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("today_empty"),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag("today_empty_title"),
            )

            Text(
                text = body,
                style =
                    MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.testTag("today_empty_body"),
            )

            Button(
                onClick = onManageCarePlan,
                modifier =
                    Modifier.testTag(
                        "today_empty_manage_care_plan",
                    ),
            ) {
                Text(
                    text = "مدیریت داروها",
                )
            }
        }
    }
}

@Composable
private fun HistoryEmptyContent() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("history_empty"),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .history_empty_title,
                    ),
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "history_empty_title",
                        ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .history_empty_body,
                    ),
                style =
                    MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.testTag(
                        "history_empty_body",
                    ),
            )
        }
    }
}

@Composable
private fun HistoryDayContent(
    day: HistoryDay,
    onOccurrenceSelected: (String) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "history_day_${day.localDate}",
                ),
    ) {
        Text(
            text =
                DATE_FORMATTER.format(
                    day.localDate,
                ),
            style =
                MaterialTheme.typography.titleMedium,
            modifier =
                Modifier
                    .carePackHeading()
                    .testTag(
                        "history_day_title_${day.localDate}",
                    ),
        )

        day.items.forEach { item ->
            HistoryOccurrenceCard(
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

@Composable
private fun TodayOccurrenceCard(
    item: TodayItem,
    onClick: () -> Unit,
) {
    val timeText =
        HOUR_MINUTE_FORMATTER.format(
            item.localTime,
        )

    val phaseText =
        temporalStatusText(
            item.temporalStatus,
        )

    val reportText =
        reportStateText(
            item.reportState,
        )

    val overdueText =
        if (item.isOverdue) {
            stringResource(
                R.string
                    .recording_time_passed,
            )
        } else {
            null
        }

    OccurrenceCardSurface(
        occurrenceId =
            item.occurrenceId,
        contentDescription =
            buildString {
                append(
                    "${item.medicationName}، $timeText، $phaseText، $reportText",
                )

                if (overdueText != null) {
                    append("، ")
                    append(overdueText)
                }
            },
        onClick = onClick,
        testTagPrefix =
            "today_occurrence",
    ) {
        Text(
            text = timeText,
            style =
                MaterialTheme.typography.labelLarge,
            modifier =
                Modifier.testTag(
                    "today_time_${item.occurrenceId}",
                ),
        )

        Text(
            text =
                item.medicationName,
            style =
                MaterialTheme.typography.titleMedium,
            modifier =
                Modifier.testTag(
                    "today_medication_${item.occurrenceId}",
                ),
        )

        Text(
            text = phaseText,
            style =
                MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.testTag(
                    "today_phase_${item.occurrenceId}",
                ),
        )

        Text(
            text = reportText,
            style =
                MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.testTag(
                    "today_report_${item.occurrenceId}",
                ),
        )

        overdueText?.let {
            Text(
                text = it,
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .carePackPoliteLiveRegion()
                        .testTag(
                            "today_overdue_${item.occurrenceId}",
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
    val timeText =
        HOUR_MINUTE_FORMATTER.format(
            item.localTime,
        )

    val reportText =
        reportStateText(
            item.reportState,
        )

    val statusText =
        when {
            item.lifecycle ==
                    OccurrenceLifecycle.CANCELLED -> {
                stringResource(
                    R.string
                        .cancelled_occurrence,
                )
            }

            item.isOverdue -> {
                stringResource(
                    R.string
                        .recording_time_passed,
                )
            }

            else -> {
                temporalStatusText(
                    item.temporalStatus,
                )
            }
        }

    OccurrenceCardSurface(
        occurrenceId =
            item.occurrenceId,
        contentDescription =
            "${item.medicationName}، $timeText، $statusText، $reportText",
        onClick = onClick,
        testTagPrefix =
            "history_occurrence",
    ) {
        Text(
            text = timeText,
            style =
                MaterialTheme.typography.labelLarge,
            modifier =
                Modifier.testTag(
                    "history_time_${item.occurrenceId}",
                ),
        )

        Text(
            text =
                item.medicationName,
            style =
                MaterialTheme.typography.titleMedium,
            modifier =
                Modifier.testTag(
                    "history_medication_${item.occurrenceId}",
                ),
        )

        Text(
            text = statusText,
            style =
                MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.testTag(
                    "history_status_${item.occurrenceId}",
                ),
        )

        Text(
            text = reportText,
            style =
                MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier.testTag(
                    "history_report_${item.occurrenceId}",
                ),
        )
    }
}

@Composable
private fun OccurrenceCardSurface(
    occurrenceId: String,
    contentDescription: String,
    onClick: () -> Unit,
    testTagPrefix: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    this.contentDescription =
                        contentDescription
                }
                .testTag(
                    "${testTagPrefix}_$occurrenceId",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("today_error"),
    ) {
        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style =
                    MaterialTheme.typography.bodyLarge,
                color =
                    MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .carePackPoliteLiveRegion()
                        .testTag("today_error_text"),
            )

            Button(
                onClick = onRetry,
                modifier =
                    Modifier.testTag(
                        "today_retry",
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
}

private val DATE_FORMATTER =
    DateTimeFormatter.ISO_LOCAL_DATE

private val HOUR_MINUTE_FORMATTER =
    DateTimeFormatter.ofPattern("HH:mm")
