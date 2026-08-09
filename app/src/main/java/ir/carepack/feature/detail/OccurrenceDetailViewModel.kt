package ir.carepack.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.BuildConfig
import ir.carepack.R
import ir.carepack.core.time.tickingNow
import ir.carepack.domain.calendar.JalaliPresentationDate
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalStatus
import ir.carepack.domain.reminder.RemindLaterOutcome
import ir.carepack.domain.reminder.ReminderCoordinator
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


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

    private val sharedNow =
        now.shareIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    private val transientState =
        MutableStateFlow(
            DetailTransientState(),
        )

    private var undoJob: Job? =
        null

    val state =
        combine(
            todayQueryService
                .observeOccurrence(
                    occurrenceId = occurrenceId,
                    now = sharedNow,
                )
                .map<OccurrenceDetail?, DetailLoad> { detail ->
                    if (detail == null) {
                        DetailLoad.NotFound
                    } else {
                        DetailLoad.Loaded(
                            detail = detail,
                        )
                    }
                }
                .onStart {
                    emit(
                        DetailLoad.Loading,
                    )
                }
                .catch { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }

                    emit(
                        DetailLoad.Failed(
                            message =
                                "خواندن نوبت انجام نشد.",
                        ),
                    )
                },
            transientState,
        ) { load, transient ->
            when (load) {
                DetailLoad.Loading -> {
                    OccurrenceDetailUiState(
                        isLoading = true,
                        snackbarMessage =
                            transient.snackbarMessage,
                        undoChange =
                            transient.undoChange,
                    )
                }

                is DetailLoad.Loaded -> {
                    OccurrenceDetailUiState(
                        isLoading = false,
                        detail =
                            load.detail,
                        snackbarMessage =
                            transient.snackbarMessage,
                        undoChange =
                            transient.undoChange,
                    )
                }

                DetailLoad.NotFound -> {
                    OccurrenceDetailUiState(
                        isLoading = false,
                        errorMessage =
                            "نوبت پیدا نشد.",
                        snackbarMessage =
                            transient.snackbarMessage,
                        undoChange =
                            transient.undoChange,
                    )
                }

                is DetailLoad.Failed -> {
                    OccurrenceDetailUiState(
                        isLoading = false,
                        errorMessage =
                            load.message,
                        snackbarMessage =
                            transient.snackbarMessage,
                        undoChange =
                            transient.undoChange,
                    )
                }
            }
        }
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted.WhileSubscribed(
                        stopTimeoutMillis = 5_000,
                    ),
                initialValue =
                    OccurrenceDetailUiState(),
            )

    fun setReport(
        state: CaregiverReportState,
    ) {
        viewModelScope.launch {
            try {
                when (
                    val outcome =
                        caregiverReportService
                            .setReport(
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

    fun remindLater() {
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
                            DetailTransientState(
                                snackbarMessage =
                                    "تغییر گزارش برگردانده شد.",
                            )
                    }

                    UndoReportOutcome.NoLongerCurrent,
                    UndoReportOutcome.OccurrenceNotFound,
                        -> {
                        undoJob?.cancel()

                        transientState.value =
                            DetailTransientState(
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

    private fun showReportChanged(
        change: ReportChange,
    ) {
        undoJob?.cancel()

        transientState.value =
            DetailTransientState(
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
            occurrenceId: String,
            todayQueryService: TodayQueryService,
            caregiverReportService: CaregiverReportService,
            reminderCoordinator: ReminderCoordinator,
            clock: Clock,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    OccurrenceDetailViewModel(
                        occurrenceId =
                            occurrenceId,
                        todayQueryService =
                            todayQueryService,
                        caregiverReportService =
                            caregiverReportService,
                        reminderCoordinator =
                            reminderCoordinator,
                        clock = clock,
                    )
                }
            }

        private const val UNDO_WINDOW_MILLIS =
            8_000L
    }
}

private data class DetailTransientState(
    val snackbarMessage: String? = null,
    val undoChange: ReportChange? = null,
)

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
