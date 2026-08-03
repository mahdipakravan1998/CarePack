package ir.carepack.feature.reminder

import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.reminder.ExactAlarmReadiness
import ir.carepack.domain.reminder.ManufacturerGuidanceType
import ir.carepack.domain.reminder.NotificationPermissionReadiness
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderDeliveryMode
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderReadinessStatus
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.ReminderTestScheduleResult
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.testing.FakeReminderCoordinator
import ir.carepack.testing.InMemoryReminderPreferenceStore
import ir.carepack.testing.InMemoryUserExperiencePreferenceStore
import ir.carepack.testing.QueueReminderTestCoordinator
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest {

    private val dispatcher =
        StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_doesNotShowPermissionOrExactRationale() =
        runTest(dispatcher) {
            val preferenceStore =
                InMemoryReminderPreferenceStore()

            val coordinator =
                FakeReminderCoordinator(
                    status =
                        status(
                            enabled = false,
                            permissionGranted = false,
                            hasActiveSchedule = true,
                            exactCapability = false,
                            availability =
                                ReminderAvailability.DISABLED,
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore = preferenceStore,
                    coordinator = coordinator,
                    permissionGranted = false,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()

            assertFalse(
                viewModel.state.value
                    .showNotificationRationale,
            )
            assertFalse(
                viewModel.state.value
                    .showExactAlarmRationale,
            )
            assertFalse(
                viewModel.state.value.remindersEnabled,
            )
            assertEquals(
                ReminderReadinessStatus
                    .REMINDERS_DISABLED,
                viewModel.state.value
                    .readiness?.status,
            )
            assertEquals(
                ReminderTestUiStatus.IDLE,
                viewModel.state.value
                    .reminderTestStatus,
            )
        }

    @Test
    fun enablingRemindersWithDeniedPermission_showsContextualRationale() =
        runTest(dispatcher) {
            val preferenceStore =
                InMemoryReminderPreferenceStore()

            val coordinator =
                FakeReminderCoordinator(
                    status =
                        status(
                            enabled = true,
                            permissionGranted = false,
                            hasActiveSchedule = true,
                            exactCapability = false,
                            availability =
                                ReminderAvailability
                                    .NOTIFICATION_PERMISSION_REQUIRED,
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore = preferenceStore,
                    coordinator = coordinator,
                    permissionGranted = false,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()

            viewModel.setRemindersEnabled(true)
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.remindersEnabled,
            )
            assertTrue(
                viewModel.state.value
                    .showNotificationRationale,
            )
            assertEquals(
                NotificationPermissionUiState.DENIED,
                viewModel.state.value
                    .notificationPermissionState,
            )
            assertEquals(
                NotificationPermissionReadiness.DENIED,
                viewModel.state.value
                    .readiness?.notificationPermission,
            )
            assertEquals(
                listOf(
                    ReconciliationReason
                        .REMINDER_PREFERENCE_CHANGED,
                ),
                coordinator.reconcileReasons,
            )
        }

    @Test
    fun exactRationale_isAvailableOnlyWithPermissionAndRealSchedule() =
        runTest(dispatcher) {
            val preferenceStore =
                InMemoryReminderPreferenceStore(
                    ReminderPreferenceState(
                        remindersEnabled = true,
                    ),
                )

            val coordinator =
                FakeReminderCoordinator(
                    status =
                        status(
                            enabled = true,
                            permissionGranted = true,
                            hasActiveSchedule = true,
                            exactCapability = false,
                            availability =
                                ReminderAvailability.APPROXIMATE,
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore = preferenceStore,
                    coordinator = coordinator,
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()
            viewModel.showExactAlarmExplanation()
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value
                    .showExactAlarmRationale,
            )
            assertEquals(
                ExactAlarmReadiness.UNAVAILABLE,
                viewModel.state.value
                    .readiness?.exactAlarm,
            )
        }

    @Test
    fun returningFromExactAlarmSettings_reconcilesPlatformState() =
        runTest(dispatcher) {
            val preferenceStore =
                InMemoryReminderPreferenceStore(
                    ReminderPreferenceState(
                        remindersEnabled = true,
                    ),
                )

            val coordinator =
                FakeReminderCoordinator(
                    status =
                        status(
                            enabled = true,
                            permissionGranted = true,
                            hasActiveSchedule = true,
                            exactCapability = true,
                            availability =
                                ReminderAvailability.EXACT,
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore = preferenceStore,
                    coordinator = coordinator,
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()
            viewModel.onExactAlarmSettingsReturned()
            advanceUntilIdle()

            assertTrue(
                coordinator.reconcileReasons.contains(
                    ReconciliationReason
                        .EXACT_ALARM_CAPABILITY_CHANGED,
                ),
            )
        }

    @Test
    fun batteryOptimizationNotIgnored_isReflectedInReadiness() =
        runTest(dispatcher) {
            val preferenceStore =
                InMemoryReminderPreferenceStore(
                    ReminderPreferenceState(
                        remindersEnabled = true,
                    ),
                )

            val coordinator =
                FakeReminderCoordinator(
                    status =
                        status(
                            enabled = true,
                            permissionGranted = true,
                            hasActiveSchedule = true,
                            exactCapability = true,
                            availability =
                                ReminderAvailability.EXACT,
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore = preferenceStore,
                    coordinator = coordinator,
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                    batteryOptimizationState =
                        BatteryOptimizationState
                            .NOT_IGNORED,
                    manufacturer = "Google",
                )

            advanceUntilIdle()

            assertEquals(
                ReminderReadinessStatus
                    .BATTERY_GUIDANCE_RECOMMENDED,
                viewModel.state.value
                    .readiness?.status,
            )
            assertEquals(
                BatteryOptimizationState.NOT_IGNORED,
                viewModel.state.value
                    .readiness?.batteryOptimizationState,
            )
        }

    @Test
    fun xiaomiManufacturer_isReflectedInReadinessGuidance() =
        runTest(dispatcher) {
            val preferenceStore =
                InMemoryReminderPreferenceStore(
                    ReminderPreferenceState(
                        remindersEnabled = true,
                    ),
                )

            val coordinator =
                FakeReminderCoordinator(
                    status =
                        status(
                            enabled = true,
                            permissionGranted = true,
                            hasActiveSchedule = true,
                            exactCapability = true,
                            availability =
                                ReminderAvailability.EXACT,
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore = preferenceStore,
                    coordinator = coordinator,
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                    batteryOptimizationState =
                        BatteryOptimizationState.IGNORED,
                    manufacturer = "XIAOMI",
                )

            advanceUntilIdle()

            assertEquals(
                ManufacturerGuidanceType.XIAOMI,
                viewModel.state.value
                    .readiness?.manufacturerGuidance?.type,
            )
            assertEquals(
                ReminderReadinessStatus
                    .OEM_GUIDANCE_RECOMMENDED,
                viewModel.state.value
                    .readiness?.status,
            )
        }

    @Test
    fun continueAnywayDismissesPermissionAndExactRationales() =
        runTest(dispatcher) {
            val preferenceStore =
                InMemoryReminderPreferenceStore(
                    ReminderPreferenceState(
                        remindersEnabled = true,
                    ),
                )

            val coordinator =
                FakeReminderCoordinator(
                    status =
                        status(
                            enabled = true,
                            permissionGranted = false,
                            hasActiveSchedule = true,
                            exactCapability = false,
                            availability =
                                ReminderAvailability
                                    .NOTIFICATION_PERMISSION_REQUIRED,
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore = preferenceStore,
                    coordinator = coordinator,
                    permissionGranted = false,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()
            viewModel.showNotificationPermissionExplanation()
            viewModel.continueWithoutPermissionChanges()
            advanceUntilIdle()

            assertFalse(
                viewModel.state.value
                    .showNotificationRationale,
            )
            assertFalse(
                viewModel.state.value
                    .showExactAlarmRationale,
            )
        }

    @Test
    fun scheduleTestReminder_exactSuccessUpdatesUiWithoutChangingReminderPreference() =
        runTest(dispatcher) {
            val triggerAt =
                Instant.parse(
                    "2026-06-24T08:00:30Z",
                )

            val testCoordinator =
                QueueReminderTestCoordinator(
                    scheduleResults =
                        listOf(
                            ReminderTestScheduleResult
                                .Scheduled(
                                    triggerAt = triggerAt,
                                    deliveryMode =
                                        ReminderDeliveryMode.EXACT,
                                ),
                        ),
                )

            val preferenceStore =
                InMemoryReminderPreferenceStore()

            val viewModel =
                viewModel(
                    preferenceStore = preferenceStore,
                    coordinator =
                        FakeReminderCoordinator(),
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                    testCoordinator = testCoordinator,
                )

            advanceUntilIdle()
            viewModel.scheduleTestReminder()
            advanceUntilIdle()

            assertEquals(
                ReminderTestUiStatus.SCHEDULED_EXACT,
                viewModel.state.value
                    .reminderTestStatus,
            )
            assertEquals(
                triggerAt,
                viewModel.state.value
                    .reminderTestScheduledAt,
            )
            assertFalse(
                viewModel.state.value.isSchedulingTest,
            )
            assertFalse(
                viewModel.state.value.remindersEnabled,
            )
            assertEquals(
                listOf(30L),
                testCoordinator.requestedDelays,
            )
        }

    @Test
    fun scheduleTestReminder_approximateFallbackIsRepresentedHonestly() =
        runTest(dispatcher) {
            val testCoordinator =
                QueueReminderTestCoordinator(
                    scheduleResults =
                        listOf(
                            ReminderTestScheduleResult
                                .Scheduled(
                                    triggerAt =
                                        Instant.parse(
                                            "2026-06-24T08:00:30Z",
                                        ),
                                    deliveryMode =
                                        ReminderDeliveryMode
                                            .APPROXIMATE,
                                ),
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore =
                        InMemoryReminderPreferenceStore(),
                    coordinator =
                        FakeReminderCoordinator(),
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                    testCoordinator = testCoordinator,
                )

            advanceUntilIdle()
            viewModel.scheduleTestReminder()
            advanceUntilIdle()

            assertEquals(
                ReminderTestUiStatus
                    .SCHEDULED_APPROXIMATE,
                viewModel.state.value
                    .reminderTestStatus,
            )
        }

    @Test
    fun scheduleTestReminder_permissionFailureDoesNotShowFalseSuccess() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    preferenceStore =
                        InMemoryReminderPreferenceStore(),
                    coordinator =
                        FakeReminderCoordinator(),
                    permissionGranted = false,
                    runtimePermissionRequired = true,
                    testCoordinator =
                        QueueReminderTestCoordinator(
                            scheduleResults =
                                listOf(
                                    ReminderTestScheduleResult
                                        .NotificationPermissionRequired,
                                ),
                        ),
                )

            advanceUntilIdle()
            viewModel.scheduleTestReminder()
            advanceUntilIdle()

            assertEquals(
                ReminderTestUiStatus
                    .NOTIFICATION_PERMISSION_REQUIRED,
                viewModel.state.value
                    .reminderTestStatus,
            )
            assertNull(
                viewModel.state.value
                    .reminderTestScheduledAt,
            )
        }

    @Test
    fun scheduleTestReminder_schedulingFailureDoesNotShowFalseSuccess() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    preferenceStore =
                        InMemoryReminderPreferenceStore(),
                    coordinator =
                        FakeReminderCoordinator(),
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                    testCoordinator =
                        QueueReminderTestCoordinator(
                            scheduleResults =
                                listOf(
                                    ReminderTestScheduleResult
                                        .SchedulingUnavailable,
                                ),
                        ),
                )

            advanceUntilIdle()
            viewModel.scheduleTestReminder()
            advanceUntilIdle()

            assertEquals(
                ReminderTestUiStatus
                    .SCHEDULING_UNAVAILABLE,
                viewModel.state.value
                    .reminderTestStatus,
            )
            assertNull(
                viewModel.state.value
                    .reminderTestScheduledAt,
            )
        }

    @Test
    fun simpleModePreferenceFlowsToReminderSettingsWithoutChangingDomainState() =
        runTest(dispatcher) {
            val experienceStore =
                InMemoryUserExperiencePreferenceStore(
                    UserExperiencePreferenceState(
                        seniorMode = SeniorMode.SIMPLE,
                    ),
                )

            val viewModel =
                viewModel(
                    preferenceStore =
                        InMemoryReminderPreferenceStore(),
                    coordinator =
                        FakeReminderCoordinator(),
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                    experienceStore = experienceStore,
                )

            advanceUntilIdle()

            assertEquals(
                SeniorMode.SIMPLE,
                viewModel.state.value.seniorMode,
            )
            assertFalse(
                viewModel.state.value.remindersEnabled,
            )
        }

    private fun viewModel(
        preferenceStore:
        InMemoryReminderPreferenceStore,
        coordinator: FakeReminderCoordinator,
        permissionGranted: Boolean,
        runtimePermissionRequired: Boolean,
        batteryOptimizationState:
        BatteryOptimizationState =
            BatteryOptimizationState.UNKNOWN,
        manufacturer: String? = "Google",
        testCoordinator:
        QueueReminderTestCoordinator =
            QueueReminderTestCoordinator(),
        experienceStore:
        InMemoryUserExperiencePreferenceStore =
            InMemoryUserExperiencePreferenceStore(),
    ): ReminderSettingsViewModel =
        ReminderSettingsViewModel(
            preferenceStore = preferenceStore,
            reminderCoordinator = coordinator,
            reminderTestCoordinator =
                testCoordinator,
            notificationPermissionGateway =
                FakeNotificationPermissionGateway(
                    permissionGranted = permissionGranted,
                    runtimePermissionRequired =
                        runtimePermissionRequired,
                ),
            userExperiencePreferenceStore =
                experienceStore,
            batteryOptimizationState = {
                batteryOptimizationState
            },
            manufacturer = {
                manufacturer
            },
        )

    private fun status(
        enabled: Boolean,
        permissionGranted: Boolean,
        hasActiveSchedule: Boolean,
        exactCapability: Boolean,
        availability: ReminderAvailability,
    ): ReminderStatus =
        ReminderStatus(
            remindersEnabled = enabled,
            notificationPermissionGranted =
                permissionGranted,
            hasActiveSchedule = hasActiveSchedule,
            exactAlarmCapabilityGranted =
                exactCapability,
            availability = availability,
        )
}

private class FakeNotificationPermissionGateway(
    private val permissionGranted: Boolean,
    private val runtimePermissionRequired: Boolean,
) : NotificationPermissionGateway {

    override fun isPermissionGranted(): Boolean =
        permissionGranted

    override fun requiresRuntimePermission(): Boolean =
        runtimePermissionRequired
}
