package ir.carepack.feature.reporting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.calendar.PersianDateText
import ir.carepack.domain.calendar.toPersianDigits
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.report.DateRangeSummary
import ir.carepack.domain.report.RangeReportFormatter
import ir.carepack.domain.report.RangeReportPeriod
import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


enum class RangeReportActionMessage {
    COPIED,
    SHARE_CHOOSER_OPENED,
}


enum class RangeReportFailure {
    LOAD_FAILED,
    COPY_FAILED,
    NO_SHARE_TARGET,
    SHARE_FAILED,
}


data class RangeReportUiState(
    val today: LocalDate,
    val period: RangeReportPeriod =
        RangeReportPeriod.SEVEN_DAYS,
    val includeRecipientName: Boolean = false,
    val seniorMode: SeniorMode = SeniorMode.STANDARD,
    val summary: DateRangeSummary? = null,
    val reportText: String = "",
    val isLoading: Boolean = true,
    val isSharing: Boolean = false,
    val actionMessage:
    RangeReportActionMessage? = null,
    val failure: RangeReportFailure? = null,
)

class RangeReportViewModel(
    private val formatter: RangeReportFormatter,
    private val privacyPreferenceStore:
    PrivacyPreferenceStore,
    private val userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    private val textShareGateway:
    TextShareGateway,
    clock: Clock,
    zoneProvider: ZoneProvider,
) : ViewModel() {

    private val reportDate =
        LocalDate.now(
            clock.withZone(
                zoneProvider.currentZone(),
            ),
        )

    private val mutableState =
        MutableStateFlow(
            RangeReportUiState(
                today = reportDate,
            ),
        )

    val state =
        mutableState.asStateFlow()

    private var reportLoadJob:
            Job? = null

    init {
        observeIncludeRecipientName()
        observeSeniorMode()
    }

    fun selectPeriod(
        period: RangeReportPeriod,
    ) {
        if (
            period ==
            mutableState
                .value
                .period
        ) {
            return
        }

        mutableState.update { current ->
            current.copy(
                period = period,
                actionMessage = null,
                failure = null,
            )
        }

        loadReport()
    }

    fun setIncludeRecipientName(
        includeRecipientName: Boolean,
    ) {
        viewModelScope.launch {
            privacyPreferenceStore
                .setIncludeRecipientName(
                    includeRecipientName,
                )
        }
    }

    fun copyReport() {
        val reportText =
            mutableState
                .value
                .reportText

        if (reportText.isBlank()) {
            return
        }

        val result =
            textShareGateway.copy(
                reportText,
            )

        mutableState.update { current ->
            when (result) {
                CopyTextResult.Copied ->
                    current.copy(
                        actionMessage =
                            RangeReportActionMessage
                                .COPIED,
                        failure = null,
                    )

                CopyTextResult.Blocked,
                CopyTextResult.InvalidText,
                    ->
                    current.copy(
                        actionMessage = null,
                        failure =
                            RangeReportFailure
                                .COPY_FAILED,
                    )
            }
        }
    }

    fun shareReport() {
        val currentState =
            mutableState.value

        if (
            currentState.reportText.isBlank() ||
            currentState.isSharing
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update { current ->
                current.copy(
                    isSharing = true,
                    actionMessage = null,
                    failure = null,
                )
            }

            try {
                val result =
                    textShareGateway.share(
                        currentState.reportText,
                    )

                mutableState.update { current ->
                    when (result) {
                        ShareTextResult.ChooserOpened ->
                            current.copy(
                                actionMessage =
                                    RangeReportActionMessage
                                        .SHARE_CHOOSER_OPENED,
                                failure = null,
                            )

                        ShareTextResult.NoShareTarget ->
                            current.copy(
                                actionMessage = null,
                                failure =
                                    RangeReportFailure
                                        .NO_SHARE_TARGET,
                            )

                        ShareTextResult.Blocked,
                        ShareTextResult.InvalidText,
                            ->
                            current.copy(
                                actionMessage = null,
                                failure =
                                    RangeReportFailure
                                        .SHARE_FAILED,
                            )
                    }
                }
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.update { current ->
                    current.copy(
                        failure =
                            RangeReportFailure
                                .SHARE_FAILED,
                    )
                }
            } finally {
                mutableState.update { current ->
                    current.copy(
                        isSharing = false,
                    )
                }
            }
        }
    }

    fun consumeActionMessage() {
        mutableState.update { current ->
            current.copy(
                actionMessage = null,
            )
        }
    }

    fun refresh() {
        loadReport()
    }

    private fun observeIncludeRecipientName() {
        viewModelScope.launch {
            privacyPreferenceStore
                .state
                .map {
                    it.includeRecipientName
                }
                .distinctUntilChanged()
                .collectLatest {
                        includeRecipientName ->
                    mutableState.update { current ->
                        current.copy(
                            includeRecipientName =
                                includeRecipientName,
                        )
                    }

                    loadReport()
                }
        }
    }

    private fun observeSeniorMode() {
        viewModelScope.launch {
            userExperiencePreferenceStore
                .state
                .map {
                    it.seniorMode
                }
                .distinctUntilChanged()
                .collectLatest { seniorMode ->
                    mutableState.update { current ->
                        current.copy(
                            seniorMode = seniorMode,
                        )
                    }
                }
        }
    }

    private fun loadReport() {
        reportLoadJob?.cancel()

        val request =
            mutableState.value

        reportLoadJob =
            viewModelScope.launch {
                mutableState.update { current ->
                    current.copy(
                        isLoading = true,
                        failure = null,
                    )
                }

                try {
                    val content =
                        formatter.createRangeReport(
                            period = request.period,
                            today = request.today,
                            includeRecipientName =
                                request
                                    .includeRecipientName,
                        )

                    mutableState.update { current ->
                        if (
                            current.period !=
                            request.period ||
                            current.includeRecipientName !=
                            request.includeRecipientName
                        ) {
                            current
                        } else {
                            current.copy(
                                summary =
                                    content.summary,
                                reportText =
                                    content
                                        .text
                                        .value,
                                isLoading = false,
                                failure = null,
                            )
                        }
                    }
                } catch (
                    cancellationException:
                    CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            summary = null,
                            reportText = "",
                            failure =
                                RangeReportFailure
                                    .LOAD_FAILED,
                        )
                    }
                }
            }
    }

    companion object {
        fun factory(
            formatter: RangeReportFormatter,
            privacyPreferenceStore:
            PrivacyPreferenceStore,
            userExperiencePreferenceStore:
            UserExperiencePreferenceStore,
            textShareGateway:
            TextShareGateway,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RangeReportViewModel(
                        formatter = formatter,
                        privacyPreferenceStore =
                            privacyPreferenceStore,
                        userExperiencePreferenceStore =
                            userExperiencePreferenceStore,
                        textShareGateway =
                            textShareGateway,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }
    }
}

