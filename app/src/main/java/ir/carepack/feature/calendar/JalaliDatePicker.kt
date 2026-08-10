package ir.carepack.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.carepack.R
import ir.carepack.domain.calendar.JalaliMonthCell
import ir.carepack.domain.calendar.JalaliMonthModel
import ir.carepack.domain.calendar.JalaliMonthModelFactory
import ir.carepack.domain.calendar.JalaliYearMonth
import ir.carepack.domain.calendar.PersianDateText
import ir.carepack.domain.calendar.toPersianDigits
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.LocalCarePackExperience
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun JalaliDatePickerDialog(
    title: String,
    selectedDate: LocalDate?,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    allowClear: Boolean,
    clearAsNoEndDate: Boolean = false,
    onDismissRequest: () -> Unit,
    onDateSelected: (LocalDate?) -> Unit,
) {
    var workingSelection by
    remember(selectedDate) {
        mutableStateOf(
            selectedDate,
        )
    }

    var displayedMonth by
    remember(
        selectedDate,
        today,
    ) {
        mutableStateOf(
            JalaliYearMonth.from(
                selectedDate ?: today,
            ),
        )
    }

    val model = remember(
            displayedMonth,
            today,
            workingSelection,
            firstDayOfWeek,
        ) {
            JalaliMonthModelFactory.create(
                displayedMonth = displayedMonth,
                today = today,
                selectedDate = workingSelection ?: today,
                firstDayOfWeek = firstDayOfWeek,
            )
        }

    val experience = LocalCarePackExperience.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            shape = MaterialTheme
                    .shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                    .fillMaxWidth().padding(
                        horizontal = experience
                                .dialogHorizontalPadding,
                        vertical = 24.dp,
                    ).testTag(
                        "jalali_date_picker_dialog",
                    ),
        ) {
            Column(
                modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(
                        experience.itemSpacing,
                    ),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme
                            .typography.headlineSmall,
                    modifier = Modifier
                            .carePackHeading().testTag(
                                "jalali_date_picker_title",
                            ),
                )

                workingSelection?.let { date ->
                        Text(
                            text = stringResource(
                                    R.string.jalali_date_picker_selected_date,
                                    PersianDateText.formatFull(
                                        date,
                                    ),
                                ),
                            style = MaterialTheme
                                    .typography.bodyLarge,
                            modifier = Modifier.testTag(
                                    "jalali_date_picker_selection",
                                ),
                        )
                    }

                JalaliDatePickerMonthHeader(
                    displayedMonth = displayedMonth,
                    onPrevious = {
                        displayedMonth = displayedMonth.previous()
                    },
                    onNext = {
                        displayedMonth = displayedMonth.next()
                    },
                )

                JalaliWeekdayHeader(
                    weekdayOrder = model.weekdayOrder,
                )

                JalaliDatePickerMonthGrid(
                    model = model,
                    selectedDate = workingSelection,
                    onDateSelected = { date ->
                        workingSelection = date

                        val selectedMonth = JalaliYearMonth.from(
                                date,
                            )

                        if (
                            selectedMonth != displayedMonth
                        ) {
                            displayedMonth = selectedMonth
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                            experience.compactSpacing,
                        ),
                ) {
                    OutlinedButton(
                        onClick = {
                            workingSelection = today
                            displayedMonth = JalaliYearMonth.from(
                                    today,
                                )
                        },
                        modifier = Modifier
                                .weight(1f).carePackInteractiveControl()
                                .testTag(
                                    "jalali_date_picker_today",
                                ),
                    ) {
                        Text(
                            text = stringResource(
                                    R.string.jalali_date_picker_today,
                                ),
                        )
                    }

                    if (allowClear) {
                        OutlinedButton(
                            onClick = {
                                workingSelection = null
                            },
                            modifier = Modifier
                                    .weight(1f).carePackInteractiveControl()
                                    .testTag(
                                        "jalali_date_picker_clear",
                                    ),
                        ) {
                            Text(
                                text = stringResource(
                                        if (clearAsNoEndDate) {
                                            R.string.jalali_date_picker_no_end_date
                                        } else {
                                            R.string.jalali_date_picker_clear
                                        },
                                    ),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                            experience.compactSpacing,
                        ),
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                                .weight(1f).carePackInteractiveControl()
                                .testTag(
                                    "jalali_date_picker_cancel",
                                ),
                    ) {
                        Text(
                            text = stringResource(
                                    R.string.jalali_date_picker_cancel,
                                ),
                        )
                    }

                    Button(
                        onClick = {
                            onDateSelected(
                                workingSelection,
                            )
                        },
                        enabled = workingSelection != null ||
                                    allowClear,
                        modifier = Modifier
                                .weight(1f).carePackPrimaryAction()
                                .testTag(
                                    "jalali_date_picker_confirm",
                                ),
                    ) {
                        Text(
                            text = stringResource(
                                    R.string.jalali_date_picker_confirm,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JalaliDatePickerMonthHeader(
    displayedMonth: JalaliYearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onPrevious,
            modifier = Modifier
                    .carePackInteractiveControl().testTag(
                        "jalali_date_picker_previous_month",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.jalali_date_picker_previous_month,
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
                        "jalali_date_picker_month_title",
                    ),
        )

        TextButton(
            onClick = onNext,
            modifier = Modifier
                    .carePackInteractiveControl().testTag(
                        "jalali_date_picker_next_month",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.jalali_date_picker_next_month,
                    ),
            )
        }
    }
}

@Composable
private fun JalaliDatePickerMonthGrid(
    model: JalaliMonthModel,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
) {
    val experience = LocalCarePackExperience.current

    Column(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "jalali_date_picker_month_grid",
                ),
        verticalArrangement = Arrangement.spacedBy(
                4.dp,
            ),
    ) {
        model.weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                        4.dp,
                    ),
            ) {
                week.forEach { cell ->
                    JalaliDatePickerDayCell(
                        cell = cell,
                        isSelected = cell.localDate ==
                                    selectedDate,
                        minHeight = experience
                                .controlMinHeight,
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
    }
}

@Composable
private fun JalaliDatePickerDayCell(
    cell: JalaliMonthCell,
    isSelected: Boolean,
    minHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = buildString {
            append(
                PersianDateText.formatFull(
                    cell.localDate,
                ),
            )

            if (cell.isToday) {
                append("، ")
                append(
                    stringResource(
                        R.string.jalali_date_picker_today_description,
                    ),
                )
            }

            if (isSelected) {
                append("، ")
                append(
                    stringResource(
                        R.string.jalali_date_picker_selected_description,
                    ),
                )
            }
        }

    val border = when {
            isSelected ->
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
        color = if (isSelected) {
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
                    minHeight = minHeight,
                ).alpha(
                    if (
                        cell.belongsToDisplayedMonth
                    ) {
                        1f
                    } else {
                        0.55f
                    },
                ).selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).semantics {
                    contentDescription = description
                    selected = isSelected
                }.testTag(
                    "jalali_date_picker_day_${cell.localDate.toEpochDay()}",
                ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                    .fillMaxWidth().heightIn(
                        min = minHeight,
                    ).padding(
                        horizontal = 2.dp,
                        vertical = 4.dp,
                    ),
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
        }
    }
}
