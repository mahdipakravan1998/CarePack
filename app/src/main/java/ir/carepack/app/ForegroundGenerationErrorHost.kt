package ir.carepack.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ir.carepack.R
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.experience.carePackExperience

@Composable
fun ForegroundGenerationErrorHost(
    errorMessage: String?,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    val experience =
        carePackExperience()

    Box(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        content()

        if (errorMessage != null) {
            Snackbar(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        )
                        .navigationBarsPadding()
                        .padding(
                            horizontal =
                                experience
                                    .screenHorizontalPadding,
                            vertical =
                                experience
                                    .compactSpacing,
                        )
                        .carePackPoliteLiveRegion()
                        .testTag(
                            "foreground_generation_error",
                        ),
                action = {
                    TextButton(
                        onClick = onRetry,
                        modifier =
                            Modifier
                                .carePackInteractiveControl()
                                .testTag(
                                    "foreground_generation_retry",
                                ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.retry_action,
                                ),
                        )
                    }
                },
            ) {
                Text(
                    text = errorMessage,
                )
            }
        }
    }
}
