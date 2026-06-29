package ir.carepack.ui

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.CarePackApp
import ir.carepack.core.id.IdSource
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.ArchiveMedicationOutcome
import ir.carepack.domain.careplan.CarePlanOverview
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.careplan.MedicationEditorSnapshot
import ir.carepack.domain.careplan.RoomCarePlanService
import ir.carepack.domain.careplan.SetupProgress
import ir.carepack.domain.careplan.StopMedicationOutcome
import ir.carepack.domain.careplan.UpdateMedicationTextCommand
import ir.carepack.domain.careplan.UpdateMedicationTextOutcome
import ir.carepack.domain.careplan.UpdateRecipientNameCommand
import ir.carepack.domain.careplan.UpdateRecipientNameOutcome
import ir.carepack.domain.careplan.UpdateScheduleCommand
import ir.carepack.domain.careplan.UpdateScheduleOutcome
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.report.RoomTodayReportFormatter
import ir.carepack.domain.today.RoomTodayQueryService
import ir.carepack.feature.careplan.CarePlanRoute
import ir.carepack.feature.careplan.CarePlanViewModel
import ir.carepack.feature.careplan.ScheduleEditRoute
import ir.carepack.feature.careplan.ScheduleEditViewModel
import ir.carepack.feature.setup.MedicationScheduleRoute
import ir.carepack.feature.setup.MedicationScheduleViewModel
import ir.carepack.feature.setup.RecipientSetupRoute
import ir.carepack.feature.setup.RecipientSetupViewModel
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.testing.InstrumentedPrivacyPreferenceStore
import ir.carepack.testing.RecordingDataDeletionCoordinator
import ir.carepack.testing.RecordingPrivacyPolicyGateway
import ir.carepack.testing.RecordingTextShareGateway
import ir.carepack.ui.theme.CarePackTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private lateinit var database:
            CarePackDatabase

    private lateinit var testClock:
            ComposeTestClock

    private lateinit var testIdSource:
            ComposeTestIdSource

    private lateinit var occurrenceGenerator:
            RoomOccurrenceGenerator

    private lateinit var carePlanService:
            RoomCarePlanService

    private val fixedClock: Clock =
        Clock.fixed(
            Instant.parse(
                "2026-06-24T08:00:00Z",
            ),
            ZoneOffset.UTC,
        )

    private val tehranZoneProvider =
        ZoneProvider {
            ZoneId.of(
                "Asia/Tehran",
            )
        }

    @Before
    fun setUp() {
        logStep(
            "SETUP_START",
        )

        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        database =
            Room
                .inMemoryDatabaseBuilder(
                    context,
                    CarePackDatabase::class.java,
                )
                .build()

        testClock =
            ComposeTestClock(
                instant =
                    Instant.parse(
                        "2026-06-24T06:00:00Z",
                    ),
            )

        testIdSource =
            ComposeTestIdSource()

        occurrenceGenerator =
            RoomOccurrenceGenerator(
                database = database,
                idSource = testIdSource,
                candidateResolver =
                    OccurrenceCandidateResolver(),
            )

        carePlanService =
            RoomCarePlanService(
                database = database,
                occurrenceGenerator =
                    occurrenceGenerator,
                clock = testClock,
                idSource = testIdSource,
            )

        logStep(
            "SETUP_FINISHED",
        )
    }

    @After
    fun tearDown() {
        logStep(
            "TEARDOWN_START",
        )

        if (
            ::database.isInitialized
        ) {
            database.close()
        }

        logStep(
            "TEARDOWN_FINISHED",
        )
    }

    @Test
    fun fullSetup_navigatesToToday_andRecordsGiven() {
        logStep(
            "TEST_START",
        )

        val ids =
            UiSequenceIdSource(
                "recipient-1",
                "medication-1",
                "series-1",
                "version-1",
                "occurrence-1",
                "occurrence-2",
            )

        val generator =
            RoomOccurrenceGenerator(
                database = database,
                idSource = ids,
                candidateResolver =
                    OccurrenceCandidateResolver(),
            )

        val service =
            RoomCarePlanService(
                database = database,
                occurrenceGenerator =
                    generator,
                clock = fixedClock,
                idSource = ids,
            )

        val todayQueryService =
            RoomTodayQueryService(
                database = database,
            )

        val reportService =
            RoomCaregiverReportService(
                database = database,
                clock = fixedClock,
            )

        val preferenceStore =
            InMemorySetupPreferenceStore()

        val reminderPreferenceStore =
            InMemoryReminderPreferenceStore()

        val reminderCoordinator =
            InMemoryReminderCoordinator()

        val notificationPermissionGateway =
            GrantedNotificationPermissionGateway()

        composeRule.setContent {
            CarePackTheme {
                CarePackApp(
                    carePlanService =
                        service,
                    todayQueryService =
                        todayQueryService,
                    caregiverReportService =
                        reportService,
                    setupPreferenceStore =
                        preferenceStore,
                    reminderPreferenceStore =
                        reminderPreferenceStore,
                    reminderCoordinator =
                        reminderCoordinator,
                    notificationPermissionGateway =
                        notificationPermissionGateway,
                    todayReportFormatter =
                        RoomTodayReportFormatter(
                            database = database,
                        ),
                    privacyPreferenceStore =
                        InstrumentedPrivacyPreferenceStore(),
                    textShareGateway =
                        RecordingTextShareGateway(),
                    privacyPolicyGateway =
                        RecordingPrivacyPolicyGateway(),
                    dataDeletionCoordinator =
                        RecordingDataDeletionCoordinator(),
                    clock = fixedClock,
                    zoneProvider =
                        tehranZoneProvider,
                )
            }
        }

        waitForTag(
            tag =
                "onboarding_local_summary",
        )

        composeRule
            .onNodeWithTag(
                "onboarding_local_summary",
            )
            .assertTextEquals(
                "اطلاعات این نسخه فقط روی همین دستگاه نگهداری می‌شود و به اینترنت ارسال نمی‌شود.",
            )

        composeRule
            .onNodeWithTag(
                "onboarding_non_medical_summary",
            )
            .assertTextEquals(
                "کرپک ابزار ثبت و یادآوری مراقبت است و جایگزین نظر پزشک، داروساز یا خدمات اورژانسی نیست.",
            )

        composeRule
            .onNodeWithTag(
                "onboarding_continue",
            )
            .assertExists()
            .performClick()

        composeRule.waitForIdle()

        waitForTag(
            tag =
                "recipient_name",
        )

        composeRule
            .onNodeWithTag(
                "recipient_name",
            )
            .assertExists()
            .performTextInput(
                "Test recipient",
            )

        closeSoftKeyboard()

        composeRule
            .onNodeWithTag(
                "recipient_save",
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitForIdle()

        waitForTag(
            tag =
                "medication_name",
        )

        composeRule
            .onNodeWithTag(
                "medication_name",
            )
            .assertExists()
            .performTextInput(
                "Test medication",
            )

        composeRule
            .onNodeWithTag(
                "medication_instruction",
            )
            .assertExists()
            .performTextInput(
                "Take after meal",
            )

        closeSoftKeyboard()

        composeRule
            .onNodeWithTag(
                "medication_schedule_save",
            )
            .performScrollTo()
            .assertIsDisplayed()

        logStep(
            "BEFORE_MEDICATION_SAVE",
        )

        composeRule
            .onNodeWithTag(
                "medication_schedule_save",
            )
            .performClick()

        composeRule.waitForIdle()

        waitForTag(
            tag =
                TODAY_OCCURRENCE_TAG,
        )

        logStep(
            "TODAY_SCREEN_READY",
        )

        composeRule
            .onNodeWithTag(
                TODAY_OCCURRENCE_TAG,
            )
            .assertExists()
            .performClick()

        composeRule.waitForIdle()

        waitForTag(
            tag =
                "record_given",
        )

        logStep(
            "DETAIL_SCREEN_READY",
        )

        composeRule
            .onNodeWithTag(
                "record_given",
            )
            .assertExists()
            .performClick()

        composeRule.waitForIdle()

        waitForTag(
            tag =
                "current_report_state",
        )

        composeRule
            .onNodeWithTag(
                "current_report_state",
            )
            .assertTextEquals(
                "مراقب ثبت کرده است که دارو داده شده است",
            )

        logStep(
            "TEST_FINISHED",
        )
    }

    @Test
    fun completedDeletion_returnsNavigationToFirstLaunch() {
        createPlan()

        val deletionCoordinator =
            RecordingDataDeletionCoordinator()

        composeRule.setContent {
            CarePackTheme {
                CarePackApp(
                    carePlanService =
                        carePlanService,
                    todayQueryService =
                        RoomTodayQueryService(
                            database = database,
                        ),
                    caregiverReportService =
                        RoomCaregiverReportService(
                            database = database,
                            clock = testClock,
                        ),
                    setupPreferenceStore =
                        InMemorySetupPreferenceStore(),
                    reminderPreferenceStore =
                        InMemoryReminderPreferenceStore(),
                    reminderCoordinator =
                        InMemoryReminderCoordinator(),
                    notificationPermissionGateway =
                        GrantedNotificationPermissionGateway(),
                    todayReportFormatter =
                        RoomTodayReportFormatter(
                            database = database,
                        ),
                    privacyPreferenceStore =
                        InstrumentedPrivacyPreferenceStore(),
                    textShareGateway =
                        RecordingTextShareGateway(),
                    privacyPolicyGateway =
                        RecordingPrivacyPolicyGateway(),
                    dataDeletionCoordinator =
                        deletionCoordinator,
                    clock = testClock,
                    zoneProvider =
                        tehranZoneProvider,
                )
            }
        }

        waitForTag(
            tag =
                "open_settings",
        )

        composeRule
            .onNodeWithTag(
                "open_settings",
            )
            .performClick()

        waitForTag(
            tag =
                "settings_delete_all",
        )

        composeRule
            .onNodeWithTag(
                "settings_delete_all",
            )
            .performScrollTo()
            .performClick()

        waitForTag(
            tag =
                "delete_all_data_request",
        )

        composeRule
            .onNodeWithTag(
                "delete_all_data_request",
            )
            .performScrollTo()
            .performClick()

        waitForTag(
            tag =
                "delete_all_data_confirm",
        )

        composeRule
            .onNodeWithTag(
                "delete_all_data_confirm",
            )
            .performClick()

        waitForTag(
            tag =
                "onboarding_screen",
        )

        composeRule
            .onNodeWithTag(
                "onboarding_screen",
            )
            .assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(
                1,
                deletionCoordinator
                    .deleteCount,
            )
        }
    }

    @Test
    fun storageFailure_doesNotNavigateOrShowSuccess() {
        val continueInvoked =
            AtomicBoolean(
                false,
            )

        val expectedError =
            "ذخیره‌سازی انجام نشد. دوباره تلاش کنید."

        val viewModel =
            RecipientSetupViewModel(
                carePlanService =
                    FailingCarePlanService(),
            )

        composeRule.setContent {
            CarePackTheme {
                RecipientSetupRoute(
                    viewModel = viewModel,
                    onContinue = {
                        continueInvoked.set(
                            true,
                        )
                    },
                )
            }
        }

        /*
         * This test intentionally does not search for recipient_name,
         * does not enter text through the Xiaomi IME, and does not wait
         * for a semantics tag before exercising the ViewModel.
         */
        composeRule.runOnIdle {
            viewModel.onDisplayNameChanged(
                "Test recipient",
            )

            viewModel.save()
        }

        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val state =
                viewModel.state.value

            assertFalse(
                state.isSaving,
            )

            assertEquals(
                "Test recipient",
                state.displayName,
            )

            assertEquals(
                expectedError,
                state.errorMessage,
            )

            assertFalse(
                continueInvoked.get(),
            )
        }
    }

    @Test
    fun invalidMedicationForm_showsFieldErrors_andWritesNothing() {
        val recipientId =
            createRecipient()

        val viewModel =
            MedicationScheduleViewModel(
                recipientId =
                    recipientId,
                carePlanService =
                    carePlanService,
                setupPreferenceStore =
                    InMemorySetupPreferenceStore(),
                completeInitialSetup =
                    false,
                clock =
                    testClock,
                zoneProvider =
                    tehranZoneProvider,
            )

        composeRule.setContent {
            CarePackTheme {
                MedicationScheduleRoute(
                    viewModel = viewModel,
                    onCompleted = {},
                )
            }
        }

        waitForTag(
            tag =
                "medication_schedule_save",
        )

        composeRule
            .onNodeWithTag(
                "medication_schedule_save",
            )
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(
                "نام دارو نمی‌تواند خالی باشد.",
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "دستور مصرف یا توضیح مراقبت نمی‌تواند خالی باشد.",
            )
            .performScrollTo()
            .assertIsDisplayed()

        runBlocking {
            assertEquals(
                0,
                database
                    .medicationDao()
                    .count(),
            )

            assertEquals(
                0,
                database
                    .scheduleDao()
                    .countVersions(),
            )

            assertEquals(
                0,
                database
                    .occurrenceDao()
                    .count(),
            )
        }
    }

    @Test
    fun invalidRecipientRename_staysOpen_showsFieldError_andKeepsValue() {
        val plan =
            createPlan()

        val viewModel =
            CarePlanViewModel(
                carePlanService =
                    carePlanService,
            )

        composeRule.setContent {
            CarePackTheme {
                CarePlanRoute(
                    viewModel = viewModel,
                    onBack = {},
                    onAddMedication = {},
                    onEditMedicationText = {},
                    onEditSchedule = {},
                )
            }
        }

        waitForTag(
            tag =
                "edit_recipient_button",
        )

        composeRule
            .onNodeWithTag(
                "edit_recipient_button",
            )
            .performClick()

        waitForTag(
            tag =
                "recipient_rename_field",
        )

        composeRule
            .onNodeWithTag(
                "recipient_rename_field",
            )
            .performClick()
            .performTextReplacement(
                "",
            )

        closeSoftKeyboard()

        composeRule
            .onNodeWithTag(
                "recipient_rename_save",
            )
            .performClick()

        waitForTag(
            tag =
                "recipient_rename_error",
            useUnmergedTree =
                true,
        )

        composeRule
            .onNodeWithTag(
                testTag =
                    "recipient_rename_error",
                useUnmergedTree =
                    true,
            )
            .assertIsDisplayed()
            .assertTextEquals(
                "نام فرد تحت مراقبت نمی‌تواند خالی باشد.",
            )

        composeRule
            .onNodeWithTag(
                "recipient_rename_field",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "recipient_rename_save",
            )
            .assertIsDisplayed()

        runBlocking {
            assertEquals(
                "فرد نمونه",
                database
                    .careRecipientDao()
                    .getSingleton()
                    ?.displayName,
            )

            assertNotNull(
                database
                    .medicationDao()
                    .getById(
                        plan.medicationId,
                    ),
            )
        }
    }

    @Test
    fun scheduleEdit_supportsMultipleTimesAndOptionalDates() {
        val plan =
            createPlan()

        val completed =
            AtomicBoolean(
                false,
            )

        val viewModel =
            ScheduleEditViewModel(
                medicationId =
                    plan.medicationId,
                carePlanService =
                    carePlanService,
                zoneProvider =
                    tehranZoneProvider,
            )

        composeRule.setContent {
            CarePackTheme {
                ScheduleEditRoute(
                    viewModel = viewModel,
                    onBack = {},
                    onCompleted = {
                        completed.set(
                            true,
                        )
                    },
                )
            }
        }

        waitForTag(
            tag =
                "schedule_time_draft",
        )

        composeRule
            .onNodeWithTag(
                "schedule_time_draft",
            )
            .performTextInput(
                "14:30",
            )

        composeRule
            .onNodeWithTag(
                "schedule_time_add",
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                "schedule_start_date",
            )
            .performTextReplacement(
                "2026-06-24",
            )

        composeRule
            .onNodeWithTag(
                "schedule_end_date",
            )
            .performTextReplacement(
                "2026-07-01",
            )

        closeSoftKeyboard()

        composeRule
            .onNodeWithText(
                "ذخیره تغییرات",
            )
            .performScrollTo()
            .performClick()

        waitUntil {
            completed.get()
        }

        val editor =
            runBlocking {
                carePlanService
                    .getMedicationEditor(
                        plan.medicationId,
                    )
            }

        assertEquals(
            listOf(
                LocalTime.of(
                    12,
                    0,
                ),
                LocalTime.of(
                    14,
                    30,
                ),
            ),
            editor
                ?.schedule
                ?.times,
        )

        assertEquals(
            LocalDate.parse(
                "2026-06-24",
            ),
            editor
                ?.schedule
                ?.startDate,
        )

        assertEquals(
            LocalDate.parse(
                "2026-07-01",
            ),
            editor
                ?.schedule
                ?.endDate,
        )
    }

    @Test
    fun activeMedication_cannotArchive_thenStopAndArchiveHidesIt() {
        val plan =
            createPlan()

        val occurrenceCountBefore =
            runBlocking {
                database
                    .occurrenceDao()
                    .count()
            }

        val viewModel =
            CarePlanViewModel(
                carePlanService =
                    carePlanService,
            )

        composeRule.setContent {
            CarePackTheme {
                CarePlanRoute(
                    viewModel = viewModel,
                    onBack = {},
                    onAddMedication = {},
                    onEditMedicationText = {},
                    onEditSchedule = {},
                )
            }
        }

        val medicationCardTag =
            "medication_card_${plan.medicationId}"

        val stopMedicationTag =
            "stop_medication_${plan.medicationId}"

        val confirmStopTag =
            "confirm_stop_${plan.medicationId}"

        val archiveMedicationTag =
            "archive_medication_${plan.medicationId}"

        val confirmArchiveTag =
            "confirm_archive_${plan.medicationId}"

        waitForTag(
            tag =
                medicationCardTag,
        )

        assertTagDoesNotExist(
            tag =
                archiveMedicationTag,
        )

        composeRule
            .onNodeWithTag(
                stopMedicationTag,
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitForIdle()

        waitForTag(
            tag =
                confirmStopTag,
            useUnmergedTree =
                true,
        )

        composeRule
            .onNodeWithTag(
                testTag =
                    confirmStopTag,
                useUnmergedTree =
                    true,
            )
            .assertIsDisplayed()
            .performClick()

        composeRule.waitForIdle()

        waitForTag(
            tag =
                archiveMedicationTag,
        )

        composeRule
            .onNodeWithTag(
                archiveMedicationTag,
            )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitForIdle()

        waitForTag(
            tag =
                confirmArchiveTag,
            useUnmergedTree =
                true,
        )

        composeRule
            .onNodeWithTag(
                testTag =
                    confirmArchiveTag,
                useUnmergedTree =
                    true,
            )
            .assertIsDisplayed()
            .performClick()

        composeRule.waitForIdle()

        waitUntil {
            composeRule
                .onAllNodesWithTag(
                    medicationCardTag,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )
                .isEmpty()
        }

        runBlocking {
            val medication =
                database
                    .medicationDao()
                    .getById(
                        plan.medicationId,
                    )

            assertNotNull(
                medication
                    ?.stoppedAtEpochMillis,
            )

            assertNotNull(
                medication
                    ?.archivedAtEpochMillis,
            )

            assertEquals(
                occurrenceCountBefore,
                database
                    .occurrenceDao()
                    .count(),
            )
        }
    }

    private fun createRecipient(): String {
        return runBlocking {
            when (
                val outcome =
                    carePlanService
                        .createRecipient(
                            CreateRecipientCommand(
                                displayName =
                                    "فرد نمونه",
                            ),
                        )
            ) {
                is CreateRecipientOutcome.Created -> {
                    outcome.recipientId
                }

                is CreateRecipientOutcome.AlreadyExists -> {
                    outcome.recipientId
                }

                is CreateRecipientOutcome.Invalid -> {
                    error(
                        "Recipient creation failed.",
                    )
                }
            }
        }
    }

    private fun createPlan():
            ComposeTestPlan {
        val recipientId =
            createRecipient()

        return runBlocking {
            val outcome =
                carePlanService
                    .createMedicationAndSchedule(
                        CreateMedicationScheduleCommand(
                            recipientId =
                                recipientId,
                            medicationName =
                                "داروی نمونه",
                            instruction =
                                "دستور نمونه",
                            weekdays =
                                setOf(
                                    DayOfWeek.WEDNESDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    12 * 60,
                                ),
                            startDate =
                                LocalDate.parse(
                                    "2026-06-24",
                                ),
                            endDate =
                                LocalDate.parse(
                                    "2026-07-01",
                                ),
                            zoneId =
                                "Asia/Tehran",
                        ),
                    )

            val created =
                outcome as?
                        CreateMedicationScheduleOutcome.Created
                    ?: error(
                        "Medication schedule creation failed.",
                    )

            ComposeTestPlan(
                medicationId =
                    created.medicationId,
            )
        }
    }

    private fun assertTagDoesNotExist(
        tag: String,
    ) {
        val nodes =
            composeRule
                .onAllNodesWithTag(
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

    private fun waitForTag(
        tag: String,
        useUnmergedTree: Boolean = false,
    ) {
        logStep(
            "WAIT_FOR_TAG:$tag",
        )

        composeRule.waitUntil(
            timeoutMillis =
                TEST_TIMEOUT_MILLIS,
        ) {
            composeRule
                .onAllNodesWithTag(
                    testTag =
                        tag,
                    useUnmergedTree =
                        useUnmergedTree,
                )
                .fetchSemanticsNodes(
                    atLeastOneRootRequired =
                        false,
                )
                .isNotEmpty()
        }

        logStep(
            "FOUND_TAG:$tag",
        )
    }

    private fun waitUntil(
        condition: () -> Boolean,
    ) {
        composeRule.waitUntil(
            timeoutMillis =
                TEST_TIMEOUT_MILLIS,
            condition = condition,
        )
    }

    private fun logStep(
        message: String,
    ) {
        Log.i(
            TEST_LOG_TAG,
            message,
        )
    }

    private companion object {

        const val TEST_TIMEOUT_MILLIS =
            20_000L

        const val TODAY_OCCURRENCE_TAG =
            "today_item_occurrence-1"

        const val TEST_LOG_TAG =
            "CarePackComposeTest"
    }
}

private data class ComposeTestPlan(
    val medicationId: String,
)

private class FailingCarePlanService :
    CarePlanService {

    override suspend fun createRecipient(
        command: CreateRecipientCommand,
    ): CreateRecipientOutcome {
        throw IllegalStateException(
            "Synthetic storage failure.",
        )
    }

    override suspend fun updateRecipientName(
        command: UpdateRecipientNameCommand,
    ): UpdateRecipientNameOutcome {
        return UpdateRecipientNameOutcome
            .NotFound
    }

    override suspend fun createMedicationAndSchedule(
        command:
        CreateMedicationScheduleCommand,
    ): CreateMedicationScheduleOutcome {
        return CreateMedicationScheduleOutcome
            .Invalid(
                errors = emptyList(),
            )
    }

    override suspend fun updateMedicationText(
        command: UpdateMedicationTextCommand,
    ): UpdateMedicationTextOutcome {
        return UpdateMedicationTextOutcome
            .NotFound
    }

    override suspend fun updateSchedule(
        command: UpdateScheduleCommand,
    ): UpdateScheduleOutcome {
        return UpdateScheduleOutcome
            .NotFound
    }

    override suspend fun stopMedication(
        medicationId: String,
    ): StopMedicationOutcome {
        return StopMedicationOutcome
            .NotFound
    }

    override suspend fun archiveMedication(
        medicationId: String,
    ): ArchiveMedicationOutcome {
        return ArchiveMedicationOutcome
            .NotFound
    }

    override suspend fun getSetupProgress():
            SetupProgress {
        return SetupProgress.Empty
    }

    override fun observeCarePlan():
            Flow<CarePlanOverview?> {
        return flowOf(
            null,
        )
    }

    override suspend fun getMedicationEditor(
        medicationId: String,
    ): MedicationEditorSnapshot? {
        return null
    }
}

private class InMemorySetupPreferenceStore :
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

private class InMemoryReminderPreferenceStore :
    ReminderPreferenceStore {

    private val mutableState =
        MutableStateFlow(
            ReminderPreferenceState(
                remindersEnabled =
                    false,
            ),
        )

    override val state:
            Flow<ReminderPreferenceState> =
        mutableState

    override suspend fun setRemindersEnabled(
        enabled: Boolean,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    remindersEnabled =
                        enabled,
                )
    }

    override suspend fun observeDeviceZone(
        zoneId: String,
    ): TimezoneObservation {
        val previousZoneId =
            mutableState
                .value
                .lastObservedZoneId

        return when {
            previousZoneId == null -> {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            lastObservedZoneId =
                                zoneId,
                        )

                TimezoneObservation.Initialized
            }

            previousZoneId == zoneId -> {
                TimezoneObservation.Unchanged
            }

            else -> {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            lastObservedZoneId =
                                zoneId,
                        )

                TimezoneObservation.Unchanged
            }
        }
    }

    override suspend fun dismissTimezoneWarning() {
        mutableState.value =
            mutableState
                .value
                .copy(
                    timezoneWarning = null,
                )
    }
}

