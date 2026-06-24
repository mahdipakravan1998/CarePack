package ir.carepack.ui

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.CarePackApp
import ir.carepack.core.id.IdSource
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.RoomCarePlanService
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.today.RoomTodayQueryService
import ir.carepack.feature.setup.RecipientSetupScreen
import ir.carepack.feature.setup.RecipientSetupUiState
import ir.carepack.ui.theme.CarePackTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarePackComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

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
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        database =
            Room.inMemoryDatabaseBuilder(
                context,
                CarePackDatabase::class.java,
            ).build()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun fullSetup_navigatesToToday_andRecordsGiven() {
        val ids =
            UiSequenceIdSource(
                "recipient-1",
                "medication-1",
                "series-1",
                "version-1",
                "occurrence-1",
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
                occurrenceGenerator = generator,
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

        composeRule
            .onNodeWithTag(
                "onboarding_continue",
            )
            .assertExists()
            .performClick()

        composeRule
            .onNodeWithTag(
                "recipient_name",
            )
            .performTextInput(
                "Test recipient",
            )

        composeRule
            .onNodeWithTag(
                "recipient_save",
            )
            .performClick()

        composeRule
            .onNodeWithTag(
                "medication_name",
            )
            .performTextInput(
                "Test medication",
            )

        composeRule
            .onNodeWithTag(
                "medication_instruction",
            )
            .performTextInput(
                "Take after meal",
            )

        composeRule
            .onNodeWithTag(
                "medication_schedule_save",
            )
            .performClick()

        composeRule.waitUntil(
            timeoutMillis = 5_000,
        ) {
            composeRule
                .onAllNodesWithTag(
                    "today_item_occurrence-1",
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule
            .onNodeWithTag(
                "today_item_occurrence-1",
            )
            .assertExists()
            .performClick()

        composeRule
            .onNodeWithTag(
                "record_given",
            )
            .assertExists()
            .performClick()

        composeRule.waitUntil(
            timeoutMillis = 5_000,
        ) {
            composeRule
                .onAllNodesWithTag(
                    "given_status",
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule
            .onNodeWithTag(
                "given_status",
            )
            .assertExists()
    }

    @Test
    fun storageFailure_doesNotShowSuccess() {
        composeRule.setContent {
            CarePackTheme {
                RecipientSetupScreen(
                    state =
                        RecipientSetupUiState(
                            displayName =
                                "Test recipient",
                            isSaving = false,
                            errorMessage =
                                "Storage failed.",
                        ),
                    onDisplayNameChanged = {},
                    onSave = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(
                "recipient_error",
            )
            .assertExists()
    }
}

private class InMemorySetupPreferenceStore :
    SetupPreferenceStore {

    private val mutableSetupComplete =
        MutableStateFlow(false)

    override val setupComplete: Flow<Boolean> =
        mutableSetupComplete

    override suspend fun markSetupComplete() {
        mutableSetupComplete.value = true
    }
}

private class UiSequenceIdSource(
    vararg ids: String,
) : IdSource {

    private val remainingIds =
        ArrayDeque(ids.toList())

    override fun nextId(): String {
        check(remainingIds.isNotEmpty()) {
            "No test ID remains."
        }

        return remainingIds.removeFirst()
    }
}
