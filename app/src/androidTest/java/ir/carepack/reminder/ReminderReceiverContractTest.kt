package ir.carepack.reminder

import android.Manifest
import android.app.AlarmManager
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
import ir.carepack.CarePackApplication
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.reminder.AlarmKey
import ir.carepack.domain.reminder.ReminderDiagnosticEvent
import ir.carepack.domain.reminder.ReminderDiagnosticEventType
import ir.carepack.domain.reminder.ReminderDiagnosticSink
import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.reminder.alarm.AlarmRequest
import ir.carepack.reminder.alarm.AndroidAlarmGateway
import ir.carepack.reminder.diagnostic.LogcatReminderDiagnosticSink
import ir.carepack.reminder.receiver.ReminderAlarmReceiver
import ir.carepack.reminder.receiver.SystemReconciliationReceiver
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderReceiverContractTest {

    private val context: Context
        get() =
            ApplicationProvider
                .getApplicationContext()

    @Test
    fun manifestDeclaresOnlyRequiredReminderPermissions() {
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

        assertTrue(
            permissions.contains(
                Manifest.permission
                    .USE_FULL_SCREEN_INTENT,
            ),
        )

        assertTrue(
            permissions.contains(
                Manifest.permission.VIBRATE,
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
                ReminderAlarmReceiver::class.java.name
            ]

        val reconciliationReceiver =
            receiverInfo[
                SystemReconciliationReceiver::class.java.name
            ]

        assertNotNull(
            alarmReceiver,
        )

        assertNotNull(
            reconciliationReceiver,
        )

        assertTrue(
            alarmReceiver?.enabled == true,
        )

        assertTrue(
            reconciliationReceiver?.enabled == true,
        )

        assertFalse(
            alarmReceiver?.exported == true,
        )

        assertFalse(
            reconciliationReceiver?.exported == true,
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
            receiverNames.any { receiverName ->
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
                            AlarmDeliveryMode.APPROXIMATE,
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
                            AlarmDeliveryMode.APPROXIMATE,
                    ),
            )

            val firstPendingIntent =
                findAlarmPendingIntent(
                    alarmKey =
                        firstKey,
                )

            val firstPendingIntentAgain =
                findAlarmPendingIntent(
                    alarmKey =
                        firstKey,
                )

            val secondPendingIntent =
                findAlarmPendingIntent(
                    alarmKey =
                        secondKey,
                )

            assertNotNull(
                firstPendingIntent,
            )

            assertNotNull(
                firstPendingIntentAgain,
            )

            assertNotNull(
                secondPendingIntent,
            )

            assertTrue(
                firstPendingIntent ==
                        firstPendingIntentAgain,
            )

            assertNotEquals(
                firstPendingIntent,
                secondPendingIntent,
            )
        } finally {
            alarmGateway.cancel(
                firstKey,
            )

            alarmGateway.cancel(
                secondKey,
            )
        }
    }

    @Test
    fun realAlarmGatewayRegistration_recordsDiagnosticMilestonesWithSafeTokens() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        Instant.parse(
                            "2026-06-24T06:00:00Z",
                        ),
                    idPrefix =
                        "real-alarm-diagnostic",
                )
                .use { fixture ->
                    val recipientId =
                        fixture.createOrGetRecipient(
                            displayName =
                                SENSITIVE_RECIPIENT_NAME,
                        )

                    val plan =
                        fixture.createPlan(
                            recipientId =
                                recipientId,
                            medicationName =
                                SENSITIVE_MEDICATION_NAME,
                            instruction =
                                SENSITIVE_INSTRUCTION_TEXT,
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

                    val alarmKey =
                        AlarmKey.forScheduleSeries(
                            scheduleSeriesId =
                                plan.scheduleSeriesId,
                        )

                    val diagnosticSink =
                        RecordingReminderDiagnosticSink()

                    val alarmGateway =
                        AndroidAlarmGateway(
                            context = context,
                            clock =
                                Clock.fixed(
                                    Instant.parse(
                                        "2026-06-24T06:00:00Z",
                                    ),
                                    ZoneOffset.UTC,
                                ),
                            diagnosticSink =
                                diagnosticSink,
                        )

                    try {
                        val registrationFailure =
                            try {
                                alarmGateway.schedule(
                                    request =
                                        AlarmRequest(
                                            alarmKey =
                                                alarmKey,
                                            occurrenceId =
                                                occurrenceId,
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

                                null
                            } catch (failure: RuntimeException) {
                                failure
                            }

                        val eventTypes =
                            diagnosticSink
                                .events
                                .map {
                                    it.type
                                }

                        assertTrue(
                            eventTypes.contains(
                                ReminderDiagnosticEventType
                                    .ALARM_REGISTRATION_ATTEMPTED,
                            ),
                        )

                        assertTrue(
                            eventTypes.contains(
                                ReminderDiagnosticEventType
                                    .ALARM_REGISTERED,
                            ) ||
                                    eventTypes.contains(
                                        ReminderDiagnosticEventType
                                            .ALARM_REGISTRATION_FAILED,
                                    ),
                        )

                        if (registrationFailure != null) {
                            assertTrue(
                                eventTypes.contains(
                                    ReminderDiagnosticEventType
                                        .ALARM_REGISTRATION_FAILED,
                                ),
                            )
                        }

                        val registrationEvents =
                            diagnosticSink
                                .events
                                .filter { event ->
                                    event.type ==
                                            ReminderDiagnosticEventType
                                                .ALARM_REGISTRATION_ATTEMPTED ||
                                            event.type ==
                                            ReminderDiagnosticEventType
                                                .ALARM_REGISTERED ||
                                            event.type ==
                                            ReminderDiagnosticEventType
                                                .ALARM_REGISTRATION_FAILED
                                }

                        assertTrue(
                            registrationEvents.isNotEmpty(),
                        )

                        registrationEvents.forEach { event ->
                            val occurrenceToken =
                                checkNotNull(
                                    event.occurrenceToken,
                                )

                            val alarmKeyToken =
                                checkNotNull(
                                    event.alarmKeyToken,
                                )

                            assertTrue(
                                occurrenceToken.length <=
                                        MAX_DIAGNOSTIC_TOKEN_LENGTH,
                            )

                            assertTrue(
                                alarmKeyToken.length <=
                                        MAX_DIAGNOSTIC_TOKEN_LENGTH,
                            )

                            assertNotEquals(
                                occurrenceId,
                                occurrenceToken,
                            )

                            assertNotEquals(
                                plan.scheduleSeriesId,
                                alarmKeyToken,
                            )

                            val payload =
                                event.toString()

                            val forbiddenValues =
                                SENSITIVE_VALUES +
                                        listOf(
                                            occurrenceId,
                                            plan.scheduleSeriesId,
                                        )

                            forbiddenValues.forEach { forbiddenValue ->
                                assertFalse(
                                    "Diagnostic payload leaked raw or sensitive value: $forbiddenValue",
                                    payload.contains(
                                        forbiddenValue,
                                    ),
                                )
                            }
                        }

                        assertEquals(
                            0,
                            fixture
                                .database
                                .reportingDao()
                                .countReports(),
                        )

                        assertNull(
                            fixture
                                .database
                                .reportingDao()
                                .getReport(
                                    occurrenceId,
                                ),
                        )
                    } finally {
                        alarmGateway.cancel(
                            alarmKey,
                        )
                    }
                }
        }

    @Test
    fun realAlarmManagerScheduledAlarm_firesThroughReceiverAndRecordsSafeDiagnostics() =
        runBlocking {
            assumeTrue(
                canScheduleExactAlarms(),
            )

            val application =
                targetApplication()

            val container =
                application.container

            val alarmKeyHolder =
                mutableListOf<AlarmKey>()

            try {
                container.database.clearAllTables()

                container
                    .snoozedReminderStore
                    .clear()

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
                                    SENSITIVE_RECIPIENT_NAME,
                            ),
                        )

                val recipientId =
                    when (recipientOutcome) {
                        is CreateRecipientOutcome.Created ->
                            recipientOutcome.recipientId

                        is CreateRecipientOutcome.AlreadyExists ->
                            recipientOutcome.recipientId

                        is CreateRecipientOutcome.Invalid ->
                            error(
                                "Recipient creation failed: $recipientOutcome",
                            )
                    }

                val targetDateTime =
                    ZonedDateTime
                        .now(
                            ZoneOffset.UTC,
                        )
                        .plusMinutes(
                            10,
                        )
                        .withSecond(
                            0,
                        )
                        .withNano(
                            0,
                        )

                val planOutcome =
                    container
                        .carePlanService
                        .createMedicationAndSchedule(
                            CreateMedicationScheduleCommand(
                                recipientId =
                                    recipientId,
                                medicationName =
                                    SENSITIVE_MEDICATION_NAME,
                                instruction =
                                    SENSITIVE_INSTRUCTION_TEXT,
                                weekdays =
                                    setOf(
                                        targetDateTime
                                            .dayOfWeek,
                                    ),
                                minutesOfDay =
                                    listOf(
                                        targetDateTime.hour * 60 +
                                                targetDateTime.minute,
                                    ),
                                startDate =
                                    targetDateTime
                                        .toLocalDate(),
                                endDate =
                                    targetDateTime
                                        .toLocalDate(),
                                zoneId =
                                    "UTC",
                            ),
                        )

                val createdPlan =
                    planOutcome as?
                            CreateMedicationScheduleOutcome
                            .Created
                        ?: error(
                            "Medication schedule creation failed: $planOutcome",
                        )

                val occurrenceId =
                    createdPlan
                        .occurrenceIds
                        .single()

                val alarmKey =
                    AlarmKey.forScheduleSeries(
                        scheduleSeriesId =
                            createdPlan
                                .scheduleSeriesId,
                    )

                alarmKeyHolder += alarmKey

                LogcatReminderDiagnosticSink
                    .clearDebugEvents()

                container
                    .alarmGateway
                    .schedule(
                        AlarmRequest(
                            alarmKey =
                                alarmKey,
                            occurrenceId =
                                occurrenceId,
                            triggerAt =
                                Instant
                                    .now()
                                    .plusMillis(
                                        REAL_ALARM_DELAY_MILLIS,
                                    ),
                            deliveryMode =
                                AlarmDeliveryMode.EXACT,
                        ),
                    )

                val diagnosticEvents =
                    waitForDebugDiagnosticEvents { events ->
                        events.any { event ->
                            event.contains(
                                "type=RECEIVER_FIRED",
                            )
                        } &&
                                events.any { event ->
                                    event.contains(
                                        "type=NOTIFICATION_SKIPPED",
                                    )
                                }
                    }

                assertTrue(
                    diagnosticEvents.any { event ->
                        event.contains(
                            "type=ALARM_REGISTRATION_ATTEMPTED",
                        )
                    },
                )

                assertTrue(
                    diagnosticEvents.any { event ->
                        event.contains(
                            "type=ALARM_REGISTERED",
                        )
                    },
                )

                assertTrue(
                    diagnosticEvents.any { event ->
                        event.contains(
                            "type=RECEIVER_FIRED",
                        )
                    },
                )

                assertTrue(
                    diagnosticEvents.any { event ->
                        event.contains(
                            "type=NOTIFICATION_SKIPPED",
                        )
                    },
                )

                val payload =
                    diagnosticEvents
                        .joinToString(
                            separator = "\n",
                        )

                val forbiddenValues =
                    SENSITIVE_VALUES +
                            listOf(
                                occurrenceId,
                                createdPlan.scheduleSeriesId,
                                createdPlan.medicationId,
                                createdPlan.scheduleVersionId,
                            )

                forbiddenValues.forEach { forbiddenValue ->
                    assertFalse(
                        "Diagnostic payload leaked raw or sensitive value: $forbiddenValue",
                        payload.contains(
                            forbiddenValue,
                        ),
                    )
                }

                assertEquals(
                    0,
                    container
                        .database
                        .reportingDao()
                        .countReportsForOccurrence(
                            occurrenceId,
                        ),
                )

                assertEquals(
                    0,
                    container
                        .database
                        .reportingDao()
                        .countReports(),
                )

                assertNull(
                    container
                        .database
                        .reportingDao()
                        .getReport(
                            occurrenceId,
                        ),
                )
            } finally {
                alarmKeyHolder.forEach { alarmKey ->
                    container
                        .alarmGateway
                        .cancel(
                            alarmKey =
                                alarmKey,
                        )
                }

                LogcatReminderDiagnosticSink
                    .clearDebugEvents()

                container
                    .database
                    .clearAllTables()

                container
                    .snoozedReminderStore
                    .clear()

                container
                    .reminderPreferenceStore
                    .setRemindersEnabled(
                        enabled = false,
                    )
            }
        }

    @Test
    fun delayedOccurrenceAlarmIdentity_isDistinctFromScheduleSeriesAlarm() {
        val normalKey =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "receiver-series-1",
            )

        val delayedKey =
            AlarmKey.forDelayedOccurrence(
                occurrenceId =
                    "receiver-occurrence-1",
            )

        assertNotEquals(
            normalKey.stableToken,
            delayedKey.stableToken,
        )
    }

    @Test
    fun reconciliationReceiverHandlesExpectedSystemActions() {
        val expectedActions =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            )

        expectedActions.forEach { action ->
            val intent =
                Intent(action).setPackage(
                    context.packageName,
                )

            assertTrue(
                "No receiver handles $action",
                queryReceivers(
                    intent =
                        intent,
                ).any { resolveInfo ->
                    resolveInfo
                        .activityInfo
                        .name ==
                            SystemReconciliationReceiver::class
                                .java
                                .name
                },
            )
        }
    }

    private fun targetApplication():
            CarePackApplication {
        return context.applicationContext as
                CarePackApplication
    }

    private fun canScheduleExactAlarms():
            Boolean {
        return Build.VERSION.SDK_INT <
                Build.VERSION_CODES.S ||
                checkNotNull(
                    context.getSystemService(
                        AlarmManager::class.java,
                    ),
                ).canScheduleExactAlarms()
    }

    private fun waitForDebugDiagnosticEvents(
        predicate: (List<String>) -> Boolean,
    ): List<String> {
        val deadline =
            android.os.SystemClock.elapsedRealtime() +
                    REAL_ALARM_TIMEOUT_MILLIS

        while (
            android.os.SystemClock.elapsedRealtime() <
            deadline
        ) {
            val events =
                LogcatReminderDiagnosticSink
                    .readDebugEvents()

            if (predicate(events)) {
                return events
            }

            android.os.SystemClock.sleep(
                POLL_INTERVAL_MILLIS,
            )
        }

        val events =
            LogcatReminderDiagnosticSink
                .readDebugEvents()

        error(
            "Expected real alarm diagnostic events were not recorded. Recorded events: $events",
        )
    }

    private fun findAlarmPendingIntent(
        alarmKey: AlarmKey,
    ): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            0,
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
                            alarmKey.stableToken,
                        )
                        .build()

                component =
                    ComponentName(
                        context,
                        ReminderAlarmReceiver::class.java,
                    )

                `package` =
                    context.packageName
            },
            PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun queryReceivers(
        intent: Intent,
    ) =
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            context
                .packageManager
                .queryBroadcastReceivers(
                    intent,
                    PackageManager.ResolveInfoFlags.of(
                        0L,
                    ),
                )
        } else {
            @Suppress("DEPRECATION")
            context
                .packageManager
                .queryBroadcastReceivers(
                    intent,
                    0,
                )
        }

    private fun readPackageInfo():
            PackageInfo {
        val flags =
            PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_RECEIVERS

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(
                    flags.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                flags,
            )
        }
    }

    private companion object {
        const val FUTURE_SECONDS =
            3_600L

        const val REAL_ALARM_DELAY_MILLIS =
            2_000L

        const val REAL_ALARM_TIMEOUT_MILLIS =
            25_000L

        const val POLL_INTERVAL_MILLIS =
            100L

        const val MAX_DIAGNOSTIC_TOKEN_LENGTH =
            16

        const val SENSITIVE_RECIPIENT_NAME =
            "نام محرمانه گیرنده مراقبت"

        const val SENSITIVE_MEDICATION_NAME =
            "نام محرمانه دارو"

        const val SENSITIVE_INSTRUCTION_TEXT =
            "دستور محرمانه مصرف دارو"

        const val SENSITIVE_CAREGIVER_REPORT_TEXT =
            "متن محرمانه گزارش مراقب"

        const val SENSITIVE_DOSAGE_TEXT =
            "دوز محرمانه ۱۰ میلی‌گرم"

        const val SENSITIVE_MEDICATION_TYPE =
            "نوع محرمانه دارو"

        val SENSITIVE_VALUES =
            listOf(
                SENSITIVE_RECIPIENT_NAME,
                SENSITIVE_MEDICATION_NAME,
                SENSITIVE_INSTRUCTION_TEXT,
                SENSITIVE_CAREGIVER_REPORT_TEXT,
                SENSITIVE_DOSAGE_TEXT,
                SENSITIVE_MEDICATION_TYPE,
            )
    }
}

private class RecordingReminderDiagnosticSink :
    ReminderDiagnosticSink {

    val events =
        mutableListOf<ReminderDiagnosticEvent>()

    override fun record(
        event: ReminderDiagnosticEvent,
    ) {
        events += event
    }
}
