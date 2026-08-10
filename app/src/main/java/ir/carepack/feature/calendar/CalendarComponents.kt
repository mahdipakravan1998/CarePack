package ir.carepack.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.carepack.R
import ir.carepack.domain.calendar.JalaliMonthCell
import ir.carepack.domain.calendar.JalaliYearMonth
import ir.carepack.domain.calendar.PersianDateText
import ir.carepack.domain.calendar.toPersianDigits
import ir.carepack.domain.report.DayRangeSummary
import ir.carepack.domain.report.RangeOccurrenceEntry
import ir.carepack.domain.report.RangeOccurrenceReportState
import ir.carepack.domain.report.MedicationRecordingDetails
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.accessibility.carePackTraversalGroup
import ir.carepack.ui.experience.LocalCarePackExperience
import java.time.LocalDate
import java.time.format.DateTimeFormatter




@Composable
internal fun CalendarTitleSection(
    onOpenRangeReport: () -> Unit,
) {
    val experience = LocalCarePackExperience.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
                experience.itemSpacing,
            ),
    ) {
        Text(
            text = stringResource(
                    R.string.calendar_title,
                ),
            style = MaterialTheme
                    .typography.headlineMedium,
            modifier = Modifier
                    .carePackHeading().testTag(
                        "calendar_title",
                    ),
        )

        Text(
            text = stringResource(
                    R.string.calendar_description,
                ),
            style = MaterialTheme
                    .typography.bodyLarge,
        )

        OutlinedButton(
            onClick = onOpenRangeReport,
            modifier = Modifier
                    .fillMaxWidth().carePackPrimaryAction()
                    .testTag(
                        "calendar_open_range_report",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.calendar_open_range_report,
                    ),
            )
        }
    }
}

@Composable
internal fun CalendarMonthControls(
    displayedMonth: JalaliYearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
) {
    val experience = LocalCarePackExperience.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
                experience.compactSpacing,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onPreviousMonth,
                modifier = Modifier
                        .carePackInteractiveControl().testTag(
                            "calendar_previous_month",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.calendar_previous_month,
                        ),
                )
            }

            Text(
                text = PersianDateText.formatMonthYear(
                        year = displayedMonth.year,
                        month = displayedMonth.month,
                    ),
                style = MaterialTheme
                        .typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                        .weight(1f).carePackHeading()
                        .testTag(
                            "calendar_month_title",
                        ),
            )

            TextButton(
                onClick = onNextMonth,
                modifier = Modifier
                        .carePackInteractiveControl().testTag(
                            "calendar_next_month",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.calendar_next_month,
                        ),
                )
            }
        }

        OutlinedButton(
            onClick = onToday,
            modifier = Modifier
                    .fillMaxWidth().carePackInteractiveControl()
                    .testTag(
                        "calendar_today",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.calendar_today,
                    ),
            )
        }
    }
}

