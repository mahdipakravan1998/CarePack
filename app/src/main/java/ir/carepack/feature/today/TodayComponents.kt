package ir.carepack.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.carepack.R
import ir.carepack.domain.calendar.JalaliPresentationDate
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryItem
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalStatus
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.report.MedicationRecordingDetails
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter



@Composable
internal fun TodayHeader(
    localDate: LocalDate,
    seniorMode: SeniorMode,
    onOpenTodayReport: () -> Unit,
) {
    val experience = carePackExperience()

    Column(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "today_header",
                ),
        verticalArrangement = Arrangement.spacedBy(
                experience.compactSpacing,
            ),
    ) {
        Text(
            text = if (seniorMode == SeniorMode.SIMPLE) {
                    stringResource(
                        R.string.today_simple_title,
                    )
                } else {
                    stringResource(
                        R.string.today_title,
                    )
                },
            style = MaterialTheme
                    .typography.headlineMedium,
            modifier = Modifier
                    .carePackHeading().testTag(
                        "today_title",
                    ),
        )

        Text(
            text = localDate
                    .toJalaliDisplayText(),
            style = MaterialTheme
                    .typography.titleMedium
                    .copy(
                        textDirection = TextDirection.Ltr,
                    ),
            modifier = Modifier.testTag(
                    "today_jalali_date",
                ),
        )

        OutlinedButton(
            onClick = onOpenTodayReport,
            modifier = Modifier
                    .fillMaxWidth().carePackInteractiveControl()
                    .testTag(
                        "today_open_report",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.carepack_today_report_action,
                    ),
            )
        }

    }
}

@Composable
internal fun ReminderAwarenessCard(
    status: ReminderStatus?,
) {
    val experience = carePackExperience()
    val message = when (status?.availability) {
            ReminderAvailability.NOTIFICATION_PERMISSION_REQUIRED -> {
                stringResource(
                    R.string.today_notification_unavailable_body,
                )
            }

            ReminderAvailability.APPROXIMATE -> {
                stringResource(
                    R.string.today_approximate_reminder_body,
                )
            }

            else -> null
        }

    if (message != null) {
        Card(
            modifier = Modifier
                    .fillMaxWidth().testTag(
                        "today_reminder_awareness",
                    ),
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(
                        experience.screenHorizontalPadding,
                    ),
            )
        }
    }
}

@Composable
internal fun TodayTabs(
    selectedSection: TodaySection,
    onTodaySelected: () -> Unit,
    onHistorySelected: () -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedSection.ordinal,
        modifier = Modifier.testTag(
                "today_tabs",
            ),
    ) {
        Tab(
            selected = selectedSection ==
                        TodaySection.TODAY,
            onClick = onTodaySelected,
            text = {
                Text(
                    text = stringResource(
                            R.string.today_title,
                        ),
                )
            },
            modifier = Modifier
                    .carePackInteractiveControl().testTag(
                        "today_tab_today",
                    ),
        )

        Tab(
            selected = selectedSection ==
                        TodaySection.HISTORY,
            onClick = onHistorySelected,
            text = {
                Text(
                    text = "سابقه اخیر",
                )
            },
            modifier = Modifier
                    .carePackInteractiveControl().testTag(
                        "today_tab_history",
                    ),
        )
    }
}

