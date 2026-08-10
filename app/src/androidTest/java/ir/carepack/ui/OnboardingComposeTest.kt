package ir.carepack.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.CarePackApp
import ir.carepack.app.CarePackUiDependencies
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
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
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingComposeTest {

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
    fun onboardingPrivacyActionOpensLocalPrivacyAndReturnKeepsSetupFlow() {
        val setupStore =
            OnboardingSetupPreferenceStore(
                setupComplete = false,
            )

        renderApp(
            setupPreferenceStore =
                setupStore,
            userExperiencePreferenceStore =
                InstrumentedUserExperiencePreferenceStore(),
        )

        waitForTag(
            tag =
                "onboarding_open_privacy",
        )

        composeRule
            .onNodeWithTag(
                "onboarding_open_privacy",
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

        assertFalse(
            runBlocking {
                setupStore
                    .setupComplete
                    .first()
            },
        )

        composeRule
            .onNodeWithTag(
                "privacy_back",
            )
            .assertIsDisplayed()
            .performClick()

        waitForTag(
            tag =
                "onboarding_screen",
        )

        assertFalse(
            runBlocking {
                setupStore
                    .setupComplete
                    .first()
            },
        )

        composeRule
            .onNodeWithTag(
                "onboarding_continue",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        waitForTag(
            tag =
                "recipient_setup_screen",
        )

        assertFalse(
            runBlocking {
                setupStore
                    .setupComplete
                    .first()
            },
        )
    }

    @Test
    fun onboardingSimpleModeSuggestionCanEnableSimpleMode() {
        val userExperiencePreferenceStore =
            InstrumentedUserExperiencePreferenceStore()

        renderApp(
            setupPreferenceStore =
                OnboardingSetupPreferenceStore(
                    setupComplete = false,
                ),
            userExperiencePreferenceStore =
                userExperiencePreferenceStore,
        )

        waitForTag(
            tag =
                "onboarding_simple_mode_card",
        )

        composeRule
            .onNodeWithTag(
                "onboarding_simple_mode_summary",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "onboarding_simple_mode_enable",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
            runBlocking {
                userExperiencePreferenceStore
                    .state
                    .first()
                    .seniorMode ==
                        SeniorMode.SIMPLE
            }
        }

        assertEquals(
            SeniorMode.SIMPLE,
            runBlocking {
                userExperiencePreferenceStore
                    .state
                    .first()
                    .seniorMode
            },
        )
    }

    @Test
    fun onboardingSimpleModeDeferKeepsStandardModeAndContinueStillWorks() {
        val setupStore =
            OnboardingSetupPreferenceStore(
                setupComplete = false,
            )

        val userExperiencePreferenceStore =
            InstrumentedUserExperiencePreferenceStore(
                initialState =
                    UserExperiencePreferenceState(
                        seniorMode =
                            SeniorMode.STANDARD,
                    ),
            )

        renderApp(
            setupPreferenceStore =
                setupStore,
            userExperiencePreferenceStore =
                userExperiencePreferenceStore,
        )

        waitForTag(
            tag =
                "onboarding_simple_mode_defer",
        )

        composeRule
            .onNodeWithTag(
                "onboarding_simple_mode_defer",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
            runBlocking {
                userExperiencePreferenceStore
                    .state
                    .first()
                    .seniorMode ==
                        SeniorMode.STANDARD
            }
        }

        assertEquals(
            SeniorMode.STANDARD,
            runBlocking {
                userExperiencePreferenceStore
                    .state
                    .first()
                    .seniorMode
            },
        )

        composeRule
            .onNodeWithTag(
                "onboarding_continue",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        waitForTag(
            tag =
                "recipient_setup_screen",
        )

        assertFalse(
            runBlocking {
                setupStore
                    .setupComplete
                    .first()
            },
        )
    }

    @Test
    fun firstMedicationSetup_enablingSimpleModeNavigatesToTodayWithDynamicTheme() {
        val setupStore =
            OnboardingSetupPreferenceStore(
                setupComplete = false,
            )

        val userExperiencePreferenceStore =
            InstrumentedUserExperiencePreferenceStore(
                initialState =
                    UserExperiencePreferenceState(
                        seniorMode =
                            SeniorMode.STANDARD,
                    ),
            )

        renderApp(
            setupPreferenceStore =
                setupStore,
            userExperiencePreferenceStore =
                userExperiencePreferenceStore,
        )

        waitForTag(
            tag =
                "onboarding_continue",
        )

        composeRule
            .onNodeWithTag(
                "onboarding_continue",
            )
            .performScrollTo()
            .performClick()

        waitForTag(
            tag =
                "recipient_setup_screen",
        )

        composeRule
            .onNodeWithTag(
                "recipient_name",
            )
            .performTextInput(
                "\u0645",
            )

        composeRule
            .onNodeWithTag(
                "recipient_save",
            )
            .performScrollTo()
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
            .performClick()

        composeRule
            .onNodeWithTag(
                "medication_name",
            )
            .performScrollTo()
            .performTextInput(
                "\u0645",
            )

        composeRule
            .onNodeWithTag(
                "medication_instruction",
            )
            .performScrollTo()
            .performTextInput(
                "\u0645",
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
            .performClick()

        composeRule
            .onNodeWithTag(
                "save_medication_schedule",
            )
            .performScrollTo()
            .performClick()

        waitForTag(
            tag =
                "post_setup_simple_mode_suggestion",
        )

        composeRule
            .onNodeWithTag(
                "post_setup_enable_simple_mode",
            )
            .assertIsDisplayed()
            .performClick()

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

        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
            runBlocking {
                userExperiencePreferenceStore
                    .state
                    .first()
                    .seniorMode ==
                        SeniorMode.SIMPLE
            }
        }

        assertEquals(
            SeniorMode.SIMPLE,
            runBlocking {
                userExperiencePreferenceStore
                    .state
                    .first()
                    .seniorMode
            },
        )

        assertTrue(
            runBlocking {
                setupStore
                    .setupComplete
                    .first()
            },
        )
    }
    private fun renderApp(
        setupPreferenceStore: SetupPreferenceStore,
        userExperiencePreferenceStore:
        InstrumentedUserExperiencePreferenceStore,
    ) {
        composeRule.setContent {
            val userExperienceState by
                userExperiencePreferenceStore
                    .state
                    .collectAsStateWithLifecycle(
                        initialValue =
                            UserExperiencePreferenceState(),
                    )

            CarePackTheme(
                seniorMode =
                    userExperienceState
                        .seniorMode,
            ) {
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
                                setupPreferenceStore,
                            reminderPreferenceStore =
                                OnboardingReminderPreferenceStore(),
                            reminderCoordinator =
                                OnboardingReminderCoordinator(),
                            reminderTestCoordinator =
                                ir.carepack.testing
                                    .InstrumentedReminderTestCoordinator(),
                            notificationPermissionGateway =
                                OnboardingNotificationPermissionGateway(),
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
                                userExperiencePreferenceStore,
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
        const val WAIT_TIMEOUT_MILLIS =
            30_000L
    }
}

private class OnboardingSetupPreferenceStore(
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

private class OnboardingReminderPreferenceStore :
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

private class OnboardingReminderCoordinator :
    ReminderCoordinator {

    override suspend fun currentStatus():
            ReminderStatus =
        ReminderStatus(
            remindersEnabled = false,
            notificationPermissionGranted = true,
            hasActiveSchedule = false,
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

private class OnboardingNotificationPermissionGateway :
    NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean =
        true

    override fun requiresRuntimePermission():
            Boolean =
        false
}
