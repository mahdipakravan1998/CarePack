package ir.carepack.feature.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
fun PrivacyRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrivacyScreen(
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "privacy_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        contentPadding,
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
                        "privacy_back",
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
                            .carepack_privacy_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "privacy_title",
                        ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_privacy_minimal_summary,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                modifier =
                    Modifier.testTag(
                        "privacy_summary",
                    ),
            )
        }
    }
}
