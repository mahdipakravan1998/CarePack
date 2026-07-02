package ir.carepack.feature.careplan

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.carepack.R
import ir.carepack.domain.calendar.JalaliPresentationDate
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.schedule.SchedulePatternRules
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.time.DayOfWeek
import java.time.LocalDate

data class ScheduleFormCallbacks(
    val onWeekdayToggled:
        (DayOfWeek) -> Unit,
    val onInputModeSelected:
        (ScheduleInputMode) -> Unit,
    val onTimeDraftChanged:
        (String) -> Unit,
    val onAddTime: () -> Unit,
    val onRemoveTime:
        (Int) -> Unit,
    val onIntervalHoursSelected:
        (Int) -> Unit,
    val onIntervalAnchorChanged:
        (String) -> Unit,
    val onStartDateChanged:
        (String) -> Unit,
    val onEndDateChanged:
        (String) -> Unit,
)

@Composable
fun ScheduleFormFields(
    state: ScheduleFormUiState,
    callbacks: ScheduleFormCallbacks,
    enabled: Boolean,
    firstDayOfWeek: DayOfWeek,
    previewAnchorDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    val leftToRightTextStyle =
        LocalTextStyle
            .current
            .copy(
                textDirection =
                    TextDirection.Ltr,
            )

    Column(
        modifier = modifier,
    ) {
        Text(
            text =
                stringResource(
                    R.string.weekday_label,
                ),
            style =
                MaterialTheme
                    .typography
                    .labelLarge,
            modifier =
                Modifier.carePackHeading(),
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp,
                ),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState(),
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            orderedWeekdays(
                firstDayOfWeek,
            ).forEach { dayOfWeek ->
                FilterChip(
                    selected =
                        dayOfWeek in
                                state.weekdays,
                    onClick = {
                        callbacks
                            .onWeekdayToggled(
                                dayOfWeek,
                            )
                    },
                    enabled = enabled,
                    label = {
                        Text(
                            text =
                                stringResource(
                                    weekdayPersianNameResource(
                                        dayOfWeek,
                                    ),
                                ),
                        )
                    },
                    modifier =
                        Modifier.testTag(
                            "weekday_${dayOfWeek.name}",
                        ),
                )
            }
        }

        state
            .errors[
            CarePlanField.WEEKDAYS
        ]?.let { message ->
            FormErrorText(
                message = message,
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    20.dp,
                ),
        )

        Text(
            text =
                stringResource(
                    R.string
                        .schedule_pattern_label,
                ),
            style =
                MaterialTheme
                    .typography
                    .labelLarge,
            modifier =
                Modifier.carePackHeading(),
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp,
                ),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState(),
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            FilterChip(
                selected =
                    state.inputMode ==
                            ScheduleInputMode.FIXED_TIMES,
                onClick = {
                    callbacks.onInputModeSelected(
                        ScheduleInputMode.FIXED_TIMES,
                    )
                },
                enabled = enabled,
                label = {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .fixed_time_schedule_label,
                            ),
                    )
                },
                modifier =
                    Modifier.testTag(
                        "schedule_pattern_fixed",
                    ),
            )

            FilterChip(
                selected =
                    state.inputMode ==
                            ScheduleInputMode.EVERY_X_HOURS,
                onClick = {
                    callbacks.onInputModeSelected(
                        ScheduleInputMode.EVERY_X_HOURS,
                    )
                },
                enabled = enabled,
                label = {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .interval_schedule_label,
                            ),
                    )
                },
                modifier =
                    Modifier.testTag(
                        "schedule_pattern_interval",
                    ),
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    16.dp,
                ),
        )

        when (state.inputMode) {
            ScheduleInputMode.FIXED_TIMES -> {
                FixedTimesEditor(
                    state = state,
                    callbacks = callbacks,
                    enabled = enabled,
                    leftToRightTextStyle =
                        leftToRightTextStyle,
                )
            }

            ScheduleInputMode.EVERY_X_HOURS -> {
                IntervalEditor(
                    state = state,
                    callbacks = callbacks,
                    enabled = enabled,
                    leftToRightTextStyle =
                        leftToRightTextStyle,
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    20.dp,
                ),
        )

        OutlinedTextField(
            value =
                state.startDateText,
            onValueChange =
                callbacks.onStartDateChanged,
            enabled = enabled,
            label = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .start_date_label,
                        ),
                )
            },
            textStyle =
                leftToRightTextStyle,
            singleLine = true,
            isError =
                state.errors
                    .containsKey(
                        CarePlanField.START_DATE,
                    ),
            supportingText = {
                state.errors[
                    CarePlanField.START_DATE
                ]?.let { errorMessage ->
                    Text(
                        text =
                            errorMessage,
                        modifier =
                            Modifier
                                .carePackPoliteLiveRegion(),
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "start_date",
                    ),
        )

        OutlinedTextField(
            value =
                state.endDateText,
            onValueChange =
                callbacks.onEndDateChanged,
            enabled = enabled,
            label = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .end_date_label,
                        ),
                )
            },
            textStyle =
                leftToRightTextStyle,
            singleLine = true,
            isError =
                state.errors
                    .containsKey(
                        CarePlanField.END_DATE,
                    ),
            supportingText = {
                state.errors[
                    CarePlanField.END_DATE
                ]?.let { errorMessage ->
                    Text(
                        text =
                            errorMessage,
                        modifier =
                            Modifier
                                .carePackPoliteLiveRegion(),
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "end_date",
                    ),
        )

        OutlinedTextField(
            value =
                state.zoneId,
            onValueChange = {},
            enabled = false,
            label = {
                Text(
                    text =
                        stringResource(
                            R.string
                                .fixed_zone_label,
                        ),
                )
            },
            textStyle =
                leftToRightTextStyle,
            singleLine = true,
            isError =
                state.errors
                    .containsKey(
                        CarePlanField.ZONE_ID,
                    ),
            supportingText = {
                state.errors[
                    CarePlanField.ZONE_ID
                ]?.let { errorMessage ->
                    Text(
                        text =
                            errorMessage,
                        modifier =
                            Modifier
                                .carePackPoliteLiveRegion(),
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "schedule_zone",
                    ),
        )

        Spacer(
            modifier =
                Modifier.height(
                    20.dp,
                ),
        )

        SchedulePreviewCard(
            state = state,
            previewAnchorDate =
                previewAnchorDate,
        )
    }
}

@Composable
private fun FixedTimesEditor(
    state: ScheduleFormUiState,
    callbacks: ScheduleFormCallbacks,
    enabled: Boolean,
    leftToRightTextStyle:
    androidx.compose.ui.text.TextStyle,
) {
    Text(
        text =
            stringResource(
                R.string
                    .selected_times_label,
            ),
        style =
            MaterialTheme
                .typography
                .labelLarge,
        modifier =
            Modifier.carePackHeading(),
    )

    Spacer(
        modifier =
            Modifier.height(
                8.dp,
            ),
    )

    state
        .minutesOfDay
        .forEach { minuteOfDay ->
            val timeText =
                minuteOfDay
                    .toHourMinuteText()

            val removeDescription =
                "${stringResource(R.string.remove_time)} $timeText"

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 4.dp,
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text =
                                timeText,
                            style =
                                leftToRightTextStyle,
                        )
                    },
                    modifier =
                        Modifier
                            .weight(
                                1f,
                            )
                            .testTag(
                                "selected_time_$minuteOfDay",
                            ),
                )

                TextButton(
                    onClick = {
                        callbacks
                            .onRemoveTime(
                                minuteOfDay,
                            )
                    },
                    enabled = enabled,
                    modifier =
                        Modifier
                            .semantics {
                                contentDescription =
                                    removeDescription
                            }
                            .testTag(
                                "remove_time_$minuteOfDay",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .remove_time,
                            ),
                    )
                }
            }
        }

    if (
        state
            .minutesOfDay
            .isEmpty()
    ) {
        Text(
            text =
                stringResource(
                    R.string
                        .schedule_preview_empty,
                ),
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
        )
    }

    state.errors[
        CarePlanField.TIMES
    ]?.let { message ->
        FormErrorText(
            message = message,
            testTag = "times_error",
        )
    }

    Spacer(
        modifier =
            Modifier.height(
                12.dp,
            ),
    )

    OutlinedTextField(
        value =
            state.timeDraft,
        onValueChange =
            callbacks.onTimeDraftChanged,
        enabled = enabled,
        label = {
            Text(
                text =
                    stringResource(
                        R.string.time_label,
                    ),
            )
        },
        textStyle =
            leftToRightTextStyle,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Text,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "time_draft",
                ),
    )

    OutlinedButton(
        onClick =
            callbacks.onAddTime,
        enabled = enabled,
        modifier =
            Modifier
                .padding(
                    top = 8.dp,
                )
                .fillMaxWidth()
                .testTag(
                    "add_time",
                ),
    ) {
        Text(
            text =
                stringResource(
                    R.string.add_time,
                ),
        )
    }
}