@Composable
internal fun SimpleTodayCard(
    item: TodayItem,
    isPrimary: Boolean,
    onGiven: () -> Unit,
    onNotGiven: () -> Unit,
    onUnknown: () -> Unit,
    onRemindLater: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val experience = carePackExperience()

    val canRecord = item.canMutateReport

    val statusText = item.statusText()

    Card(
        modifier = Modifier
                .fillMaxWidth().semantics {
                    contentDescription =
                        "نوبت امروز ${item.medicationName}، ساعت ${item.localTime.toDisplayText()}، $statusText"
                }.testTag(
                    "simple_today_card_${item.occurrenceId}",
                ),
    ) {
        Column(
            modifier = Modifier
                    .padding(
                        experience.screenHorizontalPadding,
                    ).testTag(
                        "simple_today_card",
                    ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            Text(
                text = if (isPrimary) {
                        stringResource(
                            R.string.today_next_item,
                        )
                    } else {
                        "نوبت امروز"
                    },
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier.carePackHeading(),
            )

            Text(
                text = item
                        .localTime.toDisplayText(),
                style = MaterialTheme
                        .typography.displaySmall
                        .copy(
                            textDirection = TextDirection.Ltr,
                        ),
                modifier = Modifier.testTag(
                        "simple_today_time",
                    ),
            )

            Text(
                text = item.medicationName,
                style = MaterialTheme
                        .typography.headlineMedium,
                modifier = Modifier.testTag(
                        "simple_today_medication_name",
                    ),
            )

            Text(
                text = item.medicationInstruction,
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier.testTag(
                        "simple_today_instruction",
                    ),
            )

            item.recordingDetailsText()
                .takeIf(String::isNotBlank)?.let { details ->
                    Text(
                        text = details,
                        style = MaterialTheme
                                .typography.titleMedium,
                        modifier = Modifier.testTag(
                                "simple_today_recording_details",
                            ),
                    )
                }

            Text(
                text = statusText,
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier
                        .carePackPoliteLiveRegion().testTag(
                            "simple_today_status",
                        ),
            )

            Button(
                onClick = onGiven,
                enabled = canRecord,
                modifier = Modifier
                        .fillMaxWidth().carePackPrimaryAction()
                        .heightIn(
                            min = 64.dp,
                        ).testTag(
                            "simple_today_given_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.record_given,
                        ),
                    style = MaterialTheme
                            .typography.titleLarge,
                )
            }

            Button(
                onClick = onRemindLater,
                enabled = item.canRemindLater,
                modifier = Modifier
                        .fillMaxWidth().carePackPrimaryAction()
                        .heightIn(
                            min = 64.dp,
                        ).testTag(
                            "simple_today_remind_later_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.remind_later,
                        ),
                    style = MaterialTheme
                            .typography.titleLarge,
                )
            }

            Text(
                text = stringResource(
                        R.string.today_secondary_actions,
                    ),
                style = MaterialTheme
                        .typography.titleSmall,
                modifier = Modifier.carePackHeading(),
            )

            OutlinedButton(
                onClick = onNotGiven,
                enabled = canRecord,
                modifier = Modifier
                        .fillMaxWidth().carePackInteractiveControl()
                        .defaultMinSize(
                            minHeight = 56.dp,
                        ).testTag(
                            "simple_today_not_given_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.record_not_given,
                        ),
                )
            }

            OutlinedButton(
                onClick = onUnknown,
                enabled = canRecord,
                modifier = Modifier
                        .fillMaxWidth().carePackInteractiveControl()
                        .defaultMinSize(
                            minHeight = 56.dp,
                        ).testTag(
                            "simple_today_unknown_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.record_unknown,
                        ),
                )
            }

            TextButton(
                onClick = onOpenDetails,
                modifier = Modifier
                        .fillMaxWidth().carePackInteractiveControl()
                        .defaultMinSize(
                            minHeight = 56.dp,
                        ).testTag(
                            "simple_today_details_${item.occurrenceId}",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.detail_title,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun TodayItemCard(
    item: TodayItem,
    onOpen: () -> Unit,
    onGiven: () -> Unit,
    onNotGiven: () -> Unit,
    onUnknown: () -> Unit,
    onRemindLater: () -> Unit,
) {
    val canRecord = item.canMutateReport

    val statusText = item.statusText()

    Card(
        modifier = Modifier
                .fillMaxWidth().clickable(
                    role = Role.Button,
                    onClick = onOpen,
                ).semantics {
                    contentDescription =
                        "${item.medicationName}، ساعت ${item.localTime.toDisplayText()}، $statusText"
                }.testTag(
                    "today_item_${item.occurrenceId}",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.medicationName,
                    style = MaterialTheme
                            .typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = item
                            .localTime.toDisplayText(),
                    style = MaterialTheme
                            .typography.titleMedium
                            .copy(
                                textDirection = TextDirection.Ltr,
                            ),
                )
            }

            Text(
                text = item.medicationInstruction,
                style = MaterialTheme
                        .typography.bodyLarge,
            )

            item.recordingDetailsText()
                .takeIf(String::isNotBlank)?.let { details ->
                    Text(
                        text = details,
                        style = MaterialTheme
                                .typography.bodyLarge,
                        modifier = Modifier.testTag(
                                "today_recording_details_${item.occurrenceId}",
                            ),
                    )
                }

            Text(
                text = statusText,
                style = MaterialTheme
                        .typography.bodyLarge,
                modifier = Modifier.carePackPoliteLiveRegion(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                Button(
                    onClick = onGiven,
                    enabled = canRecord,
                    modifier = Modifier
                            .weight(1f).carePackPrimaryAction()
                            .testTag(
                                "today_given_${item.occurrenceId}",
                            ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.record_given,
                            ),
                    )
                }

                OutlinedButton(
                    onClick = onRemindLater,
                    enabled = item.canRemindLater,
                    modifier = Modifier
                            .weight(1f).carePackInteractiveControl()
                            .testTag(
                                "today_remind_later_${item.occurrenceId}",
                            ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.remind_later,
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                OutlinedButton(
                    onClick = onNotGiven,
                    enabled = canRecord,
                    modifier = Modifier
                            .weight(1f).carePackInteractiveControl()
                            .testTag(
                                "today_not_given_${item.occurrenceId}",
                            ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.record_not_given,
                            ),
                    )
                }

                OutlinedButton(
                    onClick = onUnknown,
                    enabled = canRecord,
                    modifier = Modifier
                            .weight(1f).carePackInteractiveControl()
                            .testTag(
                                "today_unknown_${item.occurrenceId}",
                            ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.record_unknown,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CompactTodayItemCard(
    item: TodayItem,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
                .fillMaxWidth().clickable(
                    role = Role.Button,
                    onClick = onOpen,
                ).testTag(
                    "today_compact_item_${item.occurrenceId}",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text = "${item.localTime.toDisplayText()} — ${item.medicationName}",
                style = MaterialTheme
                        .typography.titleMedium
                        .copy(
                            textDirection = TextDirection.ContentOrLtr,
                        ),
            )

            Text(
                text = item.statusText(),
            )

            item.recordingDetailsText()
                .takeIf(String::isNotBlank)?.let { details ->
                    Text(
                        text = details,
                    )
                }
        }
    }
}

@Composable
internal fun HistoryItemCard(
    item: HistoryItem,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
                .fillMaxWidth().clickable(
                    role = Role.Button,
                    onClick = onOpen,
                ).testTag(
                    "history_item_${item.occurrenceId}",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text = "${item.localTime.toDisplayText()} — ${item.medicationName}",
                style = MaterialTheme
                        .typography.titleMedium
                        .copy(
                            textDirection = TextDirection.ContentOrLtr,
                        ),
            )

            Text(
                text = item.statusText(),
            )

            item.recordingDetailsText()
                .takeIf(String::isNotBlank)?.let { details ->
                    Text(
                        text = details,
                    )
                }
        }
    }
}

@Composable
internal fun TodayEmptyCard(
    emptyState: TodayEmptyState?,
    onOpenCarePlan: () -> Unit,
    seniorMode: SeniorMode,
) {
    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "today_empty",
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
                text = if (seniorMode == SeniorMode.SIMPLE) {
                        stringResource(
                            R.string.today_simple_empty_title,
                        )
                    } else {
                        when (emptyState) {
                            TodayEmptyState.NO_MEDICATIONS -> {
                                stringResource(
                                    R.string.today_simple_empty_body,
                                )
                            }

                            TodayEmptyState.NO_OCCURRENCES,
                            null,
                                -> {
                                stringResource(
                                    R.string.today_empty_title,
                                )
                            }
                        }
                    },
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier.carePackHeading(),
            )

            Text(
                text = when (emptyState) {
                        TodayEmptyState.NO_MEDICATIONS -> {
                            stringResource(
                                R.string.today_simple_empty_body,
                            )
                        }

                        TodayEmptyState.NO_OCCURRENCES,
                        null,
                            -> {
                            stringResource(
                                R.string.today_empty_body,
                            )
                        }
                    },
            )

            Button(
                onClick = onOpenCarePlan,
                modifier = Modifier
                        .fillMaxWidth().carePackPrimaryAction()
                        .testTag(
                            "today_empty_open_care_plan",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.manage_care_plan,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun LoadingCard(
    testTag: String,
) {
    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(testTag),
    ) {
        Column(
            modifier = Modifier
                    .fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            CircularProgressIndicator()

            Text(
                text = stringResource(
                        R.string.loading,
                    ),
            )
        }
    }
}

@Composable
internal fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    testTag: String,
) {
    Card(
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(testTag),
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
                text = message,
                color = MaterialTheme
                        .colorScheme.error,
            )

            Button(
                onClick = onRetry,
                modifier = Modifier
                        .fillMaxWidth().carePackPrimaryAction()
                        .testTag(
                            "${testTag}_retry",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.retry,
                        ),
                )
            }
        }
    }
}

