package ir.carepack.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.feature.deletion.MedicationDeletionScreen
import ir.carepack.feature.deletion.MedicationDeletionUiState
import ir.carepack.settings.deletion.MedicationDeletionPreview
import ir.carepack.ui.theme.CarePackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationDeletionComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun destructiveActionRequiresExplicitAcknowledgement() {
        var deletionState by
        mutableStateOf(
            readyState(),
        )

        var deleteCount = 0

        composeRule.setContent {
            CarePackTheme {
                MedicationDeletionScreen(
                    state = deletionState,
                    onAcknowledgedChange = {
                        deletionState =
                            deletionState.copy(
                                acknowledged = it,
                            )
                    },
                    onDelete = {
                        deleteCount += 1
                    },
                    onRetryPreview = {},
                    onRetryDeletion = {},
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "medication_deletion_confirm",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag(
                "medication_deletion_acknowledgement",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithTag(
                "medication_deletion_confirm",
            )
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(1, deleteCount)
    }

    @Test
    fun previewShowsRealImpactAndChangedPreviewWarning() {
        composeRule.setContent {
            CarePackTheme {
                MedicationDeletionScreen(
                    state =
                        readyState().copy(
                            changedSincePreview = true,
                        ),
                    onAcknowledgedChange = {},
                    onDelete = {},
                    onRetryPreview = {},
                    onRetryDeletion = {},
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "medication_deletion_preview",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "medication_deletion_name",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "medication_deletion_changed",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "medication_deletion_external_limit",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun repeatedSubmissionIsDisabledDuringDeletion() {
        composeRule.setContent {
            CarePackTheme {
                MedicationDeletionScreen(
                    state =
                        readyState().copy(
                            acknowledged = true,
                            isDeleting = true,
                        ),
                    onAcknowledgedChange = {},
                    onDelete = {},
                    onRetryPreview = {},
                    onRetryDeletion = {},
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "medication_deletion_progress",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "medication_deletion_back",
            )
            .assertIsNotEnabled()
    }

    @Test
    fun simpleModeAtLargeFontKeepsAcknowledgementAndDeleteActionReachable() {
        composeRule.setContent {
            CarePackTheme(
                seniorMode = SeniorMode.SIMPLE,
            ) {
                val density =
                    LocalDensity.current

                CompositionLocalProvider(
                    LocalDensity provides
                            Density(
                                density = density.density,
                                fontScale = 2f,
                            ),
                ) {
                    MedicationDeletionScreen(
                        state =
                            readyState().copy(
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

        composeRule
            .onNodeWithTag(
                "medication_deletion_acknowledgement",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "medication_deletion_confirm",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    private fun readyState():
            MedicationDeletionUiState =
        MedicationDeletionUiState(
            isLoading = false,
            preview = PREVIEW,
            acknowledged = false,
            isDeleting = false,
            changedSincePreview = false,
            previewLoadFailed = false,
            medicationNotFound = false,
            deletionCompleted = false,
        )

    private companion object {
        val PREVIEW =
            MedicationDeletionPreview(
                medicationId =
                    "medication-delete-ui",
                medicationName =
                    "داروی فشار",
                medicationUpdatedAtEpochMillis =
                    1_750_752_000_000L,
                scheduleSeriesCount = 2,
                scheduleVersionCount = 3,
                scheduleTimeCount = 4,
                occurrenceCount = 12,
                caregiverReportCount = 8,
            )
    }
}