@Composable
fun RangeReportRoute(
    formatter: RangeReportFormatter,
    privacyPreferenceStore:
    PrivacyPreferenceStore,
    userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    textShareGateway:
    TextShareGateway,
    clock: Clock,
    zoneProvider: ZoneProvider,
    onBack: () -> Unit,
) {
    val viewModel:
            RangeReportViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory =
                RangeReportViewModel.factory(
                    formatter = formatter,
                    privacyPreferenceStore =
                        privacyPreferenceStore,
                    userExperiencePreferenceStore =
                        userExperiencePreferenceStore,
                    textShareGateway =
                        textShareGateway,
                    clock = clock,
                    zoneProvider =
                        zoneProvider,
                ),
        )

    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    val snackbarHostState =
        remember {
            SnackbarHostState()
        }

    CompositionLocalProvider(
        LocalCarePackExperience provides
                CarePackExperience.forMode(
                    state.seniorMode,
                ),
    ) {
        RangeReportScreen(
            state = state,
            snackbarHostState =
                snackbarHostState,
            onBack = onBack,
            onPeriodSelected =
                viewModel::selectPeriod,
            onIncludeRecipientNameChanged =
                viewModel::setIncludeRecipientName,
            onCopyReport =
                viewModel::copyReport,
            onShareReport =
                viewModel::shareReport,
            onRetry =
                viewModel::refresh,
        )
    }

    RangeReportActionMessages(
        actionMessage =
            state.actionMessage,
        snackbarHostState =
            snackbarHostState,
        onConsumed =
            viewModel::consumeActionMessage,
    )
}

