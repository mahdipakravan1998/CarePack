package ir.carepack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.CarePackApp
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.domain.report.RoomTodayReportFormatter
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.testing.CarePlanRoomTestFixture
import ir.carepack.testing.InstrumentedPrivacyPreferenceStore
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarePackComposeTest {

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
    fun completedCarePlan_startsAtToday_andRecordsGiven() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        renderApp()

        waitForTag(
            tag =
                "today_occurrence_$occurrenceId",
        )

        composeRule
            .onNodeWithTag(
                "today_occurrence_$occurrenceId",
            )
            .assertIsDisplayed()
            .performClick()

        waitForTag(
            tag =
                "record_given",
        )

        composeRule
            .onNodeWithTag(
                "record_given",
            )
            .assertIsDisplayed()
            .performClick()

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
                        CaregiverReportState
                            .GIVEN
                            .name
            }
        }

        composeRule
            .onNodeWithTag(
                "current_report_state",
            )
            .assertIsDisplayed()

        assertEquals(
            CaregiverReportState.GIVEN.name,
            runBlocking {
                fixture
                    .database
                    .reportingDao()
                    .getReport(
                        occurrenceId,
                    )
                    ?.state
            },
        )
    }

    @Test
    fun settingsPrivacy_opensLocalPrivacyScreen() {
        runBlocking {
            createNoonPlanAndReturnTodayOccurrenceId()
        }

        renderApp()

        waitForTag(
            tag =
                "today_settings",
        )

        composeRule
            .onNodeWithTag(
                "today_settings",
            )
            .assertIsDisplayed()
            .performClick()

        waitForTag(
            tag =
                "settings_privacy",
        )

        composeRule
            .onNodeWithTag(
                "settings_privacy",
            )
            .assertIsDisplayed()
            .performClick()

        waitForTag(
            tag =
                "privacy_screen",
        )

        composeRule
            .onNodeWithTag(
                "privacy_summary",
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

    private fun renderApp() {
        composeRule.setContent {
            CarePackTheme {
                CarePackApp(
                    carePlanService =
                        fixture.carePlanService,
                    todayQueryService =
                        fixture.todayQueryService,
                    caregiverReportService =
                        fixture.reportService,
                    setupPreferenceStore =
                        ComposeSetupPreferenceStore(
                            setupComplete = true,
                        ),
                    reminderPreferenceStore =
                        ComposeReminderPreferenceStore(),
                    reminderCoordinator =
                        ComposeReminderCoordinator(),
                    notificationPermissionGateway =
                        ComposeNotificationPermissionGateway(),
                    todayReportFormatter =
                        RoomTodayReportFormatter(
                            database =
                                fixture.database,
                        ),
                    privacyPreferenceStore =
                        InstrumentedPrivacyPreferenceStore(),
                    textShareGateway =
                        RecordingTextShareGateway(),
                    dataDeletionCoordinator =
                        RecordingDataDeletionCoordinator(),
                    clock =
                        fixture.clock,
                    zoneProvider =
                        zoneProvider,
                )
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
                    tag,
                )
                .fetchSemanticsNodes()
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
    }
}

private class ComposeSetupPreferenceStore(
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

private class ComposeReminderPreferenceStore :
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
}

private class ComposeReminderCoordinator :
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

private class ComposeNotificationPermissionGateway :
    NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean =
        true

    override fun requiresRuntimePermission():
            Boolean =
        false
}
