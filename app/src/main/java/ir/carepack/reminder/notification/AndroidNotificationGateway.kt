package ir.carepack.reminder.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import ir.carepack.R
import ir.carepack.domain.reminder.NoOpReminderDiagnosticSink
import ir.carepack.domain.reminder.ReminderDiagnosticEventType
import ir.carepack.domain.reminder.ReminderDiagnosticSink
import ir.carepack.domain.reminder.ReminderNotification
import ir.carepack.domain.reminder.recordReminderDiagnostic
import java.time.Clock
import java.time.Instant

class AndroidNotificationGateway(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val diagnosticSink: ReminderDiagnosticSink =
        NoOpReminderDiagnosticSink,
    private val idRegistry: NotificationIdRegistry =
        PersistentNotificationIdRegistry(context),
) : NotificationGateway,
    ReminderTestNotificationGateway {

    private val applicationContext =
        context.applicationContext

    private val notificationManager =
        checkNotNull(
            applicationContext.getSystemService(
                NotificationManager::class.java,
            ),
        )

    init {
        createChannel()
    }

    override fun post(
        notification: ReminderNotification,
    ) {
        diagnosticSink.recordReminderDiagnostic(
            type =
                ReminderDiagnosticEventType
                    .NOTIFICATION_POST_ATTEMPTED,
            clock = clock,
            occurrenceId = notification.occurrenceId,
        )

        try {
            val publicNotification =
                buildGenericNotification(
                    visibility =
                        NotificationCompat.VISIBILITY_PUBLIC,
                )

            val privateNotification =
                NotificationCompat.Builder(
                    applicationContext,
                    ReminderNotificationContract.CHANNEL_ID,
                )
                    .setSmallIcon(
                        R.drawable.ic_notification_reminder,
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
                        NotificationCompat.CATEGORY_ALARM,
                    )
                    .setPriority(
                        NotificationCompat.PRIORITY_HIGH,
                    )
                    .setDefaults(
                        NotificationCompat.DEFAULT_SOUND or
                                NotificationCompat.DEFAULT_VIBRATE,
                    )
                    .setVibrate(REMINDER_VIBRATION_PATTERN)
                    .setVisibility(
                        NotificationCompat.VISIBILITY_PRIVATE,
                    )
                    .setPublicVersion(publicNotification)
                    .setContentIntent(
                        createContentPendingIntent(
                            notification.occurrenceId,
                        ),
                    )
                    .setWhen(
                        notification.scheduledAt.toEpochMilli(),
                    )
                    .setShowWhen(true)
                    .setOnlyAlertOnce(false)
                    .setAutoCancel(true)
                    .build()

            notificationManager.notify(
                occurrenceNotificationId(
                    notification.occurrenceId,
                ),
                privateNotification,
            )

            diagnosticSink.recordReminderDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .NOTIFICATION_POSTED,
                clock = clock,
                occurrenceId = notification.occurrenceId,
            )
        } catch (failure: RuntimeException) {
            diagnosticSink.recordReminderDiagnostic(
                type =
                    ReminderDiagnosticEventType
                        .NOTIFICATION_FAILED,
                clock = clock,
                occurrenceId = notification.occurrenceId,
                outcome =
                    failure.javaClass.simpleName,
            )
            throw failure
        }
    }

    override fun postTestReminder(
        scheduledAt: Instant,
    ) {
        val notification =
            NotificationCompat.Builder(
                applicationContext,
                ReminderNotificationContract.CHANNEL_ID,
            )
                .setSmallIcon(
                    R.drawable.ic_notification_reminder,
                )
                .setContentTitle(
                    applicationContext.getString(
                        R.string
                            .reminder_test_notification_title,
                    ),
                )
                .setContentText(
                    applicationContext.getString(
                        R.string
                            .reminder_test_notification_body,
                    ),
                )
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(
                    NotificationCompat.DEFAULT_SOUND or
                            NotificationCompat.DEFAULT_VIBRATE,
                )
                .setVibrate(REMINDER_VIBRATION_PATTERN)
                .setVisibility(
                    NotificationCompat.VISIBILITY_PUBLIC,
                )
                .setContentIntent(
                    createTestContentPendingIntent(),
                )
                .setWhen(scheduledAt.toEpochMilli())
                .setShowWhen(true)
                .setOnlyAlertOnce(false)
                .setAutoCancel(true)
                .build()

        notificationManager.notify(
            TEST_NOTIFICATION_ID,
            notification,
        )
    }

    override fun cancel(
        occurrenceId: String,
    ) {
        require(occurrenceId.isNotBlank())
        val notificationId =
            idRegistry.findExistingId(
                namespace = OCCURRENCE_NAMESPACE,
                stableKey = occurrenceId,
            ) ?: return

        notificationManager.cancel(notificationId)
        check(
            idRegistry.forget(
                namespace = OCCURRENCE_NAMESPACE,
                stableKey = occurrenceId,
            ),
        )
    }

    override fun cancelTestReminder() {
        notificationManager.cancel(TEST_NOTIFICATION_ID)
    }

    override fun cancelAll() {
        notificationManager.cancelAll()
        idRegistry.clearAll()
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(
                ReminderNotificationContract.CHANNEL_ID,
                applicationContext.getString(
                    R.string
                        .reminder_notification_channel_name,
                ),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description =
                    applicationContext.getString(
                        R.string
                            .reminder_notification_channel_description,
                    )
                lockscreenVisibility =
                    Notification.VISIBILITY_PRIVATE
                enableVibration(true)
                vibrationPattern = REMINDER_VIBRATION_PATTERN
            }

        notificationManager.createNotificationChannel(channel)
    }

    private fun buildGenericNotification(
        visibility: Int,
    ): Notification =
        NotificationCompat.Builder(
            applicationContext,
            ReminderNotificationContract.CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(
                applicationContext.getString(
                    R.string.reminder_notification_public_title,
                ),
            )
            .setContentText(
                applicationContext.getString(
                    R.string.reminder_notification_public_body,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(visibility)
            .build()

    private fun createContentPendingIntent(
        occurrenceId: String,
    ): PendingIntent =
        PendingIntent.getActivity(
            applicationContext,
            ReminderNotificationContract.contentRequestCode(),
            ReminderNotificationContract
                .createOpenOccurrenceIntent(
                    context = applicationContext,
                    occurrenceId = occurrenceId,
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createTestContentPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            applicationContext,
            ReminderNotificationContract
                .testContentRequestCode(),
            ReminderNotificationContract
                .createOpenReminderSettingsIntent(
                    applicationContext,
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )

    private fun occurrenceNotificationId(
        occurrenceId: String,
    ): Int =
        idRegistry.idFor(
            namespace = OCCURRENCE_NAMESPACE,
            stableKey = occurrenceId,
        )

    private companion object {
        val REMINDER_VIBRATION_PATTERN =
            longArrayOf(0L, 500L, 250L, 500L)
        const val OCCURRENCE_NAMESPACE = "occurrence"
        const val TEST_NOTIFICATION_ID = 0x7fffff01
    }
}
