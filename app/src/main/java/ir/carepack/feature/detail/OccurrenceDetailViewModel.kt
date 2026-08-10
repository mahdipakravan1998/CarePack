package ir.carepack.feature.detail

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.carepack.core.time.tickingNow
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.feature.occurrence.OccurrenceActionController
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn


data class OccurrenceDetailUiState(
    val isLoading: Boolean = true,
    val detail: OccurrenceDetail? = null,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val undoChange: ReportChange? = null,
)

enum class OccurrenceDetailEntryMode {
    NORMAL,
    REMINDER,
}

class OccurrenceDetailViewModel(
    private val occurrenceId: String,
    private val todayQueryService: TodayQueryService,
    private val caregiverReportService: CaregiverReportService,
    private val reminderCoordinator: ReminderCoordinator,
    clock: Clock,
    now: Flow<Instant> = tickingNow(clock),
) : ViewModel() {

    private val sharedNow = now.shareIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    private val occurrenceActions = OccurrenceActionController(
            caregiverReportService = caregiverReportService,
            reminderCoordinator = reminderCoordinator,
            scope = viewModelScope,
        )

    val state = combine(
            todayQueryService.observeOccurrence(
                    occurrenceId = occurrenceId,
                    now = sharedNow,
                ).map<OccurrenceDetail?, DetailLoad> { detail ->
                    if (detail == null) {
                        DetailLoad.NotFound
                    } else {
                        DetailLoad.Loaded(
                            detail = detail,
                        )
                    }
                }.onStart {
                    emit(
                        DetailLoad.Loading,
                    )
                }.catch { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }

                    emit(
                        DetailLoad.Failed(
                            message = "خواندن نوبت انجام نشد.",
                        ),
                    )
                },
            occurrenceActions.state,
        ) { load, transient ->
            when (load) {
                DetailLoad.Loading -> {
                    OccurrenceDetailUiState(
                        isLoading = true,
                        snackbarMessage = transient.snackbarMessage,
                        undoChange = transient.undoChange,
                    )
                }

                is DetailLoad.Loaded -> {
                    OccurrenceDetailUiState(
                        isLoading = false,
                        detail = load.detail,
                        snackbarMessage = transient.snackbarMessage,
                        undoChange = transient.undoChange,
                    )
                }

                DetailLoad.NotFound -> {
                    OccurrenceDetailUiState(
                        isLoading = false,
                        errorMessage = "نوبت پیدا نشد.",
                        snackbarMessage = transient.snackbarMessage,
                        undoChange = transient.undoChange,
                    )
                }

                is DetailLoad.Failed -> {
                    OccurrenceDetailUiState(
                        isLoading = false,
                        errorMessage = load.message,
                        snackbarMessage = transient.snackbarMessage,
                        undoChange = transient.undoChange,
                    )
                }
            }
        }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(
                        stopTimeoutMillis = 5_000,
                    ),
                initialValue = OccurrenceDetailUiState(),
            )

    fun setReport(state: CaregiverReportState) {
        occurrenceActions.setReport(occurrenceId, state)
    }

    fun remindLater() {
        occurrenceActions.remindLater(occurrenceId)
    }

    fun undoReportChange() {
        occurrenceActions.undoReportChange()
    }

    fun consumeSnackbar() {
        occurrenceActions.consumeSnackbar()
    }

    override fun onCleared() {
        occurrenceActions.close()
        super.onCleared()
    }

    companion object {
        fun factory(
            occurrenceId: String,
            todayQueryService: TodayQueryService,
            caregiverReportService: CaregiverReportService,
            reminderCoordinator: ReminderCoordinator,
            clock: Clock,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    OccurrenceDetailViewModel(
                        occurrenceId = occurrenceId,
                        todayQueryService = todayQueryService,
                        caregiverReportService = caregiverReportService,
                        reminderCoordinator = reminderCoordinator,
                        clock = clock,
                    )
            }

    }
}

private sealed interface DetailLoad {
    data object Loading : DetailLoad

    data class Loaded(
        val detail: OccurrenceDetail,
    ) : DetailLoad

    data object NotFound : DetailLoad

    data class Failed(
        val message: String,
    ) : DetailLoad
}