@Composable
fun RangeReportScreen(
    state: RangeReportUiState,
    snackbarHostState:
    SnackbarHostState,
    onBack: () -> Unit,
    onPeriodSelected:
        (RangeReportPeriod) -> Unit,
    onIncludeRecipientNameChanged:
        (Boolean) -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
    onRetry: () -> Unit,
) {
    val experience =
        LocalCarePackExperience.current

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState =
                    snackbarHostState,
            )
        },
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    "range_report_screen",
                ),
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
                        horizontal =
                            experience
                                .screenHorizontalPadding,
                        vertical =
                            experience
                                .screenVerticalPadding,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    experience.sectionSpacing,
                ),
        ) {
            TextButton(
                onClick = onBack,
                enabled =
                    !state.isSharing,
                modifier =
                    Modifier
                        .carePackInteractiveControl()
                        .testTag(
                            "range_report_back",
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
                        R.string
                            .range_report_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "range_report_title",
                        ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .range_report_description,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            RangePeriodSelector(
                selectedPeriod =
                    state.period,
                enabled =
                    !state.isLoading &&
                            !state.isSharing,
                onPeriodSelected =
                    onPeriodSelected,
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .range_report_date_range,
                        PersianDateText.formatNumeric(
                            state
                                .period
                                .rangeEndingAt(
                                    state.today,
                                )
                                .startDate,
                        ),
                        PersianDateText.formatNumeric(
                            state.today,
                        ),
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.testTag(
                        "range_report_date_range",
                    ),
            )

            IncludeRecipientNameToggle(
                checked =
                    state.includeRecipientName,
                enabled =
                    !state.isLoading &&
                            !state.isSharing,
                onCheckedChange =
                    onIncludeRecipientNameChanged,
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_share_destination_notice,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                modifier =
                    Modifier.testTag(
                        "range_report_share_notice",
                    ),
            )

            when {
                state.isLoading ->
                    RangeReportLoading()

                state.failure ==
                        RangeReportFailure.LOAD_FAILED ->
                    RangeReportError(
                        failure =
                            RangeReportFailure.LOAD_FAILED,
                        onRetry = onRetry,
                    )

                state.summary != null -> {
                    state.failure
                        ?.takeIf { failure ->
                            failure !=
                                    RangeReportFailure
                                        .LOAD_FAILED
                        }
                        ?.let { failure ->
                            RangeReportActionError(
                                failure = failure,
                            )
                        }

                    RangeSummaryCard(
                        summary =
                            state.summary,
                    )

                    if (
                        state.summary
                            .totalOccurrenceCount == 0
                    ) {
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        "range_report_empty",
                                    ),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string
                                            .range_report_empty,
                                    ),
                                modifier =
                                    Modifier.padding(
                                        16.dp,
                                    ),
                            )
                        }
                    }

                    RangeReportPreview(
                        reportText =
                            state.reportText,
                    )

                    RangeReportActions(
                        reportText =
                            state.reportText,
                        isSharing =
                            state.isSharing,
                        onCopyReport =
                            onCopyReport,
                        onShareReport =
                            onShareReport,
                    )
                }
            }
        }
    }
}

@Composable
private fun RangePeriodSelector(
    selectedPeriod: RangeReportPeriod,
    enabled: Boolean,
    onPeriodSelected:
        (RangeReportPeriod) -> Unit,
) {
    val experience =
        LocalCarePackExperience.current

    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                experience.compactSpacing,
            ),
    ) {
        Text(
            text =
                stringResource(
                    R.string
                        .range_report_period_label,
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
                    experience.compactSpacing,
                ),
        ) {
            FilterChip(
                selected =
                    selectedPeriod ==
                            RangeReportPeriod
                                .SEVEN_DAYS,
                onClick = {
                    onPeriodSelected(
                        RangeReportPeriod
                            .SEVEN_DAYS,
                    )
                },
                enabled = enabled,
                label = {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .range_report_7_days,
                            ),
                    )
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .carePackInteractiveControl()
                        .testTag(
                            "range_report_period_7",
                        ),
            )

            FilterChip(
                selected =
                    selectedPeriod ==
                            RangeReportPeriod
                                .THIRTY_DAYS,
                onClick = {
                    onPeriodSelected(
                        RangeReportPeriod
                            .THIRTY_DAYS,
                    )
                },
                enabled = enabled,
                label = {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .range_report_30_days,
                            ),
                    )
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .carePackInteractiveControl()
                        .testTag(
                            "range_report_period_30",
                        ),
            )
        }
    }
}

@Composable
private fun IncludeRecipientNameToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange:
        (Boolean) -> Unit,
) {
    val experience =
        LocalCarePackExperience.current

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange =
                        onCheckedChange,
                )
                .padding(
                    vertical =
                        experience.compactSpacing,
                )
                .testTag(
                    "range_report_include_recipient_name_row",
                ),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    4.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_include_recipient_name,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_include_recipient_name_description,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier =
                Modifier.testTag(
                    "range_report_include_recipient_name_switch",
                ),
        )
    }
}

@Composable
private fun RangeReportLoading() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(
                    "range_report_loading",
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
                    R.string
                        .range_report_loading,
                ),
        )
    }
}

