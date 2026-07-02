package ir.carepack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.CarePackApp
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.SetupPreferenceStore
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
    fun notificationOccurrence_navigatesToDetail_withoutCreatingReport() {
        val occurrenceId =
            runBlocking {
                createNoonPlanAndReturnTodayOccurrenceId()
            }

        var notificationHandled =
            false

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
                        NavigationSetupPreferenceStore(
                            setupComplete = true,
                        ),
                    reminderPreferenceStore =
                        NavigationReminderPreferenceStore(),
                    reminderCoordinator =
                        NavigationReminderCoordinator(),
                    notificationPermissionGateway =
                        NavigationNotificationPermissionGateway(),
                    todayReportFormatter =
                        RoomTodayReportFormatter(
                            database =
                                fixture.database,
                        ),
                    privacyPreferenceStore =
                        InstrumentedPrivacyPreferenceStore(),
                    userExperiencePreferenceStore =
                        InstrumentedUserExperiencePreferenceStore(),
                    textShareGateway =
                        RecordingTextShareGateway(),
                    dataDeletionCoordinator =
                        RecordingDataDeletionCoordinator(),
                    clock =
                        fixture.clock,
                    zoneProvider =
                        zoneProvider,
                    notificationOccurrenceId =
                        occurrenceId,
                    onNotificationOccurrenceHandled = {
                        notificationHandled =
                            true
                    },
                )
            }
        }

        waitForTag(
            tag =
                "occurrence_detail_screen",
        )

        composeRule
            .onNodeWithTag(
                "occurrence_detail_card",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "report_given",
            )
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
    fun blankNotificationOccurrence_keepsPrimaryTodayDestination() {
        runBlocking {
            createNoonPlanAndReturnTodayOccurrenceId()
        }

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
                        NavigationSetupPreferenceStore(
                            setupComplete = true,
                        ),
                    reminderPreferenceStore =
                        NavigationReminderPreferenceStore(),
                    reminderCoordinator =
                        NavigationReminderCoordinator(),
                    notificationPermissionGateway =
                        NavigationNotificationPermissionGateway(),
                    todayReportFormatter =
                        RoomTodayReportFormatter(
                            database =
                                fixture.database,
                        ),
                    privacyPreferenceStore =
                        InstrumentedPrivacyPreferenceStore(),
                    userExperiencePreferenceStore =
                        InstrumentedUserExperiencePreferenceStore(),
                    textShareGateway =
                        RecordingTextShareGateway(),
                    dataDeletionCoordinator =
                        RecordingDataDeletionCoordinator(),
                    clock =
                        fixture.clock,
                    zoneProvider =
                        zoneProvider,
                    notificationOccurrenceId =
                        "",
                )
            }
        }

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
}

private class NavigationReminderCoordinator :
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

private class NavigationNotificationPermissionGateway :
    NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean =
        true

    override fun requiresRuntimePermission():
            Boolean =
        false
}
