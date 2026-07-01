package ir.carepack.domain.reminder

enum class ReminderReadinessStatus {
    READY,
    REMINDERS_DISABLED,
    NO_ACTIVE_SCHEDULE,
    NOTIFICATION_PERMISSION_REQUIRED,
    EXACT_ALARM_ACCESS_RECOMMENDED,
    APPROXIMATE_DELIVERY,
}

data class ReminderReadiness(
    val status: ReminderReadinessStatus,
    val canAttemptReminderDelivery: Boolean,
    val usesExactAlarm: Boolean,
    val message: String,
)

object ReminderReadinessPolicy {
    fun evaluate(
        remindersEnabled: Boolean,
        hasActiveSchedule: Boolean,
        notificationPermissionGranted: Boolean,
        canScheduleExactAlarms: Boolean,
    ): ReminderReadiness =
        when {
            !remindersEnabled -> {
                ReminderReadiness(
                    status =
                        ReminderReadinessStatus
                            .REMINDERS_DISABLED,
                    canAttemptReminderDelivery =
                        false,
                    usesExactAlarm =
                        false,
                    message =
                        "یادآورها خاموش هستند.",
                )
            }

            !hasActiveSchedule -> {
                ReminderReadiness(
                    status =
                        ReminderReadinessStatus
                            .NO_ACTIVE_SCHEDULE,
                    canAttemptReminderDelivery =
                        false,
                    usesExactAlarm =
                        false,
                    message =
                        "برای فعال شدن یادآورها ابتدا یک برنامه دارویی فعال لازم است.",
                )
            }

            !notificationPermissionGranted -> {
                ReminderReadiness(
                    status =
                        ReminderReadinessStatus
                            .NOTIFICATION_PERMISSION_REQUIRED,
                    canAttemptReminderDelivery =
                        false,
                    usesExactAlarm =
                        false,
                    message =
                        "برای نمایش یادآور دارو، اجازه اعلان لازم است.",
                )
            }

            canScheduleExactAlarms -> {
                ReminderReadiness(
                    status =
                        ReminderReadinessStatus.READY,
                    canAttemptReminderDelivery =
                        true,
                    usesExactAlarm =
                        true,
                    message =
                        "یادآور دارو آماده است. اندروید یا تنظیمات باتری گوشی ممکن است یادآورها را با تأخیر نمایش دهد یا محدود کند.",
                )
            }

            else -> {
                ReminderReadiness(
                    status =
                        ReminderReadinessStatus
                            .APPROXIMATE_DELIVERY,
                    canAttemptReminderDelivery =
                        true,
                    usesExactAlarm =
                        false,
                    message =
                        "یادآور دارو فعال است، اما بدون دسترسی هشدار دقیق ممکن است اندروید یا تنظیمات باتری گوشی آن را با تأخیر نمایش دهد.",
                )
            }
        }
}
