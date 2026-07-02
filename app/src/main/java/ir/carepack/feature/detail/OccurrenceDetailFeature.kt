package ir.carepack.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.tickingNow
import ir.carepack.domain.calendar.JalaliPresentationDate
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

class OccurrenceDetailViewModel(
    private val occurrenceId: String,
    private val todayQueryService: TodayQueryService,
    private val caregiverReportService:
    CaregiverReportService,
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

    private var undoJob: Job? = null

    val state =
        combine(
            todayQueryService
                .observeOccurrence(
                    occurrenceId = occurrenceId,
                    now = sharedNow,
                )
                .map<OccurrenceDetail?, DetailLoad> {
                    if (it == null) {
                        DetailLoad.NotFound
                    } else {
                        DetailLoad.Loaded(it)
                    }
                }
                .catch {
                    emit(
                        DetailLoad.Failed(
                            "خواندن نوبت انجام نشد.",
                        ),
                    )
                },
            transientState,
        ) { load, transient ->
            when (load) {
                DetailLoad.Loading ->
                    OccurrenceDetailUiState(
                        isLoading = true,
                        snackbarMessage =
                            transient.snackbarMessage,
                        undoChange =
                            transient.undoChange,
                    )

                is DetailLoad.Loaded ->
                    OccurrenceDetailUiState(
                        isLoading = false,
                        detail = load.detail,
                        snackbarMessage =
                            transient.snackbarMessage,
                        undoChange =
                            transient.undoChange,
                    )

                DetailLoad.NotFound ->
                    OccurrenceDetailUiState(
                        isLoading = false,
                        errorMessage =
                            "نوبت پیدا نشد.",
                        snackbarMessage =
                            transient.snackbarMessage,
                        undoChange =
                            transient.undoChange,
                    )

                is DetailLoad.Failed ->
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
            when (
                val outcome =
                    caregiverReportService.setReport(
                        occurrenceId = occurrenceId,
                        newState = state,
                    )
            ) {
                is SetReportOutcome.Changed -> {
                    showReportChanged(
                        outcome.change,
                    )
                }

                is SetReportOutcome.Unchanged -> {
                    transientState.update {
                        it.copy(
                            snackbarMessage =
                                "این وضعیت قبلاً ثبت شده است.",
                        )
                    }
                }

                SetReportOutcome.OccurrenceNotFound -> {
                    transientState.update {
                        it.copy(
                            snackbarMessage =
                                "نوبت پیدا نشد.",
                        )
                    }
                }

                SetReportOutcome.CancelledOccurrenceRejected -> {
                    transientState.update {
                        it.copy(
                            snackbarMessage =
                                "برای نوبت لغوشده گزارش جدید ثبت نمی‌شود.",
                        )
                    }
                }
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
            when (
                caregiverReportService
                    .restorePrevious(change)
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

    override fun onCleared() {
        undoJob?.cancel()
        super.onCleared()
    }

    companion object {

        fun factory(
            occurrenceId: String,
            todayQueryService: TodayQueryService,
            caregiverReportService:
            CaregiverReportService,
            clock: Clock,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    OccurrenceDetailViewModel(
                        occurrenceId = occurrenceId,
                        todayQueryService =
                            todayQueryService,
                        caregiverReportService =
                            caregiverReportService,
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

@Composable
fun OccurrenceDetailRoute(
    viewModel: OccurrenceDetailViewModel,
    onBack: () -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    OccurrenceDetailScreen(
        state = state,
        onBack = onBack,
        onGiven = {
            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )
        },
        onNotGiven = {
            viewModel.setReport(
                CaregiverReportState.NOT_GIVEN,
            )
        },
        onUnknown = {
            viewModel.setReport(
                CaregiverReportState.UNKNOWN,
            )
        },
        onUndo =
            viewModel::undoReportChange,
        onSnackbarConsumed =
            viewModel::consumeSnackbar,
    )
}

@Composable
fun OccurrenceDetailScreen(
    state: OccurrenceDetailUiState,
    onBack: () -> Unit,
    onGiven: () -> Unit,
    onNotGiven: () -> Unit,
    onUnknown: () -> Unit,
    onUndo: () -> Unit,
    onSnackbarConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "occurrence_detail_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal = 20.dp,
                        vertical = 16.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            TextButton(
                onClick = onBack,
                modifier =
                    Modifier.testTag(
                        "occurrence_detail_back",
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.back,
                        ),
                )
            }

            Text(
                text =
                    stringResource(
                        R.string.detail_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "occurrence_detail_title",
                        ),
            )

            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.testTag(
                                "occurrence_detail_loading",
                            ),
                    )
                }

                state.errorMessage != null -> {
                    Text(
                        text =
                            state.errorMessage,
                        color =
                            MaterialTheme
                                .colorScheme
                                .error,
                        modifier =
                            Modifier
                                .carePackPoliteLiveRegion()
                                .testTag(
                                    "occurrence_detail_error",
                                ),
                    )
                }

                state.detail != null -> {
                    OccurrenceDetailContent(
                        detail =
                            state.detail,
                        onGiven = onGiven,
                        onNotGiven = onNotGiven,
                        onUnknown = onUnknown,
                    )
                }
            }

            if (state.snackbarMessage != null) {
                Snackbar(
                    modifier =
                        Modifier.testTag(
                            "occurrence_detail_snackbar",
                        ),
                    action = {
                        if (state.undoChange != null) {
                            TextButton(
                                onClick = onUndo,
                                modifier =
                                    Modifier.testTag(
                                        "occurrence_detail_undo",
                                    ),
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.undo_report_change,
                                        ),
                                )
                            }
                        } else {
                            TextButton(
                                onClick =
                                    onSnackbarConsumed,
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

@Composable
private fun OccurrenceDetailContent(
    detail: OccurrenceDetail,
    onGiven: () -> Unit,
    onNotGiven: () -> Unit,
    onUnknown: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "occurrence_detail_card",
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
                    detail.medicationName,
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
            )

            DetailRow(
                label =
                    stringResource(
                        R.string.scheduled_date,
                    ),
                value =
                    JalaliPresentationDate
                        .from(detail.localDate)
                        .formatNumeric(),
                leftToRight = true,
            )

            DetailRow(
                label =
                    stringResource(
                        R.string.scheduled_time,
                    ),
                value =
                    detail
                        .localTime
                        .toHourMinuteText(),
                leftToRight = true,
            )

            DetailRow(
                label =
                    stringResource(
                        R.string.instruction,
                    ),
                value =
                    detail.medicationInstruction,
            )

            DetailRow(
                label =
                    stringResource(
                        R.string.current_report,
                    ),
                value =
                    reportStateText(
                        detail.reportState,
                    ),
            )

            if (
                detail.lifecycle ==
                OccurrenceLifecycle.CANCELLED
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.cancelled_occurrence,
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                )
            } else {
                Column(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp,
                        ),
                ) {
                    Button(
                        onClick = onGiven,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "report_given",
                                ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.report_action_given,
                                ),
                        )
                    }

                    OutlinedButton(
                        onClick = onNotGiven,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "report_not_given",
                                ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.report_action_not_given,
                                ),
                        )
                    }

                    OutlinedButton(
                        onClick = onUnknown,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "report_unknown",
                                ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.report_action_unknown,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    leftToRight: Boolean = false,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                4.dp,
            ),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme
                    .typography
                    .labelLarge,
        )

        Text(
            text = value,
            style =
                MaterialTheme
                    .typography
                    .bodyLarge
                    .copy(
                        textDirection =
                            if (leftToRight) {
                                TextDirection.Ltr
                            } else {
                                TextDirection.ContentOrRtl
                            },
                    ),
        )
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