internal fun simplePriority(
    item: TodayItem,
): Int {
    if (item.reportState == null) {
        return when (item.temporalStatus) {
            TemporalStatus.DUE -> 0
            TemporalStatus.PAST -> 1
            TemporalStatus.UPCOMING -> 2
        }
    }

    return 3
}

internal fun TodayItem.recordingDetailsText(): String = MedicationRecordingDetails(
        medicationType = medicationType,
        dosageText = dosageText,
        doseUnit = doseUnit,
    ).toDisplayText()

internal fun HistoryItem.recordingDetailsText(): String = MedicationRecordingDetails(
        medicationType = medicationType,
        dosageText = dosageText,
        doseUnit = doseUnit,
    ).toDisplayText()

@Composable
internal fun TodayItem.statusText(): String {
    return when {
        lifecycle == OccurrenceLifecycle.CANCELLED -> {
            stringResource(
                R.string.today_item_cancelled,
            )
        }

        reportState == CaregiverReportState.GIVEN -> {
            stringResource(
                R.string.today_item_recorded_given,
            )
        }

        reportState == CaregiverReportState.NOT_GIVEN -> {
            stringResource(
                R.string.today_item_recorded_not_given,
            )
        }

        reportState == CaregiverReportState.UNKNOWN -> {
            stringResource(
                R.string.today_item_recorded_unknown,
            )
        }

        temporalStatus == TemporalStatus.UPCOMING -> {
            stringResource(
                R.string.today_item_upcoming,
            )
        }

        temporalStatus == TemporalStatus.DUE -> {
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

@Composable
internal fun HistoryItem.statusText(): String {
    return when {
        lifecycle == OccurrenceLifecycle.CANCELLED -> {
            stringResource(
                R.string.today_item_cancelled,
            )
        }

        reportState == CaregiverReportState.GIVEN -> {
            stringResource(
                R.string.today_item_recorded_given,
            )
        }

        reportState == CaregiverReportState.NOT_GIVEN -> {
            stringResource(
                R.string.today_item_recorded_not_given,
            )
        }

        reportState == CaregiverReportState.UNKNOWN -> {
            stringResource(
                R.string.today_item_recorded_unknown,
            )
        }

        temporalStatus == TemporalStatus.UPCOMING -> {
            stringResource(
                R.string.today_item_upcoming,
            )
        }

        temporalStatus == TemporalStatus.DUE -> {
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

internal fun LocalDate.toJalaliDisplayText(): String {
    return JalaliPresentationDate.from(this)
        .formatNumeric()
}

internal fun LocalTime.toDisplayText(): String {
    return format(
        HOUR_MINUTE_FORMATTER,
    )
}

private val HOUR_MINUTE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "HH:mm",
    )
