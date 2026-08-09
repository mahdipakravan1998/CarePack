package ir.carepack.feature.reminder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.reminder.ExactAlarmReadiness
import ir.carepack.domain.reminder.ManufacturerGuidance
import ir.carepack.domain.reminder.NotificationPermissionReadiness
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderDeliveryMode
import ir.carepack.domain.reminder.ReminderHealth
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.recoverableFailureOrNull
import ir.carepack.domain.reminder.ReminderReadiness
import ir.carepack.domain.reminder.ReminderReadinessPolicy
import ir.carepack.domain.reminder.ReminderReadinessStatus
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.ReminderTestCoordinator
import ir.carepack.domain.reminder.ReminderTestScheduleResult
import ir.carepack.reminder.permission.AndroidBatteryOptimizationGateway
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import ir.carepack.ui.experience.carePackExperience
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


enum class NotificationPermissionUiState {
    NOT_REQUIRED,
    GRANTED,
    DENIED,
}

enum class ReminderTestUiStatus {
    IDLE,
    SCHEDULED_EXACT,
    SCHEDULED_APPROXIMATE,
    NOTIFICATION_PERMISSION_REQUIRED,
    SCHEDULING_UNAVAILABLE,
}

data class ReminderSettingsUiState(
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val remindersEnabled: Boolean = false,
    val notificationPermissionState:
    NotificationPermissionUiState =
        NotificationPermissionUiState.DENIED,
    val notificationRuntimePermissionRequired:
    Boolean = false,
    val hasActiveSchedule: Boolean = false,
    val exactAlarmCapabilityGranted:
    Boolean = false,
    val availability: ReminderAvailability =
        ReminderAvailability.DISABLED,
    val readiness: ReminderReadiness? = null,
    val showNotificationRationale:
    Boolean = false,
    val showExactAlarmRationale:
    Boolean = false,
    val showOemGuidance:
    Boolean = true,
    val seniorMode: SeniorMode = SeniorMode.STANDARD,
    val health: ReminderHealth = ReminderHealth.Healthy,
    val isSchedulingTest: Boolean = false,
    val reminderTestStatus:
    ReminderTestUiStatus = ReminderTestUiStatus.IDLE,
    val reminderTestScheduledAt: Instant? = null,
    val errorMessage: String? = null,
)

private data class ReminderSettingsTransientState(
    val isApplying: Boolean = false,
    val showNotificationRationale:
    Boolean = false,
    val showExactAlarmRationale:
    Boolean = false,
    val showOemGuidance:
    Boolean = true,
    val isSchedulingTest: Boolean = false,
    val reminderTestStatus:
    ReminderTestUiStatus = ReminderTestUiStatus.IDLE,
    val reminderTestScheduledAt: Instant? = null,
    val errorMessage: String? = null,
)

