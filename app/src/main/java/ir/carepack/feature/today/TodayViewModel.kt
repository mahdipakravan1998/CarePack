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
