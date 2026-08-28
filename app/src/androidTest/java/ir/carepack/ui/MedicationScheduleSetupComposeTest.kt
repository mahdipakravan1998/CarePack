package ir.carepack.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.reminder.ManufacturerGuidanceClassifier
import ir.carepack.feature.setup.FirstSetupReminderReadinessUiState
import ir.carepack.feature.setup.MedicationScheduleRoute
import ir.carepack.feature.setup.MedicationScheduleViewModel
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.testing.CarePlanRoomTestFixture
import ir.carepack.ui.theme.CarePackTheme
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

class MedicationScheduleSetupComposeTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    private lateinit var fixture:
            CarePlanRoomTestFixture

    private val context: Context
        get() =
            ApplicationProvider
                .getApplicationContext()

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
    fun firstMedicationSchedule_showsHonestReminderReliabilityGuidance() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        renderFirstSetup(
            recipientId = recipientId,
        )

        waitForTag(
            "first_setup_reminder_guidance",
        )

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance_title",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                context.getString(
                    R.string
                        .first_setup_reminder_guidance_title,
                ),
            )

        val body =
            context.getString(
                R.string
                    .first_setup_reminder_guidance_body,
            )

        assertTrue(
            body.contains(
                "اندروید",
            ),
        )

        assertTrue(
            body.contains(
                "باتری",
            ),
        )

        assertTrue(
            body.contains(
                "سازنده",
            ),
        )

        assertTrue(
            body.contains(
                "دیر برسند",
            ),
        )

        assertTrue(
            body.contains(
                "نمایش داده نشوند",
            ),
        )

        assertTrue(
            body.contains(
                "تضمین نمی‌کند",
            ),
        )

        assertFalse(
            body.contains(
                "تضمین می‌کند",
            ),
        )

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance_body",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                body,
            )

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance_settings_path",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "first_setup_readiness_actions",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance_open_reminder_settings",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance_continue",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun firstMedicationSchedule_notificationPermissionActionIsVisibleAndExplicitOnly() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val permissionRequests =
            AtomicInteger(0)

        renderFirstSetup(
            recipientId = recipientId,
            readiness =
                defaultReadiness(
                    notificationRuntimePermissionRequired =
                        true,
                    notificationPermissionGranted =
                        false,
                    notificationPermissionCanBeRequested =
                        true,
                ),
            onRequestNotificationPermission = {
                permissionRequests.incrementAndGet()
            },
        )

        waitForTag(
            "first_setup_request_notification_permission",
        )

        assertEquals(
            0,
            permissionRequests.get(),
        )

        composeRule
            .onNodeWithTag(
                "first_setup_notification_permission_status",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                context.getString(
                    R.string
                        .notification_permission_denied,
                ),
            )

        composeRule
            .onNodeWithTag(
                "first_setup_request_notification_permission",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            1,
            permissionRequests.get(),
        )

        composeRule
            .onNodeWithTag(
                "first_setup_open_notification_settings",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun firstMedicationSchedule_notificationSettingsActionIsVisibleWhenPermissionCannotBeRequested() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val openedNotificationSettings =
            AtomicBoolean(false)

        renderFirstSetup(
            recipientId = recipientId,
            readiness =
                defaultReadiness(
                    notificationRuntimePermissionRequired =
                        true,
                    notificationPermissionGranted =
                        false,
                    notificationPermissionCanBeRequested =
                        false,
                ),
            onOpenNotificationSettings = {
                openedNotificationSettings.set(
                    true,
                )
            },
        )

        waitForTag(
            "first_setup_open_notification_settings",
        )

        assertTagDoesNotExist(
            "first_setup_request_notification_permission",
        )

        composeRule
            .onNodeWithTag(
                "first_setup_open_notification_settings",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue(
            openedNotificationSettings.get(),
        )
    }

    @Test
    fun firstMedicationSchedule_exactAlarmFallbackAndSettingsActionAreVisibleWhenExactUnavailable() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val openedExactAlarmSettings =
            AtomicBoolean(false)

        renderFirstSetup(
            recipientId = recipientId,
            readiness =
                defaultReadiness(
                    notificationRuntimePermissionRequired =
                        true,
                    notificationPermissionGranted =
                        true,
                    exactAlarmRelevant =
                        true,
                    exactAlarmAvailable =
                        false,
                ),
            onRequestExactAlarmAccess = {
                openedExactAlarmSettings.set(
                    true,
                )
            },
        )

        waitForTag(
            "first_setup_approximate_fallback",
        )

        composeRule
            .onNodeWithTag(
                "first_setup_approximate_fallback",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                context.getString(
                    R.string
                        .exact_alarm_rationale_body,
                ),
            )

        composeRule
            .onNodeWithTag(
                "first_setup_request_exact_alarm_access",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue(
            openedExactAlarmSettings.get(),
        )
    }

    @Test
    fun firstMedicationSchedule_batteryOptimizationGuidanceAndSettingsActionAreVisibleWhenNotIgnored() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val openedBatterySettings =
            AtomicBoolean(false)

        renderFirstSetup(
            recipientId = recipientId,
            readiness =
                defaultReadiness(
                    notificationPermissionGranted =
                        true,
                    exactAlarmAvailable =
                        true,
                    batteryOptimizationState =
                        BatteryOptimizationState
                            .NOT_IGNORED,
                ),
            onOpenBatterySettings = {
                openedBatterySettings.set(
                    true,
                )
            },
        )

        waitForTag(
            "first_setup_battery_guidance_card",
        )

        composeRule
            .onNodeWithTag(
                "first_setup_battery_guidance_card",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "first_setup_battery_optimization_status",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                context.getString(
                    R.string
                        .battery_optimization_not_ignored,
                ),
            )

        composeRule
            .onNodeWithTag(
                "first_setup_open_battery_settings",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue(
            openedBatterySettings.get(),
        )
    }

    @Test
    fun firstMedicationSchedule_showsXiaomiSpecificOemGuidance() {
        assertOemGuidanceBody(
            manufacturer =
                "Xiaomi",
        )
    }

    @Test
    fun firstMedicationSchedule_showsXiaomiSpecificOemGuidanceForHyperOsAlias() {
        assertOemGuidanceBody(
            manufacturer =
                "HyperOS",
            expectedManufacturer =
                "Xiaomi",
        )
    }

    @Test
    fun firstMedicationSchedule_showsSamsungSpecificOemGuidance() {
        assertOemGuidanceBody(
            manufacturer =
                "Samsung",
        )
    }

    @Test
    fun firstMedicationSchedule_showsGenericOemGuidanceForUnknownManufacturer() {
        assertOemGuidanceBody(
            manufacturer =
                "Unknown",
        )
    }

    @Test
    fun firstMedicationSchedule_reminderSettingsActionInvokesCallbackWithoutRequestingPermission() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val openedReminderSettings =
            AtomicBoolean(false)

        val permissionRequests =
            AtomicInteger(0)

        renderFirstSetup(
            recipientId = recipientId,
            onOpenReminderSettings = {
                openedReminderSettings.set(
                    true,
                )
            },
            onRequestNotificationPermission = {
                permissionRequests.incrementAndGet()
            },
        )

        waitForTag(
            "first_setup_reminder_guidance_open_reminder_settings",
        )

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance_open_reminder_settings",
            )
            .performScrollTo()
            .performClick()

        assertTrue(
            openedReminderSettings.get(),
        )

        assertEquals(
            0,
            permissionRequests.get(),
        )

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance",
            )
            .assertIsDisplayed()

        assertTagDoesNotExist(
            "request_notification_permission",
        )
    }

    @Test
    fun firstMedicationSchedule_canBeSavedAfterContinuingWithoutPermissionChanges() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val setupStore =
            RecordingSetupPreferenceStore()

        val completed =
            AtomicBoolean(false)

        val permissionRequests =
            AtomicInteger(0)

        renderFirstSetup(
            recipientId = recipientId,
            setupPreferenceStore =
                setupStore,
            readiness =
                defaultReadiness(
                    notificationRuntimePermissionRequired =
                        true,
                    notificationPermissionGranted =
                        false,
                    notificationPermissionCanBeRequested =
                        true,
                    exactAlarmRelevant =
                        true,
                    exactAlarmAvailable =
                        false,
                    batteryOptimizationState =
                        BatteryOptimizationState
                            .NOT_IGNORED,
                    manufacturer =
                        "Xiaomi",
                ),
            onCompleted = {
                completed.set(
                    true,
                )
            },
            onRequestNotificationPermission = {
                permissionRequests.incrementAndGet()
            },
        )

        waitForTag(
            "first_setup_reminder_guidance_continue",
        )

        assertEquals(
            0,
            permissionRequests.get(),
        )

        composeRule
            .onNodeWithTag(
                "first_setup_reminder_guidance_continue",
            )
            .performScrollTo()
            .performClick()

        assertTagDoesNotExist(
            "first_setup_reminder_guidance",
        )

        assertEquals(
            0,
            permissionRequests.get(),
        )

        composeRule
            .onNodeWithTag(
                "medication_name",
            )
            .performScrollTo()
            .performTextInput(
                "داروی آزمون",
            )

        composeRule
            .onNodeWithTag(
                "medication_instruction",
            )
            .performScrollTo()
            .performTextInput(
                "بعد از غذا",
            )

        composeRule
            .onNodeWithTag(
                "medication_type",
            )
            .performScrollTo()
            .performTextInput(
                "قرص",
            )

        composeRule
            .onNodeWithTag(
                "dosage_text",
            )
            .performScrollTo()
            .performTextInput(
                "نصف",
            )

        composeRule
            .onNodeWithTag(
                "dose_unit",
            )
            .performScrollTo()
            .performTextInput(
                "عدد",
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
            .performClick()

        composeRule
            .onNodeWithTag(
                "save_medication_schedule",
            )
            .performScrollTo()
            .performClick()

        waitForTag(
            "post_setup_simple_mode_suggestion",
        )


        composeRule
            .onNodeWithTag(
                "post_setup_defer_simple_mode",
            )
            .assertIsDisplayed()

        assertFalse(
            completed.get(),
        )

        assertTrue(
            runBlocking {
                setupStore
                    .setupComplete
                    .first()
            },
        )

        assertTrue(
            runBlocking {
                fixture
                    .database
                    .medicationDao()
                    .count() == 1
            },
        )

        assertTrue(
            runBlocking {
                fixture
                    .database
                    .occurrenceDao()
                    .count() > 0
            },
        )

        composeRule
            .onNodeWithTag(
                "post_setup_defer_simple_mode",
            )
            .performSemanticsAction(
                SemanticsActions.OnClick,
            )

        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
            completed.get()
        }

        assertTrue(
            completed.get(),
        )

        val createdMedication =
            runBlocking {
                fixture
                    .carePlanService
                    .observeCarePlan()
                    .first()
                    ?.medications
                    ?.single()
            }

        assertEquals(
            "قرص",
            createdMedication?.medicationType,
        )

        assertEquals(
            "نصف",
            createdMedication?.dosageText,
        )

        assertEquals(
            "عدد",
            createdMedication?.doseUnit,
        )

        assertTagDoesNotExist(
            "request_notification_permission",
        )
    }

    @Test
    fun firstMedicationSchedule_pendingTimeDraftIsNotCommittedBySaveWithoutAddAction() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        renderFirstSetup(
            recipientId = recipientId,
        )

        waitForTag(
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
                "داروی آزمون",
            )

        composeRule
            .onNodeWithTag(
                "medication_instruction",
            )
            .performScrollTo()
            .performTextInput(
                "بعد از غذا",
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
                "save_medication_schedule",
            )
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()

        assertEquals(
            0,
            runBlocking {
                fixture.database
                    .medicationDao()
                    .count()
            },
        )

        composeRule
            .onNodeWithTag(
                "medication_schedule_error",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "add_time",
            )
            .assertIsEnabled()
    }

    @Test
    fun firstMedicationSchedule_postSetupSimpleModeSuggestionCanEnableSimpleMode() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val userExperienceStore =
            InMemoryUserExperiencePreferenceStore()

        val completed =
            AtomicBoolean(false)

        renderFirstSetup(
            recipientId = recipientId,
            userExperiencePreferenceStore =
                userExperienceStore,
            onCompleted = {
                completed.set(
                    true,
                )
            },
        )

        fillValidMedicationScheduleAndSave()

        waitForTag(
            "post_setup_simple_mode_suggestion",
        )


        composeRule
            .onNodeWithTag(
                "post_setup_enable_simple_mode",
            )

        assertFalse(
            completed.get(),
        )

        composeRule
            .onNodeWithTag(
                "post_setup_enable_simple_mode",
            )
            .performSemanticsAction(
                SemanticsActions.OnClick,
            )

        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
            completed.get()
        }

        assertEquals(
            SeniorMode.SIMPLE,
            runBlocking {
                userExperienceStore
                    .state
                    .first()
                    .seniorMode
            },
        )
    }

    @Test
    fun firstMedicationSchedule_postSetupSimpleModeSuggestionCanBeDeferredAndKeepsStandardMode() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val userExperienceStore =
            InMemoryUserExperiencePreferenceStore()

        val completed =
            AtomicBoolean(false)

        renderFirstSetup(
            recipientId = recipientId,
            userExperiencePreferenceStore =
                userExperienceStore,
            onCompleted = {
                completed.set(
                    true,
                )
            },
        )

        fillValidMedicationScheduleAndSave()

        waitForTag(
            "post_setup_simple_mode_suggestion",
        )

        composeRule
            .onNodeWithTag(
                "post_setup_defer_simple_mode",
            )
            .performSemanticsAction(
                SemanticsActions.OnClick,
            )

        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
            completed.get()
        }

        assertEquals(
            SeniorMode.STANDARD,
            runBlocking {
                userExperienceStore
                    .state
                    .first()
                    .seniorMode
            },
        )
    }

    @Test
    fun firstMedicationSchedule_doesNotShowPostSetupSuggestionWhenSimpleModeAlreadyEnabled() {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val userExperienceStore =
            InMemoryUserExperiencePreferenceStore(
                initialState =
                    UserExperiencePreferenceState(
                        seniorMode =
                            SeniorMode.SIMPLE,
                    ),
            )

        val completed =
            AtomicBoolean(false)

        renderFirstSetup(
            recipientId = recipientId,
            userExperiencePreferenceStore =
                userExperienceStore,
            onCompleted = {
                completed.set(
                    true,
                )
            },
        )

        fillValidMedicationScheduleAndSave()

        composeRule.waitUntil(
            timeoutMillis =
                WAIT_TIMEOUT_MILLIS,
        ) {
            completed.get()
        }

        assertTagDoesNotExist(
            "post_setup_simple_mode_suggestion",
        )

        assertEquals(
            SeniorMode.SIMPLE,
            runBlocking {
                userExperienceStore
                    .state
                    .first()
                    .seniorMode
            },
        )
    }

    private fun assertOemGuidanceBody(
        manufacturer: String,
        expectedManufacturer: String = manufacturer,
    ) {
        val recipientId =
            runBlocking {
                fixture.createOrGetRecipient()
            }

        val expectedGuidance =
            ManufacturerGuidanceClassifier
                .classify(
                    manufacturer =
                        expectedManufacturer,
                )

        renderFirstSetup(
            recipientId = recipientId,
            readiness =
                defaultReadiness(
                    manufacturer =
                        manufacturer,
                ),
        )

        waitForTag(
            "first_setup_oem_guidance_card",
        )

        composeRule
            .onNodeWithTag(
                "first_setup_oem_guidance_title",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                expectedGuidance.title,
            )

        composeRule
            .onNodeWithTag(
                "first_setup_oem_guidance_body",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                expectedGuidance.body,
            )

        composeRule
            .onNodeWithTag(
                "first_setup_oem_guidance_action_0",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(
                "• ${expectedGuidance.actionItems.first()}",
            )
    }

    private fun fillValidMedicationScheduleAndSave() {
        waitForTag(
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
                "داروی آزمون",
            )

        composeRule
            .onNodeWithTag(
                "medication_instruction",
            )
            .performScrollTo()
            .performTextInput(
                "بعد از غذا",
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
            .performClick()

        composeRule
            .onNodeWithTag(
                "save_medication_schedule",
            )
            .performScrollTo()
            .performClick()
    }

    private fun renderFirstSetup(
        recipientId: String,
        setupPreferenceStore:
        SetupPreferenceStore =
            RecordingSetupPreferenceStore(),
        readiness:
        FirstSetupReminderReadinessUiState =
            defaultReadiness(),
        userExperiencePreferenceStore:
        UserExperiencePreferenceStore =
            InMemoryUserExperiencePreferenceStore(),
        onCompleted: () -> Unit = {},
        onOpenReminderSettings: () -> Unit = {},
        onRequestNotificationPermission: () -> Unit = {},
        onOpenNotificationSettings: () -> Unit = {},
        onRequestExactAlarmAccess: () -> Unit = {},
        onOpenBatterySettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            CarePackTheme {
                FirstSetupRouteForTest(
                    recipientId =
                        recipientId,
                    setupPreferenceStore =
                        setupPreferenceStore,
                    readiness =
                        readiness,
                    userExperiencePreferenceStore =
                        userExperiencePreferenceStore,
                    onCompleted =
                        onCompleted,
                    onOpenReminderSettings =
                        onOpenReminderSettings,
                    onRequestNotificationPermission =
                        onRequestNotificationPermission,
                    onOpenNotificationSettings =
                        onOpenNotificationSettings,
                    onRequestExactAlarmAccess =
                        onRequestExactAlarmAccess,
                    onOpenBatterySettings =
                        onOpenBatterySettings,
                )
            }
        }
    }

    @Composable
    private fun FirstSetupRouteForTest(
        recipientId: String,
        setupPreferenceStore:
        SetupPreferenceStore,
        readiness:
        FirstSetupReminderReadinessUiState,
        userExperiencePreferenceStore:
        UserExperiencePreferenceStore,
        onCompleted: () -> Unit,
        onOpenReminderSettings: () -> Unit,
        onRequestNotificationPermission: () -> Unit,
        onOpenNotificationSettings: () -> Unit,
        onRequestExactAlarmAccess: () -> Unit,
        onOpenBatterySettings: () -> Unit,
    ) {
        val viewModel:
                MedicationScheduleViewModel =
            viewModel(
                factory =
                    MedicationScheduleViewModel
                        .factory(
                            recipientId =
                                recipientId,
                            carePlanService =
                                fixture
                                    .carePlanService,
                            setupPreferenceStore =
                                setupPreferenceStore,
                            userExperiencePreferenceStore =
                                userExperiencePreferenceStore,
                            completeInitialSetup =
                                true,
                            clock =
                                fixture.clock,
                            zoneProvider =
                                zoneProvider,
                        ),
            )

        MedicationScheduleRoute(
            viewModel = viewModel,
            onCompleted =
                onCompleted,
            onOpenReminderSettings =
                onOpenReminderSettings,
            firstSetupReminderReadiness =
                readiness,
            onFirstSetupRequestNotificationPermission =
                onRequestNotificationPermission,
            onFirstSetupOpenNotificationSettings =
                onOpenNotificationSettings,
            onFirstSetupRequestExactAlarmAccess =
                onRequestExactAlarmAccess,
            onFirstSetupOpenBatterySettings =
                onOpenBatterySettings,
        )
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
                    testTag = tag,
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
            30_000L

        fun defaultReadiness(
            notificationRuntimePermissionRequired: Boolean = true,
            notificationPermissionGranted: Boolean = false,
            notificationPermissionCanBeRequested: Boolean = true,
            exactAlarmRelevant: Boolean = true,
            exactAlarmAvailable: Boolean = false,
            batteryOptimizationState:
            BatteryOptimizationState =
                BatteryOptimizationState.UNKNOWN,
            manufacturer: String? = "Google",
        ): FirstSetupReminderReadinessUiState =
            FirstSetupReminderReadinessUiState(
                notificationRuntimePermissionRequired =
                    notificationRuntimePermissionRequired,
                notificationPermissionGranted =
                    notificationPermissionGranted,
                notificationPermissionCanBeRequested =
                    notificationPermissionCanBeRequested,
                exactAlarmRelevant =
                    exactAlarmRelevant,
                exactAlarmAvailable =
                    exactAlarmAvailable,
                batteryOptimizationState =
                    batteryOptimizationState,
                manufacturer =
                    manufacturer,
            )
    }
}

private class RecordingSetupPreferenceStore :
    SetupPreferenceStore {

    private val mutableSetupComplete =
        MutableStateFlow(
            false,
        )

    override val setupComplete:
            Flow<Boolean> =
        mutableSetupComplete

    override suspend fun markSetupComplete() {
        mutableSetupComplete.value =
            true
    }
}

private class InMemoryUserExperiencePreferenceStore(
    initialState: UserExperiencePreferenceState =
        UserExperiencePreferenceState(),
) : UserExperiencePreferenceStore {

    private val mutableState =
        MutableStateFlow(
            initialState,
        )

    override val state:
            Flow<UserExperiencePreferenceState> =
        mutableState

    override suspend fun setFirstDayOfWeekPreference(
        preference: FirstDayOfWeekPreference,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    firstDayOfWeekPreference =
                        preference,
                )
    }

    override suspend fun setSeniorMode(
        seniorMode: SeniorMode,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    seniorMode =
                        seniorMode,
                )
    }
}
