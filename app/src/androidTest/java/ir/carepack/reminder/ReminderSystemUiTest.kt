package ir.carepack.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import ir.carepack.CarePackApplication
import ir.carepack.MainActivity
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.reminder.ReminderNotification
import ir.carepack.reminder.notification.AndroidNotificationGateway
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderSystemUiTest {

    private lateinit var context: Context

    private lateinit var device: UiDevice

    private lateinit var notificationManager:
            NotificationManager

    @Before
    fun setUp() {
        context =
            ApplicationProvider
                .getApplicationContext()

        device =
            UiDevice.getInstance(
                InstrumentationRegistry
                    .getInstrumentation(),
            )

        notificationManager =
            checkNotNull(
                context.getSystemService(
                    NotificationManager::class.java,
                ),
            )

        notificationManager.cancelAll()

        device.pressHome()
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()

        device.pressBack()
        device.pressHome()
    }

    @Test
    @SdkSuppress(
        minSdkVersion = 33,
    )
    fun notificationPermissionPromptAppearsAfterExplicitRequest() {
        assumeFalse(
            isNotificationPermissionGranted(),
        )

        ActivityScenario
            .launch(
                MainActivity::class.java,
            )
            .use { scenario ->
                scenario.onActivity {
                        activity ->
                    activity.requestPermissions(
                        arrayOf(
                            Manifest.permission
                                .POST_NOTIFICATIONS,
                        ),
                        NOTIFICATION_PERMISSION_REQUEST,
                    )
                }

                val denyButton =
                    waitForPermissionButton(
                        resourceName =
                            "permission_deny_button",
                    )

                assumeNotNull(denyButton)

                assertTrue(
                    denyButton!!.isEnabled,
                )

                denyButton.click()

                device.waitForIdle()
            }
    }

    @Test
    @SdkSuppress(
        minSdkVersion = 31,
    )
    fun exactAlarmSpecialAccessIntentOpensAndroidSettings() {
        val alarmManager =
            checkNotNull(
                context.getSystemService(
                    AlarmManager::class.java,
                ),
            )

        assumeFalse(
            alarmManager
                .canScheduleExactAlarms(),
        )

        val intent =
            Intent(
                Settings
                    .ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse(
                    "package:${context.packageName}",
                ),
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK,
            )

        val resolvable =
            intent.resolveActivity(
                context.packageManager,
            )

        assumeNotNull(resolvable)

        context.startActivity(intent)

        val openedSettings =
            device.wait(
                Until.hasObject(
                    By.pkg(
                        SETTINGS_PACKAGE,
                    ),
                ),
                SYSTEM_UI_TIMEOUT_MILLIS,
            )

        assertTrue(openedSettings)
    }

    @Test
    fun tappingNotificationOpensValidatedOccurrenceWithoutWritingReport() =
        runBlocking {
            assumeTrue(
                isNotificationPermissionGranted(),
            )

            assumeTrue(
                notificationManager
                    .areNotificationsEnabled(),
            )

            val occurrence =
                prepareFutureOccurrence()

            assertNull(
                targetApplication()
                    .container
                    .database
                    .reportingDao()
                    .getReport(
                        occurrence
                            .occurrenceId,
                    ),
            )

            AndroidNotificationGateway(
                context = context,
            ).post(
                notification =
                    ReminderNotification(
                        occurrenceId =
                            occurrence
                                .occurrenceId,
                        medicationName =
                            occurrence
                                .medicationName,
                        localTime =
                            occurrence
                                .localTime,
                        scheduledAt =
                            occurrence
                                .scheduledAt,
                    ),
            )

            waitForNotificationToExist()

            val shadeOpened =
                device.openNotification()

            assumeTrue(shadeOpened)

            val notificationNode =
                device.wait(
                    Until.findObject(
                        By.textContains(
                            occurrence
                                .medicationName,
                        ),
                    ),
                    SYSTEM_UI_TIMEOUT_MILLIS,
                ) ?: device.wait(
                    Until.findObject(
                        By.textContains(
                            "زمان بررسی یک نوبت دارو",
                        ),
                    ),
                    SYSTEM_UI_TIMEOUT_MILLIS,
                )

            assumeNotNull(
                notificationNode,
            )

            notificationNode!!.click()

            val detailOpened =
                device.wait(
                    Until.hasObject(
                        By.text(
                            "جزئیات نوبت",
                        ),
                    ),
                    APP_UI_TIMEOUT_MILLIS,
                )

            assertTrue(detailOpened)

            assertTrue(
                device.hasObject(
                    By.text(
                        occurrence
                            .medicationName,
                    ),
                ),
            )

            assertNull(
                targetApplication()
                    .container
                    .database
                    .reportingDao()
                    .getReport(
                        occurrence
                            .occurrenceId,
                    ),
            )
        }

    private suspend fun prepareFutureOccurrence():
            SystemUiOccurrence {
        val application =
            targetApplication()

        val container =
            application.container

        container.database.clearAllTables()

        container
            .reminderPreferenceStore
            .setRemindersEnabled(
                enabled = false,
            )

        val recipientOutcome =
            container
                .carePlanService
                .createRecipient(
                    CreateRecipientCommand(
                        displayName =
                            "فرد آزمون سیستم",
                    ),
                )

        val recipientId =
            when (recipientOutcome) {
                is CreateRecipientOutcome.Created -> {
                    recipientOutcome.recipientId
                }

                is CreateRecipientOutcome.AlreadyExists -> {
                    recipientOutcome.recipientId
                }

                is CreateRecipientOutcome.Invalid -> {
                    error(
                        "System-test recipient creation failed.",
                    )
                }
            }

        val zone =
            container
                .zoneProvider
                .currentZone()

        val targetDateTime =
            ZonedDateTime
                .now(zone)
                .plusMinutes(
                    FUTURE_MINUTES,
                )
                .withSecond(0)
                .withNano(0)

        val targetDate =
            targetDateTime
                .toLocalDate()

        val minuteOfDay =
            targetDateTime
                .hour * 60 +
                    targetDateTime.minute

        val planOutcome =
            container
                .carePlanService
                .createMedicationAndSchedule(
                    CreateMedicationScheduleCommand(
                        recipientId =
                            recipientId,
                        medicationName =
                            SYSTEM_TEST_MEDICATION_NAME,
                        instruction =
                            "دستور محرمانه آزمون سیستم",
                        weekdays =
                            setOf(
                                targetDateTime
                                    .dayOfWeek,
                            ),
                        minutesOfDay =
                            listOf(
                                minuteOfDay,
                            ),
                        startDate =
                            targetDate,
                        endDate =
                            targetDate,
                        zoneId =
                            zone.id,
                    ),
                )

        val createdPlan =
            planOutcome as?
                    CreateMedicationScheduleOutcome
                    .Created
                ?: error(
                    "System-test schedule creation failed: $planOutcome",
                )

        val occurrenceId =
            createdPlan
                .occurrenceIds
                .single()

        val occurrence =
            checkNotNull(
                container
                    .database
                    .occurrenceDao()
                    .getById(
                        occurrenceId,
                    ),
            )

        container
            .setupPreferenceStore
            .markSetupComplete()

        return SystemUiOccurrence(
            occurrenceId =
                occurrence.id,
            medicationName =
                occurrence
                    .medicationNameSnapshot,
            localTime =
                targetDateTime
                    .toLocalTime(),
            scheduledAt =
                java.time.Instant
                    .ofEpochMilli(
                        occurrence
                            .scheduledAtEpochMillis,
                    ),
        )
    }

    private fun waitForNotificationToExist() {
        val deadline =
            SystemClock.elapsedRealtime() +
                    SYSTEM_UI_TIMEOUT_MILLIS

        while (
            SystemClock.elapsedRealtime() <
            deadline
        ) {
            if (
                notificationManager
                    .activeNotifications
                    .isNotEmpty()
            ) {
                return
            }

            SystemClock.sleep(
                POLL_INTERVAL_MILLIS,
            )
        }

        error(
            "The notification was not posted.",
        )
    }

    private fun waitForPermissionButton(
        resourceName: String,
    ): UiObject2? {
        PERMISSION_CONTROLLER_PACKAGES
            .forEach { packageName ->
                val button =
                    device.wait(
                        Until.findObject(
                            By.res(
                                packageName,
                                resourceName,
                            ),
                        ),
                        PERMISSION_BUTTON_TIMEOUT_MILLIS,
                    )

                if (button != null) {
                    return button
                }
            }

        return null
    }

    private fun targetApplication():
            CarePackApplication {
        return context.applicationContext as
                CarePackApplication
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

    private data class SystemUiOccurrence(
        val occurrenceId: String,
        val medicationName: String,
        val localTime:
        java.time.LocalTime,
        val scheduledAt:
        java.time.Instant,
    )

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST =
            4_001

        const val FUTURE_MINUTES =
            10L

        const val SYSTEM_TEST_MEDICATION_NAME =
            "داروی آزمون اعلان"

        const val SETTINGS_PACKAGE =
            "com.android.settings"

        const val SYSTEM_UI_TIMEOUT_MILLIS =
            8_000L

        const val APP_UI_TIMEOUT_MILLIS =
            12_000L

        const val PERMISSION_BUTTON_TIMEOUT_MILLIS =
            2_500L

        const val POLL_INTERVAL_MILLIS =
            100L

        val PERMISSION_CONTROLLER_PACKAGES =
            listOf(
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
            )
    }
}
