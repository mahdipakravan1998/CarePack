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



@Composable
internal fun LoadingContent() {
    val experience =
        carePackExperience()

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
                experience.itemSpacing,
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
internal fun OccurrenceDetailContent(
    detail: OccurrenceDetail,
    entryMode: OccurrenceDetailEntryMode,
    onGiven: () -> Unit,
    onNotGiven: () -> Unit,
    onUnknown: () -> Unit,
    onRemindLater: () -> Unit = {},
) {
    val experience =
        carePackExperience()

    val canRecord =
        detail.lifecycle ==
                OccurrenceLifecycle.ACTIVE

    val isReminderEntry =
        entryMode ==
                OccurrenceDetailEntryMode.REMINDER

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
                    experience.screenHorizontalPadding,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    experience.itemSpacing,
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

            if (
                isReminderEntry &&
                canRecord
            ) {
                ReminderEntryPrimaryActions(
                    detail = detail,
                    onGiven = onGiven,
                    onRemindLater =
                        onRemindLater,
                )
            }

            if (isReminderEntry) {
                Text(
                    text =
                        stringResource(
                            R.string.occurrence_detail_more_details,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    modifier =
                        Modifier.carePackHeading(),
                )
            }

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

            detail
                .recordingDetailsText()
                .takeIf(String::isNotBlank)
                ?.let { details ->
                    DetailLabelValue(
                        label =
                            stringResource(
                                R.string.medication_recording_details_label,
                            ),
                        value =
                            details,
                        testTag =
                            "occurrence_detail_recording_details",
                    )
                }

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
                if (!isReminderEntry) {
                    Button(
                        onClick = onGiven,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .carePackPrimaryAction()
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
                                .carePackPrimaryAction()
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

                if (experience.isSimple) {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                experience.itemSpacing,
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
                                "ثبت مراقب: داده نشد برای ${detail.medicationName} در ساعت ${detail.localTime.toDisplayText()}",
                            testTag =
                                "report_not_given",
                            modifier =
                                Modifier.fillMaxWidth(),
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
                                Modifier.fillMaxWidth(),
                            onClick =
                                onUnknown,
                        )
                    }
                } else {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                experience.compactSpacing,
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
                                "ثبت مراقب: داده نشد برای ${detail.medicationName} در ساعت ${detail.localTime.toDisplayText()}",
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
}

@Composable
internal fun ReminderEntryPrimaryActions(
    detail: OccurrenceDetail,
    onGiven: () -> Unit,
    onRemindLater: () -> Unit,
) {
    val experience =
        carePackExperience()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "reminder_action_surface",
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    experience.screenHorizontalPadding,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.reminder_action_primary_actions,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                modifier =
                    Modifier.carePackHeading(),
            )

            Text(
                text =
                    stringResource(
                        R.string.reminder_action_summary,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            Button(
                onClick = onGiven,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .carePackPrimaryAction()
                        .heightIn(
                            min = 72.dp,
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
                            .headlineSmall,
                )
            }

            Button(
                onClick = onRemindLater,
                enabled =
                    detail.reportState == null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .carePackPrimaryAction()
                        .heightIn(
                            min = 72.dp,
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
                            .headlineSmall,
                )
            }
        }
    }
}

@Composable
internal fun StatusCard(
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
internal fun ReportActionButton(
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
                .carePackInteractiveControl()
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

internal fun OccurrenceDetail.recordingDetailsText():
        String =
    buildList {
        medicationType
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                add(
                    "نوع: $value",
                )
            }

        dosageText
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                add(
                    "مقدار ثبت‌شده: $value",
                )
            }

        doseUnit
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                add(
                    "واحد: $value",
                )
            }
    }.joinToString(
        separator = "، ",
    )

@Composable
internal fun DetailLabelValue(
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
internal fun OccurrenceDetail.statusText():
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

internal fun LocalDate.toJalaliDisplayText():
        String {
    return JalaliPresentationDate
        .from(this)
        .formatNumeric()
}

internal fun LocalTime.toDisplayText():
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
