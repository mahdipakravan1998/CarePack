package ir.carepack.reminder.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Clock

internal interface SystemReconciliationRetryAlarmGateway {
    fun schedule(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    )

    fun cancel(pendingIntent: PendingIntent)
}

private class AndroidSystemReconciliationRetryAlarmGateway(
    context: Context,
) : SystemReconciliationRetryAlarmGateway {
    private val alarmManager = checkNotNull(
            context.applicationContext.getSystemService(
                AlarmManager::class.java,
            ),
        )

    override fun schedule(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    override fun cancel(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
    }
}

class SystemReconciliationRetryScheduler internal constructor(
    context: Context,
    private val clock: Clock,
    private val alarmGateway: SystemReconciliationRetryAlarmGateway =
        AndroidSystemReconciliationRetryAlarmGateway(context),
) {
    private val applicationContext = context.applicationContext

    private val preferences = applicationContext.getSharedPreferences(
            PREFERENCE_FILE,
            Context.MODE_PRIVATE,
        )

    fun scheduleNextRetry(): Boolean {
        val currentAttempt = preferences.getInt(KEY_ATTEMPT, 0)

        if (currentAttempt >= RETRY_DELAYS_MILLIS.size) {
            return false
        }

        val nextAttempt = currentAttempt + 1
        val triggerAtMillis = clock.instant().toEpochMilli() +
                RETRY_DELAYS_MILLIS[currentAttempt]
        val pendingIntent = checkNotNull(
                retryPendingIntent(
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE,
                ),
            )

        alarmGateway.schedule(
            triggerAtMillis = triggerAtMillis,
            pendingIntent = pendingIntent,
        )

        val committed = preferences.edit()
                .putInt(KEY_ATTEMPT, nextAttempt).commit()

        check(committed)
        return true
    }

    fun markSuccessful() {
        val committed = preferences.edit().remove(KEY_ATTEMPT).commit()
        check(committed)

        retryPendingIntent(
            PendingIntent.FLAG_NO_CREATE or
                PendingIntent.FLAG_IMMUTABLE,
        )?.let(alarmGateway::cancel)
    }

    fun clearAll() {
        retryPendingIntent(
            PendingIntent.FLAG_NO_CREATE or
                PendingIntent.FLAG_IMMUTABLE,
        )?.let { pendingIntent ->
            alarmGateway.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        val committed = preferences.edit().clear().commit()
        check(committed)
    }

    private fun retryPendingIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_CODE,
            Intent(
                applicationContext,
                SystemReconciliationReceiver::class.java,
            ).apply {
                action = SystemReconciliationReceiver
                        .ACTION_RETRY_RECONCILIATION
                setPackage(applicationContext.packageName)
            },
            flags,
        )

    private companion object {
        const val PREFERENCE_FILE = "carepack_reconciliation_retry"
        const val KEY_ATTEMPT = "attempt"
        const val REQUEST_CODE = 0x6612

        val RETRY_DELAYS_MILLIS = longArrayOf(
                15L * 60L * 1_000L,
                60L * 60L * 1_000L,
                6L * 60L * 60L * 1_000L,
            )
    }
}
