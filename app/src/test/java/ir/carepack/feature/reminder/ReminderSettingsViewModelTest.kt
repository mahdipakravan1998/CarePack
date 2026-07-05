package ir.carepack.feature.reminder

import ir.carepack.domain.reminder.ExactAlarmReadiness
import ir.carepack.domain.reminder.ManufacturerGuidanceType
import ir.carepack.domain.reminder.NotificationPermissionReadiness
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderReadinessStatus
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.testing.FakeReminderCoordinator
import ir.carepack.testing.InMemoryReminderPreferenceStore
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest {

    private val dispatcher =
        StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(
            dispatcher,
        )
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
                                ReminderAvailability
                                    .DISABLED,
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore =
                        preferenceStore,
                    coordinator =
                        coordinator,
                    permissionGranted = false,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()

            assertFalse(
                viewModel
                    .state
                    .value
                    .showNotificationRationale,
            )

            assertFalse(
                viewModel
                    .state
                    .value
                    .showExactAlarmRationale,
            )

            assertFalse(
                viewModel
                    .state
                    .value
                    .remindersEnabled,
            )

            assertEquals(
                ReminderReadinessStatus
                    .REMINDERS_DISABLED,
                viewModel
                    .state
                    .value
                    .readiness
                    ?.status,
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
                    preferenceStore =
                        preferenceStore,
                    coordinator =
                        coordinator,
                    permissionGranted = false,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()

            viewModel.setRemindersEnabled(
                enabled = true,
            )

            advanceUntilIdle()

            assertTrue(
                viewModel
                    .state
                    .value
                    .remindersEnabled,
            )

            assertTrue(
                viewModel
                    .state
                    .value
                    .showNotificationRationale,
            )

            assertEquals(
                NotificationPermissionUiState.DENIED,
                viewModel
                    .state
                    .value
                    .notificationPermissionState,
            )

            assertEquals(
                NotificationPermissionReadiness.DENIED,
                viewModel
                    .state
                    .value
                    .readiness
                    ?.notificationPermission,
            )

            assertEquals(
                listOf(
                    ReconciliationReason
                        .REMINDER_PREFERENCE_CHANGED,
                ),
                coordinator
                    .reconcileReasons,
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
                                ReminderAvailability
                                    .APPROXIMATE,
                        ),
                )

            val viewModel =
                viewModel(
                    preferenceStore =
                        preferenceStore,
                    coordinator =
                        coordinator,
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()

            viewModel.showExactAlarmExplanation()

            advanceUntilIdle()

            assertTrue(
                viewModel
                    .state
                    .value
                    .showExactAlarmRationale,
            )

            assertEquals(
                ExactAlarmReadiness.UNAVAILABLE,
                viewModel
                    .state
                    .value
                    .readiness
                    ?.exactAlarm,
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
                    preferenceStore =
                        preferenceStore,
                    coordinator =
                        coordinator,
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()

            viewModel.onExactAlarmSettingsReturned()

            advanceUntilIdle()

            assertTrue(
                coordinator
                    .reconcileReasons
                    .contains(
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
                    preferenceStore =
                        preferenceStore,
                    coordinator =
                        coordinator,
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
                viewModel
                    .state
                    .value
                    .readiness
                    ?.status,
            )

            assertEquals(
                BatteryOptimizationState
                    .NOT_IGNORED,
                viewModel
                    .state
                    .value
                    .readiness
                    ?.batteryOptimizationState,
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
                    preferenceStore =
                        preferenceStore,
                    coordinator =
                        coordinator,
                    permissionGranted = true,
                    runtimePermissionRequired = true,
                    batteryOptimizationState =
                        BatteryOptimizationState
                            .IGNORED,
                    manufacturer = "XIAOMI",
                )

            advanceUntilIdle()

            assertEquals(
                ManufacturerGuidanceType.XIAOMI,
                viewModel
                    .state
                    .value
                    .readiness
                    ?.manufacturerGuidance
                    ?.type,
            )

            assertEquals(
                ReminderReadinessStatus
                    .OEM_GUIDANCE_RECOMMENDED,
                viewModel
                    .state
                    .value
                    .readiness
                    ?.status,
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
                    preferenceStore =
                        preferenceStore,
                    coordinator =
                        coordinator,
                    permissionGranted = false,
                    runtimePermissionRequired = true,
                )

            advanceUntilIdle()

            viewModel.showNotificationPermissionExplanation()
            viewModel.continueWithoutPermissionChanges()

            advanceUntilIdle()

            assertFalse(
                viewModel
                    .state
                    .value
                    .showNotificationRationale,
            )

            assertFalse(
                viewModel
                    .state
                    .value
                    .showExactAlarmRationale,
            )
        }

    private fun viewModel(
        preferenceStore: InMemoryReminderPreferenceStore,
        coordinator: FakeReminderCoordinator,
        permissionGranted: Boolean,
        runtimePermissionRequired: Boolean,
        batteryOptimizationState:
        BatteryOptimizationState =
            BatteryOptimizationState.UNKNOWN,
        manufacturer: String? =
            "Google",
    ): ReminderSettingsViewModel =
        ReminderSettingsViewModel(
            preferenceStore =
                preferenceStore,
            reminderCoordinator =
                coordinator,
            notificationPermissionGateway =
                FakeNotificationPermissionGateway(
                    permissionGranted =
                        permissionGranted,
                    runtimePermissionRequired =
                        runtimePermissionRequired,
                ),
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
            hasActiveSchedule =
                hasActiveSchedule,
            exactAlarmCapabilityGranted =
                exactCapability,
            availability = availability,
        )
}

private class FakeNotificationPermissionGateway(
    private val permissionGranted: Boolean,
    private val runtimePermissionRequired:
    Boolean,
) : NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean =
        permissionGranted

    override fun requiresRuntimePermission():
            Boolean =
        runtimePermissionRequired
}
