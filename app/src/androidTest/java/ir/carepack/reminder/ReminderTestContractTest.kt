package ir.carepack.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.R
import ir.carepack.domain.reminder.AlarmKey
import ir.carepack.domain.reminder.DefaultReminderTestCoordinator
import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.domain.reminder.ReminderTestScheduleResult
import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.reminder.alarm.AndroidAlarmGateway
import ir.carepack.reminder.alarm.ReminderTestAlarmGateway
import ir.carepack.reminder.alarm.ReminderTestAlarmRequest
import ir.carepack.reminder.notification.AndroidNotificationGateway
import ir.carepack.reminder.notification.ReminderNotificationContract
import ir.carepack.reminder.notification.ReminderTestNotificationGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.reminder.receiver.ReminderAlarmReceiver
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderTestContractTest {

    private lateinit var context: Context

    private lateinit var notificationManager:
            NotificationManager

    @Before
    fun setUp() {
        context =
            ApplicationProvider
                .getApplicationContext()

        notificationManager =
            checkNotNull(
                context.getSystemService(
                    NotificationManager::class.java,
                ),
            )

        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        AndroidAlarmGateway(
            context = context,
        ).cancelTest()

        notificationManager.cancelAll()
    }

    @Test
    fun testAlarmUsesDedicatedStableIdentityAndRepeatedSchedulingReplacesIt() {
        val gateway =
            AndroidAlarmGateway(
                context = context,
            )

        val testKey =
            AlarmKey.forTestReminder()

        assertNotEquals(
            testKey,
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "test-reminder",
            ),
        )

        assertNotEquals(
            testKey,
            AlarmKey.forDelayedOccurrence(
                occurrenceId =
                    "test-reminder",
            ),
        )

        try {
            gateway.scheduleTest(
                ReminderTestAlarmRequest(
                    triggerAt =
                        Instant.now()
                            .plusSeconds(3_600),
                    deliveryMode =
                        AlarmDeliveryMode.APPROXIMATE,
                ),
            )

            val first =
                findTestPendingIntent()

            gateway.scheduleTest(
                ReminderTestAlarmRequest(
                    triggerAt =
                        Instant.now()
                            .plusSeconds(7_200),
                    deliveryMode =
                        AlarmDeliveryMode.APPROXIMATE,
                ),
            )

            val replaced =
                findTestPendingIntent()

            assertNotNull(first)
            assertNotNull(replaced)
            assertEquals(first, replaced)
        } finally {
            gateway.cancelTest()
        }

        assertNull(
            findTestPendingIntent(),
        )
    }

    @Test
    fun testNotificationIsExplicitlyIdentifiedAndOpensReminderSettings() {
        assertTrue(
            isNotificationPermissionGranted(),
        )

        assertTrue(
            notificationManager
                .areNotificationsEnabled(),
        )

        AndroidNotificationGateway(
            context = context,
        ).postTestReminder(
            scheduledAt =
                Instant.parse(
                    "2026-06-24T08:00:30Z",
                ),
        )

        val notification =
            waitForPostedNotification()

        assertEquals(
            context.getString(
                R.string
                    .reminder_test_notification_title,
            ),
            notification
                .extras
                .getCharSequence(
                    Notification.EXTRA_TITLE,
                )
                ?.toString(),
        )

        assertEquals(
            context.getString(
                R.string
                    .reminder_test_notification_body,
            ),
            notification
                .extras
                .getCharSequence(
                    Notification.EXTRA_TEXT,
                )
                ?.toString(),
        )

        assertNotNull(
            notification.contentIntent,
        )

        assertNull(
            notification.fullScreenIntent,
        )

        val safeIntent =
            ReminderNotificationContract
                .createOpenReminderSettingsIntent(
                    context = context,
                )

        assertTrue(
            ReminderNotificationContract
                .isOpenReminderSettingsIntent(
                    safeIntent,
                ),
        )

        assertNull(
            ReminderNotificationContract
                .extractOccurrenceId(
                    safeIntent,
                ),
        )

        assertNotEquals(
            ReminderNotificationContract
                .testContentRequestCode(),
            ReminderNotificationContract
                .contentRequestCode(),
        )
    }

    @Test
    fun permissionFailureDoesNotCreateCarePlanOccurrenceOrReportRows() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val alarmGateway =
                    RecordingTestAlarmGateway()

                val notificationGateway =
                    RecordingTestNotificationGateway()

                val coordinator =
                    DefaultReminderTestCoordinator(
                        notificationPermissionGateway =
                            FixedNotificationPermissionGateway(
                                granted = false,
                            ),
                        exactAlarmCapabilityGateway =
                            FixedExactAlarmCapabilityGateway(
                                granted = true,
                            ),
                        alarmGateway = alarmGateway,
                        notificationGateway =
                            notificationGateway,
                        clock =
                            Clock.fixed(
                                Instant.parse(
                                    "2026-06-24T08:00:00Z",
                                ),
                                ZoneOffset.UTC,
                            ),
                        operationLock =
                            AppOperationGate(),
                    )

                assertEquals(
                    ReminderTestScheduleResult
                        .NotificationPermissionRequired,
                    coordinator.scheduleTestReminder(),
                )

                assertTrue(
                    alarmGateway.requests.isEmpty(),
                )

                assertTrue(
                    notificationGateway
                        .postedAt
                        .isEmpty(),
                )

                assertEquals(
                    0,
                    fixture
                        .database
                        .careRecipientDao()
                        .count(),
                )

                assertEquals(
                    0,
                    fixture
                        .database
                        .medicationDao()
                        .count(),
                )

                assertEquals(
                    0,
                    fixture
                        .database
                        .occurrenceDao()
                        .count(),
                )

                assertEquals(
                    0,
                    fixture
                        .database
                        .reportingDao()
                        .countReports(),
                )
            }
        }

    private fun findTestPendingIntent(): PendingIntent? {
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
                        .scheme("carepack")
                        .authority("reminder")
                        .appendPath("alarm")
                        .appendPath(
                            AlarmKey
                                .forTestReminder()
                                .stableToken,
                        )
                        .build()

                component =
                    ComponentName(
                        context,
                        ReminderAlarmReceiver::class.java,
                    )

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

    private fun waitForPostedNotification():
            Notification {
        val deadline =
            SystemClock.elapsedRealtime() +
                    NOTIFICATION_TIMEOUT_MILLIS

        while (
            SystemClock.elapsedRealtime() <
            deadline
        ) {
            val notification =
                notificationManager
                    .activeNotifications
                    .firstOrNull()
                    ?.notification

            if (notification != null) {
                return notification
            }

            SystemClock.sleep(
                POLL_INTERVAL_MILLIS,
            )
        }

        error(
            "The test reminder notification was not posted.",
        )
    }

    private fun isNotificationPermissionGranted():
            Boolean =
        Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission
                        .POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val NOTIFICATION_TIMEOUT_MILLIS =
            5_000L

        const val POLL_INTERVAL_MILLIS =
            100L
    }
}

private class FixedNotificationPermissionGateway(
    private val granted: Boolean,
) : NotificationPermissionGateway {

    override fun isPermissionGranted(): Boolean =
        granted

    override fun requiresRuntimePermission(): Boolean =
        true
}

private class FixedExactAlarmCapabilityGateway(
    private val granted: Boolean,
) : ExactAlarmCapabilityGateway {

    override fun canScheduleExactAlarms(): Boolean =
        granted
}

private class RecordingTestAlarmGateway :
    ReminderTestAlarmGateway {

    val requests =
        mutableListOf<ReminderTestAlarmRequest>()

    var cancelCount: Int = 0

    override fun scheduleTest(
        request: ReminderTestAlarmRequest,
    ) {
        requests += request
    }

    override fun cancelTest() {
        cancelCount += 1
    }
}

private class RecordingTestNotificationGateway :
    ReminderTestNotificationGateway {

    val postedAt =
        mutableListOf<Instant>()

    var cancelCount: Int = 0

    override fun postTestReminder(
        scheduledAt: Instant,
    ) {
        postedAt += scheduledAt
    }

    override fun cancelTestReminder() {
        cancelCount += 1
    }
}
