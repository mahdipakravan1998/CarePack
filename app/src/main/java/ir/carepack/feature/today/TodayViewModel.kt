package ir.carepack.feature.today

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.carepack.core.time.ZoneProvider
import ir.carepack.core.time.tickingNow
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.model.TodayModel
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.feature.occurrence.OccurrenceActionController
import ir.carepack.feature.occurrence.OccurrenceActionUiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
    val seniorMode: SeniorMode = SeniorMode.STANDARD,
    val snackbarMessage: String? = null,
    val undoChange: ReportChange? = null,
)

private data class TodayUserState(
    val reminderPreferenceState: ReminderPreferenceState,
    val userExperienceState: UserExperiencePreferenceState,
    val reminderStatus: ReminderStatus?,
    val transient: OccurrenceActionUiState,
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

    private val initialLocalDate = clock
            .instant().atZone(
                zoneProvider.currentZone(),
            ).toLocalDate()

    private val sharedNow = now.shareIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    private val selectedSection = MutableStateFlow(
            TodaySection.TODAY,
        )

    private val retryVersion = MutableStateFlow(0L)

    private val mutableReminderStatus = MutableStateFlow<ReminderStatus?>(
            null,
        )

    private val occurrenceActions = OccurrenceActionController(
            caregiverReportService = caregiverReportService,
            reminderCoordinator = reminderCoordinator,
            scope = viewModelScope,
            onReminderAttemptCompleted = ::refreshReminderStatus,
        )

    private val reminderPreferences = reminderPreferenceStore?.state
            ?: flowOf(
                ReminderPreferenceState(),
            )

    private val userExperiencePreferences = userExperiencePreferenceStore?.state
            ?: flowOf(
                UserExperiencePreferenceState(),
            )

    private val dateRequests = combine(
            sharedNow.map { instant ->
                    instant.atZone(
                            zoneProvider.currentZone(),
                        ).toLocalDate()
                }.distinctUntilChanged(),
            retryVersion,
        ) { localDate, retry ->
            DateRequest(
                localDate = localDate,
                retryVersion = retry,
            )
        }

    private val content = dateRequests.flatMapLatest { request ->
            combine(
                observeToday(
                    localDate = request.localDate,
                ),
                observeHistory(
                    localDate = request.localDate,
                ),
            ) { today, history ->
                DateContent(
                    localDate = request.localDate,
                    today = today,
                    history = history,
                )
            }
        }

    private val userState = combine(
            reminderPreferences,
            userExperiencePreferences,
            mutableReminderStatus,
            occurrenceActions.state,
        ) {
                reminderPreferenceState,
                userExperienceState,
                reminderStatus,
                transient,
            ->
            TodayUserState(
                reminderPreferenceState = reminderPreferenceState,
                userExperienceState = userExperienceState,
                reminderStatus = reminderStatus,
                transient = transient,
            )
        }

    val state = combine(
            selectedSection,
            content,
            userState,
        ) {
                section,
                dateContent,
                userState,
            ->
            TodayUiState(
                localDate = dateContent.localDate,
                selectedSection = section,
                isLoading = dateContent.today is TodayLoad.Loading,
                items = (dateContent.today as? TodayLoad.Loaded)
                        ?.model?.items
                        .orEmpty(),
                emptyState = (dateContent.today as? TodayLoad.Loaded)
                        ?.model?.emptyState,
                errorMessage = (dateContent.today as? TodayLoad.Failed)
                        ?.message,
                isHistoryLoading = dateContent.history is HistoryLoad.Loading,
                historyDays = (dateContent.history as? HistoryLoad.Loaded)
                        ?.days.orEmpty(),
                historyErrorMessage = (dateContent.history as? HistoryLoad.Failed)
                        ?.message,
                reminderStatus = userState
                        .reminderStatus?.copy(
                            remindersEnabled = userState
                                    .reminderPreferenceState.remindersEnabled,
                        ),
                seniorMode = userState
                        .userExperienceState.seniorMode,
                snackbarMessage = userState
                        .transient.snackbarMessage,
                undoChange = userState
                        .transient.undoChange,
            )
        }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = TodayUiState(
                        localDate = initialLocalDate,
                    ),
            )

    init {
        refresh()
    }

    fun showToday() {
        selectedSection.value = TodaySection.TODAY
    }

    fun showHistory() {
        selectedSection.value = TodaySection.HISTORY
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
        occurrenceActions.setReport(occurrenceId, state)
    }

    fun remindLater(occurrenceId: String) {
        occurrenceActions.remindLater(occurrenceId)
    }

    fun undoReportChange() {
        occurrenceActions.undoReportChange()
    }

    fun consumeSnackbar() {
        occurrenceActions.consumeSnackbar()
    }

    private fun refreshReminderStatus() {
        viewModelScope.launch {
            try {
                mutableReminderStatus.value = reminderCoordinator
                        .currentStatus()
            } catch (
                cancellation: CancellationException,
            ) {
                throw cancellation
            } catch (_: Exception) {
                mutableReminderStatus.value = null
            }
        }
    }

    private fun observeToday(
        localDate: LocalDate,
    ): Flow<TodayLoad> = todayQueryService
            .observeToday(
                localDate = localDate,
                now = sharedNow,
            ).map<TodayModel, TodayLoad> {
                TodayLoad.Loaded(it)
            }.onStart {
                emit(TodayLoad.Loading)
            }.catch { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }

                emit(
                    TodayLoad.Failed(
                        message = "خواندن امروز انجام نشد.",
                    ),
                )
            }

    private fun observeHistory(
        localDate: LocalDate,
    ): Flow<HistoryLoad> = todayQueryService
            .observeRecentHistory(
                anchorDate = localDate,
                now = sharedNow,
            ).map<List<HistoryDay>, HistoryLoad> {
                HistoryLoad.Loaded(it)
            }.onStart {
                emit(HistoryLoad.Loading)
            }.catch { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }

                emit(
                    HistoryLoad.Failed(
                        message = "خواندن سابقه انجام نشد.",
                    ),
                )
            }

    override fun onCleared() {
        occurrenceActions.close()
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
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    @Suppress("UNUSED_VARIABLE")
                    val retainedCarePlanService = carePlanService

                    TodayViewModel(
                        todayQueryService = todayQueryService,
                        caregiverReportService = caregiverReportService,
                        reminderCoordinator = reminderCoordinator,
                        reminderPreferenceStore = reminderPreferenceStore,
                        userExperiencePreferenceStore = userExperiencePreferenceStore,
                        clock = clock,
                        zoneProvider = zoneProvider,
                    )
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
