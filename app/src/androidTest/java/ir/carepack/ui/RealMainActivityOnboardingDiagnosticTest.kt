package ir.carepack.ui

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.CarePackApplication
import ir.carepack.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealMainActivityOnboardingDiagnosticTest {

    @get:Rule
    val composeRule =
        createAndroidComposeRule<MainActivity>()

    private val application:
            CarePackApplication
        get() =
            ApplicationProvider
                .getApplicationContext()

    @Test
    fun realMainActivity_firstCarePlanSaveEitherCompletesOrReportsPersistentState() {
        waitForTag(
            tag = "onboarding_continue",
            timeoutMillis = STARTUP_TIMEOUT_MILLIS,
        )

        composeRule
            .onNodeWithTag(
                "onboarding_continue",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        waitForTag(
            tag = "recipient_setup_screen",
        )

        composeRule
            .onNodeWithTag(
                "recipient_name",
            )
            .performTextInput(
                "م",
            )

        composeRule
            .onNodeWithTag(
                "recipient_save",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        waitForTag(
            tag =
                "first_setup_reminder_guidance_continue",
        )

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance_continue",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        waitForTag(
            tag = "medication_schedule_screen",
        )

        composeRule
            .onNodeWithTag(
                "medication_name",
            )
            .performTextInput(
                "م",
            )

        composeRule
            .onNodeWithTag(
                "medication_instruction",
            )
            .performTextInput(
                "م",
            )

        composeRule
            .onNodeWithTag(
                "time_draft",
            )
            .performScrollTo()
            .performTextInput(
                "12:00",
            )

        composeRule
            .onNodeWithTag(
                "add_time",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule
            .onNodeWithTag(
                "save_medication_schedule",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        val outcome =
            waitForSaveOutcome()

        if (outcome != SaveOutcome.POST_SETUP_SUGGESTION) {
            failWithDiagnostic(
                reason =
                    "Fresh first-care-plan completion must visibly present the post-setup Simple Mode suggestion before Today. Outcome=$outcome",
            )
        }

        composeRule
            .onNodeWithTag(
                "post_setup_simple_mode_suggestion",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "post_setup_enable_simple_mode",
            )
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        waitForTag(
            tag = "today_screen",
            timeoutMillis =
                SAVE_TIMEOUT_MILLIS,
        )

        composeRule
            .onNodeWithTag(
                "today_screen",
            )
            .assertIsDisplayed()
    }

    private fun waitForSaveOutcome():
            SaveOutcome {
        val deadline =
            SystemClock.elapsedRealtime() +
                    SAVE_TIMEOUT_MILLIS

        while (
            SystemClock.elapsedRealtime() <
            deadline
        ) {
            composeRule.waitForIdle()

            when {
                hasTag(
                    "post_setup_simple_mode_suggestion",
                ) -> {
                    return SaveOutcome
                        .POST_SETUP_SUGGESTION
                }

                hasTag(
                    "today_screen",
                ) -> {
                    return SaveOutcome.TODAY
                }

                hasTag(
                    "medication_schedule_error",
                ) -> {
                    return SaveOutcome.VISIBLE_ERROR
                }
            }

            SystemClock.sleep(
                POLL_INTERVAL_MILLIS,
            )
        }

        return SaveOutcome.TIMEOUT
    }

    private fun waitForTag(
        tag: String,
        timeoutMillis: Long =
            DEFAULT_WAIT_TIMEOUT_MILLIS,
    ) {
        try {
            composeRule.waitUntil(
                timeoutMillis =
                    timeoutMillis,
            ) {
                hasTag(tag)
            }
        } catch (failure: AssertionError) {
            failWithDiagnostic(
                reason =
                    "Required UI tag did not appear: $tag",
                cause = failure,
            )
        }
    }

    private fun hasTag(
        tag: String,
    ): Boolean =
        composeRule
            .onAllNodesWithTag(
                testTag = tag,
            )
            .fetchSemanticsNodes(
                atLeastOneRootRequired =
                    false,
            )
            .isNotEmpty()

    private fun failWithDiagnostic(
        reason: String,
        cause: Throwable? = null,
    ): Nothing {
        val persistentState =
            runBlocking {
                withTimeoutOrNull(
                    DIAGNOSTIC_QUERY_TIMEOUT_MILLIS,
                ) {
                    val container =
                        application.container

                    val database =
                        container.database

                    buildString {
                        appendLine(
                            "recipientCount=" +
                                    database
                                        .careRecipientDao()
                                        .count(),
                        )

                        appendLine(
                            "medicationCount=" +
                                    database
                                        .medicationDao()
                                        .count(),
                        )

                        appendLine(
                            "scheduleSeriesCount=" +
                                    database
                                        .scheduleDao()
                                        .countSeries(),
                        )

                        appendLine(
                            "scheduleVersionCount=" +
                                    database
                                        .scheduleDao()
                                        .countVersions(),
                        )

                        appendLine(
                            "scheduleTimeCount=" +
                                    database
                                        .scheduleDao()
                                        .countTimes(),
                        )

                        appendLine(
                            "occurrenceCount=" +
                                    database
                                        .occurrenceDao()
                                        .count(),
                        )

                        appendLine(
                            "setupComplete=" +
                                    container
                                        .setupPreferenceStore
                                        .setupComplete
                                        .first(),
                        )

                        appendLine(
                            "seniorMode=" +
                                    container
                                        .userExperiencePreferenceStore
                                        .state
                                        .first()
                                        .seniorMode,
                        )
                    }
                } ?: "Persistent-state diagnostic timed out."
            }

        val visibleTags =
            DIAGNOSTIC_TAGS
                .filter(
                    ::hasTag,
                )
                .joinToString(
                    separator = ", ",
                )
                .ifBlank {
                    "<none>"
                }

        val semanticsTree =
            runCatching {
                composeRule
                    .onRoot(
                        useUnmergedTree = true,
                    )
                    .printToString(
                        maxDepth = 12,
                    )
            }.getOrElse { throwable ->
                "Unable to print semantics tree: " +
                        throwable::class.java.name
            }

        val message =
            buildString {
                appendLine(
                    "REAL MAINACTIVITY ONBOARDING DIAGNOSTIC FAILED",
                )
                appendLine()
                appendLine(
                    "Reason:",
                )
                appendLine(reason)
                appendLine()
                appendLine(
                    "Visible diagnostic tags:",
                )
                appendLine(visibleTags)
                appendLine()
                appendLine(
                    "Persistent state:",
                )
                appendLine(persistentState)
                appendLine(
                    "Semantics tree:",
                )
                appendLine(semanticsTree)
            }

        throw AssertionError(
            message,
            cause,
        )
    }

    private enum class SaveOutcome {
        POST_SETUP_SUGGESTION,
        TODAY,
        VISIBLE_ERROR,
        TIMEOUT,
    }

    private companion object {
        const val STARTUP_TIMEOUT_MILLIS =
            30_000L

        const val DEFAULT_WAIT_TIMEOUT_MILLIS =
            15_000L

        const val SAVE_TIMEOUT_MILLIS =
            30_000L

        const val DIAGNOSTIC_QUERY_TIMEOUT_MILLIS =
            5_000L

        const val POLL_INTERVAL_MILLIS =
            100L

        val DIAGNOSTIC_TAGS =
            listOf(
                "onboarding_screen",
                "recipient_setup_screen",
                "first_setup_reminder_guidance",
                "medication_schedule_screen",
                "save_medication_schedule",
                "medication_schedule_error",
                "post_setup_simple_mode_suggestion",
                "today_screen",
                "primary_navigation",
                "foreground_generation_error",
            )
    }
}
