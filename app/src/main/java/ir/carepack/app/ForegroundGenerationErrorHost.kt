package ir.carepack.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun ForegroundGenerationErrorHost(
    errorMessage: String?,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
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
                        .padding(16.dp)
                        .testTag(
                            "foreground_generation_error",
                        ),
                action = {
                    TextButton(
                        onClick = onRetry,
                        modifier =
                            Modifier.testTag(
                                "foreground_generation_retry",
                            ),
                    ) {
                        Text(
                            text = "تلاش دوباره",
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
