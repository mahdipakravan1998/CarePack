package ir.carepack.domain.reminder

import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.alarm.AlarmRequest
import ir.carepack.reminder.notification.NotificationGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.first

class DefaultReminderCoordinator(
    private val scheduleSource:
    ReminderScheduleSource,
    private val preferenceStore:
    ReminderPreferenceStore,
    private val snoozedReminderStore:
    SnoozedReminderStore,
    private val notificationPermissionGateway:
    NotificationPermissionGateway,
    private val exactAlarmCapabilityGateway:
    ExactAlarmCapabilityGateway,
    private val alarmGateway:
    AlarmGateway,
    private val notificationGateway:
    NotificationGateway,
    private val clock: Clock,
    private val diagnosticSink:
    ReminderDiagnosticSink =
        NoOpReminderDiagnosticSink,
    private val operationLock:
    ReminderOperationLock =
        ReminderOperationLock(),
) : ReminderCoordinator {

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
        return operationLock.withLock {
            reconcileLocked(
                reason = reason,
            )
        }
    }

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult {
        require(occurrenceId.isNotBlank())

        return operationLock.withLock {
            handleAlarmFiredLocked(
                occurrenceId =
                    occurrenceId,
            )
        }
    }

    override suspend fun remindLater(
        occurrenceId: String,
        delayMinutes: Long,
    ): RemindLaterOutcome {
        require(occurrenceId.isNotBlank())

        return operationLock.withLock {
            remindLaterLocked(
                occurrenceId =
                    occurrenceId,
                delayMinutes =
                    delayMinutes,
            )
        }
    }

    override suspend fun cancelReminderDelay(
        occurrenceId: String,
    ) {
        require(occurrenceId.isNotBlank())

        operationLock.withLock {
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .USER_ACTION_HANDLED,
                occurrenceId =
                    occurrenceId,
                outcome =
                    "cancel_remind_later",
            )

            snoozedReminderStore.delete(
                occurrenceId =
                    occurrenceId,
            )

            runPlatformOperation {
                alarmGateway.cancel(
                    alarmKey =
                        AlarmKey
                            .forDelayedOccurrence(
                                occurrenceId =
                                    occurrenceId,
                            ),
                )
            }
        }
    }

    override suspend fun cancelAllOwnedReminderState() {
        operationLock.withLock {
            val scheduleAlarmKeys =
                scheduleSource
                    .getAllScheduleSeriesIds()
                    .map(
                        AlarmKey::forScheduleSeries,
                    )
                    .toSet()

            val delayedAlarmKeys =
                snoozedReminderStore
                    .reminders
                    .first()
                    .map {
                        it.alarmKey
                    }
                    .toSet()

            runPlatformOperation {
                alarmGateway.cancelAll(
                    alarmKeys =
                        scheduleAlarmKeys +
                                delayedAlarmKeys,
                )
            }

            runPlatformOperation {
                notificationGateway.cancelAll()
            }

            snoozedReminderStore.clear()
        }
    }

    private suspend fun handleAlarmFiredLocked(
        occurrenceId: String,
    ): AlarmFireResult {
        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .RECEIVER_FIRED,
            occurrenceId =
                occurrenceId,
        )

        val preferenceState =
            preferenceStore
                .state
                .first()

        if (!preferenceState.remindersEnabled) {
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .REMINDERS_DISABLED,
                occurrenceId =
                    occurrenceId,
            )

            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .NOTIFICATION_SKIPPED,
                occurrenceId =
                    occurrenceId,
                outcome =
                    "reminders_disabled",
            )

            return ignoredAlarmFire(
                occurrenceId =
                    occurrenceId,
                reason =
                    AlarmFireIgnoreReason
                        .REMINDERS_DISABLED,
            )
        }

        val notificationPermissionGranted =
            notificationPermissionGateway
                .isPermissionGranted()

        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .NOTIFICATION_PERMISSION_CHECKED,
            occurrenceId =
                occurrenceId,
            outcome =
                notificationPermissionGranted
                    .toString(),
        )

        if (!notificationPermissionGranted) {
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .NOTIFICATION_SKIPPED,
                occurrenceId =
                    occurrenceId,
                outcome =
                    "notification_permission_denied",
            )

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

        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .FUTURE_OCCURRENCE_CHECKED,
            occurrenceId =
                occurrenceId,
            outcome =
                (target != null)
                    .toString(),
        )

        if (target == null) {
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .NOTIFICATION_SKIPPED,
                occurrenceId =
                    occurrenceId,
                outcome =
                    "occurrence_not_eligible",
            )

            snoozedReminderStore.delete(
                occurrenceId =
                    occurrenceId,
            )

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
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .NOTIFICATION_SKIPPED,
                occurrenceId =
                    occurrenceId,
                outcome =
                    "alarm_fired_early",
            )

            return ignoredAlarmFire(
                occurrenceId =
                    occurrenceId,
                reason =
                    AlarmFireIgnoreReason
                        .ALARM_FIRED_EARLY,
            )
        }

        snoozedReminderStore.delete(
            occurrenceId =
                occurrenceId,
        )

        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .NOTIFICATION_POST_ATTEMPTED,
            occurrenceId =
                target.occurrenceId,
        )

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

        if (notificationPosted) {
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .NOTIFICATION_POSTED,
                occurrenceId =
                    target.occurrenceId,
            )
        } else {
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .NOTIFICATION_FAILED,
                occurrenceId =
                    target.occurrenceId,
                outcome =
                    "notification_gateway_failed",
            )
        }

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

    private suspend fun remindLaterLocked(
        occurrenceId: String,
        delayMinutes: Long,
    ): RemindLaterOutcome {
        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .USER_ACTION_HANDLED,
            occurrenceId =
                occurrenceId,
            outcome =
                "remind_later",
        )

        if (delayMinutes <= 0L) {
            return RemindLaterOutcome
                .Ignored(
                    reason =
                        RemindLaterIgnoreReason
                            .INVALID_DELAY,
                )
        }

        val target =
            scheduleSource
                .getEligibleOccurrence(
                    occurrenceId =
                        occurrenceId,
                ) ?: return RemindLaterOutcome
                .Ignored(
                    reason =
                        RemindLaterIgnoreReason
                            .OCCURRENCE_NOT_ELIGIBLE,
                )

        val now =
            clock.instant()

        val decision =
            SnoozedReminderPolicy.create(
                occurrenceId =
                    occurrenceId,
                now = now,
                remindAt =
                    now.plusSeconds(
                        delayMinutes *
                                SECONDS_PER_MINUTE,
                    ),
                occurrenceAlreadyReported =
                    false,
                occurrenceActive =
                    true,
            )

        val snoozedReminder =
            when (decision) {
                is SnoozedReminderDecision.Ignore -> {
                    return RemindLaterOutcome
                        .Ignored(
                            reason =
                                decision.reason,
                        )
                }

                is SnoozedReminderDecision.Schedule -> {
                    decision.snoozedReminder
                }
            }

        snoozedReminderStore.upsert(
            reminder =
                snoozedReminder,
        )

        val canScheduleExactAlarms =
            exactAlarmCapabilityGateway
                .canScheduleExactAlarms()

        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .EXACT_ALARM_CAPABILITY_CHECKED,
            occurrenceId =
                occurrenceId,
            outcome =
                canScheduleExactAlarms
                    .toString(),
        )

        val mode =
            if (canScheduleExactAlarms) {
                AlarmDeliveryMode.EXACT
            } else {
                recordDiagnostic(
                    type =
                        ReminderDiagnosticEventType
                            .EXACT_ALARM_UNAVAILABLE,
                    occurrenceId =
                        occurrenceId,
                )

                recordDiagnostic(
                    type =
                        ReminderDiagnosticEventType
                            .APPROXIMATE_FALLBACK_SELECTED,
                    occurrenceId =
                        occurrenceId,
                )

                AlarmDeliveryMode.APPROXIMATE
            }

        val scheduledMode =
            scheduleWithFallback(
                target =
                    target.copy(
                        alarmKey =
                            snoozedReminder
                                .alarmKey,
                        scheduledAt =
                            snoozedReminder
                                .remindAt,
                    ),
                preferredMode =
                    mode,
            )

        return if (scheduledMode == null) {
            snoozedReminderStore.delete(
                occurrenceId =
                    occurrenceId,
            )

            RemindLaterOutcome
                .SchedulingFailed
        } else {
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .SNOOZE_SCHEDULED,
                occurrenceId =
                    occurrenceId,
                alarmKey =
                    snoozedReminder.alarmKey,
                deliveryMode =
                    scheduledMode
                        .toReminderDeliveryMode(),
            )

            RemindLaterOutcome
                .Scheduled(
                    snoozedReminder =
                        snoozedReminder,
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

        val scheduleAlarmKeys =
            scheduleSource
                .getAllScheduleSeriesIds()
                .map(
                    AlarmKey::forScheduleSeries,
                )
                .toSet()

        val storedDelayedReminders =
            snoozedReminderStore
                .reminders
                .first()

        val delayedAlarmKeys =
            storedDelayedReminders
                .map {
                    it.alarmKey
                }
                .toSet()

        val allAlarmKeys =
            scheduleAlarmKeys +
                    delayedAlarmKeys

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
            if (
                initialStatus.availability ==
                ReminderAvailability.DISABLED
            ) {
                recordDiagnostic(
                    type =
                        ReminderDiagnosticEventType
                            .REMINDERS_DISABLED,
                    availability =
                        initialStatus.availability,
                )
            }

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

        val normalTargets =
            scheduleSource
                .getNextEligibleTargets(
                    now = now,
                )

        val delayedTargets =
            resolveDelayedTargets(
                reminders =
                    storedDelayedReminders,
                now = now,
            )

        val targets =
            normalTargets +
                    delayedTargets

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
            val canScheduleExactAlarms =
                exactAlarmCapabilityGateway
                    .canScheduleExactAlarms()

            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .EXACT_ALARM_CAPABILITY_CHECKED,
                occurrenceId =
                    target.occurrenceId,
                alarmKey =
                    target.alarmKey,
                outcome =
                    canScheduleExactAlarms
                        .toString(),
            )

            val preferredMode =
                if (canScheduleExactAlarms) {
                    AlarmDeliveryMode.EXACT
                } else {
                    recordDiagnostic(
                        type =
                            ReminderDiagnosticEventType
                                .EXACT_ALARM_UNAVAILABLE,
                        occurrenceId =
                            target.occurrenceId,
                        alarmKey =
                            target.alarmKey,
                    )

                    recordDiagnostic(
                        type =
                            ReminderDiagnosticEventType
                                .APPROXIMATE_FALLBACK_SELECTED,
                        occurrenceId =
                            target.occurrenceId,
                        alarmKey =
                            target.alarmKey,
                    )

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

    private suspend fun resolveDelayedTargets(
        reminders: List<SnoozedReminder>,
        now: Instant,
    ): List<ReminderTarget> {
        return reminders.mapNotNull { reminder ->
            val target =
                scheduleSource
                    .getEligibleOccurrence(
                        occurrenceId =
                            reminder.occurrenceId,
                    )

            if (target == null) {
                snoozedReminderStore.delete(
                    occurrenceId =
                        reminder.occurrenceId,
                )

                null
            } else {
                target.copy(
                    alarmKey =
                        reminder.alarmKey,
                    scheduledAt =
                        maxOfInstant(
                            first =
                                reminder.remindAt,
                            second =
                                now,
                        ),
                )
            }
        }
    }

    private suspend fun buildStatus(
        preferenceState:
        ReminderPreferenceState,
    ): ReminderStatus {
        val permissionGranted =
            notificationPermissionGateway
                .isPermissionGranted()

        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .NOTIFICATION_PERMISSION_CHECKED,
            outcome =
                permissionGranted
                    .toString(),
        )

        val hasActiveSchedule =
            scheduleSource
                .hasActiveSchedule()

        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .FUTURE_OCCURRENCE_CHECKED,
            outcome =
                hasActiveSchedule
                    .toString(),
        )

        val exactCapabilityGranted =
            if (
                preferenceState
                    .remindersEnabled &&
                permissionGranted &&
                hasActiveSchedule
            ) {
                exactAlarmCapabilityGateway
                    .canScheduleExactAlarms()
                    .also { granted ->
                        recordDiagnostic(
                            type =
                                ReminderDiagnosticEventType
                                    .EXACT_ALARM_CAPABILITY_CHECKED,
                            outcome =
                                granted.toString(),
                        )
                    }
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
        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .ALARM_REGISTRATION_ATTEMPTED,
            occurrenceId =
                target.occurrenceId,
            alarmKey =
                target.alarmKey,
            deliveryMode =
                preferredMode
                    .toReminderDeliveryMode(),
        )

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
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .ALARM_REGISTERED,
                occurrenceId =
                    target.occurrenceId,
                alarmKey =
                    target.alarmKey,
                deliveryMode =
                    preferredMode
                        .toReminderDeliveryMode(),
            )

            return preferredMode
        }

        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .ALARM_REGISTRATION_FAILED,
            occurrenceId =
                target.occurrenceId,
            alarmKey =
                target.alarmKey,
            deliveryMode =
                preferredMode
                    .toReminderDeliveryMode(),
        )

        if (
            preferredMode !=
            AlarmDeliveryMode.EXACT
        ) {
            return null
        }

        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .APPROXIMATE_FALLBACK_SELECTED,
            occurrenceId =
                target.occurrenceId,
            alarmKey =
                target.alarmKey,
        )

        recordDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .ALARM_REGISTRATION_ATTEMPTED,
            occurrenceId =
                target.occurrenceId,
            alarmKey =
                target.alarmKey,
            deliveryMode =
                ReminderDeliveryMode.APPROXIMATE,
        )

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
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .ALARM_REGISTERED,
                occurrenceId =
                    target.occurrenceId,
                alarmKey =
                    target.alarmKey,
                deliveryMode =
                    ReminderDeliveryMode.APPROXIMATE,
            )

            AlarmDeliveryMode.APPROXIMATE
        } else {
            recordDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .ALARM_REGISTRATION_FAILED,
                occurrenceId =
                    target.occurrenceId,
                alarmKey =
                    target.alarmKey,
                deliveryMode =
                    ReminderDeliveryMode.APPROXIMATE,
            )

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

    private fun recordDiagnostic(
        type: ReminderDiagnosticEventType,
        occurrenceId: String? = null,
        alarmKey: AlarmKey? = null,
        availability: ReminderAvailability? = null,
        deliveryMode: ReminderDeliveryMode? = null,
        outcome: String? = null,
    ) {
        diagnosticSink.recordReminderDiagnostic(
            type = type,
            clock = clock,
            occurrenceId = occurrenceId,
            alarmKey = alarmKey,
            availability = availability,
            deliveryMode = deliveryMode,
            outcome = outcome,
        )
    }

    private fun AlarmDeliveryMode.toReminderDeliveryMode():
            ReminderDeliveryMode =
        when (this) {
            AlarmDeliveryMode.EXACT ->
                ReminderDeliveryMode.EXACT

            AlarmDeliveryMode.APPROXIMATE ->
                ReminderDeliveryMode.APPROXIMATE
        }

    private fun maxOfInstant(
        first: Instant,
        second: Instant,
    ): Instant {
        return if (first >= second) {
            first
        } else {
            second
        }
    }

    private data class AlarmCancellationResult(
        val successfulCount: Int,
        val failedCount: Int,
    )

    private companion object {
        const val SECONDS_PER_MINUTE =
            60L
    }
}
