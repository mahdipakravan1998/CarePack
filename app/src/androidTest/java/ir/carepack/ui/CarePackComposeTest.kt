package ir.carepack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertTrue
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
                "primary_navigation",
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
                "report_given",
        )

        composeRule
            .onNodeWithTag(
                "report_given",
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
    fun primaryNavigation_exposesTodayMedicationsHistoryAndSettings() {
        runBlocking {
            createNoonPlanAndReturnTodayOccurrenceId()
        }

        renderApp()

        waitForTag(
            tag =
                "primary_navigation",
        )

        composeRule
            .onNodeWithTag(
                "primary_nav_today",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "primary_nav_medications",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "primary_nav_history",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "primary_nav_settings",
            )
            .assertIsDisplayed()
    }

    @Test
    fun medicationsNavigation_showsMultipleSchedulesUnderMedication() {
        runBlocking {
            val plan =
                fixture.createPlan(
                    minutesOfDay =
                        listOf(
                            NOON_MINUTE_OF_DAY,
                        ),
                    startDate =
                        TODAY_DATE,
                    endDate =
                        TODAY_DATE,
                )

            fixture.addSchedule(
                medicationId =
                    plan.medicationId,
                minutesOfDay =
                    listOf(
                        20 * 60,
                    ),
                startDate =
                    TODAY_DATE,
                endDate =
                    TODAY_DATE,
            )
        }

        renderApp()

        waitForTag(
            tag =
                "primary_nav_medications",
        )

        composeRule
            .onNodeWithTag(
                "primary_nav_medications",
            )
            .performClick()

        waitForTag(
            tag =
                "care_plan_screen",
        )

        composeRule
            .onNodeWithTag(
                "care_plan_screen",
            )
            .assertIsDisplayed()

        waitForAtLeastCount(
            tagPrefix =
                "schedule_card_",
            count =
                2,
        )
    }

    @Test
    fun scheduleCardWithStartDateDisplaysJalaliDate() {
        runBlocking {
            fixture.createPlan(
                minutesOfDay =
                    listOf(
                        NOON_MINUTE_OF_DAY,
                    ),
                startDate =
                    TODAY_DATE,
                endDate = null,
            )
        }

        openMedicationsScreen()

        waitForText(
            TODAY_JALALI_DATE,
        )

        composeRule
            .onNodeWithText(
                text = TODAY_JALALI_DATE,
                substring = true,
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun scheduleCardWithEndDateDisplaysJalaliDate() {
        runBlocking {
            fixture.createPlan(
                minutesOfDay =
                    listOf(
                        NOON_MINUTE_OF_DAY,
                    ),
                startDate = null,
                endDate =
                    TOMORROW_DATE,
            )
        }

        openMedicationsScreen()

        waitForText(
            TOMORROW_JALALI_DATE,
        )

        composeRule
            .onNodeWithText(
                text = TOMORROW_JALALI_DATE,
                substring = true,
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun scheduleCardDoesNotExposeRawGregorianStartOrEndDates() {
        runBlocking {
            fixture.createPlan(
                minutesOfDay =
                    listOf(
                        NOON_MINUTE_OF_DAY,
                    ),
                startDate =
                    TODAY_DATE,
                endDate =
                    TOMORROW_DATE,
            )
        }

        openMedicationsScreen()

        waitForText(
            TODAY_JALALI_DATE,
        )

        assertTextAbsent(
            TODAY_GREGORIAN_DATE,
        )

        assertTextAbsent(
            TOMORROW_GREGORIAN_DATE,
        )
    }

    @Test
    fun multipleScheduleCardsDisplayJalaliStartAndEndDates() {
        runBlocking {
            val plan =
                fixture.createPlan(
                    minutesOfDay =
                        listOf(
                            NOON_MINUTE_OF_DAY,
                        ),
                    startDate =
                        TODAY_DATE,
                    endDate =
                        TOMORROW_DATE,
                )

            fixture.addSchedule(
                medicationId =
                    plan.medicationId,
                minutesOfDay =
                    listOf(
                        20 * 60,
                    ),
                startDate =
                    SECOND_SCHEDULE_START_DATE,
                endDate =
                    SECOND_SCHEDULE_END_DATE,
            )
        }

        openMedicationsScreen()

        waitForAtLeastCount(
            tagPrefix =
                "schedule_card_",
            count =
                2,
        )

        listOf(
            TODAY_JALALI_DATE,
            TOMORROW_JALALI_DATE,
            SECOND_SCHEDULE_START_JALALI_DATE,
            SECOND_SCHEDULE_END_JALALI_DATE,
        ).forEach { jalaliDate ->
            waitForText(
                jalaliDate,
            )

            composeRule
                .onNodeWithText(
                    text = jalaliDate,
                    substring = true,
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
        }

        listOf(
            TODAY_GREGORIAN_DATE,
            TOMORROW_GREGORIAN_DATE,
            SECOND_SCHEDULE_START_GREGORIAN_DATE,
            SECOND_SCHEDULE_END_GREGORIAN_DATE,
        ).forEach { gregorianDate ->
            assertTextAbsent(
                gregorianDate,
            )
        }
    }

    @Test
    fun settingsPrivacy_opensLocalPrivacyScreen() {
        runBlocking {
            createNoonPlanAndReturnTodayOccurrenceId()
        }

        renderApp()

        waitForTag(
            tag =
                "primary_nav_settings",
        )

        composeRule
            .onNodeWithTag(
                "primary_nav_settings",
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
            .performScrollTo()
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

    @Test
    fun settingsShowsApplicationVersion() {
        runBlocking {
            createNoonPlanAndReturnTodayOccurrenceId()
        }

        renderApp()

        waitForTag(
            tag =
                "primary_nav_settings",
        )

        composeRule
            .onNodeWithTag(
                "primary_nav_settings",
            )
            .performClick()

        waitForTag(
            tag =
                "settings_app_version",
        )

        composeRule
            .onNodeWithTag(
                "settings_app_version",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "settings_app_version_value",
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

    private fun openMedicationsScreen() {
        renderApp()

        waitForTag(
            tag =
                "primary_nav_medications",
        )

        composeRule
            .onNodeWithTag(
                "primary_nav_medications",
            )
            .performClick()

        waitForTag(
            tag =
                "care_plan_screen",
        )
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

    private fun waitForAtLeastCount(
        tagPrefix: String,
        count: Int,
    ) {
        composeRule.waitUntil(
            timeoutMillis =
                10_000,
        ) {
            composeRule
                .onAllNodesWithTag(
                    testTag =
                        "",
                    useUnmergedTree =
                        true,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )
                .count {
                    it
                        .config
                        .toString()
                        .contains(
                            tagPrefix,
                        )
                } >= count
        }
    }

    private fun waitForText(
        text: String,
    ) {
        composeRule.waitUntil(
            timeoutMillis =
                10_000,
        ) {
            composeRule
                .onAllNodesWithText(
                    text = text,
                    substring = true,
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )
                .isNotEmpty()
        }
    }

    private fun assertTextAbsent(
        text: String,
    ) {
        val nodes =
            composeRule
                .onAllNodesWithText(
                    text = text,
                    substring = true,
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )

        assertTrue(
            "Expected text '$text' not to exist.",
            nodes.isEmpty(),
        )
    }

    private companion object {
        const val TODAY_GREGORIAN_DATE =
            "2026-06-24"

        const val TOMORROW_GREGORIAN_DATE =
            "2026-06-25"

        const val SECOND_SCHEDULE_START_GREGORIAN_DATE =
            "2026-06-26"

        const val SECOND_SCHEDULE_END_GREGORIAN_DATE =
            "2026-06-27"

        const val TODAY_JALALI_DATE =
            "1405/04/03"

        const val TOMORROW_JALALI_DATE =
            "1405/04/04"

        const val SECOND_SCHEDULE_START_JALALI_DATE =
            "1405/04/05"

        const val SECOND_SCHEDULE_END_JALALI_DATE =
            "1405/04/06"

        val TODAY_DATE: LocalDate =
            LocalDate.parse(
                TODAY_GREGORIAN_DATE,
            )

        val TOMORROW_DATE: LocalDate =
            LocalDate.parse(
                TOMORROW_GREGORIAN_DATE,
            )

        val SECOND_SCHEDULE_START_DATE: LocalDate =
            LocalDate.parse(
                SECOND_SCHEDULE_START_GREGORIAN_DATE,
            )

        val SECOND_SCHEDULE_END_DATE: LocalDate =
            LocalDate.parse(
                SECOND_SCHEDULE_END_GREGORIAN_DATE,
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
