package ir.carepack.feature.reporting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.calendar.PersianDateText
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.report.RangeReportFormatter
import ir.carepack.domain.report.RangeReportPeriod
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import java.time.Clock




@Composable
fun RangeReportRoute(
    formatter: RangeReportFormatter,
    privacyPreferenceStore: PrivacyPreferenceStore,
    userExperiencePreferenceStore: UserExperiencePreferenceStore,
    textShareGateway: TextShareGateway,
    clock: Clock,
    zoneProvider: ZoneProvider,
    onBack: () -> Unit,
) {
    val viewModel: RangeReportViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = RangeReportViewModel.factory(
                    formatter = formatter,
                    privacyPreferenceStore = privacyPreferenceStore,
                    userExperiencePreferenceStore = userExperiencePreferenceStore,
                    textShareGateway = textShareGateway,
                    clock = clock,
                    zoneProvider = zoneProvider,
                ),
        )

    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    val snackbarHostState = remember {
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
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onPeriodSelected = viewModel::selectPeriod,
            onIncludeRecipientNameChanged = viewModel::setIncludeRecipientName,
            onCopyReport = viewModel::copyReport,
            onShareReport = viewModel::shareReport,
            onRetry = viewModel::refresh,
        )
    }

    RangeReportActionMessages(
        actionMessage = state.actionMessage,
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::consumeActionMessage,
    )
}

@Composable
fun RangeReportScreen(
    state: RangeReportUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPeriodSelected: (RangeReportPeriod) -> Unit,
    onIncludeRecipientNameChanged: (Boolean) -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
    onRetry: () -> Unit,
) {
    val experience = LocalCarePackExperience.current

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
            )
        },
        modifier = Modifier
                .fillMaxSize().testTag(
                    "range_report_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                    .fillMaxSize().padding(
                        contentPadding,
                    ).imePadding()
                    .navigationBarsPadding().verticalScroll(
                        rememberScrollState(),
                    ).padding(
                        horizontal = experience
                                .screenHorizontalPadding,
                        vertical = experience
                                .screenVerticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.sectionSpacing,
                ),
        ) {
            TextButton(
                onClick = onBack,
                enabled = !state.isSharing,
                modifier = Modifier
                        .carePackInteractiveControl().testTag(
                            "range_report_back",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.back,
                        ),
                )
            }

            Text(
                text = stringResource(
                        R.string.range_report_title,
                    ),
                style = MaterialTheme
                        .typography.headlineMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "range_report_title",
                        ),
            )

            Text(
                text = stringResource(
                        R.string.range_report_description,
                    ),
                style = MaterialTheme
                        .typography.bodyLarge,
            )

            RangePeriodSelector(
                selectedPeriod = state.period,
                enabled = !state.isLoading &&
                            !state.isSharing,
                onPeriodSelected = onPeriodSelected,
            )

            Text(
                text = stringResource(
                        R.string.range_report_date_range,
                        PersianDateText.formatNumeric(
                            state.period
                                .rangeEndingAt(
                                    state.today,
                                ).startDate,
                        ),
                        PersianDateText.formatNumeric(
                            state.today,
                        ),
                    ),
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier.testTag(
                        "range_report_date_range",
                    ),
            )

            IncludeRecipientNameToggle(
                checked = state.includeRecipientName,
                enabled = !state.isLoading &&
                            !state.isSharing,
                onCheckedChange = onIncludeRecipientNameChanged,
            )

            Text(
                text = stringResource(
                        R.string.carepack_share_destination_notice,
                    ),
                style = MaterialTheme
                        .typography.bodyMedium,
                modifier = Modifier.testTag(
                        "range_report_share_notice",
                    ),
            )

            when {
                state.isLoading ->
                    RangeReportLoading()

                state.failure == RangeReportFailure.LOAD_FAILED ->
                    RangeReportError(
                        failure = RangeReportFailure.LOAD_FAILED,
                        onRetry = onRetry,
                    )

                state.summary != null -> {
                    state.failure?.takeIf { failure ->
                            failure != RangeReportFailure
                                        .LOAD_FAILED
                        }?.let { failure ->
                            RangeReportActionError(
                                failure = failure,
                            )
                        }

                    RangeSummaryCard(
                        summary = state.summary,
                    )

                    if (
                        state.summary.totalOccurrenceCount == 0
                    ) {
                        Card(
                            modifier = Modifier
                                    .fillMaxWidth().testTag(
                                        "range_report_empty",
                                    ),
                        ) {
                            Text(
                                text = stringResource(
                                        R.string.range_report_empty,
                                    ),
                                modifier = Modifier.padding(
                                        16.dp,
                                    ),
                            )
                        }
                    }

                    RangeReportPreview(
                        reportText = state.reportText,
                    )

                    RangeReportActions(
                        reportText = state.reportText,
                        isSharing = state.isSharing,
                        onCopyReport = onCopyReport,
                        onShareReport = onShareReport,
                    )
                }
            }
        }
    }
}
