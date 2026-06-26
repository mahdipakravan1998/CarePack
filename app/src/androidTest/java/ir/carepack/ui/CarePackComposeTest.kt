package ir.carepack.ui

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.today.RoomTodayQueryService
import ir.carepack.feature.setup.RecipientSetupRoute
import ir.carepack.feature.setup.RecipientSetupViewModel
import ir.carepack.ui.theme.CarePackTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertFalse
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

    private val fixedClock: Clock =
        Clock.fixed(
            Instant.parse(
                "2026-06-24T08:00:00Z",
            ),
            ZoneOffset.UTC,
        )

    private val tehranZoneProvider =
        ZoneProvider {
            ZoneId.of("Asia/Tehran")
        }

    @Before
    fun setUp() {
        logStep("SETUP_START")

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

        logStep("SETUP_FINISHED")
    }

    @After
    fun tearDown() {
        logStep("TEARDOWN_START")

        if (::database.isInitialized) {
            database.close()
        }

        logStep("TEARDOWN_FINISHED")
    }

    @Test
    fun fullSetup_navigatesToToday_andRecordsGiven() {
        logStep("TEST_START")

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

        val carePlanService =
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

        composeRule.setContent {
            CarePackTheme {
                CarePackApp(
                    carePlanService =
                        carePlanService,
                    todayQueryService =
                        todayQueryService,
                    caregiverReportService =
                        reportService,
                    setupPreferenceStore =
                        preferenceStore,
                    clock = fixedClock,
                    zoneProvider =
                        tehranZoneProvider,
                )
            }
        }

        waitForTag(
            tag = "onboarding_local_summary",
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

        runQueuedMainTasks()

        waitForTag(
            tag = "recipient_name",
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

        /*
         * createRecipient() is launched in viewModelScope.
         * Compose testing v2 queues it on the test main dispatcher.
         */
        runQueuedMainTasks()

        waitForTag(
            tag = "medication_name",
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

        /*
         * The keyboard reduces the visible viewport and the save button
         * is located below the currently visible portion of the form.
         */
        closeSoftKeyboard()

        composeRule
            .onNodeWithTag(
                "medication_schedule_save",
            )
            .performScrollTo()
            .assertIsDisplayed()

        logStep("BEFORE_MEDICATION_SAVE")

        composeRule
            .onNodeWithTag(
                "medication_schedule_save",
            )
            .performClick()

        /*
         * Start the ViewModel save coroutine. It suspends while Room
         * performs the transaction and resumes later on test Main.
         */
        runQueuedMainTasks()

        waitForTag(
            tag = TODAY_OCCURRENCE_TAG,
        )

        logStep("TODAY_SCREEN_READY")

        composeRule
            .onNodeWithTag(
                TODAY_OCCURRENCE_TAG,
            )
            .assertExists()
            .performClick()

        runQueuedMainTasks()

        waitForTag(
            tag = "record_given",
        )

        logStep("DETAIL_SCREEN_READY")

        composeRule
            .onNodeWithTag(
                "record_given",
            )
            .assertExists()
            .performClick()

        runQueuedMainTasks()

        waitForTag(
            tag = "given_status",
        )

        composeRule
            .onNodeWithTag(
                "given_status",
            )
            .assertExists()

        logStep("TEST_FINISHED")
    }

    @Test
    fun storageFailure_doesNotNavigateOrShowSuccess() {
        val continueInvoked =
            AtomicBoolean(false)

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

        waitForTag(
            tag = "recipient_name",
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

        runQueuedMainTasks()

        waitForTag(
            tag = "recipient_error",
        )

        composeRule
            .onNodeWithTag(
                "recipient_error",
            )
            .assertTextEquals(
                "ذخیره‌سازی انجام نشد. دوباره تلاش کنید.",
            )

        composeRule.runOnIdle {
            assertFalse(
                continueInvoked.get(),
            )
        }

        composeRule
            .onNodeWithTag(
                "recipient_save",
            )
            .assertExists()
    }

    private fun waitForTag(
        tag: String,
    ) {
        logStep(
            "WAIT_FOR_TAG:$tag",
        )

        composeRule.waitUntil(
            timeoutMillis =
                TEST_TIMEOUT_MILLIS,
        ) {
            /*
             * Start or resume ViewModel coroutines that have returned
             * from Room or another background dispatcher.
             *
             * Do not advance Compose frames here. Advancing frames while
             * Navigation is changing its back stack can disturb lifecycle
             * ordering during test teardown.
             */
            runQueuedMainTasks()

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

        logStep(
            "FOUND_TAG:$tag",
        )
    }

    private fun runQueuedMainTasks() {
        composeRule
            .mainClock
            .scheduler
            .runCurrent()
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
        return flowOf(null)
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
        MutableStateFlow(false)

    override val setupComplete:
            Flow<Boolean> =
        mutableSetupComplete

    override suspend fun markSetupComplete() {
        mutableSetupComplete.value =
            true
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

        nextOccurrenceNumber +=
            1

        return generatedId
    }
}