@Composable
internal fun CalendarMonthContent(
    state: CalendarUiState,
    onDateSelected: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
                .fillMaxWidth().carePackTraversalGroup()
                .testTag(
                    "calendar_month_grid",
                ),
        verticalArrangement = Arrangement.spacedBy(
                4.dp,
            ),
    ) {
        JalaliWeekdayHeader(
            weekdayOrder = state
                    .monthModel.weekdayOrder,
        )

        state.monthModel
            .weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                            4.dp,
                        ),
                ) {
                    week.forEach { cell ->
                        CalendarDayCell(
                            cell = cell,
                            summary = state.summary
                                    ?.summaryFor(
                                        cell.localDate,
                                    ),
                            onClick = {
                                onDateSelected(
                                    cell.localDate,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

        if (state.isLoading) {
            Row(
                modifier = Modifier
                        .fillMaxWidth().padding(
                            top = 8.dp,
                        ).carePackPoliteLiveRegion()
                        .testTag(
                            "calendar_loading",
                        ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(
                            24.dp,
                        ),
                )

                Text(
                    text = stringResource(
                            R.string.calendar_loading,
                        ),
                    modifier = Modifier.padding(
                            start = 12.dp,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun CalendarDayCell(
    cell: JalaliMonthCell,
    summary: DayRangeSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val experience = LocalCarePackExperience.current

    val total = summary
            ?.totalOccurrenceCount ?: 0

    val description = calendarDayDescription(
            cell = cell,
            summary = summary,
        )

    val border = when {
            cell.isSelected ->
                BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme
                            .colorScheme.primary,
                )

            cell.isToday ->
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme
                            .colorScheme.outline,
                )

            else -> null
        }

    Surface(
        color = if (cell.isSelected) {
                MaterialTheme.colorScheme
                    .primaryContainer
            } else {
                MaterialTheme.colorScheme
                    .surface
            },
        border = border,
        shape = MaterialTheme
                .shapes.medium,
        modifier = modifier
                .sizeIn(
                    minHeight = experience
                            .calendarCellMinHeight,
                ).alpha(
                    if (
                        cell.belongsToDisplayedMonth
                    ) {
                        1f
                    } else {
                        0.5f
                    },
                ).selectable(
                    selected = cell.isSelected,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).semantics {
                    contentDescription = description
                    selected = cell.isSelected
                }.testTag(
                    "calendar_day_${cell.localDate.toEpochDay()}",
                ),
    ) {
        Column(
            modifier = Modifier
                    .fillMaxWidth().heightIn(
                        min = experience
                                .calendarCellMinHeight,
                    ).padding(
                        horizontal = 2.dp,
                        vertical = 6.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = cell
                        .jalaliDate.dayOfMonth
                        .value.toString()
                        .toPersianDigits(),
                style = MaterialTheme
                        .typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            if (total > 0) {
                Text(
                    text = stringResource(
                            R.string.calendar_day_total_compact,
                            total.toString()
                                .toPersianDigits(),
                        ),
                    style = MaterialTheme
                            .typography.labelSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(
                            "calendar_day_count_${cell.localDate.toEpochDay()}",
                        ),
                )

                CalendarCompactStatus(
                    summary = checkNotNull(summary),
                )
            }
        }
    }
}

@Composable
internal fun CalendarCompactStatus(
    summary: DayRangeSummary,
) {
    val text = buildList {
            if (summary.givenCount > 0) {
                add(
                    "✓" + summary.givenCount
                                .toString().toPersianDigits(),
                )
            }

            if (summary.notGivenCount > 0) {
                add(
                    "×" + summary.notGivenCount
                                .toString().toPersianDigits(),
                )
            }

            if (summary.unknownCount > 0) {
                add(
                    "؟" + summary.unknownCount
                                .toString().toPersianDigits(),
                )
            }

            if (summary.noReportCount > 0) {
                add(
                    "ـ" + summary.noReportCount
                                .toString().toPersianDigits(),
                )
            }
        }.joinToString(
            separator = " ",
        )

    if (text.isNotBlank()) {
        Text(
            text = text,
            style = MaterialTheme
                    .typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun calendarDayDescription(
    cell: JalaliMonthCell,
    summary: DayRangeSummary?,
): String {
    val total = summary
            ?.totalOccurrenceCount ?: 0

    return buildString {
        append(
            stringResource(
                R.string.calendar_day_semantics,
                PersianDateText.formatFull(
                    cell.localDate,
                ),
                total.toString()
                    .toPersianDigits(),
                (summary?.givenCount ?: 0).toString()
                    .toPersianDigits(),
                (summary?.notGivenCount ?: 0).toString()
                    .toPersianDigits(),
                (summary?.unknownCount ?: 0).toString()
                    .toPersianDigits(),
                (summary?.noReportCount ?: 0).toString()
                    .toPersianDigits(),
            ),
        )

        if (cell.isToday) {
            append("، ")
            append(
                stringResource(
                    R.string.calendar_today_description,
                ),
            )
        }

        if (cell.isSelected) {
            append("، ")
            append(
                stringResource(
                    R.string.calendar_selected_description,
                ),
            )
        }

        if (
            !cell.belongsToDisplayedMonth
        ) {
            append("، ")
            append(
                stringResource(
                    R.string.calendar_adjacent_month_description,
                ),
            )
        }
    }
}

@Composable
internal fun SelectedDaySection(
    state: CalendarUiState,
    onOpenOccurrence: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val experience = LocalCarePackExperience.current

    Column(
        modifier = Modifier
                .fillMaxWidth().carePackTraversalGroup()
                .testTag(
                    "calendar_selected_day",
                ),
        verticalArrangement = Arrangement.spacedBy(
                experience.itemSpacing,
            ),
    ) {
        Text(
            text = stringResource(
                    R.string.calendar_selected_day_title,
                    PersianDateText.formatFull(
                        state.selectedDate,
                    ),
                ),
            style = MaterialTheme
                    .typography.titleLarge,
            modifier = Modifier
                    .carePackHeading().testTag(
                        "calendar_selected_day_title",
                    ),
        )

        when {
            state.failure != null -> {
                CalendarError(
                    failure = state.failure,
                    onRetry = onRetry,
                )
            }

            state.isLoading -> {
                Text(
                    text = stringResource(
                            R.string.calendar_selected_day_loading,
                        ),
                    modifier = Modifier
                            .carePackPoliteLiveRegion().testTag(
                                "calendar_selected_day_loading",
                            ),
                )
            }

            state.selectedDaySummary == null || state.selectedDaySummary
                        .totalOccurrenceCount == 0 -> {
                Card(
                    modifier = Modifier
                            .fillMaxWidth().testTag(
                                "calendar_empty_day",
                            ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.calendar_empty_day,
                            ),
                        modifier = Modifier.padding(
                                16.dp,
                            ),
                    )
                }
            }

            else -> {
                state.selectedDaySummary
                    .entries.forEach { entry ->
                        CalendarOccurrenceCard(
                            entry = entry,
                            onOpen = {
                                onOpenOccurrence(
                                    entry.occurrenceId,
                                )
                            },
                        )
                    }
            }
        }
    }
}

@Composable
internal fun CalendarError(
    failure: CalendarFailure,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(
                    "calendar_error",
                ),
        verticalArrangement = Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text = when (failure) {
                    CalendarFailure.LOAD_FAILED ->
                        stringResource(
                            R.string.calendar_error,
                        )
                },
            color = MaterialTheme
                    .colorScheme.error,
        )

        Button(
            onClick = onRetry,
            modifier = Modifier
                    .fillMaxWidth().carePackPrimaryAction()
                    .testTag(
                        "calendar_retry",
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
internal fun CalendarOccurrenceCard(
    entry: RangeOccurrenceEntry,
    onOpen: () -> Unit,
) {
    val experience = LocalCarePackExperience.current

    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "calendar_occurrence_${entry.occurrenceId}",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.medicationName,
                    style = MaterialTheme
                            .typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = entry
                            .localTime.format(
                                HOUR_MINUTE_FORMATTER,
                            ).toPersianDigits(),
                    style = MaterialTheme
                            .typography.titleMedium
                            .copy(
                                textDirection = TextDirection.Ltr,
                            ),
                    modifier = Modifier.testTag(
                            "calendar_occurrence_time_${entry.occurrenceId}",
                        ),
                )
            }

            Text(
                text = reportStateLabel(
                        entry.reportState,
                    ),
                style = MaterialTheme
                        .typography.bodyLarge,
                modifier = Modifier.testTag(
                        "calendar_occurrence_state_${entry.occurrenceId}",
                    ),
            )

            if (entry.instruction.isNotBlank()) {
                Text(
                    text = stringResource(
                            R.string.calendar_entry_instruction,
                            entry.instruction,
                        ),
                    style = MaterialTheme
                            .typography.bodyMedium,
                )
            }

            val recordingDetails = recordingDetails(
                    entry,
                )

            if (recordingDetails.isNotBlank()) {
                Text(
                    text = recordingDetails,
                    style = MaterialTheme
                            .typography.bodyMedium,
                )
            }

            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier
                        .fillMaxWidth().carePackInteractiveControl()
                        .testTag(
                            "calendar_open_occurrence_${entry.occurrenceId}",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.calendar_open_occurrence,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun reportStateLabel(
    state: RangeOccurrenceReportState,
): String = when (state) {
        RangeOccurrenceReportState.GIVEN ->
            stringResource(
                R.string.calendar_report_given,
            )

        RangeOccurrenceReportState.NOT_GIVEN ->
            stringResource(
                R.string.calendar_report_not_given,
            )

        RangeOccurrenceReportState.UNKNOWN ->
            stringResource(
                R.string.calendar_report_unknown,
            )

        RangeOccurrenceReportState.NO_REPORT ->
            stringResource(
                R.string.calendar_report_no_report,
            )
    }

@Composable
internal fun recordingDetails(entry: RangeOccurrenceEntry): String = MedicationRecordingDetails(
        medicationType = entry.medicationType,
        dosageText = entry.dosageText,
        doseUnit = entry.doseUnit,
    ).toDisplayText()

private val HOUR_MINUTE_FORMATTER = DateTimeFormatter.ofPattern(
        "HH:mm",
    )