@Composable
private fun RangeReportError(
    failure: RangeReportFailure,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(
                    "range_report_error",
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text =
                rangeReportFailureText(
                    failure,
                ),
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
                    .carePackPrimaryAction()
                    .testTag(
                        "range_report_retry",
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

@Composable
private fun RangeReportActionError(
    failure: RangeReportFailure,
) {
    Text(
        text =
            rangeReportFailureText(
                failure,
            ),
        color =
            MaterialTheme
                .colorScheme
                .error,
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(
                    "range_report_action_error",
                ),
    )
}

@Composable
private fun rangeReportFailureText(
    failure: RangeReportFailure,
): String =
    when (failure) {
        RangeReportFailure.LOAD_FAILED ->
            stringResource(
                R.string
                    .range_report_load_failed,
            )

        RangeReportFailure.COPY_FAILED ->
            stringResource(
                R.string
                    .range_report_copy_failed,
            )

        RangeReportFailure.NO_SHARE_TARGET ->
            stringResource(
                R.string
                    .range_report_no_share_target,
            )

        RangeReportFailure.SHARE_FAILED ->
            stringResource(
                R.string
                    .range_report_share_failed,
            )
    }

@Composable
private fun RangeSummaryCard(
    summary: DateRangeSummary,
) {
    val experience =
        LocalCarePackExperience.current

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "range_report_summary",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    experience.compactSpacing,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .range_report_summary_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                modifier =
                    Modifier.carePackHeading(),
            )

            SummaryLine(
                label =
                    stringResource(
                        R.string
                            .range_report_total,
                    ),
                count =
                    summary
                        .totalOccurrenceCount,
                testTag =
                    "range_report_total",
            )

            SummaryLine(
                label =
                    stringResource(
                        R.string
                            .range_report_given,
                    ),
                count = summary.givenCount,
                testTag =
                    "range_report_given",
            )

            SummaryLine(
                label =
                    stringResource(
                        R.string
                            .range_report_not_given,
                    ),
                count =
                    summary.notGivenCount,
                testTag =
                    "range_report_not_given",
            )

            SummaryLine(
                label =
                    stringResource(
                        R.string
                            .range_report_unknown,
                    ),
                count = summary.unknownCount,
                testTag =
                    "range_report_unknown",
            )

            SummaryLine(
                label =
                    stringResource(
                        R.string
                            .range_report_no_report,
                    ),
                count = summary.noReportCount,
                testTag =
                    "range_report_no_report",
            )
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    count: Int,
    testTag: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(testTag),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme
                    .typography
                    .bodyLarge,
        )

        Text(
            text =
                count
                    .toString()
                    .toPersianDigits(),
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
        )
    }
}

@Composable
private fun RangeReportPreview(
    reportText: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "range_report_preview_card",
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
                    stringResource(
                        R.string
                            .range_report_preview_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.carePackHeading(),
            )

            SelectionContainer {
                Text(
                    text = reportText,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                            .copy(
                                textDirection =
                                    TextDirection
                                        .ContentOrRtl,
                            ),
                    modifier =
                        Modifier.testTag(
                            "range_report_preview_text",
                        ),
                )
            }
        }
    }
}

@Composable
private fun RangeReportActions(
    reportText: String,
    isSharing: Boolean,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
) {
    val experience =
        LocalCarePackExperience.current

    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                experience.itemSpacing,
            ),
    ) {
        OutlinedButton(
            onClick = onCopyReport,
            enabled =
                reportText.isNotBlank() &&
                        !isSharing,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .carePackPrimaryAction()
                    .testTag(
                        "range_report_copy",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string
                            .range_report_copy,
                    ),
            )
        }

        Button(
            onClick = onShareReport,
            enabled =
                reportText.isNotBlank() &&
                        !isSharing,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .carePackPrimaryAction()
                    .testTag(
                        "range_report_share",
                    ),
        ) {
            if (isSharing) {
                CircularProgressIndicator()
            } else {
                Text(
                    text =
                        stringResource(
                            R.string
                                .range_report_share,
                        ),
                )
            }
        }
    }
}

@Composable
private fun RangeReportActionMessages(
    actionMessage:
    RangeReportActionMessage?,
    snackbarHostState:
    SnackbarHostState,
    onConsumed: () -> Unit,
) {
    val copiedMessage =
        stringResource(
            R.string
                .range_report_copied,
        )

    val shareOpenedMessage =
        stringResource(
            R.string
                .range_report_share_opened,
        )

    LaunchedEffect(
        actionMessage,
    ) {
        if (actionMessage == null) {
            return@LaunchedEffect
        }

        snackbarHostState.showSnackbar(
            when (actionMessage) {
                RangeReportActionMessage.COPIED ->
                    copiedMessage

                RangeReportActionMessage
                    .SHARE_CHOOSER_OPENED ->
                    shareOpenedMessage
            },
        )

        onConsumed()
    }
}
