package ir.carepack.domain.reminder

import java.time.Instant

sealed interface ReminderTestScheduleResult {

    data class Scheduled(
        val triggerAt: Instant,
        val deliveryMode: ReminderDeliveryMode,
    ) : ReminderTestScheduleResult

    data object NotificationPermissionRequired :
        ReminderTestScheduleResult

    data object SchedulingUnavailable :
        ReminderTestScheduleResult
}

sealed interface ReminderTestFireResult {

    data object NotificationPosted :
        ReminderTestFireResult

    data object NotificationPermissionUnavailable :
        ReminderTestFireResult

    data object NotificationFailed :
        ReminderTestFireResult
}

interface ReminderTestCoordinator {

    suspend fun scheduleTestReminder(
        delaySeconds: Long = DEFAULT_TEST_DELAY_SECONDS,
    ): ReminderTestScheduleResult

    suspend fun handleTestAlarmFired():
            ReminderTestFireResult

    suspend fun cancelPendingTest()

    companion object {
        const val DEFAULT_TEST_DELAY_SECONDS =
            30L
    }
}
