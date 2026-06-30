package ir.carepack.feature.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.feature.reporting.reportStateText
import ir.carepack.feature.reporting.temporalStatusText
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UndoUiState(
    val token: Long,
    val change: ReportChange,
)

data class OccurrenceDetailUiState(
    val isLoading: Boolean = true,
    val occurrence: OccurrenceDetail? = null,
    val isSaving: Boolean = false,
    val pendingReportState: CaregiverReportState? = null,
    val undo: UndoUiState? = null,
    val errorMessage: String? = null,
)

class OccurrenceDetailViewModel(
    private val occurrenceId: String,
    todayQueryService: TodayQueryService,
    private val caregiverReportService: CaregiverReportService,
    clock: Clock = Clock.systemUTC(),
    now: Flow<Instant> = tickingNow(clock),
) : ViewModel() {

    private val sharedNow =
        now.shareIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    private val mutableState =
        MutableStateFlow(
            OccurrenceDetailUiState(),
        )

    val state =
        mutableState.asStateFlow()

    private var undoTokenCounter =
        0L

    private var undoExpiryJob:
            Job? = null

    init {
        todayQueryService
            .observeOccurrence(
                occurrenceId = occurrenceId,
                now = sharedNow,
            )
            .onEach { occurrence ->
                mutableState.update { current ->
                    val pendingStateWasPersisted =
                        current.pendingReportState != null &&
                                current.pendingReportState ==
                                occurrence?.reportState

                    current.copy(
                        isLoading = false,
                        occurrence = occurrence,
                        pendingReportState =
                            if (pendingStateWasPersisted) {
                                null
                            } else {
                                current.pendingReportState
                            },
                        errorMessage = null,
                    )
                }
            }
            .catch {
                mutableState.update { current ->
                    current.copy(
                        isLoading = false,
                        pendingReportState = null,
                        errorMessage =
                            "خواندن جزئیات انجام نشد.",
                    )
                }
            }
            .launchIn(
                viewModelScope,
            )
    }

    fun setReport(
        newState: CaregiverReportState,
    ) {
        val currentState =
            mutableState.value

        val occurrence =
            currentState.occurrence
                ?: return

        if (
            currentState.isSaving ||
            occurrence.lifecycle !=
            OccurrenceLifecycle.ACTIVE
        ) {
            return
        }

        val displayedState =
            currentState.pendingReportState
                ?: occurrence.reportState

        if (displayedState == newState) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isSaving = true,
                    pendingReportState = newState,
                    errorMessage = null,
                )
            }

            try {
                when (
                    val outcome =
                        caregiverReportService.setReport(
                            occurrenceId = occurrenceId,
                            newState = newState,
                        )
                ) {
                    is SetReportOutcome.Changed -> {
                        showUndo(
                            outcome.change,
                        )
                    }

                    is SetReportOutcome.Unchanged -> {
                        clearPendingReportState()
                    }

                    SetReportOutcome.OccurrenceNotFound -> {
                        showError(
                            "نوبت پیدا نشد.",
                        )
                    }

                    SetReportOutcome.CancelledOccurrenceRejected -> {
                        showError(
                            "برای نوبت لغوشده نمی‌توان گزارش تازه‌ای ثبت کرد.",
                        )
                    }
                }
            } catch (_: Exception) {
                showError(
                    "ثبت گزارش انجام نشد. دوباره تلاش کنید.",
                )
            } finally {
                mutableState.update {
                    it.copy(
                        isSaving = false,
                    )
                }
            }
        }
    }

    fun undo(
        token: Long,
    ) {
        val undoState =
            mutableState.value.undo
                ?: return

        if (undoState.token != token) {
            return
        }

        undoExpiryJob?.cancel()

        mutableState.update {
            it.copy(
                undo = null,
                pendingReportState = null,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                when (
                    caregiverReportService
                        .restorePrevious(
                            undoState.change,
                        )
                ) {
                    is UndoReportOutcome.Restored -> {
                        clearPendingReportState()
                    }

                    UndoReportOutcome.NoLongerCurrent -> {
                        showError(
                            "این تغییر دیگر قابل بازگردانی نیست.",
                        )
                    }

                    UndoReportOutcome.OccurrenceNotFound -> {
                        showError(
                            "نوبت پیدا نشد.",
                        )
                    }
                }
            } catch (_: Exception) {
                showError(
                    "بازگردانی گزارش انجام نشد.",
                )
            }
        }
    }

    private fun showUndo(
        change: ReportChange,
    ) {
        undoExpiryJob?.cancel()

        undoTokenCounter += 1L

        val undoState =
            UndoUiState(
                token = undoTokenCounter,
                change = change,
            )

        mutableState.update {
            it.copy(
                undo = undoState,
                errorMessage = null,
            )
        }

        undoExpiryJob =
            viewModelScope.launch {
                delay(
                    UNDO_DURATION_MILLIS,
                )

                mutableState.update { current ->
                    if (current.undo?.token == undoState.token) {
                        current.copy(
                            undo = null,
                        )
                    } else {
                        current
                    }
                }
            }
    }

    private fun clearPendingReportState() {
        mutableState.update {
            it.copy(
                pendingReportState = null,
            )
        }
    }

    private fun showError(
        message: String,
    ) {
        mutableState.update {
            it.copy(
                pendingReportState = null,
                errorMessage = message,
            )
        }
    }

    companion object {

        private const val UNDO_DURATION_MILLIS =
            8_000L

        fun factory(
            occurrenceId: String,
            todayQueryService: TodayQueryService,
            caregiverReportService: CaregiverReportService,
            clock: Clock = Clock.systemUTC(),
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    OccurrenceDetailViewModel(
                        occurrenceId = occurrenceId,
                        todayQueryService = todayQueryService,
                        caregiverReportService = caregiverReportService,
                        clock = clock,
                    )
                }
            }
    }
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
        onSetReport = viewModel::setReport,
        onUndo = viewModel::undo,
        onBack = onBack,
    )
}

