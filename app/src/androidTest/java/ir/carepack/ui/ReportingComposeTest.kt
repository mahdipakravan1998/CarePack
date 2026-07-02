package ir.carepack.ui

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.R
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.HistoryDay
import ir.carepack.domain.model.HistoryItem
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TemporalStatus
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.report.ReportChange
import ir.carepack.feature.detail.OccurrenceDetailScreen
import ir.carepack.feature.detail.OccurrenceDetailUiState
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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
                        reportState =
                            null,
                        phase =
                            TemporalStatus.UPCOMING,
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
                            selectedSection =
                                TodaySection.TODAY,
                            isLoading =
                                false,
                            items =
                                visibleItems.value,
                            emptyState =
                                null,
                            isHistoryLoading =
                                false,
                        ),
                    onTodaySelected = {},
                    onHistorySelected = {},
                    onRetry = {},
                    onOpenCarePlan = {},
                    onOpenSettings = {},
                    onOpenOccurrence = {},
                )
            }
        }

        assertVisibleText(
            context.getString(
                R.string.report_no_report,
            ),
        )

        replaceVisibleTodayItem(
            state =
                visibleItems,
            item =
                todayItem(
                    id =
                        "unknown",
                    localTime =
                        LocalTime.of(
                            9,
                            0,
                        ),
                    reportState =
                        CaregiverReportState.UNKNOWN,
                    phase =
                        TemporalStatus.DUE,
                ),
        )

        assertVisibleText(
            context.getString(
                R.string.report_unknown,
            ),
        )

        replaceVisibleTodayItem(
            state =
                visibleItems,
            item =
                todayItem(
                    id =
                        "given",
                    localTime =
                        LocalTime.of(
                            10,
                            0,
                        ),
                    reportState =
                        CaregiverReportState.GIVEN,
                    phase =
                        TemporalStatus.PAST,
                ),
        )

        assertVisibleText(
            context.getString(
                R.string.report_given,
            ),
        )

        replaceVisibleTodayItem(
            state =
                visibleItems,
            item =
                todayItem(
                    id =
                        "not-given",
                    localTime =
                        LocalTime.of(
                            11,
                            0,
                        ),
                    reportState =
                        CaregiverReportState.NOT_GIVEN,
                    phase =
                        TemporalStatus.PAST,
                ),
        )

        assertVisibleText(
            context.getString(
                R.string.report_not_given,
            ),
        )
    }

    @Test
    fun todayCanShowOccurrencesFromMultipleSchedules() {
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
                                            "schedule-one",
                                        localTime =
                                            LocalTime.of(
                                                8,
                                                0,
                                            ),
                                        reportState =
                                            null,
                                        phase =
                                            TemporalStatus.DUE,
                                    ),
                                    todayItem(
                                        id =
                                            "schedule-two",
                                        localTime =
                                            LocalTime.of(
                                                20,
                                                0,
                                            ),
                                        reportState =
                                            null,
                                        phase =
                                            TemporalStatus.UPCOMING,
                                    ),
                                ),
                            emptyState =
                                null,
                            isHistoryLoading =
                                false,
                        ),
                    onTodaySelected = {},
                    onHistorySelected = {},
                    onRetry = {},
                    onOpenCarePlan = {},
                    onOpenSettings = {},
                    onOpenOccurrence = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "today_item_schedule-one",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "today_item_schedule-two",
            )
            .assertIsDisplayed()
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
                                CaregiverReportState.GIVEN,
                        ),
                    onBack = {},
                    onGiven = {
                        selectedState =
                            CaregiverReportState.GIVEN
                    },
                    onNotGiven = {
                        selectedState =
                            CaregiverReportState.NOT_GIVEN
                    },
                    onUnknown = {
                        selectedState =
                            CaregiverReportState.UNKNOWN
                    },
                    onUndo = {},
                    onSnackbarConsumed = {},
                )
            }
        }

        assertTagExists(
            tag =
                "report_given",
        )

        assertTagExists(
            tag =
                "report_not_given",
        )

        assertTagExists(
            tag =
                "report_unknown",
        )

        composeRule
            .onNodeWithTag(
                "report_unknown",
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
                            isLoading =
                                false,
                            detail =
                                detailOccurrence(
                                    lifecycle =
                                        OccurrenceLifecycle.CANCELLED,
                                    reportState =
                                        CaregiverReportState.GIVEN,
                                    cancellationReason =
                                        OccurrenceCancellationReason
                                            .SCHEDULE_REPLACED,
                                ),
                        ),
                    onBack = {},
                    onGiven = {},
                    onNotGiven = {},
                    onUnknown = {},
                    onUndo = {},
                    onSnackbarConsumed = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "occurrence_detail_card",
            )
            .assertIsDisplayed()

        assertVisibleText(
            context.getString(
                R.string.cancelled_occurrence,
            ),
        )

        assertTagDoesNotExist(
            tag =
                "report_given",
        )

        assertTagDoesNotExist(
            tag =
                "report_not_given",
        )

        assertTagDoesNotExist(
            tag =
                "report_unknown",
        )
    }

    @Test
    fun undoSnackbar_callsCurrentUndoToken() {
        var undoCount =
            0

        composeRule.setContent {
            CarePackTheme {
                OccurrenceDetailScreen(
                    state =
                        activeDetailState(
                            undoChange =
                                ReportChange(
                                    occurrenceId =
                                        "occurrence-1",
                                    previousState =
                                        null,
                                    newState =
                                        CaregiverReportState.GIVEN,
                                    changedAtEpochMillis =
                                        TEST_INSTANT.toEpochMilli(),
                                ),
                            snackbarMessage =
                                "گزارش ثبت شد.",
                        ),
                    onBack = {},
                    onGiven = {},
                    onNotGiven = {},
                    onUnknown = {},
                    onUndo = {
                        undoCount += 1
                    },
                    onSnackbarConsumed = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "occurrence_detail_snackbar",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "occurrence_detail_undo",
            )
            .performClick()

        assertEquals(
            1,
            undoCount,
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
                    selectedSection =
                        TodaySection.TODAY,
                    isLoading =
                        false,
                    items =
                        emptyList(),
                    emptyState =
                        TodayEmptyState.NO_MEDICATIONS,
                    isHistoryLoading =
                        false,
                ),
            )

        composeRule.setContent {
            CarePackTheme {
                TodayScreen(
                    state =
                        state.value,
                    onTodaySelected = {},
                    onHistorySelected = {},
                    onRetry = {
                        retryCount +=
                            1
                    },
                    onOpenCarePlan = {},
                    onOpenSettings = {},
                    onOpenOccurrence = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "today_empty",
            )
            .assertIsDisplayed()

        composeRule.runOnIdle {
            state.value =
                state
                    .value
                    .copy(
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

        assertEquals(
            0,
            retryCount,
        )
    }

    @Test
    fun historySectionShowsGroupedRecentHistory() {
        var selectedOccurrenceId:
                String? =
            null

        composeRule.setContent {
            CarePackTheme {
                TodayScreen(
                    state =
                        TodayUiState(
                            localDate =
                                TEST_DATE,
                            selectedSection =
                                TodaySection.HISTORY,
                            isLoading =
                                false,
                            items =
                                emptyList(),
                            emptyState =
                                null,
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
                                                            20,
                                                            0,
                                                        ),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    onTodaySelected = {},
                    onHistorySelected = {},
                    onRetry = {},
                    onOpenCarePlan = {},
                    onOpenSettings = {},
                    onOpenOccurrence = {
                            occurrenceId ->
                        selectedOccurrenceId =
                            occurrenceId
                    },
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "history_item_history-1",
            )
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(
            "history-1",
            selectedOccurrenceId,
        )

        composeRule
            .onNodeWithTag(
                "history_item_history-2",
            )
            .assertIsDisplayed()
    }

    private fun assertVisibleText(
        text: String,
    ) {
        composeRule
            .onNodeWithText(
                text =
                    text,
                substring =
                    true,
                useUnmergedTree =
                    true,
            )
            .assertIsDisplayed()
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

    private fun assertTagExists(
        tag: String,
    ) {
        assertTrue(
            "Expected tag '$tag' to exist.",
            tagExists(
                tag,
            ),
        )
    }

    private fun assertTagDoesNotExist(
        tag: String,
    ) {
        assertTrue(
            "Expected tag '$tag' not to exist.",
            !tagExists(
                tag,
            ),
        )
    }

    private fun tagExists(
        tag: String,
    ): Boolean =
        composeRule
            .onAllNodesWithTag(
                testTag =
                    tag,
            )
            .fetchSemanticsNodes(
                atLeastOneRootRequired =
                    false,
            )
            .isNotEmpty()

    private fun activeDetailState(
        reportState:
        CaregiverReportState? =
            null,
        undoChange: ReportChange? = null,
        snackbarMessage: String? = null,
    ): OccurrenceDetailUiState =
        OccurrenceDetailUiState(
            isLoading =
                false,
            detail =
                detailOccurrence(
                    lifecycle =
                        OccurrenceLifecycle.ACTIVE,
                    reportState =
                        reportState,
                ),
            snackbarMessage =
                snackbarMessage,
            undoChange =
                undoChange,
        )

    private fun detailOccurrence(
        lifecycle: OccurrenceLifecycle,
        reportState: CaregiverReportState?,
        cancellationReason:
        OccurrenceCancellationReason? =
            null,
    ): OccurrenceDetail =
        OccurrenceDetail(
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
            temporalStatus =
                TemporalStatus.DUE,
            isOverdue =
                false,
            cancellationReason =
                cancellationReason,
        )

    private fun todayItem(
        id: String,
        localTime: LocalTime,
        reportState:
        CaregiverReportState?,
        phase: TemporalStatus,
        isOverdue: Boolean =
            false,
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
                reportState,
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
                isOverdue,
        )

    private fun historyItem(
        id: String,
        date: LocalDate,
        time: LocalTime,
    ): HistoryItem =
        HistoryItem(
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
                OccurrenceLifecycle.ACTIVE,
            reportState =
                null,
            temporalStatus =
                TemporalStatus.PAST,
            isOverdue =
                true,
        )

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
