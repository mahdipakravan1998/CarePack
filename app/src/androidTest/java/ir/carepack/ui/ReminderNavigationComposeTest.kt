package ir.carepack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.CarePackApp
import ir.carepack.app.CarePackUiDependencies
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.RemindLaterOutcome
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.SnoozedReminder
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.data.service.RoomTodayReportFormatter
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.testing.CarePlanRoomTestFixture
import ir.carepack.testing.InstrumentedPrivacyPreferenceStore
import ir.carepack.testing.InstrumentedUserExperiencePreferenceStore
import ir.carepack.testing.RecordingDataDeletionCoordinator
import ir.carepack.testing.RecordingTextShareGateway
import ir.carepack.ui.theme.CarePackTheme
import java.time.Instant
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
class ReminderNavigationComposeTest {

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
    fun notificationOccurrence_navigatesToReminderActionSurface_withoutCreatingReport() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        var notificationHandled =
            false

        setAppContent(
            notificationOccurrenceId =
                occurrenceId,
            onNotificationOccurrenceHandled = {
                notificationHandled =
                    true
            },
        )

        waitForTag(
            tag =
                "reminder_action_surface",
        )

        composeRule
            .onNodeWithTag(
                "occurrence_detail_screen",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "reminder_action_surface",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "report_given",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "remind_later",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "report_not_given",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "report_unknown",
            )
            .performScrollTo()
            .assertIsDisplayed()

        assertTrue(
            notificationHandled,
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
    }

    @Test
    fun reminderAction_remindLaterDoesNotCreateReport() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        val reminderCoordinator =
            NavigationReminderCoordinator()

        setAppContent(
            notificationOccurrenceId =
                occurrenceId,
            reminderCoordinator =
                reminderCoordinator,
        )

        waitForTag(
            tag =
                "reminder_action_surface",
        )

        composeRule
            .onNodeWithTag(
                "remind_later",
            )
            .performClick()

        composeRule.waitUntil(
            timeoutMillis =
                10_000,
        ) {
            reminderCoordinator
                .remindLaterCalls == 1
        }

        assertEquals(
            occurrenceId,
            reminderCoordinator
                .lastRemindLaterOccurrenceId,
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
    }

    @Test
    fun reminderAction_givenWritesReportOnlyAfterExplicitTap() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        setAppContent(
            notificationOccurrenceId =
                occurrenceId,
        )

        waitForTag(
            tag =
                "reminder_action_surface",
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

        composeRule
            .onNodeWithTag(
                "report_given",
            )
            .performClick()

        waitForReportState(
            occurrenceId =
                occurrenceId,
            expectedState =
                CaregiverReportState.GIVEN,
        )
    }

    @Test
    fun reminderAction_notGivenSecondaryActionWritesReport() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        setAppContent(
            notificationOccurrenceId =
                occurrenceId,
        )

        waitForTag(
            tag =
                "reminder_action_surface",
        )

        composeRule
            .onNodeWithTag(
                "report_not_given",
            )
            .performScrollTo()
            .performClick()

