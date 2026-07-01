package ir.carepack.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderReadinessPolicyTest {

    @Test
    fun disabledReminders_areNotReady() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = false,
                hasActiveSchedule = true,
                notificationPermissionGranted = true,
                canScheduleExactAlarms = true,
            )

        assertEquals(
            ReminderReadinessStatus
                .REMINDERS_DISABLED,
            readiness.status,
        )

        assertFalse(
            readiness.canAttemptReminderDelivery,
        )

        assertFalse(
            readiness.usesExactAlarm,
        )
    }

    @Test
    fun missingSchedule_isNotReady() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = false,
                notificationPermissionGranted = true,
                canScheduleExactAlarms = true,
            )

        assertEquals(
            ReminderReadinessStatus
                .NO_ACTIVE_SCHEDULE,
            readiness.status,
        )

        assertFalse(
            readiness.canAttemptReminderDelivery,
        )
    }

    @Test
    fun notificationPermissionIsRequiredBeforeDelivery() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = true,
                notificationPermissionGranted = false,
                canScheduleExactAlarms = true,
            )

        assertEquals(
            ReminderReadinessStatus
                .NOTIFICATION_PERMISSION_REQUIRED,
            readiness.status,
        )

        assertFalse(
            readiness.canAttemptReminderDelivery,
        )
    }

    @Test
    fun exactAlarmAvailable_isReady() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = true,
                notificationPermissionGranted = true,
                canScheduleExactAlarms = true,
            )

        assertEquals(
            ReminderReadinessStatus.READY,
            readiness.status,
        )

        assertTrue(
            readiness.canAttemptReminderDelivery,
        )

        assertTrue(
            readiness.usesExactAlarm,
        )

        assertTrue(
            readiness.message.contains(
                "تأخیر",
            ),
        )
    }

    @Test
    fun exactAlarmUnavailable_usesApproximateDelivery() {
        val readiness =
            ReminderReadinessPolicy.evaluate(
                remindersEnabled = true,
                hasActiveSchedule = true,
                notificationPermissionGranted = true,
                canScheduleExactAlarms = false,
            )

        assertEquals(
            ReminderReadinessStatus
                .APPROXIMATE_DELIVERY,
            readiness.status,
        )

        assertTrue(
            readiness.canAttemptReminderDelivery,
        )

        assertFalse(
            readiness.usesExactAlarm,
        )

        assertTrue(
            readiness.message.contains(
                "ممکن است",
            ),
        )
    }
}