@Composable
fun OccurrenceDetailScreen(
    state: OccurrenceDetailUiState,
    onSetReport: (CaregiverReportState) -> Unit,
    onUndo: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "occurrence_detail_screen",
                ),
    ) {
        Scaffold(
            modifier =
                Modifier.fillMaxSize(),
        ) { contentPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            contentPadding,
                        )
                        .imePadding()
                        .navigationBarsPadding()
                        .verticalScroll(
                            rememberScrollState(),
                        )
                        .padding(
                            horizontal = 24.dp,
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
                            "detail_back",
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
                                "detail_title",
                            ),
                )

                OccurrenceDetailContent(
                    state = state,
                    onSetReport = onSetReport,
                )
            }
        }

        state.undo?.let { undoState ->
            Snackbar(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        )
                        .navigationBarsPadding()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 16.dp,
                        )
                        .carePackPoliteLiveRegion()
                        .testTag(
                            "report_undo_snackbar",
                        ),
                action = {
                    TextButton(
                        onClick = {
                            onUndo(
                                undoState.token,
                            )
                        },
                        modifier =
                            Modifier.testTag(
                                "undo_report",
                            ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .undo_report_change,
                                ),
                        )
                    }
                },
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.report_changed,
                        ),
                )
            }
        }
    }
}

@Composable
private fun OccurrenceDetailContent(
    state: OccurrenceDetailUiState,
    onSetReport: (CaregiverReportState) -> Unit,
) {
    when {
        state.isLoading -> {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .carePackPoliteLiveRegion()
                        .testTag(
                            "detail_loading",
                        ),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        state.errorMessage != null &&
                state.occurrence == null -> {
            Text(
                text = state.errorMessage,
                color =
                    MaterialTheme
                        .colorScheme
                        .error,
                modifier =
                    Modifier
                        .carePackPoliteLiveRegion()
                        .testTag(
                            "detail_error",
                        ),
            )
        }

        state.occurrence == null -> {
            Text(
                text =
                    stringResource(
                        R.string.occurrence_not_found,
                    ),
                modifier =
                    Modifier
                        .carePackPoliteLiveRegion()
                        .testTag(
                            "detail_not_found",
                        ),
            )
        }

        else -> {
            val occurrence =
                state.occurrence

            val displayedReportState =
                state.pendingReportState
                    ?: occurrence.reportState

            Text(
                text =
                    occurrence.medicationName,
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "detail_medication_name",
                        ),
            )

            DetailLabelValue(
                label =
                    stringResource(
                        R.string.scheduled_time,
                    ),
                value =
                    occurrence
                        .localTime
                        .format(
                            HOUR_MINUTE_FORMATTER,
                        ),
                forceLeftToRight = true,
            )

            DetailLabelValue(
                label =
                    stringResource(
                        R.string.scheduled_date,
                    ),
                value =
                    occurrence
                        .localDate
                        .toString(),
                forceLeftToRight = true,
            )

            DetailLabelValue(
                label =
                    stringResource(
                        R.string.schedule_zone,
                    ),
                value =
                    occurrence.zoneId,
                forceLeftToRight = true,
            )

            DetailLabelValue(
                label =
                    stringResource(
                        R.string.temporal_status_label,
                    ),
                value =
                    temporalStatusText(
                        occurrence.temporalStatus,
                    ),
            )

            Column(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalArrangement =
                    Arrangement.spacedBy(
                        4.dp,
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.instruction,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .labelLarge,
                )

                Text(
                    text =
                        occurrence
                            .medicationInstruction,
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge,
                    modifier =
                        Modifier.testTag(
                            "detail_instruction",
                        ),
                )
            }

            ReportStatusCard(
                occurrence = occurrence,
                displayedReportState = displayedReportState,
            )

            if (BuildConfig.DEBUG) {
                Text(
                    text =
                        stringResource(
                            R.string.debug_occurrence_id,
                            occurrence.occurrenceId,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                            .copy(
                                textDirection =
                                    TextDirection.Ltr,
                            ),
                    modifier =
                        Modifier.testTag(
                            "debug_occurrence_id",
                        ),
                )
            }

            state.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                    modifier =
                        Modifier
                            .carePackPoliteLiveRegion()
                            .testTag(
                                "report_error",
                            ),
                )
            }

            if (
                occurrence.lifecycle ==
                OccurrenceLifecycle.CANCELLED
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.cancelled_report_disabled,
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                    modifier =
                        Modifier
                            .carePackPoliteLiveRegion()
                            .testTag(
                                "cancelled_report_disabled",
                            ),
                )
            } else {
                ReportActions(
                    occurrence = occurrence,
                    currentState = displayedReportState,
                    isSaving = state.isSaving,
                    onSetReport = onSetReport,
                )
            }

            Spacer(
                modifier =
                    Modifier.padding(
                        bottom = 8.dp,
                    ),
            )
        }
    }
}

