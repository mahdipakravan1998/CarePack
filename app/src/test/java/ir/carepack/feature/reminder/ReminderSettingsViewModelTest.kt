package ir.carepack.feature.reminder

import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.reminder.permission.NotificationPermissionGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
                ViewModelReminderPreferenceStore(
                    remindersEnabled =
                        false,
                )

            val coordinator =
                ViewModelReminderCoordinator(
                    currentStatus =
                        status(
                            enabled = false,
                            permissionGranted =
                                false,
                            hasActiveSchedule =
                                true,
                            exactCapability =
                                false,
                            availability =
                                ReminderAvailability
                                    .DISABLED,
                        ),
                )

            val viewModel =
                ReminderSettingsViewModel(
                    preferenceStore =
                        preferenceStore,
                    reminderCoordinator =
                        coordinator,
                    notificationPermissionGateway =
                        ViewModelNotificationPermissionGateway(
                            permissionGranted =
                                false,
                            runtimePermissionRequired =
                                true,
                        ),
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
        }

    @Test
    fun enablingRemindersWithDeniedPermission_showsContextualRationale() =
        runTest(dispatcher) {
            val preferenceStore =
                ViewModelReminderPreferenceStore(
                    remindersEnabled =
                        false,
                )

            val coordinator =
                ViewModelReminderCoordinator(
                    currentStatus =
                        status(
                            enabled = false,
                            permissionGranted =
                                false,
                            hasActiveSchedule =
                                true,
                            exactCapability =
                                false,
                            availability =
                                ReminderAvailability
                                    .DISABLED,
                        ),
                    reconciledStatus =
                        status(
                            enabled = true,
                            permissionGranted =
                                false,
                            hasActiveSchedule =
                                true,
                            exactCapability =
                                false,
                            availability =
                                ReminderAvailability
                                    .NOTIFICATION_PERMISSION_REQUIRED,
                        ),
                )

            val viewModel =
                ReminderSettingsViewModel(
                    preferenceStore =
                        preferenceStore,
                    reminderCoordinator =
                        coordinator,
                    notificationPermissionGateway =
                        ViewModelNotificationPermissionGateway(
                            permissionGranted =
                                false,
                            runtimePermissionRequired =
                                true,
                        ),
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
                NotificationPermissionUiState
                    .DENIED,
                viewModel
                    .state
                    .value
                    .notificationPermissionState,
            )

            assertEquals(
                ReminderAvailability
                    .NOTIFICATION_PERMISSION_REQUIRED,
                viewModel
                    .state
                    .value
                    .availability,
            )

            assertEquals(
                listOf(
                    ReconciliationReason
                        .REMINDER_PREFERENCE_CHANGED,
                ),
                coordinator
                    .reconciliationReasons,
            )
        }

    @Test
    fun exactRationale_isAvailableOnlyWithPermissionAndRealSchedule() =
        runTest(dispatcher) {
            val preferenceStore =
                ViewModelReminderPreferenceStore(
                    remindersEnabled =
                        true,
                )

            val coordinator =
                ViewModelReminderCoordinator(
                    currentStatus =
                        status(
                            enabled = true,
                            permissionGranted =
                                true,
                            hasActiveSchedule =
                                true,
                            exactCapability =
                                false,
                            availability =
                                ReminderAvailability
                                    .APPROXIMATE,
                        ),
                )

            val viewModel =
                ReminderSettingsViewModel(
                    preferenceStore =
                        preferenceStore,
                    reminderCoordinator =
                        coordinator,
                    notificationPermissionGateway =
                        ViewModelNotificationPermissionGateway(
                            permissionGranted =
                                true,
                            runtimePermissionRequired =
                                true,
                        ),
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

            viewModel.dismissExactAlarmExplanation()

            advanceUntilIdle()

            assertFalse(
                viewModel
                    .state
                    .value
                    .showExactAlarmRationale,
            )
        }

    @Test
    fun exactRationale_isNotShownWithoutActiveSchedule() =
        runTest(dispatcher) {
            val preferenceStore =
                ViewModelReminderPreferenceStore(
                    remindersEnabled =
                        true,
                )

            val coordinator =
                ViewModelReminderCoordinator(
                    currentStatus =
                        status(
                            enabled = true,
                            permissionGranted =
                                true,
                            hasActiveSchedule =
                                false,
                            exactCapability =
                                false,
                            availability =
                                ReminderAvailability
                                    .NO_ACTIVE_SCHEDULE,
                        ),
                )

            val viewModel =
                ReminderSettingsViewModel(
                    preferenceStore =
                        preferenceStore,
                    reminderCoordinator =
                        coordinator,
                    notificationPermissionGateway =
                        ViewModelNotificationPermissionGateway(
                            permissionGranted =
                                true,
                            runtimePermissionRequired =
                                true,
                        ),
                )

            advanceUntilIdle()

            viewModel.showExactAlarmExplanation()

            advanceUntilIdle()

            assertFalse(
                viewModel
                    .state
                    .value
                    .showExactAlarmRationale,
            )
        }

    @Test
    fun permissionCompletion_reconcilesAndRefreshesGrantedState() =
        runTest(dispatcher) {
            val preferenceStore =
                ViewModelReminderPreferenceStore(
                    remindersEnabled =
                        true,
                )

            val permissionGateway =
                ViewModelNotificationPermissionGateway(
                    permissionGranted =
                        false,
                    runtimePermissionRequired =
                        true,
                )

            val coordinator =
                ViewModelReminderCoordinator(
                    currentStatus =
                        status(
                            enabled = true,
                            permissionGranted =
                                false,
                            hasActiveSchedule =
                                true,
                            exactCapability =
                                false,
                            availability =
                                ReminderAvailability
                                    .NOTIFICATION_PERMISSION_REQUIRED,
                        ),
                    reconciledStatus =
                        status(
                            enabled = true,
                            permissionGranted =
                                true,
                            hasActiveSchedule =
                                true,
                            exactCapability =
                                false,
                            availability =
                                ReminderAvailability
                                    .APPROXIMATE,
                        ),
                )

            val viewModel =
                ReminderSettingsViewModel(
                    preferenceStore =
                        preferenceStore,
                    reminderCoordinator =
                        coordinator,
                    notificationPermissionGateway =
                        permissionGateway,
                )

            advanceUntilIdle()

            permissionGateway
                .permissionGranted =
                true

            viewModel
                .onNotificationPermissionRequestCompleted()

            advanceUntilIdle()

            assertEquals(
                ReminderAvailability
                    .APPROXIMATE,
                viewModel
                    .state
                    .value
                    .availability,
            )

            assertEquals(
                ReconciliationReason
                    .NOTIFICATION_PERMISSION_CHANGED,
                coordinator
                    .reconciliationReasons
                    .last(),
            )
        }

    private fun status(
        enabled: Boolean,
        permissionGranted: Boolean,
        hasActiveSchedule: Boolean,
        exactCapability: Boolean,
        availability:
        ReminderAvailability,
    ): ReminderStatus {
        return ReminderStatus(
            remindersEnabled =
                enabled,
            notificationPermissionGranted =
                permissionGranted,
            hasActiveSchedule =
                hasActiveSchedule,
            exactAlarmCapabilityGranted =
                exactCapability,
            availability =
                availability,
        )
    }
}

private class ViewModelReminderPreferenceStore(
    remindersEnabled: Boolean,
) : ReminderPreferenceStore {

    private val mutableState =
        MutableStateFlow(
            ReminderPreferenceState(
                remindersEnabled =
                    remindersEnabled,
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

private class ViewModelReminderCoordinator(
    var currentStatus: ReminderStatus,
    var reconciledStatus:
    ReminderStatus = currentStatus,
) : ReminderCoordinator {

    val reconciliationReasons =
        mutableListOf<ReconciliationReason>()

    override suspend fun currentStatus():
            ReminderStatus {
        return currentStatus
    }

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        reconciliationReasons +=
            reason

        currentStatus =
            reconciledStatus

        return ReminderReconciliationResult
            .Reconciled(
                reason = reason,
                status =
                    reconciledStatus,
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

private class ViewModelNotificationPermissionGateway(
    var permissionGranted: Boolean,
    private val runtimePermissionRequired:
    Boolean,
) : NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean {
        return permissionGranted
    }

    override fun requiresRuntimePermission():
            Boolean {
        return runtimePermissionRequired
    }
}
