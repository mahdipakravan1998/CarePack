package ir.carepack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import ir.carepack.data.service.RoomTodayReportFormatter
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareRecipientEditComposeTest {

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
    fun settingsRecipientEdit_updatesOnlySingletonRecipient() {
        val recipientId =
            runBlocking {
                fixture
                    .createPlan(
                        medicationName = "داروی صبح",
                        instruction = "بعد از صبحانه",
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                        startDate =
                            TEST_DATE,
                        endDate =
                            TEST_DATE,
                    )
                    .recipientId
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
                "settings_edit_recipient_name",
        )

        composeRule
            .onNodeWithTag(
                "settings_edit_recipient_name",
            )
            .assertIsDisplayed()
            .performClick()

        waitForTag(
            tag =
                "recipient_name_edit_screen",
        )

        composeRule
            .onNodeWithTag(
                "recipient_name_edit_field",
            )
            .performTextClearance()

        composeRule
            .onNodeWithTag(
                "recipient_name_edit_field",
            )
            .performTextInput(
                "پدر",
            )

        composeRule
            .onNodeWithTag(
                "recipient_name_edit_save",
            )
            .performClick()

        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
            runBlocking {
                fixture
                    .database
                    .careRecipientDao()
                    .getSingleton()
                    ?.displayName == "پدر"
            }
        }

        val singleton =
            runBlocking {
                fixture
                    .database
                    .careRecipientDao()
                    .getSingleton()
            }

        assertEquals(
            recipientId,
            singleton?.id,
        )

        assertEquals(
            "پدر",
            singleton?.displayName,
        )

        assertEquals(
            1,
            runBlocking {
                fixture
                    .database
                    .careRecipientDao()
                    .count()
            },
        )
    }

    @Test
    fun recipientEditValidationError_staysNearNameField() {
        runBlocking {
            fixture.createPlan(
                minutesOfDay =
                    listOf(
                        12 * 60,
                    ),
                startDate =
                    TEST_DATE,
                endDate =
                    TEST_DATE,
            )
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
                "settings_edit_recipient_name",
        )

        composeRule
            .onNodeWithTag(
                "settings_edit_recipient_name",
            )
            .performClick()

        waitForTag(
            tag =
                "recipient_name_edit_field",
        )

        composeRule
            .onNodeWithTag(
                "recipient_name_edit_field",
            )
            .performTextClearance()

        composeRule
            .onNodeWithTag(
                "recipient_name_edit_save",
            )
            .performClick()

        waitForTag(
            tag =
                "recipient_name_edit_error",
        )

        composeRule
            .onNodeWithTag(
                "recipient_name_edit_error",
            )
            .assertIsDisplayed()
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
                        RecipientEditSetupPreferenceStore(
                            setupComplete = true,
                        ),
                    reminderPreferenceStore =
                        RecipientEditReminderPreferenceStore(),
                    reminderCoordinator =
                        RecipientEditReminderCoordinator(),
                    reminderTestCoordinator =
                        ir.carepack.testing
                            .InstrumentedReminderTestCoordinator(),
                    notificationPermissionGateway =
                        RecipientEditNotificationPermissionGateway(),
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
                    testTag = tag,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired = false,
                )
                .isNotEmpty()
        }
    }

    private companion object {
        val TEST_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )

        const val WAIT_TIMEOUT_MILLIS =
            30_000L
    }
}

private class RecipientEditSetupPreferenceStore(
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
        mutableSetupComplete.value = true
    }
}

private class RecipientEditReminderPreferenceStore :
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

private class RecipientEditReminderCoordinator :
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

private class RecipientEditNotificationPermissionGateway :
    NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean =
        true

    override fun requiresRuntimePermission():
            Boolean =
        false
}
