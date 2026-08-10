package ir.carepack.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.carepack.domain.reminder.AlarmKey
import ir.carepack.domain.reminder.NoOpReminderDiagnosticSink
import ir.carepack.domain.reminder.ReminderDiagnosticEventType
import ir.carepack.domain.reminder.ReminderDiagnosticSink
import ir.carepack.domain.reminder.recordReminderDiagnostic
import ir.carepack.reminder.receiver.ReminderAlarmReceiver
import java.time.Clock

class AndroidAlarmGateway(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val diagnosticSink: ReminderDiagnosticSink =
        NoOpReminderDiagnosticSink,
) : AlarmGateway,
    ReminderTestAlarmGateway {

    private val applicationContext = context.applicationContext

    private val alarmManager = checkNotNull(
            applicationContext.getSystemService(
                    AlarmManager::class.java,
                ),
        )

    override fun schedule(
        request: AlarmRequest,
    ) {
        diagnosticSink.recordReminderDiagnostic(
            type = ReminderDiagnosticEventType
                    .ALARM_REGISTRATION_ATTEMPTED,
            clock = clock,
            occurrenceId = request.occurrenceId,
            alarmKey = request.alarmKey,
            deliveryMode = request
                    .deliveryMode.toReminderDeliveryMode(),
        )

        try {
            val pendingIntent = createOccurrencePendingIntent(
                    alarmKey = request.alarmKey,
                    occurrenceId = request.occurrenceId,
                    flags = PendingIntent
                            .FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE,
                )

            registerAlarm(
                triggerAtEpochMillis = request
                        .triggerAt.toEpochMilli(),
                deliveryMode = request.deliveryMode,
                pendingIntent = pendingIntent,
            )

            diagnosticSink.recordReminderDiagnostic(
                type = ReminderDiagnosticEventType
                        .ALARM_REGISTERED,
                clock = clock,
                occurrenceId = request.occurrenceId,
                alarmKey = request.alarmKey,
                deliveryMode = request
                        .deliveryMode.toReminderDeliveryMode(),
            )
        } catch (failure: RuntimeException) {
            diagnosticSink.recordReminderDiagnostic(
                type = ReminderDiagnosticEventType
                        .ALARM_REGISTRATION_FAILED,
                clock = clock,
                occurrenceId = request.occurrenceId,
                alarmKey = request.alarmKey,
                deliveryMode = request
                        .deliveryMode.toReminderDeliveryMode(),
                outcome = failure
                        .javaClass.simpleName,
            )

            throw failure
        }
    }

    override fun cancel(
        alarmKey: AlarmKey,
    ) {
        val existingPendingIntent = findOccurrencePendingIntent(
                alarmKey = alarmKey,
            ) ?: return

        cancelPendingIntent(
            pendingIntent = existingPendingIntent,
        )
    }

    override fun scheduleTest(
        request: ReminderTestAlarmRequest,
    ) {
        val alarmKey = AlarmKey.forTestReminder()

        diagnosticSink.recordReminderDiagnostic(
            type = ReminderDiagnosticEventType
                    .ALARM_REGISTRATION_ATTEMPTED,
            clock = clock,
            alarmKey = alarmKey,
            deliveryMode = request
                    .deliveryMode.toReminderDeliveryMode(),
            outcome = TEST_DIAGNOSTIC_OUTCOME,
        )

        try {
            val pendingIntent = createTestPendingIntent(
                    flags = PendingIntent
                            .FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE,
                )

            registerAlarm(
                triggerAtEpochMillis = request
                        .triggerAt.toEpochMilli(),
                deliveryMode = request.deliveryMode,
                pendingIntent = pendingIntent,
            )

            diagnosticSink.recordReminderDiagnostic(
                type = ReminderDiagnosticEventType
                        .ALARM_REGISTERED,
                clock = clock,
                alarmKey = alarmKey,
                deliveryMode = request
                        .deliveryMode.toReminderDeliveryMode(),
                outcome = TEST_DIAGNOSTIC_OUTCOME,
            )
        } catch (failure: RuntimeException) {
            diagnosticSink.recordReminderDiagnostic(
                type = ReminderDiagnosticEventType
                        .ALARM_REGISTRATION_FAILED,
                clock = clock,
                alarmKey = alarmKey,
                deliveryMode = request
                        .deliveryMode.toReminderDeliveryMode(),
                outcome = "$TEST_DIAGNOSTIC_OUTCOME:" +
                            failure.javaClass
                                .simpleName,
            )

            throw failure
        }
    }

    override fun cancelTest() {
        val pendingIntent = findTestPendingIntent()
                ?: return

        cancelPendingIntent(
            pendingIntent = pendingIntent,
        )
    }

    private fun registerAlarm(
        triggerAtEpochMillis: Long,
        deliveryMode: AlarmDeliveryMode,
        pendingIntent: PendingIntent,
    ) {
        when (deliveryMode) {
            AlarmDeliveryMode.EXACT -> {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMillis,
                        pendingIntent,
                    )
            }

            AlarmDeliveryMode.APPROXIMATE -> {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMillis,
                        pendingIntent,
                    )
            }
        }
    }

    private fun createOccurrencePendingIntent(
        alarmKey: AlarmKey,
        occurrenceId: String,
        flags: Int,
    ): PendingIntent {
        val intent = createAlarmIntent(
                alarmKey = alarmKey,
            ).apply {
                putExtra(
                    ReminderAlarmReceiver.EXTRA_ALARM_TYPE,
                    ReminderAlarmReceiver.ALARM_TYPE_OCCURRENCE,
                )

                putExtra(
                    ReminderAlarmReceiver.EXTRA_OCCURRENCE_ID,
                    occurrenceId,
                )

                putExtra(
                    ReminderAlarmReceiver.EXTRA_SCHEDULE_SERIES_ID,
                    alarmKey.scheduleSeriesId,
                )
            }

        return PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_CODE,
            intent,
            flags,
        )
    }

    private fun createTestPendingIntent(
        flags: Int,
    ): PendingIntent {
        val intent = createAlarmIntent(
                alarmKey = AlarmKey.forTestReminder(),
            ).apply {
                putExtra(
                    ReminderAlarmReceiver.EXTRA_ALARM_TYPE,
                    ReminderAlarmReceiver.ALARM_TYPE_TEST,
                )
            }

        return PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_CODE,
            intent,
            flags,
        )
    }

    private fun findOccurrencePendingIntent(
        alarmKey: AlarmKey,
    ): PendingIntent? = PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_CODE,
            createAlarmIntent(
                alarmKey = alarmKey,
            ),
            PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE,
        )

    private fun findTestPendingIntent(): PendingIntent? =
        PendingIntent.getBroadcast(
            applicationContext,
            REQUEST_CODE,
            createAlarmIntent(
                alarmKey = AlarmKey.forTestReminder(),
            ),
            PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancelPendingIntent(
        pendingIntent: PendingIntent,
    ) {
        alarmManager.cancel(
            pendingIntent,
        )

        pendingIntent.cancel()
    }

    private fun createAlarmIntent(
        alarmKey: AlarmKey,
    ): Intent = Intent(
            applicationContext,
            ReminderAlarmReceiver::class.java,
        ).apply {
            action = ReminderAlarmReceiver
                    .ACTION_FIRE_REMINDER

            data = Uri.Builder()
                    .scheme(URI_SCHEME).authority(URI_AUTHORITY)
                    .appendPath(URI_ALARM_PATH).appendPath(
                        alarmKey.stableToken,
                    ).build()

            `package` = applicationContext
                    .packageName
        }

    private companion object {
        const val REQUEST_CODE = 0

        const val URI_SCHEME = "carepack"

        const val URI_AUTHORITY = "reminder"

        const val URI_ALARM_PATH = "alarm"

        const val TEST_DIAGNOSTIC_OUTCOME = "test_reminder"
    }
}
