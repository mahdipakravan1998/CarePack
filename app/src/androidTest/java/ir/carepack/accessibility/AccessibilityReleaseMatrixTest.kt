package ir.carepack.accessibility

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.carepack.feature.deletion.MedicationDeletionScreen
import ir.carepack.feature.deletion.MedicationDeletionUiState
import ir.carepack.settings.deletion.MedicationDeletionPreview
import org.junit.Rule
import org.junit.Test

class AccessibilityReleaseMatrixTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun destructiveScreen_remainsOperableAtTwoHundredPercentFontRtlAndSmallWidth() {
        composeRule.setContent {
            val density = LocalDensity.current

            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides
                    Density(
                        density = density.density,
                        fontScale = 2.0f,
                    ),
            ) {
                MaterialTheme {
                    Box(
                        modifier = Modifier.width(320.dp),
                    ) {
                        MedicationDeletionScreen(
                            state =
                                MedicationDeletionUiState(
                                    isLoading = false,
                                    preview = preview(),
                                    acknowledged = true,
                                ),
                            onAcknowledgedChange = {},
                            onDelete = {},
                            onRetryPreview = {},
                            onRetryDeletion = {},
                            onBack = {},
                        )
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag("medication_deletion_screen")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag("medication_deletion_title")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag("medication_deletion_acknowledgement")

            .assertIsEnabled()
            .assertHasClickAction()

        composeRule
            .onNodeWithTag("medication_deletion_confirm")
            .performScrollTo()
            .assertIsEnabled()
            .assertHasClickAction()

        composeRule
            .onNode(
                hasTestTag("medication_deletion_confirm") and
                    hasAnyAncestor(
                        hasTestTag("medication_deletion_screen"),
                    ),
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    private fun preview(): MedicationDeletionPreview =
        MedicationDeletionPreview(
            medicationId = "medication-accessibility",
            medicationName = "داروی آزمون دسترس‌پذیری",
            medicationUpdatedAtEpochMillis = 1_750_752_000_000L,
            scheduleSeriesCount = 2,
            scheduleVersionCount = 3,
            scheduleTimeCount = 4,
            occurrenceCount = 35,
            caregiverReportCount = 7,
        )
}
