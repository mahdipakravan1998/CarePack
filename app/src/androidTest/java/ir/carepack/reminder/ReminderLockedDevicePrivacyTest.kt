package ir.carepack.reminder

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ir.carepack.domain.reminder.ReminderNotification
import ir.carepack.reminder.notification.AndroidNotificationGateway
import java.time.Instant
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderLockedDevicePrivacyTest {

    private lateinit var context: Context
    private lateinit var device: UiDevice
    private lateinit var notificationManager: NotificationManager
    private lateinit var keyguardManager: KeyguardManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        device =
            UiDevice.getInstance(
                InstrumentationRegistry.getInstrumentation(),
            )
        notificationManager =
            checkNotNull(
                context.getSystemService(NotificationManager::class.java),
            )
        keyguardManager =
            checkNotNull(
                context.getSystemService(KeyguardManager::class.java),
            )
        notificationManager.cancelAll()
        assertTrue(
            "The release privacy test device must have a secure lock configured.",
            keyguardManager.isDeviceSecure,
        )
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
        device.wakeUp()

        if (keyguardManager.isDeviceLocked) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                20,
            )
            device.executeShellCommand(
                "input text $TEST_DEVICE_PIN",
            )
            device.pressEnter()
            device.wait(
                Until.gone(By.pkg("com.android.systemui")),
                UI_TIMEOUT_MILLIS,
            )
        }

        device.pressHome()
    }

    @Test
    fun lockedDevice_notificationAndTapExposeNoPiiOrCaregiverActions() {
        val medicationName = "داروی بسیار محرمانه آزمون قفل"
        val recipientName = "نام محرمانه فرد"
        val instruction = "دستور محرمانه دارو"

        AndroidNotificationGateway(context).post(
            ReminderNotification(
                occurrenceId = "locked-privacy-occurrence",
                medicationName = medicationName,
                localTime = LocalTime.of(12, 30),
                scheduledAt = Instant.parse("2026-06-24T09:00:00Z"),
            ),
        )

        waitForNotification()
        device.sleep()
        device.wakeUp()
        device.waitForIdle()

        assertTrue(
            "The device must still be locked while privacy assertions run.",
            keyguardManager.isDeviceLocked,
        )

        device.openNotification()
        device.waitForIdle()

        assertSensitiveTextAbsent(
            medicationName,
            recipientName,
            instruction,
            "مراقب: داده شد",
            "مراقب: داده نشد",
            "ثبت نوبت یادآوری",
        )

        val genericNotification =
            device.wait(
                Until.findObject(
                    By.textContains("یادآوری CarePack"),
                ),
                UI_TIMEOUT_MILLIS,
            ) ?: device.wait(
                Until.findObject(
                    By.textContains("زمان بررسی یک نوبت دارو"),
                ),
                UI_TIMEOUT_MILLIS,
            )

        assertTrue(
            "A generic reminder notification must be visible on the locked device.",
            genericNotification != null,
        )

        genericNotification?.click()
        device.waitForIdle()

        assertTrue(
            "The keyguard must remain active after tapping the reminder.",
            keyguardManager.isDeviceLocked,
        )

        assertSensitiveTextAbsent(
            medicationName,
            recipientName,
            instruction,
            "مراقب: داده شد",
            "مراقب: داده نشد",
            "ثبت نوبت یادآوری",
        )
    }

    private fun assertSensitiveTextAbsent(
        vararg forbiddenValues: String,
    ) {
        forbiddenValues.forEach { forbiddenValue ->
            assertFalse(
                "Locked UI exposed forbidden text: $forbiddenValue",
                device.hasObject(By.textContains(forbiddenValue)),
            )
        }
    }

    private fun waitForNotification() {
        val deadline =
            SystemClock.elapsedRealtime() + UI_TIMEOUT_MILLIS

        while (SystemClock.elapsedRealtime() < deadline) {
            if (notificationManager.activeNotifications.isNotEmpty()) {
                return
            }
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }

        error("The reminder notification was not posted.")
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 10_000L
        const val TEST_DEVICE_PIN = "246810"
        const val POLL_INTERVAL_MILLIS = 100L
    }
}
