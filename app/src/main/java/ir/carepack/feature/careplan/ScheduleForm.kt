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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.carepack.R
import ir.carepack.domain.careplan.CarePlanField
import java.time.DayOfWeek

data class ScheduleFormCallbacks(
    val onWeekdayToggled: (DayOfWeek) -> Unit,
    val onTimeDraftChanged: (String) -> Unit,
    val onAddTime: () -> Unit,
    val onRemoveTime: (Int) -> Unit,
    val onStartDateChanged: (String) -> Unit,
    val onEndDateChanged: (String) -> Unit,
)

@Composable
fun ScheduleFormFields(
    state: ScheduleFormUiState,
    callbacks: ScheduleFormCallbacks,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.weekday_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayOfWeek.entries.forEach { dayOfWeek ->
                FilterChip(
                    selected = dayOfWeek in state.weekdays,
                    onClick = { callbacks.onWeekdayToggled(dayOfWeek) },
                    enabled = enabled,
                    label = {
                        Text(stringResource(weekdayPersianNameResource(dayOfWeek)))
                    },
                    modifier = Modifier.testTag("weekday_${dayOfWeek.name}"),
                )
            }
        }
        state.errors[CarePlanField.WEEKDAYS]?.let { message ->
            FormErrorText(message)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.selected_times_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))

        state.minutesOfDay.forEach { minuteOfDay ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = minuteOfDay.toHourMinuteText(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(
                    onClick = { callbacks.onRemoveTime(minuteOfDay) },
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.remove_time))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.timeDraft,
                onValueChange = callbacks.onTimeDraftChanged,
                enabled = enabled,
                label = { Text(stringResource(R.string.time_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("schedule_time_draft"),
            )
            OutlinedButton(
                onClick = callbacks.onAddTime,
                enabled = enabled,
                modifier = Modifier.testTag("schedule_time_add"),
            ) {
                Text(stringResource(R.string.add_time))
            }
        }
        state.errors[CarePlanField.TIMES]?.let { message ->
            FormErrorText(message = message, testTag = "schedule_times_error")
        }

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.startDateText,
            onValueChange = callbacks.onStartDateChanged,
            enabled = enabled,
            label = { Text(stringResource(R.string.start_date_label)) },
            placeholder = { Text("2026-06-24") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            isError = state.errors.containsKey(CarePlanField.START_DATE),
            supportingText = {
                state.errors[CarePlanField.START_DATE]?.let { Text(it) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("schedule_start_date"),
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.endDateText,
            onValueChange = callbacks.onEndDateChanged,
            enabled = enabled,
            label = { Text(stringResource(R.string.end_date_label)) },
            placeholder = { Text("2026-07-24") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            isError = state.errors.containsKey(CarePlanField.END_DATE),
            supportingText = {
                state.errors[CarePlanField.END_DATE]?.let { Text(it) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("schedule_end_date"),
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.zoneId,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.fixed_zone_label)) },
            isError = state.errors.containsKey(CarePlanField.ZONE_ID),
            supportingText = {
                state.errors[CarePlanField.ZONE_ID]?.let { Text(it) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("schedule_zone"),
        )
    }
}

@Composable
private fun FormErrorText(
    message: String,
    testTag: String? = null,
) {
    val modifier = if (testTag == null) {
        Modifier.padding(top = 4.dp)
    } else {
        Modifier
            .padding(top = 4.dp)
            .testTag(testTag)
    }

    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

@StringRes
internal fun weekdayPersianNameResource(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.SATURDAY -> R.string.saturday
    DayOfWeek.SUNDAY -> R.string.sunday
    DayOfWeek.MONDAY -> R.string.monday
    DayOfWeek.TUESDAY -> R.string.tuesday
    DayOfWeek.WEDNESDAY -> R.string.wednesday
    DayOfWeek.THURSDAY -> R.string.thursday
    DayOfWeek.FRIDAY -> R.string.friday
}
