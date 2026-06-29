package ir.carepack.domain.reminder

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class ReminderDeliveryMode {
    EXACT,
    APPROXIMATE,
}

enum class ReminderAvailability {
    DISABLED,
    NOTIFICATION_PERMISSION_REQUIRED,
    NO_ACTIVE_SCHEDULE,
    EXACT,
    APPROXIMATE,
}

data class ReminderStatus(
    val remindersEnabled: Boolean,
    val notificationPermissionGranted: Boolean,
    val hasActiveSchedule: Boolean,
    val exactAlarmCapabilityGranted: Boolean,
    val availability: ReminderAvailability,
)

enum class ReconciliationReason {
    APPLICATION_FOREGROUND,
    REMINDER_PREFERENCE_CHANGED,
    NOTIFICATION_PERMISSION_CHANGED,
    EXACT_ALARM_CAPABILITY_CHANGED,
    CARE_PLAN_CHANGED,
    REPORT_CHANGED,
    ALARM_FIRED,
    BOOT_COMPLETED,
    TIME_CHANGED,
    TIMEZONE_CHANGED,
    PACKAGE_REPLACED,
    MANUAL_RETRY,
}

@JvmInline
value class AlarmKey private constructor(
    val scheduleSeriesId: String,
) {
    init {
        require(scheduleSeriesId.isNotBlank())
    }

    val stableToken: String
        get() {
            val digest =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                        scheduleSeriesId
                            .toByteArray(
                                Charsets.UTF_8,
                            ),
                    )

            return digest.joinToString(
                separator = "",
            ) { byte ->
                "%02x".format(
                    byte.toInt() and 0xff,
                )
            }
        }

    companion object {
        fun forScheduleSeries(
            scheduleSeriesId: String,
        ): AlarmKey {
            return AlarmKey(
                scheduleSeriesId =
                    scheduleSeriesId,
            )
        }
    }
}

data class ReminderTarget(
    val alarmKey: AlarmKey,
    val occurrenceId: String,
    val scheduledAt: Instant,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneId: String,
    val medicationName: String,
) {
    init {
        require(occurrenceId.isNotBlank())
        require(zoneId.isNotBlank())
        require(medicationName.isNotBlank())
    }
}

data class ReminderNotification(
    val occurrenceId: String,
    val medicationName: String,
    val localTime: LocalTime,
    val scheduledAt: Instant,
) {
    init {
        require(occurrenceId.isNotBlank())
        require(medicationName.isNotBlank())
    }
}

sealed interface ReminderReconciliationResult {

    val reason: ReconciliationReason
    val status: ReminderStatus
    val scheduledCount: Int
    val cancelledCount: Int

    data class Reconciled(
        override val reason:
        ReconciliationReason,
        override val status:
        ReminderStatus,
        override val scheduledCount: Int,
        override val cancelledCount: Int,
    ) : ReminderReconciliationResult

    data class PartialFailure(
        override val reason:
        ReconciliationReason,
        override val status:
        ReminderStatus,
        override val scheduledCount: Int,
        override val cancelledCount: Int,
        val failedOperationCount: Int,
    ) : ReminderReconciliationResult {
        init {
            require(failedOperationCount > 0)
        }
    }
}

enum class AlarmFireIgnoreReason {
    REMINDERS_DISABLED,
    NOTIFICATION_PERMISSION_UNAVAILABLE,
    OCCURRENCE_NOT_ELIGIBLE,
    ALARM_FIRED_EARLY,
}

sealed interface AlarmFireResult {

    data class NotificationPosted(
        val occurrenceId: String,
        val reconciliation:
        ReminderReconciliationResult,
    ) : AlarmFireResult

    data class Ignored(
        val occurrenceId: String,
        val reason:
        AlarmFireIgnoreReason,
        val reconciliation:
        ReminderReconciliationResult,
    ) : AlarmFireResult

    data class NotificationFailure(
        val occurrenceId: String,
        val reconciliation:
        ReminderReconciliationResult,
    ) : AlarmFireResult
}
