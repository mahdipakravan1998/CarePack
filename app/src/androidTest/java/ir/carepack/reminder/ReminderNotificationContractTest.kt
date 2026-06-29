package ir.carepack.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.MainActivity
import ir.carepack.domain.reminder.ReminderNotification
import ir.carepack.reminder.navigation.NotificationNavigationValidator
import ir.carepack.reminder.notification.AndroidNotificationGateway
import ir.carepack.reminder.notification.ReminderNotificationContract
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNotificationContractTest {

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
        notificationManager.cancelAll()
    }

    @Test
    fun openOccurrenceIntent_isExplicitAndRoundTripsOccurrenceId() {
        val occurrenceId =
            "occurrence-contract-1"

        val intent =
            ReminderNotificationContract
                .createOpenOccurrenceIntent(
                    context = context,
                    occurrenceId =
                        occurrenceId,
                )

        assertEquals(
            ReminderNotificationContract
                .ACTION_OPEN_OCCURRENCE,
            intent.action,
        )

        assertEquals(
            MainActivity::class.java.name,
            intent.component
                ?.className,
        )

        assertEquals(
            context.packageName,
            intent.component
                ?.packageName,
        )

        assertEquals(
            "carepack",
            intent.data?.scheme,
        )

        assertEquals(
            "reminder",
            intent.data?.authority,
        )

        assertEquals(
            listOf(
                "occurrence",
                occurrenceId,
            ),
            intent.data?.pathSegments,
        )

        assertTrue(
            intent.flags and
                    Intent.FLAG_ACTIVITY_CLEAR_TOP !=
                    0,
        )

        assertTrue(
            intent.flags and
                    Intent.FLAG_ACTIVITY_SINGLE_TOP !=
                    0,
        )

        assertEquals(
            occurrenceId,
            ReminderNotificationContract
                .extractOccurrenceId(
                    intent = intent,
                ),
        )
    }

    @Test
    fun mismatchedUriAndExtra_areRejected() {
        val intent =
            ReminderNotificationContract
                .createOpenOccurrenceIntent(
                    context = context,
                    occurrenceId =
                        "occurrence-uri",
                )
                .putExtra(
                    ReminderNotificationContract
                        .EXTRA_OCCURRENCE_ID,
                    "occurrence-extra",
                )

        assertNull(
            ReminderNotificationContract
                .extractOccurrenceId(
                    intent = intent,
                ),
        )
    }

    @Test
    fun unexpectedAction_isRejected() {
        val intent =
            ReminderNotificationContract
                .createOpenOccurrenceIntent(
                    context = context,
                    occurrenceId =
                        "occurrence-1",
                )
                .setAction(
                    "ir.carepack.action.INVALID",
                )

        assertNull(
            ReminderNotificationContract
                .extractOccurrenceId(
                    intent = intent,
                ),
        )
    }

    @Test
    fun navigationValidator_acceptsOnlyPersistedOccurrence_withoutWritingReport() =
        runBlocking {
            val fixture =
                CarePlanRoomTestFixture.create(
                    initialInstant =
                        Instant.parse(
                            "2026-06-24T06:00:00Z",
                        ),
                    idPrefix =
                        "notification-contract",
                )

            try {
                val plan =
                    fixture.createPlan(
                        weekdays =
                            setOf(
                                DayOfWeek.WEDNESDAY,
                            ),
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                        startDate =
                            LocalDate.parse(
                                "2026-06-24",
                            ),
                        endDate =
                            LocalDate.parse(
                                "2026-06-24",
                            ),
                    )

                val occurrenceId =
                    plan.occurrenceIds.single()

                val validator =
                    NotificationNavigationValidator(
                        database =
                            fixture.database,
                    )

                val validIntent =
                    ReminderNotificationContract
                        .createOpenOccurrenceIntent(
                            context = context,
                            occurrenceId =
                                occurrenceId,
                        )

                assertEquals(
                    occurrenceId,
                    validator
                        .validatedOccurrenceId(
                            intent =
                                validIntent,
                        ),
                )

                val missingIntent =
                    ReminderNotificationContract
                        .createOpenOccurrenceIntent(
                            context = context,
                            occurrenceId =
                                "missing-occurrence",
                        )

                assertNull(
                    validator
                        .validatedOccurrenceId(
                            intent =
                                missingIntent,
                        ),
                )

                assertEquals(
                    0,
                    fixture.database
                        .reportingDao()
                        .countReports(),
                )

                assertNull(
                    fixture.database
                        .reportingDao()
                        .getReport(
                            occurrenceId,
                        ),
                )
            } finally {
                fixture.close()
            }
        }

    @Test
    fun notificationChannel_doesNotExposePublicLockScreenContent() {
        AndroidNotificationGateway(
            context = context,
        )

        val channel =
            notificationManager
                .getNotificationChannel(
                    ReminderNotificationContract
                        .CHANNEL_ID,
                )

        assertNotNull(channel)

        assertFalse(
            channel?.lockscreenVisibility ==
                    Notification.VISIBILITY_PUBLIC,
        )
    }

    @Test
    fun postedNotification_hasPrivateFullContentAndGenericPublicVersion() {
        assumeTrue(
            isNotificationPermissionGranted(),
        )

        assumeTrue(
            notificationManager
                .areNotificationsEnabled(),
        )

        val gateway =
            AndroidNotificationGateway(
                context = context,
            )

        val sensitiveInstruction =
            "دستور محرمانه نباید در اعلان باشد"

        val sensitiveRecipient =
            "نام فرد محرمانه"

        val medicationName =
            "داروی قابل نمایش"

        gateway.post(
            notification =
                ReminderNotification(
                    occurrenceId =
                        "privacy-occurrence",
                    medicationName =
                        medicationName,
                    localTime =
                        LocalTime.of(
                            12,
                            30,
                        ),
                    scheduledAt =
                        Instant.parse(
                            "2026-06-24T09:00:00Z",
                        ),
                ),
        )

        val notification =
            waitForPostedNotification()

        assertEquals(
            Notification.VISIBILITY_PRIVATE,
            notification.visibility,
        )

        val publicVersion =
            notification.publicVersion

        assertNotNull(publicVersion)

        assertEquals(
            Notification.VISIBILITY_PUBLIC,
            publicVersion?.visibility,
        )

        val fullText =
            notification
                .extras
                .getCharSequence(
                    Notification.EXTRA_TEXT,
                )
                ?.toString()
                .orEmpty()

        assertTrue(
            fullText.contains(
                medicationName,
            ),
        )

        assertFalse(
            fullText.contains(
                sensitiveInstruction,
            ),
        )

        assertFalse(
            fullText.contains(
                sensitiveRecipient,
            ),
        )

        val publicTitle =
            publicVersion
                ?.extras
                ?.getCharSequence(
                    Notification.EXTRA_TITLE,
                )
                ?.toString()
                .orEmpty()

        val publicText =
            publicVersion
                ?.extras
                ?.getCharSequence(
                    Notification.EXTRA_TEXT,
                )
                ?.toString()
                .orEmpty()

        assertFalse(
            publicTitle.contains(
                medicationName,
            ),
        )

        assertFalse(
            publicText.contains(
                medicationName,
            ),
        )

        assertFalse(
            publicText.contains(
                sensitiveInstruction,
            ),
        )

        assertFalse(
            publicText.contains(
                sensitiveRecipient,
            ),
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
            "The reminder notification was not posted.",
        )
    }

    private fun isNotificationPermissionGranted():
            Boolean {
        return Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission
                        .POST_NOTIFICATIONS,
                ) ==
                PackageManager
                    .PERMISSION_GRANTED
    }

    private companion object {
        const val NOTIFICATION_TIMEOUT_MILLIS =
            5_000L

        const val POLL_INTERVAL_MILLIS =
            100L
    }
}
