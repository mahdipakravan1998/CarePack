package ir.carepack.feature.reporting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.carepack.R
import ir.carepack.domain.calendar.toPersianDigits
import ir.carepack.domain.report.DateRangeSummary
import ir.carepack.domain.report.RangeReportPeriod
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.LocalCarePackExperience




@Composable
internal fun RangePeriodSelector(
    selectedPeriod: RangeReportPeriod,
    enabled: Boolean,
    onPeriodSelected: (RangeReportPeriod) -> Unit,
) {
    val experience = LocalCarePackExperience.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
                experience.compactSpacing,
            ),
    ) {
        Text(
            text = stringResource(
                    R.string.range_report_period_label,
                ),
            style = MaterialTheme
                    .typography.titleMedium,
            modifier = Modifier.carePackHeading(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                    experience.compactSpacing,
                ),
        ) {
            FilterChip(
                selected = selectedPeriod ==
                            RangeReportPeriod.SEVEN_DAYS,
                onClick = {
                    onPeriodSelected(
                        RangeReportPeriod.SEVEN_DAYS,
                    )
                },
                enabled = enabled,
                label = {
                    Text(
                        text = stringResource(
                                R.string.range_report_7_days,
                            ),
                    )
                },
                modifier = Modifier
                        .weight(1f).carePackInteractiveControl()
                        .testTag(
                            "range_report_period_7",
                        ),
            )

            FilterChip(
                selected = selectedPeriod ==
                            RangeReportPeriod.THIRTY_DAYS,
                onClick = {
                    onPeriodSelected(
                        RangeReportPeriod.THIRTY_DAYS,
                    )
                },
                enabled = enabled,
                label = {
                    Text(
                        text = stringResource(
                                R.string.range_report_30_days,
                            ),
                    )
                },
                modifier = Modifier
                        .weight(1f).carePackInteractiveControl()
                        .testTag(
                            "range_report_period_30",
                        ),
            )
        }
    }
}

@Composable
internal fun IncludeRecipientNameToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val experience = LocalCarePackExperience.current

    Row(
        modifier = Modifier
                .fillMaxWidth().toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).padding(
                    vertical = experience.compactSpacing,
                ).testTag(
                    "range_report_include_recipient_name_row",
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(
                    4.dp,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.carepack_include_recipient_name,
                    ),
                style = MaterialTheme
                        .typography.titleMedium,
            )

            Text(
                text = stringResource(
                        R.string.carepack_include_recipient_name_description,
                    ),
                style = MaterialTheme
                        .typography.bodySmall,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.testTag(
                    "range_report_include_recipient_name_switch",
                ),
        )
    }
}

@Composable
internal fun RangeReportLoading() {
    Column(
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(
                    "range_report_loading",
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        CircularProgressIndicator()

        Text(
            text = stringResource(
                    R.string.range_report_loading,
                ),
        )
    }
}

@Composable
internal fun RangeReportError(
    failure: RangeReportFailure,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(
                    "range_report_error",
                ),
        verticalArrangement = Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text = rangeReportFailureText(
                    failure,
                ),
            color = MaterialTheme
                    .colorScheme.error,
        )

        Button(
            onClick = onRetry,
            modifier = Modifier
                    .fillMaxWidth().carePackPrimaryAction()
                    .testTag(
                        "range_report_retry",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.retry_action,
                    ),
            )
        }
    }
}

@Composable
internal fun RangeReportActionError(
    failure: RangeReportFailure,
) {
    Text(
        text = rangeReportFailureText(
                failure,
            ),
        color = MaterialTheme
                .colorScheme.error,
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(
                    "range_report_action_error",
                ),
    )
}

@Composable
internal fun rangeReportFailureText(
    failure: RangeReportFailure,
): String = when (failure) {
        RangeReportFailure.LOAD_FAILED ->
            stringResource(
                R.string.range_report_load_failed,
            )

        RangeReportFailure.COPY_FAILED ->
            stringResource(
                R.string.range_report_copy_failed,
            )

        RangeReportFailure.NO_SHARE_TARGET ->
            stringResource(
                R.string.range_report_no_share_target,
            )

        RangeReportFailure.SHARE_FAILED ->
            stringResource(
                R.string.range_report_share_failed,
            )
    }

@Composable
internal fun RangeSummaryCard(
    summary: DateRangeSummary,
) {
    val experience = LocalCarePackExperience.current

    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "range_report_summary",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.compactSpacing,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.range_report_summary_title,
                    ),
                style = MaterialTheme
                        .typography.titleLarge,
                modifier = Modifier.carePackHeading(),
            )

            SummaryLine(
                label = stringResource(
                        R.string.range_report_total,
                    ),
                count = summary
                        .totalOccurrenceCount,
                testTag = "range_report_total",
            )

            SummaryLine(
                label = stringResource(
                        R.string.range_report_given,
                    ),
                count = summary.givenCount,
                testTag = "range_report_given",
            )

            SummaryLine(
                label = stringResource(
                        R.string.range_report_not_given,
                    ),
                count = summary.notGivenCount,
                testTag = "range_report_not_given",
            )

            SummaryLine(
                label = stringResource(
                        R.string.range_report_unknown,
                    ),
                count = summary.unknownCount,
                testTag = "range_report_unknown",
            )

            SummaryLine(
                label = stringResource(
                        R.string.range_report_no_report,
                    ),
                count = summary.noReportCount,
                testTag = "range_report_no_report",
            )
        }
    }
}

@Composable
internal fun SummaryLine(
    label: String,
    count: Int,
    testTag: String,
) {
    Row(
        modifier = Modifier
                .fillMaxWidth().testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme
                    .typography.bodyLarge,
        )

        Text(
            text = count
                    .toString().toPersianDigits(),
            style = MaterialTheme
                    .typography.titleMedium,
        )
    }
}

@Composable
internal fun RangeReportPreview(
    reportText: String,
) {
    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "range_report_preview_card",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.range_report_preview_title,
                    ),
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier.carePackHeading(),
            )

            SelectionContainer {
                Text(
                    text = reportText,
                    style = MaterialTheme
                            .typography.bodyMedium
                            .copy(
                                textDirection = TextDirection
                                        .ContentOrRtl,
                            ),
                    modifier = Modifier.testTag(
                            "range_report_preview_text",
                        ),
                )
            }
        }
    }
}

@Composable
internal fun RangeReportActions(
    reportText: String,
    isSharing: Boolean,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
) {
    val experience = LocalCarePackExperience.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
                experience.itemSpacing,
            ),
    ) {
        OutlinedButton(
            onClick = onCopyReport,
            enabled = reportText.isNotBlank() &&
                        !isSharing,
            modifier = Modifier
                    .fillMaxWidth().carePackPrimaryAction()
                    .testTag(
                        "range_report_copy",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.range_report_copy,
                    ),
            )
        }

        Button(
            onClick = onShareReport,
            enabled = reportText.isNotBlank() &&
                        !isSharing,
            modifier = Modifier
                    .fillMaxWidth().carePackPrimaryAction()
                    .testTag(
                        "range_report_share",
                    ),
        ) {
            if (isSharing) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = stringResource(
                            R.string.range_report_share,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun RangeReportActionMessages(
    actionMessage: RangeReportActionMessage?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    val copiedMessage = stringResource(
            R.string.range_report_copied,
        )

    val shareOpenedMessage = stringResource(
            R.string.range_report_share_opened,
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

                RangeReportActionMessage.SHARE_CHOOSER_OPENED ->
                    shareOpenedMessage
            },
        )

        onConsumed()
    }
}
