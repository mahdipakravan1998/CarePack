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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ir.carepack.R
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    onOpenPrivacy: () -> Unit,
    simpleModeEnabled: Boolean,
    isSavingSimpleMode: Boolean = false,
    simpleModeErrorMessage: String? = null,
    onEnableSimpleMode: () -> Unit,
    onKeepStandardMode: () -> Unit,
    onRetrySimpleModeSelection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val experience = carePackExperience()

    Scaffold(
        modifier = modifier
                .fillMaxSize().testTag(
                    "onboarding_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                    .fillMaxSize().padding(
                        contentPadding,
                    ).verticalScroll(
                        rememberScrollState(),
                    ).navigationBarsPadding()
                    .padding(
                        horizontal = experience.screenHorizontalPadding,
                        vertical = experience.screenVerticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.onboarding_title,
                    ),
                style = MaterialTheme
                        .typography.headlineLarge,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "onboarding_title",
                        ),
            )

            Spacer(
                modifier = Modifier.height(
                        experience.compactSpacing,
                    ),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                            R.string.onboarding_local_summary,
                        ),
                    modifier = Modifier
                            .padding(
                                experience.screenHorizontalPadding,
                            ).testTag(
                                "onboarding_local_summary",
                            ),
                    style = MaterialTheme
                            .typography.bodyLarge,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                            R.string.onboarding_non_medical_summary,
                        ),
                    modifier = Modifier
                            .padding(
                                experience.screenHorizontalPadding,
                            ).testTag(
                                "onboarding_non_medical_summary",
                            ),
                    style = MaterialTheme
                            .typography.bodyLarge,
                )
            }

            TextButton(
                onClick = onOpenPrivacy,
                modifier = Modifier
                        .fillMaxWidth().carePackInteractiveControl()
                        .testTag(
                            "onboarding_open_privacy",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.onboarding_privacy_action,
                        ),
                )
            }

            Card(
                modifier = Modifier
                        .fillMaxWidth().testTag(
                            "onboarding_simple_mode_card",
                        ),
            ) {
                Column(
                    modifier = Modifier.padding(
                            experience.screenHorizontalPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(
                            experience.itemSpacing,
                        ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.onboarding_simple_mode_title,
                            ),
                        style = MaterialTheme
                                .typography.titleMedium,
                        modifier = Modifier.testTag(
                                "onboarding_simple_mode_title",
                            ),
                    )

                    Text(
                        text = stringResource(
                                R.string.onboarding_simple_mode_summary,
                            ),
                        style = MaterialTheme
                                .typography.bodyLarge,
                        modifier = Modifier.testTag(
                                "onboarding_simple_mode_summary",
                            ),
                    )

                    Text(
                        text = stringResource(
                                if (simpleModeEnabled) {
                                    R.string.onboarding_simple_mode_enabled
                                } else {
                                    R.string.onboarding_simple_mode_standard
                                },
                            ),
                        style = MaterialTheme
                                .typography.bodyMedium,
                        modifier = Modifier.testTag(
                                "onboarding_simple_mode_status",
                            ),
                    )

                    if (isSavingSimpleMode) {
                        Text(
                            text = "در حال ذخیره انتخاب نمایش…",
                            modifier = Modifier
                                    .carePackPoliteLiveRegion().testTag(
                                        "onboarding_simple_mode_saving",
                                    ),
                        )
                    }

                    simpleModeErrorMessage?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                    .carePackPoliteLiveRegion().testTag(
                                        "onboarding_simple_mode_save_error",
                                    ),
                        )

                        TextButton(
                            onClick = onRetrySimpleModeSelection,
                            modifier = Modifier
                                    .fillMaxWidth().carePackInteractiveControl()
                                    .testTag("onboarding_simple_mode_retry"),
                        ) {
                            Text(text = "تلاش دوباره")
                        }
                    }

                    Button(
                        onClick = onEnableSimpleMode,
                        enabled = !isSavingSimpleMode,
                        modifier = Modifier
                                .fillMaxWidth().carePackPrimaryAction()
                                .testTag(
                                    "onboarding_simple_mode_enable",
                                ),
                    ) {
                        Text(
                            text = stringResource(
                                    R.string.onboarding_simple_mode_enable,
                                ),
                        )
                    }

                    OutlinedButton(
                        onClick = onKeepStandardMode,
                        enabled = !isSavingSimpleMode,
                        modifier = Modifier
                                .fillMaxWidth().carePackInteractiveControl()
                                .testTag(
                                    "onboarding_simple_mode_defer",
                                ),
                    ) {
                        Text(
                            text = stringResource(
                                    R.string.onboarding_simple_mode_defer,
                                ),
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                        experience.compactSpacing,
                    ),
            )

            Button(
                onClick = onContinue,
                modifier = Modifier
                        .fillMaxWidth().carePackPrimaryAction()
                        .testTag(
                            "onboarding_continue",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.onboarding_continue,
                        ),
                )
            }
        }
    }
}
