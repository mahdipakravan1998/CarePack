package ir.carepack.feature.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.carepack.R
import ir.carepack.domain.reminder.TimezoneWarning
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion

@Composable
fun TimezoneWarningBanner(
    warning: TimezoneWarning,
    errorMessage: String?,
    onReviewSchedules: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .carePackPoliteLiveRegion().testTag("timezone_warning_banner"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.timezone_warning_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.carePackHeading(),
            )
            Text(
                text = stringResource(
                    R.string.timezone_warning_body,
                    warning.previousZoneId,
                    warning.currentZoneId,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("timezone_warning_error"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onReviewSchedules,
                    modifier = Modifier.carePackInteractiveControl()
                        .testTag("timezone_warning_review"),
                ) {
                    Text(stringResource(R.string.review_schedules))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.carePackInteractiveControl()
                        .testTag("timezone_warning_dismiss"),
                ) {
                    Text(stringResource(R.string.timezone_warning_acknowledge))
                }
            }
        }
    }
}