@Composable
private fun IntervalEditor(
    state: ScheduleFormUiState,
    callbacks: ScheduleFormCallbacks,
    enabled: Boolean,
    leftToRightTextStyle:
    androidx.compose.ui.text.TextStyle,
) {
    Text(
        text =
            stringResource(
                R.string
                    .interval_presets_label,
            ),
        style =
            MaterialTheme
                .typography
                .labelLarge,
        modifier =
            Modifier.carePackHeading(),
    )

    Spacer(
        modifier =
            Modifier.height(
                8.dp,
            ),
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState(),
                ),
        horizontalArrangement =
            Arrangement.spacedBy(
                8.dp,
            ),
    ) {
        SchedulePatternRules
            .allowedIntervalHours
            .sorted()
            .forEach { hours ->
                FilterChip(
                    selected =
                        state.intervalHours ==
                                hours,
                    onClick = {
                        callbacks
                            .onIntervalHoursSelected(
                                hours,
                            )
                    },
                    enabled = enabled,
                    label = {
                        Text(
                            text =
                                stringResource(
                                    when (hours) {
                                        6 ->
                                            R.string
                                                .every_6_hours

                                        8 ->
                                            R.string
                                                .every_8_hours

                                        else ->
                                            R.string
                                                .every_12_hours
                                    },
                                ),
                        )
                    },
                    modifier =
                        Modifier.testTag(
                            "interval_${hours}_hours",
                        ),
                )
            }
    }

    Spacer(
        modifier =
            Modifier.height(
                12.dp,
            ),
    )

    OutlinedTextField(
        value =
            state.intervalAnchorDraft,
        onValueChange =
            callbacks.onIntervalAnchorChanged,
        enabled = enabled,
        label = {
            Text(
                text =
                    stringResource(
                        R.string
                            .first_dose_time_label,
                    ),
            )
        },
        textStyle =
            leftToRightTextStyle,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Text,
            ),
        isError =
            state.errors
                .containsKey(
                    CarePlanField.TIMES,
                ),
        supportingText = {
            state.errors[
                CarePlanField.TIMES
            ]?.let { errorMessage ->
                Text(
                    text =
                        errorMessage,
                    modifier =
                        Modifier
                            .carePackPoliteLiveRegion(),
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "interval_anchor_time",
                ),
    )
}

