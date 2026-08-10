package ir.carepack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.CarePackApp
import ir.carepack.app.CarePackUiDependencies
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.data.service.RoomTodayReportFormatter
import ir.carepack.feature.today.TodayScreen
import ir.carepack.feature.today.TodaySection
import ir.carepack.feature.today.TodayUiState
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.testing.CarePlanRoomTestFixture
import ir.carepack.testing.InstrumentedPrivacyPreferenceStore
import ir.carepack.testing.InstrumentedUserExperiencePreferenceStore
import ir.carepack.testing.RecordingDataDeletionCoordinator
import ir.carepack.testing.RecordingTextShareGateway
import ir.carepack.ui.theme.CarePackTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayReportEntryComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    private lateinit var fixture:
            CarePlanRoomTestFixture

    private val zoneProvider =
        ZoneProvider {
            ZoneId.of(
                "UTC",
            )
        }

    @Before
    fun setUp() {
        fixture =
            CarePlanRoomTestFixture.create()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun todayReportActionOpensPreviewWithoutWritingReportOrSharing() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        val shareGateway =
            RecordingTextShareGateway()

        renderApp(
            textShareGateway =
                shareGateway,
        )

        waitForTag(
            tag =
                "today_open_report",
        )

        composeRule
            .onNodeWithTag(
                "today_item_$occurrenceId",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "today_open_report",
            )
            .assertIsDisplayed()
            .performClick()

        waitForTag(
            tag =
                "today_report_screen",
        )

        waitForTag(
            tag =
                "today_report_preview_text",
        )

        composeRule
            .onNodeWithTag(
                "today_report_title",
            )
            .assertIsDisplayed()

        assertEquals(
            0,
            runBlocking {
                fixture
                    .database
                    .reportingDao()
                    .countReports()
            },
        )

        assertNull(
            runBlocking {
                fixture
                    .database
                    .reportingDao()
                    .getReport(
                        occurrenceId,
                    )
            },
        )

        assertTrue(
            shareGateway
                .copiedTexts
                .isEmpty(),
        )

        assertTrue(
            shareGateway
                .sharedTexts
                .isEmpty(),
        )
    }

    @Test
    fun todayReportActionRemainsVisibleForEmptyTodayScreen() {
        var openedReportCount =
            0

        composeRule.setContent {
            CarePackTheme {
                TodayScreen(
                    state =
                        TodayUiState(
                            localDate =
                                TODAY_DATE,
                            selectedSection =
                                TodaySection.TODAY,
                            isLoading =
                                false,
                            items =
                                emptyList(),
                            emptyState =
                                TodayEmptyState.NO_OCCURRENCES,
                            isHistoryLoading =
                                false,
                        ),
                    onTodaySelected = {},
                    onHistorySelected = {},
                    onRetry = {},
                    onOpenCarePlan = {},
                    onOpenSettings = {},
                    onOpenTodayReport = {
                        openedReportCount += 1
                    },
                    onOpenOccurrence = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "today_open_report",
            )
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                1,
                openedReportCount,
            )
        }
    }

    @Test
    fun existingTodayOccurrenceNavigationStillWorks() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        renderApp(
            textShareGateway =
                RecordingTextShareGateway(),
        )

        waitForTag(
            tag =
                "today_item_$occurrenceId",
        )

        composeRule
            .onNodeWithTag(
                "today_item_$occurrenceId",
            )
            .assertIsDisplayed()
            .performClick()

        waitForTag(
            tag =
                "occurrence_detail_screen",
        )

        composeRule
            .onNodeWithTag(
                "occurrence_detail_screen",
            )
            .assertIsDisplayed()
    }

    private suspend fun createNoonPlanAndReturnTodayOccurrenceId():
            String {
        val plan =
            fixture.createPlan(
                minutesOfDay =
                    listOf(
                        NOON_MINUTE_OF_DAY,
                    ),
            )

        return fixture
            .occurrenceOn(
                medicationId =
                    plan.medicationId,
                date = TODAY_DATE,
                minuteOfDay =
                    NOON_MINUTE_OF_DAY,
            )
            .id
    }

    private fun renderApp(
        textShareGateway: RecordingTextShareGateway,
    ) {
        composeRule.setContent {
            CarePackTheme {
                CarePackApp(
                    dependencies =
                        CarePackUiDependencies(
                            carePlanService =
                                fixture.carePlanService,
                            todayQueryService =
                                fixture.todayQueryService,
                            caregiverReportService =
                                fixture.reportService,
                            setupPreferenceStore =
                                TodayReportEntrySetupPreferenceStore(
                                    setupComplete = true,
                                ),
                            reminderPreferenceStore =
                                TodayReportEntryReminderPreferenceStore(),
                            reminderCoordinator =
                                TodayReportEntryReminderCoordinator(),
                            reminderTestCoordinator =
                                ir.carepack.testing
                                    .InstrumentedReminderTestCoordinator(),
                            notificationPermissionGateway =
                                TodayReportEntryNotificationPermissionGateway(),
                            todayReportFormatter =
                                RoomTodayReportFormatter(
                                    database =
                                        fixture.database,
                                ),
                            dateRangeSummaryService =
                                ir.carepack.data.service.RoomDateRangeSummaryService(
                                        database =
                                            fixture.database,
                                    ),
                            rangeReportFormatter =
                                ir.carepack.data.service.RoomRangeReportFormatter(
                                        database =
                                            fixture.database,
                                        summaryService =
                                            ir.carepack.data.service.RoomDateRangeSummaryService(
                                                    database =
                                                        fixture.database,
                                                ),
                                    ),
                            privacyPreferenceStore =
                                InstrumentedPrivacyPreferenceStore(),
                            userExperiencePreferenceStore =
                                InstrumentedUserExperiencePreferenceStore(),
                            textShareGateway =
                                textShareGateway,
                            dataDeletionCoordinator =
                                RecordingDataDeletionCoordinator(),
                            medicationDeletionCoordinator =
                                ir.carepack.testing
                                    .InstrumentedMedicationDeletionCoordinator(),
                            clock =
                                fixture.clock,
                            zoneProvider =
                                zoneProvider,
                        ),
                )
            }
        }
    }

    private fun waitForTag(
        tag: String,
    ) {
        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
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
        }
    }

    private companion object {
        val TODAY_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )

        const val NOON_MINUTE_OF_DAY =
            12 * 60

        const val WAIT_TIMEOUT_MILLIS =
            30_000L
    }
}

