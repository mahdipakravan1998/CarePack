package ir.carepack.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.CarePackApp
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.careplan.RoomCarePlanService
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
import ir.carepack.domain.today.RoomTodayQueryService
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.testing.SequenceIdSource
import ir.carepack.ui.theme.CarePackTheme
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    private lateinit var database:
            CarePackDatabase

    private val fixedClock =
        Clock.fixed(
            Instant.parse(
                "2026-06-24T08:00:00Z",
            ),
            ZoneOffset.UTC,
        )

    private val zoneProvider =
        ZoneProvider {
            ZoneId.of(
                "Asia/Tehran",
            )
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
        database.close()
    }

    @Test
    fun notificationOccurrence_navigatesToDetail_withoutCreatingReport() {
        val ids =
            SequenceIdSource(
                "navigation-recipient",
                "navigation-medication",
                "navigation-series",
                "navigation-version",
                "navigation-occurrence",
            )

        val occurrenceGenerator =
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
                    occurrenceGenerator,
                clock = fixedClock,
                idSource = ids,
            )

        val occurrenceId =
            runBlocking {
                val recipientOutcome =
                    carePlanService
                        .createRecipient(
                            CreateRecipientCommand(
                                displayName =
                                    "فرد آزمون مسیر اعلان",
                            ),
                        )

                val recipientId =
                    when (recipientOutcome) {
                        is CreateRecipientOutcome.Created -> {
                            recipientOutcome.recipientId
                        }

                        is CreateRecipientOutcome.AlreadyExists -> {
                            recipientOutcome.recipientId
                        }

                        is CreateRecipientOutcome.Invalid -> {
                            error(
                                "Recipient creation failed.",
                            )
                        }
                    }

                val planOutcome =
                    carePlanService
                        .createMedicationAndSchedule(
                            CreateMedicationScheduleCommand(
                                recipientId =
                                    recipientId,
                                medicationName =
                                    "داروی مسیر اعلان",
                                instruction =
                                    "دستور مسیر اعلان",
                                weekdays =
                                    setOf(
                                        DayOfWeek
                                            .WEDNESDAY,
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
                                        "2026-06-24",
                                    ),
                                zoneId =
                                    "Asia/Tehran",
                            ),
                        )

                val created =
                    planOutcome as
                            CreateMedicationScheduleOutcome
                            .Created

                created
                    .occurrenceIds
                    .single()
            }

        val notificationWasHandled =
            AtomicBoolean(false)

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
                            clock = fixedClock,
                        ),
                    setupPreferenceStore =
                        NavigationSetupPreferenceStore(),
                    reminderPreferenceStore =
                        NavigationReminderPreferenceStore(),
                    reminderCoordinator =
                        NavigationReminderCoordinator(),
                    notificationPermissionGateway =
                        NavigationNotificationPermissionGateway(),
                    clock = fixedClock,
                    zoneProvider =
                        zoneProvider,
                    notificationOccurrenceId =
                        occurrenceId,
                    onNotificationOccurrenceHandled = {
                        notificationWasHandled.set(
                            true,
                        )
                    },
                )
            }
        }

        waitForTag(
            tag =
                "record_given",
        )

        composeRule
            .onNodeWithTag(
                "record_given",
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(
                "debug_occurrence_id",
            )
            .assertIsDisplayed()

        composeRule.runOnIdle {
            assertTrue(
                notificationWasHandled.get(),
            )
        }

        runBlocking {
            assertNull(
                database
                    .reportingDao()
                    .getReport(
                        occurrenceId,
                    ),
            )
        }
    }

    private fun waitForTag(
        tag: String,
    ) {
        composeRule.waitUntil(
            timeoutMillis =
                TEST_TIMEOUT_MILLIS,
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

    private companion object {
        const val TEST_TIMEOUT_MILLIS =
            20_000L
    }
}

private class NavigationSetupPreferenceStore :
    SetupPreferenceStore {

    private val mutableState =
        MutableStateFlow(true)

    override val setupComplete:
            Flow<Boolean> =
        mutableState

    override suspend fun markSetupComplete() {
        mutableState.value =
            true
    }
}

private class NavigationReminderPreferenceStore :
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
        return TimezoneObservation.Unchanged
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

private class NavigationReminderCoordinator :
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
            "Alarm fire is not used in this test.",
        )
    }

    override suspend fun cancelAllOwnedReminderState() {
        Unit
    }
}

private class NavigationNotificationPermissionGateway :
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
