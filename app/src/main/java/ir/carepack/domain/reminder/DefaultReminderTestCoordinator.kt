package ir.carepack.domain.reminder

import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.reminder.alarm.ReminderTestAlarmGateway
import ir.carepack.reminder.alarm.ReminderTestAlarmRequest
import ir.carepack.reminder.notification.ReminderTestNotificationGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException

class DefaultReminderTestCoordinator(
    private val notificationPermissionGateway:
    NotificationPermissionGateway,
    private val exactAlarmCapabilityGateway:
    ExactAlarmCapabilityGateway,
    private val alarmGateway:
    ReminderTestAlarmGateway,
    private val notificationGateway:
    ReminderTestNotificationGateway,
    private val clock: Clock,
    private val operationLock:
    ReminderOperationLock,
) : ReminderTestCoordinator {

    override suspend fun scheduleTestReminder(
        delaySeconds: Long,
    ): ReminderTestScheduleResult {
        require(delaySeconds > 0L)

        return operationLock.withLock {
            try {
                cancelExistingTestLocked()

                if (
                    !notificationPermissionGateway
                        .isPermissionGranted()
                ) {
                    return@withLock ReminderTestScheduleResult
                        .NotificationPermissionRequired
                }

                val triggerAt =
                    clock
                        .instant()
                        .plusSeconds(
                            delaySeconds,
                        )

                val preferredMode =
                    if (
                        exactAlarmCapabilityGateway
                            .canScheduleExactAlarms()
                    ) {
                        AlarmDeliveryMode.EXACT
                    } else {
                        AlarmDeliveryMode.APPROXIMATE
                    }

                val scheduledMode =
                    scheduleWithFallback(
                        triggerAt = triggerAt,
                        preferredMode =
                            preferredMode,
                    )
                        ?: return@withLock ReminderTestScheduleResult
                            .SchedulingUnavailable

                ReminderTestScheduleResult.Scheduled(
                    triggerAt = triggerAt,
                    deliveryMode =
                        when (scheduledMode) {
                            AlarmDeliveryMode.EXACT ->
                                ReminderDeliveryMode.EXACT

                            AlarmDeliveryMode.APPROXIMATE ->
                                ReminderDeliveryMode.APPROXIMATE
                        },
                )
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: RuntimeException) {
                ReminderTestScheduleResult
                    .SchedulingUnavailable
            }
        }
    }

    override suspend fun handleTestAlarmFired():
            ReminderTestFireResult =
        operationLock.withLock {
            if (
                !notificationPermissionGateway
                    .isPermissionGranted()
            ) {
                return@withLock ReminderTestFireResult
                    .NotificationPermissionUnavailable
            }

            try {
                notificationGateway
                    .postTestReminder(
                        scheduledAt =
                            clock.instant(),
                    )

                ReminderTestFireResult
                    .NotificationPosted
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: RuntimeException) {
                ReminderTestFireResult
                    .NotificationFailed
            }
        }

    override suspend fun cancelPendingTest() {
        operationLock.withLock {
            cancelExistingTestLocked()
        }
    }

    private fun cancelExistingTestLocked() {
        alarmGateway.cancelTest()
        notificationGateway.cancelTestReminder()
    }

    private fun scheduleWithFallback(
        triggerAt: Instant,
        preferredMode: AlarmDeliveryMode,
    ): AlarmDeliveryMode? {
        val preferredScheduled =
            trySchedule(
                triggerAt = triggerAt,
                mode = preferredMode,
            )

        if (preferredScheduled) {
            return preferredMode
        }

        if (
            preferredMode ==
            AlarmDeliveryMode.APPROXIMATE
        ) {
            return null
        }

        return if (
            trySchedule(
                triggerAt = triggerAt,
                mode =
                    AlarmDeliveryMode.APPROXIMATE,
            )
        ) {
            AlarmDeliveryMode.APPROXIMATE
        } else {
            null
        }
    }

    private fun trySchedule(
        triggerAt: Instant,
        mode: AlarmDeliveryMode,
    ): Boolean =
        try {
            alarmGateway.scheduleTest(
                ReminderTestAlarmRequest(
                    triggerAt = triggerAt,
                    deliveryMode = mode,
                ),
            )

            true
        } catch (
            cancellationException:
            CancellationException,
        ) {
            throw cancellationException
        } catch (_: RuntimeException) {
            false
        }
}