        waitForReportState(
            occurrenceId =
                occurrenceId,
            expectedState =
                CaregiverReportState.NOT_GIVEN,
        )
    }

    @Test
    fun reminderAction_unknownSecondaryActionWritesReport() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        setAppContent(
            notificationOccurrenceId =
                occurrenceId,
        )

        waitForTag(
            tag =
                "reminder_action_surface",
        )

        composeRule
            .onNodeWithTag(
                "report_unknown",
            )
            .performScrollTo()
            .performClick()

        waitForReportState(
            occurrenceId =
                occurrenceId,
            expectedState =
                CaregiverReportState.UNKNOWN,
        )
    }

    @Test
    fun cancelledNotificationOccurrence_disablesReminderReportActionsSafely() {
        val occurrenceId =
            runBlocking {
                createCancelledNoonOccurrenceId()
            }

        setAppContent(
            notificationOccurrenceId =
                occurrenceId,
        )

        waitForTag(
            tag =
                "occurrence_cancelled_report_disabled",
        )

        composeRule
            .onNodeWithTag(
                "occurrence_detail_screen",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "occurrence_cancelled_report_disabled",
            )
            .assertIsDisplayed()

        assertTagDoesNotExist(
            tag =
                "reminder_action_surface",
        )

        assertTagDoesNotExist(
            tag =
                "report_given",
        )

        assertTagDoesNotExist(
            tag =
                "remind_later",
        )

        assertEquals(
            0,
            runBlocking {
                fixture
                    .database
                    .reportingDao()
                    .countReports()
            },
        )
    }

    @Test
    fun invalidNotificationOccurrence_opensSafeErrorStateWithoutCreatingReport() {
        var notificationHandled =
            false

        setAppContent(
            notificationOccurrenceId =
                "missing-occurrence",
            onNotificationOccurrenceHandled = {
                notificationHandled =
                    true
            },
        )

        waitForTag(
            tag =
                "occurrence_detail_error",
        )

        composeRule
            .onNodeWithTag(
                "occurrence_detail_error",
            )
            .assertIsDisplayed()

        assertTrue(
            notificationHandled,
        )

        assertEquals(
            0,
            runBlocking {
                fixture
                    .database
                    .reportingDao()
                    .countReports()
            },
        )
    }

    @Test
    fun blankNotificationOccurrence_keepsPrimaryTodayDestination() {
        runBlocking {
            createNoonPlanAndReturnTodayOccurrenceId()
        }

        setAppContent(
            notificationOccurrenceId =
                "",
        )

        waitForTag(
            tag =
                "today_screen",
        )

        composeRule
            .onNodeWithTag(
                "today_screen",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "primary_navigation",
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
    }

    @Test
    fun todayOccurrenceNavigation_stillOpensNormalDetail() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        setAppContent()

        waitForTag(
            tag =
                "today_item_$occurrenceId",
        )

        composeRule
            .onNodeWithTag(
                "today_item_$occurrenceId",
            )
            .performClick()

        waitForTag(
            tag =
                "occurrence_detail_card",
        )

        composeRule
            .onNodeWithTag(
                "occurrence_detail_card",
            )
            .assertIsDisplayed()

        assertTagDoesNotExist(
            tag =
                "reminder_action_surface",
        )

        composeRule
            .onNodeWithTag(
                "report_given",
            )
            .performScrollTo()
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
    }


    @Test
    fun testReminderRequestOpensReminderSettingsWithoutWritingReport() {
        runBlocking {
            createNoonPlanAndReturnTodayOccurrenceId()
        }

        var handledCount = 0

        setAppContent(
            openReminderSettingsRequested = true,
            onReminderSettingsRequestHandled = {
                handledCount += 1
            },
        )

        waitForTag(
            tag =
                "reminder_settings_intro",
        )

        composeRule
            .onNodeWithTag(
                "reminder_settings_intro",
            )
            .assertIsDisplayed()

        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
            handledCount == 1
        }

        assertEquals(
            0,
            runBlocking {
                fixture
                    .database
                    .reportingDao()
                    .countReports()
            },
        )
    }

    private fun setAppContent(
        notificationOccurrenceId: String? = null,
        reminderCoordinator: NavigationReminderCoordinator =
            NavigationReminderCoordinator(),
        onNotificationOccurrenceHandled: () -> Unit = {},
        openReminderSettingsRequested: Boolean = false,
        onReminderSettingsRequestHandled: () -> Unit = {},
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
                                NavigationSetupPreferenceStore(
                                    setupComplete = true,
                                ),
                            reminderPreferenceStore =
                                NavigationReminderPreferenceStore(),
                            reminderCoordinator =
                                reminderCoordinator,
                            reminderTestCoordinator =
                                ir.carepack.testing
                                    .InstrumentedReminderTestCoordinator(),
                            notificationPermissionGateway =
                                NavigationNotificationPermissionGateway(),
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
                                RecordingTextShareGateway(),
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
                    notificationOccurrenceId =
                        notificationOccurrenceId,
                    onNotificationOccurrenceHandled =
                        onNotificationOccurrenceHandled,
                    openReminderSettingsRequested =
                        openReminderSettingsRequested,
                    onReminderSettingsRequestHandled =
                        onReminderSettingsRequestHandled,
                )
            }
        }
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

    private suspend fun createCancelledNoonOccurrenceId():
            String {
        val plan =
            fixture.createPlan(
                minutesOfDay =
                    listOf(
                        NOON_MINUTE_OF_DAY,
                    ),
            )

        val occurrenceId =
            fixture
                .occurrenceOn(
                    medicationId =
                        plan.medicationId,
                    date = TODAY_DATE,
                    minuteOfDay =
                        NOON_MINUTE_OF_DAY,
                )
                .id

        fixture
            .carePlanService
            .stopMedication(
                plan.medicationId,
            )

        return occurrenceId
    }

    private fun waitForReportState(
        occurrenceId: String,
        expectedState: CaregiverReportState,
    ) {
        composeRule.waitUntil(
            timeoutMillis =
                10_000,
        ) {
            runBlocking {
                fixture
                    .database
                    .reportingDao()
                    .getReport(
                        occurrenceId,
                    )
                    ?.state ==
                        expectedState.name
            }
        }
    }

    private fun waitForTag(
        tag: String,
    ) {
        composeRule.waitUntil(
            timeoutMillis =
                10_000,
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

    private fun assertTagDoesNotExist(
        tag: String,
    ) {
        val nodes =
            composeRule
                .onAllNodesWithTag(
                    testTag =
                        tag,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )

        assertTrue(
            nodes.isEmpty(),
        )
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS =
            5_000L

        val TODAY_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )

        const val NOON_MINUTE_OF_DAY =
            12 * 60
    }
}

private class NavigationSetupPreferenceStore(
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

private class NavigationReminderPreferenceStore :
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

private class NavigationReminderCoordinator :
    ReminderCoordinator {

    var remindLaterCalls:
            Int =
        0

    var lastRemindLaterOccurrenceId:
            String? =
        null

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

    override suspend fun remindLater(
        occurrenceId: String,
        delayMinutes: Long,
    ): RemindLaterOutcome {
        remindLaterCalls += 1
        lastRemindLaterOccurrenceId =
            occurrenceId

        val createdAt =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        return RemindLaterOutcome.Scheduled(
            SnoozedReminder(
                occurrenceId = occurrenceId,
                createdAt = createdAt,
                remindAt =
                    createdAt.plusSeconds(
                        delayMinutes * 60L,
                    ),
            ),
        )
    }

    override suspend fun cancelAllOwnedReminderState() {
        Unit
    }
}

private class NavigationNotificationPermissionGateway :
    NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean =
        true

    override fun requiresRuntimePermission():
            Boolean =
        false
}
