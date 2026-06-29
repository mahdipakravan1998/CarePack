package ir.carepack.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.carepack.domain.reminder.AlarmKey
import ir.carepack.reminder.receiver.ReminderAlarmReceiver

class AndroidAlarmGateway(
    context: Context,
) : AlarmGateway {

    private val applicationContext =
        context.applicationContext

    private val alarmManager =
        checkNotNull(
            applicationContext
                .getSystemService(
                    AlarmManager::class.java,
                ),
        )

    override fun schedule(
        request: AlarmRequest,
    ) {
        val pendingIntent =
            createPendingIntent(
                alarmKey =
                    request.alarmKey,
                occurrenceId =
                    request.occurrenceId,
                flags =
                    PendingIntent
                        .FLAG_UPDATE_CURRENT or
                            PendingIntent
                                .FLAG_IMMUTABLE,
            )

        when (request.deliveryMode) {
            AlarmDeliveryMode.EXACT -> {
                alarmManager
                    .setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        request
                            .triggerAt
                            .toEpochMilli(),
                        pendingIntent,
                    )
            }

            AlarmDeliveryMode.APPROXIMATE -> {
                alarmManager
                    .setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        request
                            .triggerAt
                            .toEpochMilli(),
                        pendingIntent,
                    )
            }
        }
    }

    override fun cancel(
        alarmKey: AlarmKey,
    ) {
        val existingPendingIntent =
            findPendingIntent(
                alarmKey =
                    alarmKey,
            ) ?: return

        alarmManager.cancel(
            existingPendingIntent,
        )

        existingPendingIntent.cancel()
    }

    private fun createPendingIntent(
        alarmKey: AlarmKey,
        occurrenceId: String,
        flags: Int,
    ): PendingIntent {
        val intent =
            createAlarmIntent(
                alarmKey =
                    alarmKey,
            ).apply {
                putExtra(
                    ReminderAlarmReceiver
                        .EXTRA_OCCURRENCE_ID,
                    occurrenceId,
                )

                putExtra(
                    ReminderAlarmReceiver
                        .EXTRA_SCHEDULE_SERIES_ID,
                    alarmKey
                        .scheduleSeriesId,
                )
            }

        return PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_CODE,
            intent,
            flags,
        )
    }

    private fun findPendingIntent(
        alarmKey: AlarmKey,
    ): PendingIntent? {
        return PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_CODE,
            createAlarmIntent(
                alarmKey =
                    alarmKey,
            ),
            PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createAlarmIntent(
        alarmKey: AlarmKey,
    ): Intent {
        return Intent(
            applicationContext,
            ReminderAlarmReceiver::class.java,
        ).apply {
            action =
                ReminderAlarmReceiver
                    .ACTION_FIRE_REMINDER

            data =
                Uri.Builder()
                    .scheme(URI_SCHEME)
                    .authority(URI_AUTHORITY)
                    .appendPath(URI_ALARM_PATH)
                    .appendPath(
                        alarmKey.stableToken,
                    )
                    .build()

            `package` =
                applicationContext
                    .packageName
        }
    }

    private companion object {
        const val REQUEST_CODE =
            0

        const val URI_SCHEME =
            "carepack"

        const val URI_AUTHORITY =
            "reminder"

        const val URI_ALARM_PATH =
            "alarm"
    }
}
