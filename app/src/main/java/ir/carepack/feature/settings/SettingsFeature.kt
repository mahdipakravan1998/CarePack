package ir.carepack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.carepack.R
import ir.carepack.ui.accessibility.carePackHeading

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onOpenTodayReport: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDeleteAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "settings_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        contentPadding,
                    )
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal = 20.dp,
                        vertical = 16.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier =
                    Modifier.testTag(
                        "settings_back",
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.back,
                        ),
                )
            }

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_settings_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "settings_title",
                        ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_settings_description,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            SettingsAction(
                title =
                    stringResource(
                        R.string
                            .carepack_settings_reminders,
                    ),
                description =
                    stringResource(
                        R.string
                            .carepack_settings_reminders_description,
                    ),
                testTag =
                    "settings_reminders",
                onClick =
                    onOpenReminderSettings,
            )

            SettingsAction(
                title =
                    stringResource(
                        R.string
                            .carepack_settings_today_report,
                    ),
                description =
                    stringResource(
                        R.string
                            .carepack_settings_today_report_description,
                    ),
                testTag =
                    "settings_today_report",
                onClick =
                    onOpenTodayReport,
            )

            SettingsAction(
                title =
                    stringResource(
                        R.string
                            .carepack_settings_privacy,
                    ),
                description =
                    stringResource(
                        R.string
                            .carepack_settings_privacy_description,
                    ),
                testTag =
                    "settings_privacy",
                onClick =
                    onOpenPrivacy,
            )

            SettingsAction(
                title =
                    stringResource(
                        R.string
                            .carepack_settings_delete_all,
                    ),
                description =
                    stringResource(
                        R.string
                            .carepack_settings_delete_all_description,
                    ),
                testTag =
                    "settings_delete_all",
                onClick =
                    onDeleteAllData,
                destructive = true,
            )
        }
    }
}

@Composable
private fun SettingsAction(
    title: String,
    description: String,
    testTag: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp,
            ),
    ) {
        Text(
            text = title,
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
        )

        Text(
            text = description,
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
        )

        if (destructive) {
            OutlinedButton(
                onClick = onClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            testTag,
                        ),
            ) {
                Text(
                    text = title,
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                )
            }
        } else {
            Button(
                onClick = onClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            testTag,
                        ),
            ) {
                Text(
                    text = title,
                )
            }
        }
    }
}
