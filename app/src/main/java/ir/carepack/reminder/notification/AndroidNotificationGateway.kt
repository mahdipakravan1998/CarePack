package ir.carepack.reminder.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import ir.carepack.R
import ir.carepack.domain.reminder.ReminderNotification
import java.util.Locale

class AndroidNotificationGateway(
    context: Context,
) : NotificationGateway {

    private val applicationContext =
        context.applicationContext

    private val notificationManager =
        checkNotNull(
            applicationContext
                .getSystemService(
                    NotificationManager::class.java,
                ),
        )

    init {
        createChannel()
    }

    override fun post(
        notification: ReminderNotification,
    ) {
        val publicNotification =
            buildPublicNotification()

        val fullNotification =
            NotificationCompat.Builder(
                applicationContext,
                ReminderNotificationContract
                    .CHANNEL_ID,
            )
                .setSmallIcon(
                    R.drawable
                        .ic_notification_reminder,
                )
                .setContentTitle(
                    applicationContext.getString(
                        R.string
                            .reminder_notification_title,
                    ),
                )
                .setContentText(
                    applicationContext.getString(
                        R.string
                            .reminder_notification_body,
                        notification
                            .medicationName,
                        notification
                            .localTime
                            .toDisplayText(),
                    ),
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_REMINDER,
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_HIGH,
                )
                .setVisibility(
                    NotificationCompat
                        .VISIBILITY_PRIVATE,
                )
                .setPublicVersion(
                    publicNotification,
                )
                .setContentIntent(
                    createContentPendingIntent(
                        occurrenceId =
                            notification
                                .occurrenceId,
                    ),
                )
                .setWhen(
                    notification
                        .scheduledAt
                        .toEpochMilli(),
                )
                .setShowWhen(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()

        notificationManager.notify(
            notificationId(
                occurrenceId =
                    notification
                        .occurrenceId,
            ),
            fullNotification,
        )
    }

    override fun cancel(
        occurrenceId: String,
    ) {
        require(occurrenceId.isNotBlank())

        notificationManager.cancel(
            notificationId(
                occurrenceId =
                    occurrenceId,
            ),
        )
    }

    override fun cancelAll() {
        notificationManager.cancelAll()
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(
                ReminderNotificationContract
                    .CHANNEL_ID,
                applicationContext.getString(
                    R.string
                        .reminder_notification_channel_name,
                ),
                NotificationManager
                    .IMPORTANCE_HIGH,
            ).apply {
                description =
                    applicationContext.getString(
                        R.string
                            .reminder_notification_channel_description,
                    )

                lockscreenVisibility =
                    Notification
                        .VISIBILITY_PRIVATE

                enableVibration(true)
            }

        notificationManager
            .createNotificationChannel(
                channel,
            )
    }

    private fun buildPublicNotification():
            Notification {
        return NotificationCompat.Builder(
            applicationContext,
            ReminderNotificationContract
                .CHANNEL_ID,
        )
            .setSmallIcon(
                R.drawable
                    .ic_notification_reminder,
            )
            .setContentTitle(
                applicationContext.getString(
                    R.string
                        .reminder_notification_public_title,
                ),
            )
            .setContentText(
                applicationContext.getString(
                    R.string
                        .reminder_notification_public_body,
                ),
            )
            .setCategory(
                NotificationCompat
                    .CATEGORY_REMINDER,
            )
            .setVisibility(
                NotificationCompat
                    .VISIBILITY_PUBLIC,
            )
            .build()
    }

    private fun createContentPendingIntent(
        occurrenceId: String,
    ): PendingIntent {
        return PendingIntent.getActivity(
            applicationContext,
            CONTENT_REQUEST_CODE,
            ReminderNotificationContract
                .createOpenOccurrenceIntent(
                    context =
                        applicationContext,
                    occurrenceId =
                        occurrenceId,
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(
        occurrenceId: String,
    ): Int {
        return occurrenceId
            .hashCode() and
                Int.MAX_VALUE
    }

    private fun java.time.LocalTime
            .toDisplayText(): String {
        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            hour,
            minute,
        )
    }

    private companion object {
        const val CONTENT_REQUEST_CODE =
            0
    }
}
