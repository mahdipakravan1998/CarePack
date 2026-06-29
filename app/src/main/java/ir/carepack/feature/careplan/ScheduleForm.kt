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
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.time.DayOfWeek

data class ScheduleFormCallbacks(
    val onWeekdayToggled:
        (DayOfWeek) -> Unit,
    val onTimeDraftChanged:
        (String) -> Unit,
    val onAddTime: () -> Unit,
    val onRemoveTime:
        (Int) -> Unit,
    val onStartDateChanged:
        (String) -> Unit,
    val onEndDateChanged:
        (String) -> Unit,
)

@Composable
fun ScheduleFormFields(
    state: ScheduleFormUiState,
    callbacks:
    ScheduleFormCallbacks,
    enabled: Boolean,
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
            DayOfWeek
                .entries
                .forEach {
                        dayOfWeek ->
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

        state.errors[
            CarePlanField.WEEKDAYS
        ]?.let {
                message ->
            FormErrorText(
                message =
                    message,
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

        state.minutesOfDay
            .forEach {
                    minuteOfDay ->
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
                    Text(
                        text =
                            timeText,
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                                .copy(
                                    textDirection =
                                        TextDirection.Ltr,
                                ),
                        modifier =
                            Modifier
                                .weight(
                                    1f,
                                )
                                .padding(
                                    top = 12.dp,
                                )
                                .testTag(
                                    "schedule_time_$minuteOfDay",
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
                                    "schedule_time_remove_$minuteOfDay",
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

        Column(
            modifier =
                Modifier.fillMaxWidth(),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            OutlinedTextField(
                value =
                    state.timeDraft,
                onValueChange =
                    callbacks
                        .onTimeDraftChanged,
                enabled = enabled,
                label = {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .time_label,
                            ),
                    )
                },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Number,
                    ),
                textStyle =
                    leftToRightTextStyle,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "schedule_time_draft",
                        ),
            )

            OutlinedButton(
                onClick =
                    callbacks.onAddTime,
                enabled = enabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "schedule_time_add",
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

        state.errors[
            CarePlanField.TIMES
        ]?.let {
                message ->
            FormErrorText(
                message =
                    message,
                testTag =
                    "schedule_times_error",
            )
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
                callbacks
                    .onStartDateChanged,
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
            placeholder = {
                Text(
                    text =
                        "2026-06-24",
                    style =
                        leftToRightTextStyle,
                )
            },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Ascii,
                ),
            textStyle =
                leftToRightTextStyle,
            isError =
                state.errors
                    .containsKey(
                        CarePlanField
                            .START_DATE,
                    ),
            supportingText = {
                state.errors[
                    CarePlanField
                        .START_DATE
                ]?.let {
                        errorMessage ->
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
                        "schedule_start_date",
                    ),
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp,
                ),
        )

        OutlinedTextField(
            value =
                state.endDateText,
            onValueChange =
                callbacks
                    .onEndDateChanged,
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
            placeholder = {
                Text(
                    text =
                        "2026-07-24",
                    style =
                        leftToRightTextStyle,
                )
            },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Ascii,
                ),
            textStyle =
                leftToRightTextStyle,
            isError =
                state.errors
                    .containsKey(
                        CarePlanField
                            .END_DATE,
                    ),
            supportingText = {
                state.errors[
                    CarePlanField.END_DATE
                ]?.let {
                        errorMessage ->
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
                        "schedule_end_date",
                    ),
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp,
                ),
        )

        OutlinedTextField(
            value =
                state.zoneId,
            onValueChange = {},
            readOnly = true,
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
            isError =
                state.errors
                    .containsKey(
                        CarePlanField.ZONE_ID,
                    ),
            supportingText = {
                state.errors[
                    CarePlanField.ZONE_ID
                ]?.let {
                        errorMessage ->
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

@StringRes
internal fun weekdayPersianNameResource(
    dayOfWeek: DayOfWeek,
): Int =
    when (dayOfWeek) {
        DayOfWeek.SATURDAY -> {
            R.string.saturday
        }

        DayOfWeek.SUNDAY -> {
            R.string.sunday
        }

        DayOfWeek.MONDAY -> {
            R.string.monday
        }

        DayOfWeek.TUESDAY -> {
            R.string.tuesday
        }

        DayOfWeek.WEDNESDAY -> {
            R.string.wednesday
        }

        DayOfWeek.THURSDAY -> {
            R.string.thursday
        }

        DayOfWeek.FRIDAY -> {
            R.string.friday
        }
    }