private class TodayReportEntrySetupPreferenceStore(
    setupComplete: Boolean,
) : SetupPreferenceStore {

    private val mutableSetupComplete =
        MutableStateFlow(
            setupComplete,
        )

    override val setupComplete:
            Flow<Boolean> =
        mutableSetupComplete

    override suspend fun markSetupComplete() {
        mutableSetupComplete.value =
            true
    }
}

private class TodayReportEntryReminderPreferenceStore :
    ReminderPreferenceStore {

    override val state:
            Flow<ReminderPreferenceState> =
        MutableStateFlow(
            ReminderPreferenceState(),
        )

    override suspend fun setRemindersEnabled(
        enabled: Boolean,
    ) {
        Unit
    }

    override suspend fun observeDeviceZone(
        zoneId: String,
    ): TimezoneObservation =
        TimezoneObservation.Initialized

    override suspend fun dismissTimezoneWarning() {
        Unit
    }

    override suspend fun markHealthy() {
        Unit
    }

    override suspend fun markFailure(
        failure: ir.carepack.core.error.SafeAppFailure,
        failedAtEpochMillis: Long,
    ) {
        Unit
    }
}

private class TodayReportEntryReminderCoordinator :
    ReminderCoordinator {

    override suspend fun currentStatus():
            ReminderStatus =
        ReminderStatus(
            remindersEnabled = false,
            notificationPermissionGranted = true,
            hasActiveSchedule = true,
            exactAlarmCapabilityGranted = true,
            availability =
                ReminderAvailability.DISABLED,
        )

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult =
        ReminderReconciliationResult.Reconciled(
            reason = reason,
            status = currentStatus(),
            scheduledCount = 0,
            cancelledCount = 0,
        )

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult {
        error(
            "Alarm firing is not used in this Compose test.",
        )
    }

    override suspend fun cancelAllOwnedReminderState() {
        Unit
    }
}

private class TodayReportEntryNotificationPermissionGateway :
    NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean =
        true

    override fun requiresRuntimePermission():
            Boolean =
        false
}