private class InMemoryReminderCoordinator :
    ReminderCoordinator {

    private val status =
        ReminderStatus(
            remindersEnabled =
                false,
            notificationPermissionGranted =
                true,
            hasActiveSchedule =
                true,
            exactAlarmCapabilityGranted =
                false,
            availability =
                ReminderAvailability.DISABLED,
        )

    override suspend fun currentStatus():
            ReminderStatus {
        return status
    }

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        return ReminderReconciliationResult
            .Reconciled(
                reason = reason,
                status = status,
                scheduledCount = 0,
                cancelledCount = 0,
            )
    }

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult {
        error(
            "Alarm fire is not used by this test.",
        )
    }

    override suspend fun cancelAllOwnedReminderState() {
        Unit
    }
}

private class GrantedNotificationPermissionGateway :
    NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean {
        return true
    }

    override fun requiresRuntimePermission():
            Boolean {
        return true
    }
}

private class UiSequenceIdSource(
    vararg fixedIds: String,
) : IdSource {

    private val remainingFixedIds =
        ArrayDeque(
            fixedIds.toList(),
        )

    private var nextOccurrenceNumber =
        3

    override fun nextId(): String {
        if (
            remainingFixedIds.isNotEmpty()
        ) {
            return remainingFixedIds
                .removeFirst()
        }

        val generatedId =
            "occurrence-$nextOccurrenceNumber"

        nextOccurrenceNumber += 1

        return generatedId
    }
}

private class ComposeTestIdSource :
    IdSource {

    private val counter =
        AtomicInteger(
            0,
        )

    override fun nextId(): String {
        return "compose-test-${counter.incrementAndGet()}"
    }
}

private class ComposeTestClock(
    private val instant: Instant,
) : Clock() {

    override fun getZone(): ZoneId {
        return ZoneOffset.UTC
    }

    override fun withZone(
        zone: ZoneId,
    ): Clock {
        return this
    }

    override fun instant(): Instant {
        return instant
    }
}
