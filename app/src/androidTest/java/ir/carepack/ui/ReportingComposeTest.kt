package ir.carepack.ui

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import ir.carepack.R
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.HistoryItem
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalPhase
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.report.ReportChange
import ir.carepack.feature.detail.OccurrenceDetailScreen
import ir.carepack.feature.detail.OccurrenceDetailUiState
import ir.carepack.feature.detail.UndoUiState
import ir.carepack.feature.today.TodayScreen
import ir.carepack.feature.today.TodaySection
import ir.carepack.feature.today.TodayUiState
import ir.carepack.ui.theme.CarePackTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportingComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    private val context: Context
        get() =
            ApplicationProvider
                .getApplicationContext()

    @Test
    fun noReportAndUnknown_haveDifferentVisibleText() {
        val visibleItems =
            mutableStateOf(
                listOf(
                    todayItem(
                        id =
                            "no-report",
                        localTime =
                            LocalTime.of(
                                8,
                                0,
                            ),
                        reportState = null,
                        phase =
                            TemporalPhase
                                .UPCOMING,
                    ),
                ),
            )

        composeRule.setContent {
            CarePackTheme {
                TodayScreen(
                    state =
                        TodayUiState(
                            localDate =
                                TEST_DATE,
                            isLoading = false,
                            items =
                                visibleItems.value,
                            emptyState = null,
                            isHistoryLoading =
                                false,
                        ),
                    onOccurrenceSelected = {},
                    onManageCarePlan = {},
                )
            }
        }

        assertTaggedText(
            tag =
                "today_report_no-report",
            expectedText =
                context.getString(
                    R.string
                        .pr3_report_no_report,
                ),
        )

        assertTaggedText(
            tag =
                "today_phase_no-report",
            expectedText =
                context.getString(
                    R.string
                        .pr3_phase_upcoming,
                ),
        )

        replaceVisibleTodayItem(
            state =
                visibleItems,
            item =
                todayItem(
                    id = "unknown",
                    localTime =
                        LocalTime.of(
                            9,
                            0,
                        ),
                    reportState =
                        CaregiverReportState
                            .UNKNOWN,
                    phase =
                        TemporalPhase.DUE,
                ),
        )

        assertTaggedText(
            tag =
                "today_report_unknown",
            expectedText =
                context.getString(
                    R.string
                        .pr3_report_unknown,
                ),
        )

        assertTaggedText(
            tag =
                "today_phase_unknown",
            expectedText =
                context.getString(
                    R.string
                        .pr3_phase_due,
                ),
        )

        replaceVisibleTodayItem(
            state =
                visibleItems,
            item =
                todayItem(
                    id = "given",
                    localTime =
                        LocalTime.of(
                            10,
                            0,
                        ),
                    reportState =
                        CaregiverReportState
                            .GIVEN,
                    phase =
                        TemporalPhase.PAST,
                ),
        )

        assertTaggedText(
            tag =
                "today_report_given",
            expectedText =
                context.getString(
                    R.string
                        .pr3_report_given,
                ),
        )

        assertTaggedText(
            tag =
                "today_phase_given",
            expectedText =
                context.getString(
                    R.string
                        .pr3_phase_past,
                ),
        )

        replaceVisibleTodayItem(
            state =
                visibleItems,
            item =
                todayItem(
                    id = "not-given",
                    localTime =
                        LocalTime.of(
                            11,
                            0,
                        ),
                    reportState =
                        CaregiverReportState
                            .NOT_GIVEN,
                    phase =
                        TemporalPhase.PAST,
                ),
        )

        assertTaggedText(
            tag =
                "today_report_not-given",
            expectedText =
                context.getString(
                    R.string
                        .pr3_report_not_given,
                ),
        )

        replaceVisibleTodayItem(
            state =
                visibleItems,
            item =
                todayItem(
                    id = "overdue",
                    localTime =
                        LocalTime.of(
                            12,
                            0,
                        ),
                    reportState = null,
                    phase =
                        TemporalPhase.PAST,
                    isOverdue = true,
                ),
        )

        assertTaggedText(
            tag =
                "today_report_overdue",
            expectedText =
                context.getString(
                    R.string
                        .pr3_recording_time_passed,
                ),
        )
    }

    @Test
    fun detail_exposesAllThreeReportActions() {
        var selectedState:
                CaregiverReportState? =
            null

        composeRule.setContent {
            CarePackTheme {
                OccurrenceDetailScreen(
                    state =
                        activeDetailState(
                            reportState =
                                CaregiverReportState
                                    .GIVEN,
                        ),
                    onSetReport = {
                        selectedState = it
                    },
                    onUndo = {},
                    onBack = {},
                )
            }
        }

        assertTagExists(
            tag = "record_given",
        )

        assertTagExists(
            tag = "record_not_given",
        )

        assertTagExists(
            tag = "record_unknown",
        )

        composeRule
            .onNodeWithTag(
                "record_unknown",
            )
            .performScrollTo()
            .performClick()

        assertEquals(
            CaregiverReportState.UNKNOWN,
            selectedState,
        )
    }

    @Test
    fun cancelledOccurrence_disablesNewReportActions() {
        composeRule.setContent {
            CarePackTheme {
                OccurrenceDetailScreen(
                    state =
                        OccurrenceDetailUiState(
                            isLoading = false,
                            occurrence =
                                detailOccurrence(
                                    lifecycle =
                                        OccurrenceLifecycle
                                            .CANCELLED,
                                    reportState =
                                        CaregiverReportState
                                            .GIVEN,
                                    cancellationReason =
                                        OccurrenceCancellationReason
                                            .SCHEDULE_REPLACED,
                                ),
                        ),
                    onSetReport = {},
                    onUndo = {},
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "cancelled_report_disabled",
            )
            .performScrollTo()
            .assertIsDisplayed()

        assertTagDoesNotExist(
            tag = "record_given",
        )

        assertTagDoesNotExist(
            tag = "record_not_given",
        )

        assertTagDoesNotExist(
            tag = "record_unknown",
        )

        composeRule
            .onNodeWithTag(
                "current_report_state",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                context.getString(
                    R.string
                        .pr3_report_given,
                ),
            )
    }

    @Test
    fun undoSnackbar_callsCurrentUndoToken() {
        var receivedToken: Long? =
            null

        val undo =
            UndoUiState(
                token = 42L,
                change =
                    ReportChange(
                        occurrenceId =
                            "occurrence-1",
                        previousState =
                            null,
                        newState =
                            CaregiverReportState
                                .GIVEN,
                        changedAtEpochMillis =
                            TEST_INSTANT
                                .toEpochMilli(),
                    ),
            )

        composeRule.setContent {
            CarePackTheme {
                OccurrenceDetailScreen(
                    state =
                        activeDetailState(
                            undo = undo,
                        ),
                    onSetReport = {},
                    onUndo = { token ->
                        receivedToken =
                            token
                    },
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "report_undo_snackbar",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "undo_report",
            )
            .performClick()

        assertEquals(
            42L,
            receivedToken,
        )
    }

    @Test
    fun todayShowsSeparateNoMedicationEmptyState() {
        var retryCount =
            0

        val state =
            mutableStateOf(
                TodayUiState(
                    localDate =
                        TEST_DATE,
                    isLoading = false,
                    items = emptyList(),
                    emptyState =
                        TodayEmptyState
                            .NO_MEDICATIONS,
                    isHistoryLoading =
                        false,
                ),
            )

        composeRule.setContent {
            CarePackTheme {
                TodayScreen(
                    state =
                        state.value,
                    onOccurrenceSelected = {},
                    onManageCarePlan = {},
                    onRetry = {
                        retryCount +=
                            1
                    },
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "today_empty_no_medications",
            )
            .assertIsDisplayed()

        assertTagDoesNotExist(
            tag =
                "today_empty_no_occurrences",
        )

        composeRule.runOnIdle {
            state.value =
                state.value.copy(
                    emptyState =
                        TodayEmptyState
                            .NO_OCCURRENCES,
                )
        }

        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(
                "today_empty_no_occurrences",
            )
            .assertIsDisplayed()

        assertTagDoesNotExist(
            tag =
                "today_empty_no_medications",
        )

        composeRule.runOnIdle {
            state.value =
                state.value.copy(
                    errorMessage =
                        "خطای آزمایشی",
                )
        }

        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(
                "today_error",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "today_error_retry",
            )
            .performClick()

        assertEquals(
            1,
            retryCount,
        )
    }

    @Test
    fun historySectionShowsGroupedRecentHistory() {
        var selectedOccurrenceId:
                String? =
            null

        val previousDate =
            TEST_DATE.minusDays(
                1,
            )

        composeRule.setContent {
            CarePackTheme {
                TodayScreen(
                    state =
                        TodayUiState(
                            localDate =
                                TEST_DATE,
                            selectedSection =
                                TodaySection
                                    .HISTORY,
                            isLoading = false,
                            items = emptyList(),
                            isHistoryLoading =
                                false,
                            historyDays =
                                listOf(
                                    HistoryDay(
                                        localDate =
                                            TEST_DATE,
                                        items =
                                            listOf(
                                                historyItem(
                                                    id =
                                                        "history-1",
                                                    date =
                                                        TEST_DATE,
                                                    time =
                                                        LocalTime.of(
                                                            8,
                                                            0,
                                                        ),
                                                ),
                                                historyItem(
                                                    id =
                                                        "history-2",
                                                    date =
                                                        TEST_DATE,
                                                    time =
                                                        LocalTime.of(
                                                            10,
                                                            0,
                                                        ),
                                                ),
                                            ),
                                    ),
                                    HistoryDay(
                                        localDate =
                                            previousDate,
                                        items =
                                            listOf(
                                                historyItem(
                                                    id =
                                                        "history-3",
                                                    date =
                                                        previousDate,
                                                    time =
                                                        LocalTime.of(
                                                            9,
                                                            0,
                                                        ),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    onOccurrenceSelected = {
                        selectedOccurrenceId =
                            it
                    },
                    onManageCarePlan = {},
                )
            }
        }

        val historyList =
            composeRule
                .onNodeWithTag(
                    "history_list",
                )
                .assertIsDisplayed()

        historyList.performScrollToNode(
            hasTestTag(
                "history_item_history-2",
            ),
        )

        composeRule
            .onNodeWithTag(
                "history_item_history-2",
            )
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(
            "history-2",
            selectedOccurrenceId,
        )

        historyList.performScrollToNode(
            hasTestTag(
                "history_date_$previousDate",
            ),
        )

        composeRule
            .onNodeWithTag(
                "history_date_$previousDate",
            )
            .assertIsDisplayed()

        historyList.performScrollToNode(
            hasTestTag(
                "history_item_history-3",
            ),
        )

        composeRule
            .onNodeWithTag(
                "history_item_history-3",
            )
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    private fun replaceVisibleTodayItem(
        state:
        MutableState<List<TodayItem>>,
        item: TodayItem,
    ) {
        composeRule.runOnIdle {
            state.value =
                listOf(
                    item,
                )
        }

        composeRule.waitForIdle()
    }

    private fun assertTaggedText(
        tag: String,
        expectedText: String,
    ) {
        composeRule
            .onNodeWithTag(
                testTag = tag,
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
            .assertTextEquals(
                expectedText,
            )
    }

    private fun assertTagExists(
        tag: String,
    ) {
        val nodes =
            composeRule
                .onAllNodesWithTag(
                    testTag = tag,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )

        assertTrue(
            "Expected tag '$tag' to exist.",
            nodes.isNotEmpty(),
        )
    }

    private fun assertTagDoesNotExist(
        tag: String,
    ) {
        val nodes =
            composeRule
                .onAllNodesWithTag(
                    testTag = tag,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )

        assertTrue(
            "Expected tag '$tag' not to exist.",
            nodes.isEmpty(),
        )
    }

    private fun activeDetailState(
        reportState:
        CaregiverReportState? =
            null,
        undo: UndoUiState? = null,
    ): OccurrenceDetailUiState {
        return OccurrenceDetailUiState(
            isLoading = false,
            occurrence =
                detailOccurrence(
                    lifecycle =
                        OccurrenceLifecycle
                            .ACTIVE,
                    reportState =
                        reportState,
                ),
            undo = undo,
        )
    }

    private fun detailOccurrence(
        lifecycle:
        OccurrenceLifecycle,
        reportState:
        CaregiverReportState?,
        cancellationReason:
        OccurrenceCancellationReason? =
            null,
    ): OccurrenceDetail {
        return OccurrenceDetail(
            occurrenceId =
                "occurrence-1",
            localDate =
                TEST_DATE,
            localTime =
                LocalTime.of(
                    9,
                    0,
                ),
            scheduledAt =
                TEST_INSTANT,
            medicationName =
                "داروی نمونه",
            medicationInstruction =
                "دستور نمونه",
            lifecycle =
                lifecycle,
            reportState =
                reportState,
            zoneId =
                "Asia/Tehran",
            temporalPhase =
                TemporalPhase.DUE,
            isOverdue =
                false,
            cancellationReason =
                cancellationReason,
        )
    }

    private fun todayItem(
        id: String,
        localTime: LocalTime,
        reportState:
        CaregiverReportState?,
        phase: TemporalPhase,
        isOverdue: Boolean = false,
    ): TodayItem {
        return TodayItem(
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
                OccurrenceLifecycle
                    .ACTIVE,
            reportState =
                reportState,
            scheduledAt =
                TEST_DATE
                    .atTime(
                        localTime,
                    )
                    .toInstant(
                        ZoneOffset.UTC,
                    ),
            temporalPhase =
                phase,
            isOverdue =
                isOverdue,
        )
    }

    private fun historyItem(
        id: String,
        date: LocalDate,
        time: LocalTime,
    ): HistoryItem {
        return HistoryItem(
            occurrenceId =
                id,
            localDate =
                date,
            localTime =
                time,
            scheduledAt =
                date
                    .atTime(
                        time,
                    )
                    .toInstant(
                        ZoneOffset.UTC,
                    ),
            medicationName =
                "داروی $id",
            medicationInstruction =
                "دستور نمونه",
            lifecycle =
                OccurrenceLifecycle
                    .ACTIVE,
            reportState =
                null,
            temporalPhase =
                TemporalPhase.PAST,
            isOverdue =
                true,
        )
    }

    private companion object {

        val TEST_DATE: LocalDate =
            LocalDate.of(
                2026,
                6,
                24,
            )

        val TEST_INSTANT: Instant =
            Instant.parse(
                "2026-06-24T05:30:00Z",
            )
    }
}
