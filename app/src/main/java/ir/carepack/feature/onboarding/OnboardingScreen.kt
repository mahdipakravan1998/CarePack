package ir.carepack.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
fun OnboardingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "onboarding_screen",
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
                    .navigationBarsPadding()
                    .padding(
                        24.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.onboarding_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineLarge,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "onboarding_title",
                        ),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .onboarding_local_summary,
                        ),
                    modifier =
                        Modifier
                            .padding(
                                20.dp,
                            )
                            .testTag(
                                "onboarding_local_summary",
                            ),
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge,
                )
            }

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .onboarding_non_medical_summary,
                        ),
                    modifier =
                        Modifier
                            .padding(
                                20.dp,
                            )
                            .testTag(
                                "onboarding_non_medical_summary",
                            ),
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            Button(
                onClick = onContinue,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "onboarding_continue",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .onboarding_continue,
                        ),
                )
            }
        }
    }
}
