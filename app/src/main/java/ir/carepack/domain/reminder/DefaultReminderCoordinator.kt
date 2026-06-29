package ir.carepack.domain.reminder

import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.alarm.AlarmRequest
import ir.carepack.reminder.notification.NotificationGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import java.time.Clock
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultReminderCoordinator(
    private val scheduleSource:
    ReminderScheduleSource,
    private val preferenceStore:
    ReminderPreferenceStore,
    private val notificationPermissionGateway:
    NotificationPermissionGateway,
    private val exactAlarmCapabilityGateway:
    ExactAlarmCapabilityGateway,
    private val alarmGateway:
    AlarmGateway,
    private val notificationGateway:
    NotificationGateway,
    private val clock: Clock,
) : ReminderCoordinator {

    private val reconciliationMutex =
        Mutex()

    override suspend fun currentStatus():
            ReminderStatus {
        val preferenceState =
            preferenceStore
                .state
                .first()

        return buildStatus(
            preferenceState =
                preferenceState,
        )
    }

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        return reconciliationMutex.withLock {
            reconcileLocked(
                reason = reason,
            )
        }
    }

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult {
        require(occurrenceId.isNotBlank())

        return reconciliationMutex.withLock {
            handleAlarmFiredLocked(
                occurrenceId =
                    occurrenceId,
            )
        }
    }

    override suspend fun cancelAllOwnedReminderState() {
        reconciliationMutex.withLock {
            val alarmKeys =
                scheduleSource
                    .getAllScheduleSeriesIds()
                    .map(
                        AlarmKey::forScheduleSeries,
                    )
                    .toSet()

            runPlatformOperation {
                alarmGateway.cancelAll(
                    alarmKeys = alarmKeys,
                )
            }

            runPlatformOperation {
                notificationGateway.cancelAll()
            }
        }
    }

    private suspend fun handleAlarmFiredLocked(
        occurrenceId: String,
    ): AlarmFireResult {
        val preferenceState =
            preferenceStore
                .state
                .first()

        if (!preferenceState.remindersEnabled) {
            return ignoredAlarmFire(
                occurrenceId =
                    occurrenceId,
                reason =
                    AlarmFireIgnoreReason
                        .REMINDERS_DISABLED,
            )
        }

        if (
            !notificationPermissionGateway
                .isPermissionGranted()
        ) {
            return ignoredAlarmFire(
                occurrenceId =
                    occurrenceId,
                reason =
                    AlarmFireIgnoreReason
                        .NOTIFICATION_PERMISSION_UNAVAILABLE,
            )
        }

        val target =
            scheduleSource
                .getEligibleOccurrence(
                    occurrenceId =
                        occurrenceId,
                )

        if (target == null) {
            return ignoredAlarmFire(
                occurrenceId =
                    occurrenceId,
                reason =
                    AlarmFireIgnoreReason
                        .OCCURRENCE_NOT_ELIGIBLE,
            )
        }

        val now =
            clock.instant()

        if (target.scheduledAt > now) {
            return ignoredAlarmFire(
                occurrenceId =
                    occurrenceId,
                reason =
                    AlarmFireIgnoreReason
                        .ALARM_FIRED_EARLY,
            )
        }

        val notificationPosted =
            runPlatformOperation {
                notificationGateway.post(
                    notification =
                        ReminderNotification(
                            occurrenceId =
                                target.occurrenceId,
                            medicationName =
                                target.medicationName,
                            localTime =
                                target.localTime,
                            scheduledAt =
                                target.scheduledAt,
                        ),
                )
            }

        val reconciliation =
            reconcileLocked(
                reason =
                    ReconciliationReason
                        .ALARM_FIRED,
            )

        return if (notificationPosted) {
            AlarmFireResult
                .NotificationPosted(
                    occurrenceId =
                        occurrenceId,
                    reconciliation =
                        reconciliation,
                )
        } else {
            AlarmFireResult
                .NotificationFailure(
                    occurrenceId =
                        occurrenceId,
                    reconciliation =
                        reconciliation,
                )
        }
    }

    private suspend fun ignoredAlarmFire(
        occurrenceId: String,
        reason: AlarmFireIgnoreReason,
    ): AlarmFireResult.Ignored {
        return AlarmFireResult.Ignored(
            occurrenceId =
                occurrenceId,
            reason =
                reason,
            reconciliation =
                reconcileLocked(
                    reason =
                        ReconciliationReason
                            .ALARM_FIRED,
                ),
        )
    }

    private suspend fun reconcileLocked(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        val preferenceState =
            preferenceStore
                .state
                .first()

        val initialStatus =
            buildStatus(
                preferenceState =
                    preferenceState,
            )

        val allAlarmKeys =
            scheduleSource
                .getAllScheduleSeriesIds()
                .map(
                    AlarmKey::forScheduleSeries,
                )
                .toSet()

        if (
            initialStatus.availability ==
            ReminderAvailability.DISABLED ||
            initialStatus.availability ==
            ReminderAvailability
                .NOTIFICATION_PERMISSION_REQUIRED ||
            initialStatus.availability ==
            ReminderAvailability
                .NO_ACTIVE_SCHEDULE
        ) {
            val cancellationResult =
                cancelAlarmKeys(
                    alarmKeys =
                        allAlarmKeys,
                )

            return createResult(
                reason = reason,
                status =
                    initialStatus,
                scheduledCount = 0,
                cancelledCount =
                    cancellationResult
                        .successfulCount,
                failedOperationCount =
                    cancellationResult
                        .failedCount,
            )
        }

        val now =
            clock.instant()

        val targets =
            scheduleSource
                .getNextEligibleTargets(
                    now = now,
                )

        val targetKeys =
            targets
                .map(
                    ReminderTarget::alarmKey,
                )
                .toSet()

        val staleKeys =
            allAlarmKeys -
                    targetKeys

        val cancellationResult =
            cancelAlarmKeys(
                alarmKeys =
                    staleKeys,
            )

        var exactRegistrationCount =
            0

        var approximateRegistrationCount =
            0

        var scheduleFailureCount =
            0

        targets.forEach { target ->
            val preferredMode =
                if (
                    exactAlarmCapabilityGateway
                        .canScheduleExactAlarms()
                ) {
                    AlarmDeliveryMode.EXACT
                } else {
                    AlarmDeliveryMode
                        .APPROXIMATE
                }

            val registrationMode =
                scheduleWithFallback(
                    target = target,
                    preferredMode =
                        preferredMode,
                )

            when (registrationMode) {
                AlarmDeliveryMode.EXACT -> {
                    exactRegistrationCount +=
                        1
                }

                AlarmDeliveryMode.APPROXIMATE -> {
                    approximateRegistrationCount +=
                        1
                }

                null -> {
                    scheduleFailureCount +=
                        1
                }
            }
        }

        val finalStatus =
            when {
                targets.isEmpty() -> {
                    buildStatus(
                        preferenceState =
                            preferenceState,
                    )
                }

                approximateRegistrationCount > 0 -> {
                    initialStatus.copy(
                        exactAlarmCapabilityGranted =
                            false,
                        availability =
                            ReminderAvailability
                                .APPROXIMATE,
                    )
                }

                exactRegistrationCount > 0 -> {
                    initialStatus.copy(
                        exactAlarmCapabilityGranted =
                            true,
                        availability =
                            ReminderAvailability
                                .EXACT,
                    )
                }

                else -> {
                    buildStatus(
                        preferenceState =
                            preferenceState,
                    )
                }
            }

        return createResult(
            reason = reason,
            status = finalStatus,
            scheduledCount =
                exactRegistrationCount +
                        approximateRegistrationCount,
            cancelledCount =
                cancellationResult
                    .successfulCount,
            failedOperationCount =
                scheduleFailureCount +
                        cancellationResult
                            .failedCount,
        )
    }

    private suspend fun buildStatus(
        preferenceState:
        ReminderPreferenceState,
    ): ReminderStatus {
        val permissionGranted =
            notificationPermissionGateway
                .isPermissionGranted()

        val hasActiveSchedule =
            scheduleSource
                .hasActiveSchedule()

        val exactCapabilityGranted =
            if (
                preferenceState
                    .remindersEnabled &&
                permissionGranted &&
                hasActiveSchedule
            ) {
                exactAlarmCapabilityGateway
                    .canScheduleExactAlarms()
            } else {
                false
            }

        val availability =
            when {
                !preferenceState
                    .remindersEnabled -> {
                    ReminderAvailability
                        .DISABLED
                }

                !permissionGranted -> {
                    ReminderAvailability
                        .NOTIFICATION_PERMISSION_REQUIRED
                }

                !hasActiveSchedule -> {
                    ReminderAvailability
                        .NO_ACTIVE_SCHEDULE
                }

                exactCapabilityGranted -> {
                    ReminderAvailability
                        .EXACT
                }

                else -> {
                    ReminderAvailability
                        .APPROXIMATE
                }
            }

        return ReminderStatus(
            remindersEnabled =
                preferenceState
                    .remindersEnabled,
            notificationPermissionGranted =
                permissionGranted,
            hasActiveSchedule =
                hasActiveSchedule,
            exactAlarmCapabilityGranted =
                exactCapabilityGranted,
            availability =
                availability,
        )
    }

    private fun scheduleWithFallback(
        target: ReminderTarget,
        preferredMode:
        AlarmDeliveryMode,
    ): AlarmDeliveryMode? {
        val preferredSucceeded =
            runPlatformOperation {
                alarmGateway.schedule(
                    request =
                        AlarmRequest(
                            alarmKey =
                                target.alarmKey,
                            occurrenceId =
                                target.occurrenceId,
                            triggerAt =
                                target.scheduledAt,
                            deliveryMode =
                                preferredMode,
                        ),
                )
            }

        if (preferredSucceeded) {
            return preferredMode
        }

        if (
            preferredMode !=
            AlarmDeliveryMode.EXACT
        ) {
            return null
        }

        val fallbackSucceeded =
            runPlatformOperation {
                alarmGateway.schedule(
                    request =
                        AlarmRequest(
                            alarmKey =
                                target.alarmKey,
                            occurrenceId =
                                target.occurrenceId,
                            triggerAt =
                                target.scheduledAt,
                            deliveryMode =
                                AlarmDeliveryMode
                                    .APPROXIMATE,
                        ),
                )
            }

        return if (fallbackSucceeded) {
            AlarmDeliveryMode.APPROXIMATE
        } else {
            null
        }
    }

    private fun cancelAlarmKeys(
        alarmKeys: Set<AlarmKey>,
    ): AlarmCancellationResult {
        var successfulCount =
            0

        var failedCount =
            0

        alarmKeys.forEach { alarmKey ->
            val succeeded =
                runPlatformOperation {
                    alarmGateway.cancel(
                        alarmKey =
                            alarmKey,
                    )
                }

            if (succeeded) {
                successfulCount += 1
            } else {
                failedCount += 1
            }
        }

        return AlarmCancellationResult(
            successfulCount =
                successfulCount,
            failedCount =
                failedCount,
        )
    }

    private fun createResult(
        reason: ReconciliationReason,
        status: ReminderStatus,
        scheduledCount: Int,
        cancelledCount: Int,
        failedOperationCount: Int,
    ): ReminderReconciliationResult {
        return if (
            failedOperationCount == 0
        ) {
            ReminderReconciliationResult
                .Reconciled(
                    reason = reason,
                    status = status,
                    scheduledCount =
                        scheduledCount,
                    cancelledCount =
                        cancelledCount,
                )
        } else {
            ReminderReconciliationResult
                .PartialFailure(
                    reason = reason,
                    status = status,
                    scheduledCount =
                        scheduledCount,
                    cancelledCount =
                        cancelledCount,
                    failedOperationCount =
                        failedOperationCount,
                )
        }
    }

    private fun runPlatformOperation(
        operation: () -> Unit,
    ): Boolean {
        return try {
            operation()
            true
        } catch (
            cancellation:
            CancellationException,
        ) {
            throw cancellation
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private data class AlarmCancellationResult(
        val successfulCount: Int,
        val failedCount: Int,
    )
}