@Composable
private fun SchedulePreviewCard(
    state: ScheduleFormUiState,
    previewAnchorDate: LocalDate,
) {
    val previewItems =
        state.previewItems(
            anchorDate =
                previewAnchorDate,
        )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "schedule_preview",
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
                    stringResource(
                        R.string
                            .schedule_preview_label,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.carePackHeading(),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .schedule_preview_zone,
                        state.zoneId,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
                        .copy(
                            textDirection =
                                TextDirection.Ltr,
                        ),
            )

            if (previewItems.isEmpty()) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .schedule_preview_empty,
                        ),
                    modifier =
                        Modifier.testTag(
                            "schedule_preview_empty",
                        ),
                )
            } else {
                previewItems
                    .forEachIndexed {
                            index,
                            item ->
                        val weekday =
                            stringResource(
                                weekdayPersianNameResource(
                                    item.dayOfWeek,
                                ),
                            )

                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .schedule_preview_item,
                                    JalaliPresentationDate
                                        .from(
                                            item.localDate,
                                        )
                                        .formatNumeric(),
                                    weekday,
                                    item.minuteOfDay
                                        .toHourMinuteText(),
                                ),
                            modifier =
                                Modifier.testTag(
                                    "schedule_preview_item_$index",
                                ),
                        )
                    }
            }
        }
    }
}

@Composable
private fun FormErrorText(
    message: String,
    testTag: String? = null,
) {
    val modifier =
        if (testTag == null) {
            Modifier
                .padding(
                    top = 4.dp,
                )
                .carePackPoliteLiveRegion()
        } else {
            Modifier
                .padding(
                    top = 4.dp,
                )
                .carePackPoliteLiveRegion()
                .testTag(
                    testTag,
                )
        }

    Text(
        text =
            message,
        color =
            MaterialTheme
                .colorScheme
                .error,
        style =
            MaterialTheme
                .typography
                .bodySmall,
        modifier =
            modifier,
    )
}

private fun orderedWeekdays(
    firstDayOfWeek: DayOfWeek,
): List<DayOfWeek> {
    val days =
        DayOfWeek.entries

    val startIndex =
        days
            .indexOf(
                firstDayOfWeek,
            )
            .coerceAtLeast(
                0,
            )

    return days.drop(
        startIndex,
    ) +
            days.take(
                startIndex,
            )
}

@StringRes
internal fun weekdayPersianNameResource(
    dayOfWeek: DayOfWeek,
): Int =
    when (dayOfWeek) {
        DayOfWeek.SATURDAY ->
            R.string.saturday

        DayOfWeek.SUNDAY ->
            R.string.sunday

        DayOfWeek.MONDAY ->
            R.string.monday

        DayOfWeek.TUESDAY ->
            R.string.tuesday

        DayOfWeek.WEDNESDAY ->
            R.string.wednesday

        DayOfWeek.THURSDAY ->
            R.string.thursday

        DayOfWeek.FRIDAY ->
            R.string.friday
    }
