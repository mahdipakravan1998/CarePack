package ir.carepack.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalStatus
import ir.carepack.domain.model.TodayItem
import ir.carepack.feature.today.TodayScreen
import ir.carepack.feature.today.TodaySection
import ir.carepack.feature.today.TodayUiState
import ir.carepack.ui.theme.CarePackTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeniorModeTodayComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun seniorMode_rendersEveryTodayItemWithSeniorControls() {
        renderSeniorToday()

        listOf(
            MORNING_ID,
            EVENING_ID,
        ).forEach { occurrenceId ->
            assertVisibleAfterScroll(
                "simple_today_card_$occurrenceId",
            )

            assertActionVisibleAndClickable(
                "simple_today_given_$occurrenceId",
            )

            assertActionVisibleAndClickable(
                "simple_today_remind_later_$occurrenceId",
            )

            assertActionVisibleAndClickable(
                "simple_today_not_given_$occurrenceId",
            )

            assertActionVisibleAndClickable(
                "simple_today_unknown_$occurrenceId",
            )

            assertActionVisibleAndClickable(
                "simple_today_details_$occurrenceId",
            )
        }
    }

    @Test
    fun seniorMode_nonPrimaryItemActionsCallCorrectCallbacks() {
        val actions =
            mutableListOf<String>()

        renderSeniorToday(
            onGiven = { occurrenceId ->
                actions +=
                    "given:$occurrenceId"
            },
            onRemindLater = { occurrenceId ->
                actions +=
                    "remind-later:$occurrenceId"
            },
            onNotGiven = { occurrenceId ->
                actions +=
                    "not-given:$occurrenceId"
            },
            onUnknown = { occurrenceId ->
                actions +=
                    "unknown:$occurrenceId"
            },
        )

        clickAfterScroll(
            "simple_today_given_$EVENING_ID",
        )

        clickAfterScroll(
            "simple_today_remind_later_$EVENING_ID",
        )

        clickAfterScroll(
            "simple_today_not_given_$EVENING_ID",
        )

        clickAfterScroll(
            "simple_today_unknown_$EVENING_ID",
        )

        assertEquals(
            listOf(
                "given:$EVENING_ID",
                "remind-later:$EVENING_ID",
                "not-given:$EVENING_ID",
                "unknown:$EVENING_ID",
            ),
            actions,
        )
    }

    private fun renderSeniorToday(
        onGiven: (String) -> Unit = {},
        onNotGiven: (String) -> Unit = {},
        onUnknown: (String) -> Unit = {},
        onRemindLater: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            CarePackTheme {
                TodayScreen(
                    state =
                        TodayUiState(
                            localDate =
                                TEST_DATE,
                            selectedSection =
                                TodaySection.TODAY,
                            isLoading =
                                false,
                            items =
                                listOf(
                                    todayItem(
                                        id =
                                            MORNING_ID,
                                        localTime =
                                            LocalTime.of(
                                                8,
                                                0,
                                            ),
                                        phase =
                                            TemporalStatus.DUE,
                                    ),
                                    todayItem(
                                        id =
                                            EVENING_ID,
                                        localTime =
                                            LocalTime.of(
                                                20,
                                                0,
                                            ),
                                        phase =
                                            TemporalStatus.UPCOMING,
                                    ),
                                ),
                            emptyState =
                                null,
                            isHistoryLoading =
                                false,
                            seniorMode =
                                SeniorMode.SIMPLE,
                        ),
                    onTodaySelected = {},
                    onHistorySelected = {},
                    onRetry = {},
                    onOpenCarePlan = {},
                    onOpenSettings = {},
                    onOpenOccurrence = {},
                    onGiven = onGiven,
                    onNotGiven = onNotGiven,
                    onUnknown = onUnknown,
                    onRemindLater =
                        onRemindLater,
                )
            }
        }

        composeRule.waitForIdle()
    }

    private fun assertActionVisibleAndClickable(
        tag: String,
    ) {
        assertVisibleAfterScroll(
            tag = tag,
        )

        composeRule
            .onNodeWithTag(
                testTag =
                    tag,
                useUnmergedTree =
                    true,
            )
            .assertHasClickAction()
    }

    private fun clickAfterScroll(
        tag: String,
    ) {
        assertActionVisibleAndClickable(
            tag = tag,
        )

        composeRule
            .onNodeWithTag(
                testTag =
                    tag,
                useUnmergedTree =
                    true,
            )
            .performClick()
    }

    private fun assertVisibleAfterScroll(
        tag: String,
    ) {
        composeRule
            .onNodeWithTag(
                testTag =
                    "today_content",
            )
            .performScrollToNode(
                matcher =
                    hasTestTag(
                        tag,
                    ),
            )

        composeRule
            .onNodeWithTag(
                testTag =
                    tag,
                useUnmergedTree =
                    true,
            )
            .assertIsDisplayed()
    }

    private fun todayItem(
        id: String,
        localTime: LocalTime,
        phase: TemporalStatus,
    ): TodayItem =
        TodayItem(
            occurrenceId =
                id,
            localDate =
                TEST_DATE,
            localTime =
                localTime,
            medicationName =
                "داروی $id",
            medicationInstruction =
                "دستور نمونه",
            lifecycle =
                OccurrenceLifecycle.ACTIVE,
            reportState =
                null,
            scheduledAt =
                TEST_DATE
                    .atTime(
                        localTime,
                    )
                    .toInstant(
                        ZoneOffset.UTC,
                    ),
            temporalStatus =
                phase,
            isOverdue =
                phase == TemporalStatus.PAST,
        )

    private companion object {
        const val MORNING_ID =
            "occurrence-morning"

        const val EVENING_ID =
            "occurrence-evening"

        val TEST_DATE: LocalDate =
            LocalDate.of(
                2026,
                6,
                24,
            )
    }
}