class ReminderSettingsViewModel(
    private val preferenceStore:
    ReminderPreferenceStore,
    private val reminderCoordinator:
    ReminderCoordinator,
    private val reminderTestCoordinator:
    ReminderTestCoordinator,
    private val notificationPermissionGateway:
    NotificationPermissionGateway,
    private val userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    private val clock: Clock = Clock.systemUTC(),
    private val batteryOptimizationState:
        () -> BatteryOptimizationState = {
        BatteryOptimizationState.UNKNOWN
    },
    private val manufacturer:
        () -> String? = {
        Build.MANUFACTURER
    },
) : ViewModel() {

    private val operationMutex =
        Mutex()

    private val mutableStatus =
        MutableStateFlow<ReminderStatus?>(
            null,
        )

    private val mutableTransientState =
        MutableStateFlow(
            ReminderSettingsTransientState(),
        )

    val state =
        combine(
            preferenceStore.state,
            mutableStatus,
            mutableTransientState,
            userExperiencePreferenceStore.state,
        ) {
                preferenceState,
                status,
                transientState,
                userExperienceState ->
            val runtimePermissionRequired =
                notificationPermissionGateway
                    .requiresRuntimePermission()

            val permissionUiState =
                permissionUiStateFor(
                    runtimePermissionRequired =
                        runtimePermissionRequired,
                    status =
                        status,
                )

            val readiness =
                status?.let {
                        currentStatus ->
                    ReminderReadinessPolicy.evaluate(
                        remindersEnabled =
                            preferenceState
                                .remindersEnabled,
                        hasActiveSchedule =
                            currentStatus
                                .hasActiveSchedule,
                        notificationRuntimePermissionRequired =
                            runtimePermissionRequired,
                        notificationPermissionGranted =
                            currentStatus
                                .notificationPermissionGranted,
                        canScheduleExactAlarms =
                            currentStatus
                                .exactAlarmCapabilityGranted,
                        exactAlarmRelevant =
                            currentStatus
                                .hasActiveSchedule &&
                                    preferenceState
                                        .remindersEnabled,
                        batteryOptimizationState =
                            batteryOptimizationState(),
                        manufacturer =
                            manufacturer(),
                    )
                }

            ReminderSettingsUiState(
                isLoading =
                    status == null,
                isApplying =
                    transientState
                        .isApplying,
                remindersEnabled =
                    preferenceState
                        .remindersEnabled,
                notificationPermissionState =
                    permissionUiState,
                notificationRuntimePermissionRequired =
                    runtimePermissionRequired,
                hasActiveSchedule =
                    status
                        ?.hasActiveSchedule
                        ?: false,
                exactAlarmCapabilityGranted =
                    status
                        ?.exactAlarmCapabilityGranted
                        ?: false,
                availability =
                    status
                        ?.availability
                        ?: ReminderAvailability
                            .DISABLED,
                readiness =
                    readiness,
                showNotificationRationale =
                    transientState
                        .showNotificationRationale,
                showExactAlarmRationale =
                    transientState
                        .showExactAlarmRationale,
                showOemGuidance =
                    transientState
                        .showOemGuidance,
                seniorMode =
                    userExperienceState
                        .seniorMode,
                health =
                    preferenceState
                        .health,
                isSchedulingTest =
                    transientState
                        .isSchedulingTest,
                reminderTestStatus =
                    transientState
                        .reminderTestStatus,
                reminderTestScheduledAt =
                    transientState
                        .reminderTestScheduledAt,
                errorMessage =
                    transientState
                        .errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted.Eagerly,
            initialValue =
                ReminderSettingsUiState(
                    notificationRuntimePermissionRequired =
                        notificationPermissionGateway
                            .requiresRuntimePermission(),
                ),
        )

    init {
        loadInitialStatus()
    }

    fun setRemindersEnabled(
        enabled: Boolean,
    ) {
        runOperation {
            preferenceStore
                .setRemindersEnabled(
                    enabled = enabled,
                )

            val status =
                reconcileAndRecordHealth(
                    reason =
                        ReconciliationReason
                            .REMINDER_PREFERENCE_CHANGED,
                )

            mutableStatus.value =
                status

            val readiness =
                currentReadinessFor(
                    remindersEnabled =
                        enabled,
                    status =
                        status,
                )

            mutableTransientState.update {
                    transient ->
                transient.copy(
                    showNotificationRationale =
                        enabled &&
                                readiness
                                    .notificationPermission ==
                                NotificationPermissionReadiness
                                    .DENIED,
                    showExactAlarmRationale =
                        enabled &&
                                readiness
                                    .exactAlarm ==
                                ExactAlarmReadiness
                                    .UNAVAILABLE,
                    showOemGuidance =
                        true,
                )
            }
        }
    }

    fun scheduleTestReminder() {
        viewModelScope.launch {
            operationMutex.withLock {
                mutableTransientState.update { transient ->
                    transient.copy(
                        isSchedulingTest = true,
                        reminderTestStatus =
                            ReminderTestUiStatus.IDLE,
                        reminderTestScheduledAt = null,
                        errorMessage = null,
                    )
                }

                try {
                    when (
                        val result =
                            reminderTestCoordinator
                                .scheduleTestReminder()
                    ) {
                        is ReminderTestScheduleResult.Scheduled -> {
                            mutableTransientState.update { transient ->
                                transient.copy(
                                    reminderTestStatus =
                                        when (result.deliveryMode) {
                                            ReminderDeliveryMode.EXACT ->
                                                ReminderTestUiStatus
                                                    .SCHEDULED_EXACT

                                            ReminderDeliveryMode.APPROXIMATE ->
                                                ReminderTestUiStatus
                                                    .SCHEDULED_APPROXIMATE
                                        },
                                    reminderTestScheduledAt =
                                        result.triggerAt,
                                )
                            }
                        }

                        ReminderTestScheduleResult
                            .NotificationPermissionRequired -> {
                            mutableTransientState.update { transient ->
                                transient.copy(
                                    reminderTestStatus =
                                        ReminderTestUiStatus
                                            .NOTIFICATION_PERMISSION_REQUIRED,
                                )
                            }
                        }

                        ReminderTestScheduleResult
                            .SchedulingUnavailable -> {
                            mutableTransientState.update { transient ->
                                transient.copy(
                                    reminderTestStatus =
                                        ReminderTestUiStatus
                                            .SCHEDULING_UNAVAILABLE,
                                )
                            }
                        }
                    }
                } catch (
                    cancellationException:
                    CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    mutableTransientState.update { transient ->
                        transient.copy(
                            reminderTestStatus =
                                ReminderTestUiStatus
                                    .SCHEDULING_UNAVAILABLE,
                        )
                    }
                } finally {
                    mutableTransientState.update { transient ->
                        transient.copy(
                            isSchedulingTest = false,
                        )
                    }
                }
            }
        }
    }

    fun showNotificationPermissionExplanation() {
        val currentState =
            state.value

        if (
            !currentState.remindersEnabled ||
            currentState
                .notificationPermissionState !=
            NotificationPermissionUiState.DENIED
        ) {
            return
        }

        mutableTransientState.update {
                transient ->
            transient.copy(
                showNotificationRationale =
                    true,
                errorMessage = null,
            )
        }
    }

    fun dismissNotificationPermissionExplanation() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showNotificationRationale =
                    false,
            )
        }
    }

    fun onNotificationPermissionRequestCompleted() {
        dismissNotificationPermissionExplanation()

        reconcilePlatformState(
            reason =
                ReconciliationReason
                    .NOTIFICATION_PERMISSION_CHANGED,
        )
    }

    fun showExactAlarmExplanation() {
        val currentState =
            state.value

        val canRequest =
            currentState
                .remindersEnabled &&
                    currentState
                        .notificationPermissionState !=
                    NotificationPermissionUiState
                        .DENIED &&
                    currentState
                        .hasActiveSchedule &&
                    currentState
                        .readiness
                        ?.exactAlarm ==
                    ExactAlarmReadiness
                        .UNAVAILABLE

        if (!canRequest) {
            return
        }

        mutableTransientState.update {
                transient ->
            transient.copy(
                showExactAlarmRationale =
                    true,
                errorMessage = null,
            )
        }
    }

    fun dismissExactAlarmExplanation() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showExactAlarmRationale =
                    false,
            )
        }
    }

    fun onExactAlarmSettingsReturned() {
        dismissExactAlarmExplanation()

        reconcilePlatformState(
            reason =
                ReconciliationReason
                    .EXACT_ALARM_CAPABILITY_CHANGED,
        )
    }

    fun onNotificationSettingsReturned() {
        reconcilePlatformState(
            reason =
                ReconciliationReason
                    .NOTIFICATION_PERMISSION_CHANGED,
        )
    }

    fun refreshPlatformState() {
        reconcilePlatformState(
            reason =
                ReconciliationReason
                    .MANUAL_RETRY,
        )
    }

    fun retryReminderHealth() {
        reconcilePlatformState(
            reason =
                ReconciliationReason
                    .MANUAL_RETRY,
        )
    }

    fun continueWithoutPermissionChanges() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showNotificationRationale =
                    false,
                showExactAlarmRationale =
                    false,
                errorMessage = null,
            )
        }
    }

    fun showOemGuidance() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showOemGuidance =
                    true,
            )
        }
    }

    fun onPlatformLaunchFailed() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                showNotificationRationale =
                    false,
                showExactAlarmRationale =
                    false,
                errorMessage =
                    "باز کردن تنظیمات اندروید انجام نشد.",
            )
        }
    }

    fun clearError() {
        mutableTransientState.update {
                transient ->
            transient.copy(
                errorMessage = null,
            )
        }
    }

    private fun loadInitialStatus() {
        runOperation {
            mutableStatus.value =
                reminderCoordinator
                    .currentStatus()
        }
    }

    private fun reconcilePlatformState(
        reason: ReconciliationReason,
    ) {
        runOperation {
            mutableStatus.value =
                reconcileAndRecordHealth(reason)
        }
    }

    private suspend fun reconcileAndRecordHealth(
        reason: ReconciliationReason,
    ): ReminderStatus {
        val result = reminderCoordinator.reconcile(reason)
        val failure = result.recoverableFailureOrNull()

        if (failure == null) {
            preferenceStore.markHealthy()
        } else {
            preferenceStore.markFailure(
                failure = failure,
                failedAtEpochMillis =
                    clock.instant().toEpochMilli(),
            )
        }

        return result.status
    }

    private fun currentReadinessFor(
        remindersEnabled: Boolean,
        status: ReminderStatus,
    ): ReminderReadiness {
        return ReminderReadinessPolicy.evaluate(
            remindersEnabled =
                remindersEnabled,
            hasActiveSchedule =
                status.hasActiveSchedule,
            notificationRuntimePermissionRequired =
                notificationPermissionGateway
                    .requiresRuntimePermission(),
            notificationPermissionGranted =
                status.notificationPermissionGranted,
            canScheduleExactAlarms =
                status.exactAlarmCapabilityGranted,
            exactAlarmRelevant =
                remindersEnabled &&
                        status.hasActiveSchedule,
            batteryOptimizationState =
                batteryOptimizationState(),
            manufacturer =
                manufacturer(),
        )
    }

    private fun runOperation(
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            operationMutex.withLock {
                mutableTransientState.update {
                        transient ->
                    transient.copy(
                        isApplying = true,
                        errorMessage = null,
                    )
                }

                try {
                    operation()
                } catch (
                    cancellation:
                    CancellationException,
                ) {
                    throw cancellation
                } catch (_: Exception) {
                    mutableTransientState.update {
                            transient ->
                        transient.copy(
                            errorMessage =
                                "به‌روزرسانی تنظیمات یادآور انجام نشد.",
                        )
                    }
                } finally {
                    mutableTransientState.update {
                            transient ->
                        transient.copy(
                            isApplying = false,
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun factory(
            preferenceStore:
            ReminderPreferenceStore,
            reminderCoordinator:
            ReminderCoordinator,
            reminderTestCoordinator:
            ReminderTestCoordinator,
            notificationPermissionGateway:
            NotificationPermissionGateway,
            userExperiencePreferenceStore:
            UserExperiencePreferenceStore,
            clock: Clock = Clock.systemUTC(),
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ReminderSettingsViewModel(
                        preferenceStore =
                            preferenceStore,
                        reminderCoordinator =
                            reminderCoordinator,
                        reminderTestCoordinator =
                            reminderTestCoordinator,
                        notificationPermissionGateway =
                            notificationPermissionGateway,
                        userExperiencePreferenceStore =
                            userExperiencePreferenceStore,
                        clock = clock,
                    )
                }
            }
        }
    }
}


private fun permissionUiStateFor(
    runtimePermissionRequired: Boolean,
    status: ReminderStatus?,
): NotificationPermissionUiState =
    when {
        !runtimePermissionRequired -> {
            NotificationPermissionUiState
                .NOT_REQUIRED
        }

        status
            ?.notificationPermissionGranted ==
                true -> {
            NotificationPermissionUiState
                .GRANTED
        }

        else -> {
            NotificationPermissionUiState
                .DENIED
        }
    }