@Composable
private fun DetailLabelValue(
    label: String,
    value: String,
    forceLeftToRight: Boolean = false,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
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
                if (forceLeftToRight) {
                    MaterialTheme
                        .typography
                        .bodyLarge
                        .copy(
                            textDirection =
                                TextDirection.Ltr,
                        )
                } else {
                    MaterialTheme
                        .typography
                        .bodyLarge
                },
        )
    }
}

@Composable
private fun ReportStatusCard(
    occurrence: OccurrenceDetail,
    displayedReportState: CaregiverReportState?,
) {
    val statusText =
        if (occurrence.isOverdue) {
            stringResource(
                R.string.recording_time_passed,
            )
        } else {
            reportStateText(
                displayedReportState,
            )
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .semantics {
                    contentDescription =
                        "وضعیت گزارش مراقب: $statusText"
                },
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    4.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.current_report,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .labelLarge,
            )

            Text(
                text = statusText,
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                modifier =
                    Modifier.testTag(
                        "current_report_state",
                    ),
            )
        }
    }
}

@Composable
private fun ReportActions(
    occurrence: OccurrenceDetail,
    currentState: CaregiverReportState?,
    isSaving: Boolean,
    onSetReport: (CaregiverReportState) -> Unit,
) {
    val timeText =
        occurrence
            .localTime
            .format(
                HOUR_MINUTE_FORMATTER,
            )

    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text =
                stringResource(
                    R.string.current_report,
                ),
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            modifier =
                Modifier.carePackHeading(),
        )

        ReportActionButton(
            text =
                stringResource(
                    R.string.report_action_given,
                ),
            accessibilityLabel =
                "ثبت داده شدن ${occurrence.medicationName} برای ساعت $timeText",
            selected =
                currentState ==
                        CaregiverReportState.GIVEN,
            enabled = !isSaving,
            testTag = "record_given",
            onClick = {
                onSetReport(
                    CaregiverReportState.GIVEN,
                )
            },
        )

        ReportActionButton(
            text =
                stringResource(
                    R.string.report_action_not_given,
                ),
            accessibilityLabel =
                "ثبت داده نشدن ${occurrence.medicationName} برای ساعت $timeText",
            selected =
                currentState ==
                        CaregiverReportState.NOT_GIVEN,
            enabled = !isSaving,
            testTag = "record_not_given",
            onClick = {
                onSetReport(
                    CaregiverReportState.NOT_GIVEN,
                )
            },
        )

        ReportActionButton(
            text =
                stringResource(
                    R.string.report_action_unknown,
                ),
            accessibilityLabel =
                "ثبت مشخص نبودن نتیجه ${occurrence.medicationName} برای ساعت $timeText",
            selected =
                currentState ==
                        CaregiverReportState.UNKNOWN,
            enabled = !isSaving,
            testTag = "record_unknown",
            onClick = {
                onSetReport(
                    CaregiverReportState.UNKNOWN,
                )
            },
        )

        if (isSaving) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 8.dp,
                        )
                        .carePackPoliteLiveRegion()
                        .testTag(
                            "report_saving",
                        ),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                CircularProgressIndicator()

                Text(
                    text = "در حال ذخیره گزارش…",
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ReportActionButton(
    text: String,
    accessibilityLabel: String,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colorScheme =
        MaterialTheme.colorScheme

    val containerColor =
        if (selected) {
            colorScheme.primary
        } else {
            Color.Transparent
        }

    val contentColor =
        if (selected) {
            colorScheme.onPrimary
        } else {
            colorScheme.primary
        }

    val borderColor =
        if (selected) {
            colorScheme.primary
        } else {
            colorScheme.outline
        }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults
                .outlinedButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    disabledContainerColor = containerColor,
                    disabledContentColor = contentColor,
                ),
        border =
            BorderStroke(
                width = 1.dp,
                color = borderColor,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        accessibilityLabel

                    this.selected =
                        selected

                    stateDescription =
                        if (selected) {
                            "وضعیت فعلی"
                        } else {
                            "انتخاب‌نشده"
                        }
                }
                .testTag(
                    testTag,
                ),
    ) {
        Text(
            text =
                if (selected) {
                    "$text — وضعیت فعلی"
                } else {
                    text
                },
        )
    }
}

private val HOUR_MINUTE_FORMATTER =
    DateTimeFormatter.ofPattern(
        "HH:mm",
    )
