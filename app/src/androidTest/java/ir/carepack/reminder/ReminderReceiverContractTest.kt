package ir.carepack.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.reminder.AlarmKey
import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.reminder.alarm.AlarmRequest
import ir.carepack.reminder.alarm.AndroidAlarmGateway
import ir.carepack.reminder.receiver.ReminderAlarmReceiver
import ir.carepack.reminder.receiver.SystemReconciliationReceiver
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderReceiverContractTest {

    private val context: Context
        get() =
            ApplicationProvider
                .getApplicationContext()

    @Test
    fun manifestDeclaresOnlyRequiredPhaseFourPermissions() {
        val permissions =
            readPackageInfo()
                .requestedPermissions
                .orEmpty()
                .toSet()

        assertTrue(
            permissions.contains(
                Manifest.permission
                    .POST_NOTIFICATIONS,
            ),
        )

        assertTrue(
            permissions.contains(
                Manifest.permission
                    .SCHEDULE_EXACT_ALARM,
            ),
        )

        assertTrue(
            permissions.contains(
                Manifest.permission
                    .RECEIVE_BOOT_COMPLETED,
            ),
        )

        assertFalse(
            permissions.contains(
                Manifest.permission.INTERNET,
            ),
        )
    }

    @Test
    fun reminderReceivers_areEnabledAndNotExported() {
        val receiverInfo =
            readPackageInfo()
                .receivers
                .orEmpty()
                .associateBy {
                    it.name
                }

        val alarmReceiver =
            receiverInfo[
                ReminderAlarmReceiver::class.java
                    .name
            ]

        val reconciliationReceiver =
            receiverInfo[
                SystemReconciliationReceiver::class.java
                    .name
            ]

        assertNotNull(alarmReceiver)
        assertNotNull(reconciliationReceiver)

        assertTrue(
            alarmReceiver?.enabled == true,
        )

        assertTrue(
            reconciliationReceiver
                ?.enabled == true,
        )

        assertFalse(
            alarmReceiver?.exported == true,
        )

        assertFalse(
            reconciliationReceiver
                ?.exported == true,
        )
    }

    @Test
    fun lockedBootReceiver_isNotDeclared() {
        val receiverNames =
            readPackageInfo()
                .receivers
                .orEmpty()
                .map {
                    it.name
                }
                .toSet()

        assertFalse(
            receiverNames.any {
                    receiverName ->
                receiverName.contains(
                    "LockedBoot",
                    ignoreCase = true,
                )
            },
        )

        val lockedBootIntent =
            Intent(
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
            ).setPackage(
                context.packageName,
            )

        assertTrue(
            queryReceivers(
                intent =
                    lockedBootIntent,
            ).isEmpty(),
        )
    }

    @Test
    fun alarmPendingIntentIdentity_isStablePerSeriesAndDistinctAcrossSeries() {
        val alarmGateway =
            AndroidAlarmGateway(
                context = context,
            )

        val firstKey =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "receiver-series-1",
            )

        val secondKey =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "receiver-series-2",
            )

        try {
            alarmGateway.schedule(
                request =
                    AlarmRequest(
                        alarmKey =
                            firstKey,
                        occurrenceId =
                            "receiver-occurrence-1",
                        triggerAt =
                            Instant.now()
                                .plusSeconds(
                                    FUTURE_SECONDS,
                                ),
                        deliveryMode =
                            AlarmDeliveryMode
                                .APPROXIMATE,
                    ),
            )

            alarmGateway.schedule(
                request =
                    AlarmRequest(
                        alarmKey =
                            secondKey,
                        occurrenceId =
                            "receiver-occurrence-2",
                        triggerAt =
                            Instant.now()
                                .plusSeconds(
                                    FUTURE_SECONDS,
                                ),
                        deliveryMode =
                            AlarmDeliveryMode
                                .APPROXIMATE,
                    ),
            )

            val firstPendingIntent =
                findAlarmPendingIntent(
                    alarmKey =
                        firstKey,
                )

            val secondPendingIntent =
                findAlarmPendingIntent(
                    alarmKey =
                        secondKey,
                )

            assertNotNull(firstPendingIntent)
            assertNotNull(secondPendingIntent)

            assertNotEquals(
                firstPendingIntent,
                secondPendingIntent,
            )

            val repeatedFirstPendingIntent =
                findAlarmPendingIntent(
                    alarmKey =
                        firstKey,
                )

            assertNotNull(
                repeatedFirstPendingIntent,
            )

            assertTrue(
                firstPendingIntent ==
                        repeatedFirstPendingIntent,
            )
        } finally {
            alarmGateway.cancel(
                alarmKey =
                    firstKey,
            )

            alarmGateway.cancel(
                alarmKey =
                    secondKey,
            )
        }
    }

    @Test
    fun alarmReceiverIgnoresUnknownActionAndMissingOccurrenceId() {
        val receiver =
            ReminderAlarmReceiver()

        receiver.onReceive(
            context,
            Intent(
                "ir.carepack.action.UNKNOWN",
            ),
        )

        receiver.onReceive(
            context,
            Intent(
                context,
                ReminderAlarmReceiver::class.java,
            ).apply {
                action =
                    ReminderAlarmReceiver
                        .ACTION_FIRE_REMINDER
            },
        )
    }

    private fun findAlarmPendingIntent(
        alarmKey: AlarmKey,
    ): PendingIntent? {
        val intent =
            Intent(
                context,
                ReminderAlarmReceiver::class.java,
            ).apply {
                action =
                    ReminderAlarmReceiver
                        .ACTION_FIRE_REMINDER

                data =
                    Uri.Builder()
                        .scheme(
                            "carepack",
                        )
                        .authority(
                            "reminder",
                        )
                        .appendPath(
                            "alarm",
                        )
                        .appendPath(
                            alarmKey.stableToken,
                        )
                        .build()

                `package` =
                    context.packageName
            }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun readPackageInfo():
            PackageInfo {
        val packageManager =
            context.packageManager

        val flags =
            PackageManager.GET_RECEIVERS or
                    PackageManager
                        .GET_PERMISSIONS

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager
                    .PackageInfoFlags
                    .of(
                        flags.toLong(),
                    ),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(
                context.packageName,
                flags,
            )
        }
    }

    private fun queryReceivers(
        intent: Intent,
    ) = if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.TIRAMISU
    ) {
        context.packageManager
            .queryBroadcastReceivers(
                intent,
                PackageManager
                    .ResolveInfoFlags
                    .of(0L),
            )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager
            .queryBroadcastReceivers(
                intent,
                0,
            )
    }

    private companion object {
        const val FUTURE_SECONDS =
            86_400L
    }
}
