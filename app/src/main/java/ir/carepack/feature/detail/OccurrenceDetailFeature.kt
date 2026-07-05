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
        onRemindLater =
            viewModel::remindLater,
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
    onRemindLater: () -> Unit = {},
    onUndo: () -> Unit,
    onSnackbarConsumed: () -> Unit = {},
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
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
                        LoadingContent()
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
                            onNotGiven =
                                onNotGiven,
                            onUnknown =
                                onUnknown,
                            onRemindLater =
                                onRemindLater,
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
                                        "occurrence_detail_snackbar_dismiss",
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

@Composable
private fun LoadingContent() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(
                    "occurrence_detail_loading",
                ),
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

@Composable
private fun OccurrenceDetailContent(
    detail: OccurrenceDetail,
    onGiven: () -> Unit,
    onNotGiven: () -> Unit,
    onUnknown: () -> Unit,
    onRemindLater: () -> Unit = {},
) {
    val canRecord =
        detail.lifecycle ==
                OccurrenceLifecycle.ACTIVE

    val statusText =
        detail.statusText()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "${detail.medicationName}، ساعت ${detail.localTime.toDisplayText()}، $statusText"
                }
                .testTag(
                    "occurrence_detail_card",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    20.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            Text(
                text =
                    detail.medicationName,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "occurrence_detail_medication_name",
                        ),
            )

            DetailLabelValue(
                label =
                    stringResource(
                        R.string.scheduled_time,
                    ),
                value =
                    detail
                        .localTime
                        .toDisplayText(),
                forceLeftToRight = true,
                testTag =
                    "occurrence_detail_time",
            )

            DetailLabelValue(
                label =
                    "تاریخ",
                value =
                    detail
                        .localDate
                        .toJalaliDisplayText(),
                forceLeftToRight = true,
                testTag =
                    "occurrence_detail_date",
            )

            DetailLabelValue(
                label =
                    stringResource(
                        R.string.schedule_zone,
                    ),
                value =
                    detail.zoneId,
                forceLeftToRight = true,
                testTag =
                    "occurrence_detail_zone",
            )

            DetailLabelValue(
                label =
                    stringResource(
                        R.string.instruction,
                    ),
                value =
                    detail.medicationInstruction,
                testTag =
                    "occurrence_detail_instruction",
            )

            StatusCard(
                detail =
                    detail,
            )

            if (BuildConfig.DEBUG) {
                Text(
                    text =
                        stringResource(
                            R.string.debug_occurrence_id,
                            detail.occurrenceId,
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

            if (!canRecord) {
                Text(
                    text =
                        "برای نوبت لغوشده نمی‌توان گزارش تازه ثبت کرد.",
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                    modifier =
                        Modifier
                            .carePackPoliteLiveRegion()
                            .testTag(
                                "occurrence_cancelled_report_disabled",
                            ),
                )
            } else {
                Button(
                    onClick = onGiven,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 64.dp,
                            )
                            .testTag(
                                "report_given",
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
                        detail.reportState == null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 64.dp,
                            )
                            .testTag(
                                "remind_later",
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
                            .titleMedium,
                    modifier =
                        Modifier.carePackHeading(),
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp,
                        ),
                ) {
                    ReportActionButton(
                        text =
                            stringResource(
                                R.string.record_not_given,
                            ),
                        selected =
                            detail.reportState ==
                                    CaregiverReportState.NOT_GIVEN,
                        enabled = true,
                        accessibilityLabel =
                            "ثبت مصرف نشد برای ${detail.medicationName} در ساعت ${detail.localTime.toDisplayText()}",
                        testTag =
                            "report_not_given",
                        modifier =
                            Modifier.weight(1f),
                        onClick =
                            onNotGiven,
                    )

                    ReportActionButton(
                        text =
                            stringResource(
                                R.string.record_unknown,
                            ),
                        selected =
                            detail.reportState ==
                                    CaregiverReportState.UNKNOWN,
                        enabled = true,
                        accessibilityLabel =
                            "ثبت نامشخص برای ${detail.medicationName} در ساعت ${detail.localTime.toDisplayText()}",
                        testTag =
                            "report_unknown",
                        modifier =
                            Modifier.weight(1f),
                        onClick =
                            onUnknown,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    detail: OccurrenceDetail,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(
                    "occurrence_detail_status_card",
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
                    "وضعیت نوبت",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.carePackHeading(),
            )

            Text(
                text =
                    detail.statusText(),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                modifier =
                    Modifier.testTag(
                        "occurrence_detail_status",
                    ),
            )
        }
    }
}

@Composable
private fun ReportActionButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    accessibilityLabel: String,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .defaultMinSize(
                    minHeight = 56.dp,
                )
                .semantics {
                    this.selected =
                        selected

                    contentDescription =
                        accessibilityLabel
                }
                .testTag(
                    testTag,
                ),
    ) {
        Text(
            text = text,
        )
    }
}

@Composable
private fun DetailLabelValue(
    label: String,
    value: String,
    testTag: String,
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
            modifier =
                Modifier.testTag(
                    testTag,
                ),
        )
    }
}

@Composable
private fun OccurrenceDetail.statusText():
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
