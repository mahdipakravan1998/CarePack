package ir.carepack.domain.reminder

import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.alarm.AlarmRequest
import ir.carepack.reminder.notification.NotificationGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultReminderCoordinatorTest {

    @Test
    fun disabledReminders_cancelOwnedAlarmsAndDoNotSchedule() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            600,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = false,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(
                            target,
                        ),
                )

            val result =
                fixture.coordinator.reconcile(
                    reason =
                        ReconciliationReason
                            .REMINDER_PREFERENCE_CHANGED,
                )

            assertEquals(
                ReminderAvailability.DISABLED,
                result.status.availability,
            )

            assertTrue(
                fixture
                    .alarmGateway
                    .ownedRequests
                    .isEmpty(),
            )

            assertEquals(
                listOf(
                    AlarmKey.forScheduleSeries(
                        "series-1",
                    ),
                ).map {
                    it.stableToken
                },
                fixture
                    .alarmGateway
                    .cancelledKeys
                    .map {
                        it.stableToken
                    },
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .eventTypes()
                    .contains(
                        ReminderDiagnosticEventType
                            .REMINDERS_DISABLED,
                    ),
            )
        }

    @Test
    fun missingNotificationPermission_keepsCoreUsableButCancelsAlarms() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            600,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = false,
                    exactCapabilityGranted = true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(
                            target,
                        ),
                )

            val result =
                fixture.coordinator.reconcile(
                    reason =
                        ReconciliationReason
                            .NOTIFICATION_PERMISSION_CHANGED,
                )

            assertEquals(
                ReminderAvailability
                    .NOTIFICATION_PERMISSION_REQUIRED,
                result.status.availability,
            )

            assertTrue(
                fixture
                    .alarmGateway
                    .ownedRequests
                    .isEmpty(),
            )

            assertEquals(
                1,
                fixture
                    .alarmGateway
                    .cancelledKeys
                    .size,
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .events
                    .any { event ->
                        event.type ==
                                ReminderDiagnosticEventType
                                    .NOTIFICATION_PERMISSION_CHECKED &&
                                event.outcome == "false"
                    },
            )
        }

    @Test
    fun exactCapability_schedulesExactAlarmAndEmitsRegistrationDiagnostics() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            600,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(
                            target,
                        ),
                )

            val result =
                fixture.coordinator.reconcile(
                    reason =
                        ReconciliationReason
                            .CARE_PLAN_CHANGED,
                )

            assertEquals(
                ReminderAvailability.EXACT,
                result.status.availability,
            )

            assertEquals(
                AlarmDeliveryMode.EXACT,
                fixture
                    .alarmGateway
                    .ownedRequests
                    .getValue(
                        target.alarmKey,
                    )
                    .deliveryMode,
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .events
                    .any { event ->
                        event.type ==
                                ReminderDiagnosticEventType
                                    .ALARM_REGISTRATION_ATTEMPTED &&
                                event.deliveryMode ==
                                ReminderDeliveryMode.EXACT
                    },
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .events
                    .any { event ->
                        event.type ==
                                ReminderDiagnosticEventType
                                    .ALARM_REGISTERED &&
                                event.deliveryMode ==
                                ReminderDeliveryMode.EXACT
                    },
            )
        }

    @Test
    fun exactDenied_schedulesApproximateFallbackAndEmitsCapabilityDiagnostics() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            600,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = true,
                    exactCapabilityGranted = false,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(
                            target,
                        ),
                )

            val result =
                fixture.coordinator.reconcile(
                    reason =
                        ReconciliationReason
                            .EXACT_ALARM_CAPABILITY_CHANGED,
                )

            assertEquals(
                ReminderAvailability.APPROXIMATE,
                result.status.availability,
            )

            assertEquals(
                AlarmDeliveryMode.APPROXIMATE,
                fixture
                    .alarmGateway
                    .ownedRequests
                    .getValue(
                        target.alarmKey,
                    )
                    .deliveryMode,
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .eventTypes()
                    .contains(
                        ReminderDiagnosticEventType
                            .EXACT_ALARM_UNAVAILABLE,
                    ),
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .eventTypes()
                    .contains(
                        ReminderDiagnosticEventType
                            .APPROXIMATE_FALLBACK_SELECTED,
                    ),
            )
        }

    @Test
    fun exactRegistrationFailure_fallsBackToApproximateAndEmitsFailureDiagnostic() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            600,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(
                            target,
                        ),
                    failedDeliveryModes =
                        setOf(
                            AlarmDeliveryMode.EXACT,
                        ),
                )

            val result =
                fixture.coordinator.reconcile(
                    reason =
                        ReconciliationReason
                            .CARE_PLAN_CHANGED,
                )

            assertEquals(
                ReminderAvailability.APPROXIMATE,
                result.status.availability,
            )

            assertEquals(
                AlarmDeliveryMode.APPROXIMATE,
                fixture
                    .alarmGateway
                    .ownedRequests
                    .getValue(
                        target.alarmKey,
                    )
                    .deliveryMode,
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .events
                    .any { event ->
                        event.type ==
                                ReminderDiagnosticEventType
                                    .ALARM_REGISTRATION_FAILED &&
                                event.deliveryMode ==
                                ReminderDeliveryMode.EXACT
                    },
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .events
                    .any { event ->
                        event.type ==
                                ReminderDiagnosticEventType
                                    .ALARM_REGISTERED &&
                                event.deliveryMode ==
                                ReminderDeliveryMode.APPROXIMATE
                    },
            )
        }

    @Test
    fun alarmFire_postsNotificationWithoutWritingReportAndSchedulesNextTarget() =
        runTest {
            val firedTarget =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-fired",
                    scheduledAt =
                        FIXED_NOW.minusSeconds(
                            60,
                        ),
                )

            val nextTarget =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-next",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            600,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(
                            nextTarget,
                        ),
                    eligibleTargets =
                        mapOf(
                            firedTarget.occurrenceId to
                                    firedTarget,
                        ),
                )

            val result =
                fixture.coordinator
                    .handleAlarmFired(
                        occurrenceId =
                            firedTarget.occurrenceId,
                    )

            assertTrue(
                result is
                        AlarmFireResult
                        .NotificationPosted,
            )

            assertEquals(
                firedTarget.occurrenceId,
                fixture
                    .notificationGateway
                    .postedNotifications
                    .single()
                    .occurrenceId,
            )

            assertEquals(
                nextTarget.occurrenceId,
                fixture
                    .alarmGateway
                    .ownedRequests
                    .getValue(
                        nextTarget.alarmKey,
                    )
                    .occurrenceId,
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .eventTypes()
                    .containsAll(
                        listOf(
                            ReminderDiagnosticEventType
                                .RECEIVER_FIRED,
                            ReminderDiagnosticEventType
                                .NOTIFICATION_POST_ATTEMPTED,
                            ReminderDiagnosticEventType
                                .NOTIFICATION_POSTED,
                        ),
                    ),
            )
        }

    @Test
    fun notificationFailure_emitsFailureDiagnosticWithoutWritingReport() =
        runTest {
            val firedTarget =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-fired",
                    scheduledAt =
                        FIXED_NOW.minusSeconds(
                            60,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        emptyList(),
                    eligibleTargets =
                        mapOf(
                            firedTarget.occurrenceId to
                                    firedTarget,
                        ),
                    notificationPostFails =
                        true,
                )

            val result =
                fixture.coordinator
                    .handleAlarmFired(
                        occurrenceId =
                            firedTarget.occurrenceId,
                    )

            assertTrue(
                result is
                        AlarmFireResult
                        .NotificationFailure,
            )

            assertTrue(
                fixture
                    .notificationGateway
                    .postedNotifications
                    .isEmpty(),
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .eventTypes()
                    .contains(
                        ReminderDiagnosticEventType
                            .NOTIFICATION_FAILED,
                    ),
            )
        }

    @Test
    fun remindLater_schedulesDelayedReminderAndDoesNotWriteReport() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.minusSeconds(
                            60,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        emptyList(),
                    eligibleTargets =
                        mapOf(
                            target.occurrenceId to
                                    target,
                        ),
                )

            val result =
                fixture.coordinator.remindLater(
                    occurrenceId =
                        target.occurrenceId,
                    delayMinutes = 10,
                )

            assertTrue(
                result is
                        RemindLaterOutcome
                        .Scheduled,
            )

            assertEquals(
                target.occurrenceId,
                fixture
                    .snoozedReminderStore
                    .reminders
                    .first()
                    .single()
                    .occurrenceId,
            )

            val delayedAlarmKey =
                AlarmKey.forDelayedOccurrence(
                    target.occurrenceId,
                )

            assertEquals(
                target.occurrenceId,
                fixture
                    .alarmGateway
                    .ownedRequests
                    .getValue(
                        delayedAlarmKey,
                    )
                    .occurrenceId,
            )

            assertTrue(
                fixture
                    .notificationGateway
                    .postedNotifications
                    .isEmpty(),
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .eventTypes()
                    .contains(
                        ReminderDiagnosticEventType
                            .USER_ACTION_HANDLED,
                    ),
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .eventTypes()
                    .contains(
                        ReminderDiagnosticEventType
                            .SNOOZE_SCHEDULED,
                    ),
            )
        }

    @Test
    fun cancelReminderDelay_removesStoredDelayAndCancelsDelayedAlarm() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId = "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.minusSeconds(
                            60,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        emptyList(),
                    eligibleTargets =
                        mapOf(
                            target.occurrenceId to
                                    target,
                        ),
                )

            fixture.coordinator.remindLater(
                occurrenceId =
                    target.occurrenceId,
                delayMinutes = 10,
            )

            fixture.coordinator.cancelReminderDelay(
                occurrenceId =
                    target.occurrenceId,
            )

            assertTrue(
                fixture
                    .snoozedReminderStore
                    .reminders
                    .first()
                    .isEmpty(),
            )

            assertTrue(
                fixture
                    .alarmGateway
                    .cancelledKeys
                    .any {
                        it.stableToken ==
                                AlarmKey
                                    .forDelayedOccurrence(
                                        target
                                            .occurrenceId,
                                    )
                                    .stableToken
                    },
            )
        }

    @Test
    fun nonexistentAlarmOccurrence_isIgnoredWithoutNotification() =
        runTest {
            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = true,
                    exactCapabilityGranted = false,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        emptyList(),
                    eligibleTargets =
                        emptyMap(),
                )

            val result =
                fixture.coordinator.handleAlarmFired(
                    occurrenceId =
                        "missing-occurrence",
                )

            assertEquals(
                AlarmFireIgnoreReason
                    .OCCURRENCE_NOT_ELIGIBLE,
                (
                        result as
                                AlarmFireResult.Ignored
                        ).reason,
            )

            assertTrue(
                fixture
                    .notificationGateway
                    .postedNotifications
                    .isEmpty(),
            )

            assertTrue(
                fixture
                    .diagnosticSink
                    .eventTypes()
                    .contains(
                        ReminderDiagnosticEventType
                            .NOTIFICATION_SKIPPED,
                    ),
            )
        }

    @Test
    fun diagnosticPayloadDoesNotExposeMedicationNameInstructionRecipientReportOrRawIds() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-sensitive-raw",
                    occurrenceId = "occurrence-sensitive-raw",
                    scheduledAt =
                        FIXED_NOW.minusSeconds(
                            60,
                        ),
                    medicationName =
                        "داروی محرمانه",
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled = true,
                    permissionGranted = true,
                    exactCapabilityGranted = true,
                    allSeriesIds =
                        setOf(
                            "series-sensitive-raw",
                        ),
                    nextTargets =
                        emptyList(),
                    eligibleTargets =
                        mapOf(
                            target.occurrenceId to
                                    target,
                        ),
                )

            fixture.coordinator.handleAlarmFired(
                occurrenceId =
                    target.occurrenceId,
            )

            val diagnosticText =
                fixture
                    .diagnosticSink
                    .events
                    .joinToString(
                        separator = "\n",
                    )

            assertFalse(
                diagnosticText.contains(
                    "داروی محرمانه",
                ),
            )

            assertFalse(
                diagnosticText.contains(
                    "دستور محرمانه",
                ),
            )

            assertFalse(
                diagnosticText.contains(
                    "نام فرد محرمانه",
                ),
            )

            assertFalse(
                diagnosticText.contains(
                    "گزارش محرمانه",
                ),
            )

            assertFalse(
                diagnosticText.contains(
                    "occurrence-sensitive-raw",
                ),
            )

            assertFalse(
                diagnosticText.contains(
                    "series-sensitive-raw",
                ),
            )
        }

    private fun reminderTarget(
        seriesId: String,
        occurrenceId: String,
        scheduledAt: Instant,
        medicationName: String = "داروی نمونه",
    ): ReminderTarget =
        ReminderTarget(
            alarmKey =
                AlarmKey.forScheduleSeries(
                    scheduleSeriesId =
                        seriesId,
                ),
            occurrenceId =
                occurrenceId,
            scheduledAt =
                scheduledAt,
            localDate =
                LocalDate.of(
                    2026,
                    6,
                    24,
                ),
            localTime =
                LocalTime.of(
                    12,
                    0,
                ),
            zoneId =
                "Asia/Tehran",
            medicationName =
                medicationName,
        )

    companion object {
        val FIXED_NOW: Instant =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )
    }
}

private class CoordinatorFixture(
    remindersEnabled: Boolean,
    permissionGranted: Boolean,
    exactCapabilityGranted: Boolean,
    allSeriesIds: Set<String>,
    nextTargets: List<ReminderTarget>,
    eligibleTargets: Map<String, ReminderTarget> =
        emptyMap(),
    failedDeliveryModes: Set<AlarmDeliveryMode> =
        emptySet(),
    notificationPostFails: Boolean =
        false,
) {
    val scheduleSource =
        FakeReminderScheduleSource(
            allSeriesIds =
                allSeriesIds,
            nextTargets =
                nextTargets,
            eligibleTargets =
                eligibleTargets,
        )

    val preferenceStore =
        FakeReminderPreferenceStore(
            initialState =
                ReminderPreferenceState(
                    remindersEnabled =
                        remindersEnabled,
                ),
        )

    val snoozedReminderStore =
        FakeSnoozedReminderStore()

    val permissionGateway =
        MutableNotificationPermissionGateway(
            permissionGranted =
                permissionGranted,
        )

    val exactCapabilityGateway =
        MutableExactAlarmCapabilityGateway(
            capabilityGranted =
                exactCapabilityGranted,
        )

    val alarmGateway =
        RecordingAlarmGateway(
            failedDeliveryModes =
                failedDeliveryModes,
        )

    val notificationGateway =
        RecordingNotificationGateway(
            postFails =
                notificationPostFails,
        )

    val diagnosticSink =
        RecordingReminderDiagnosticSink()

    val coordinator =
        DefaultReminderCoordinator(
            scheduleSource =
                scheduleSource,
            preferenceStore =
                preferenceStore,
            snoozedReminderStore =
                snoozedReminderStore,
            notificationPermissionGateway =
                permissionGateway,
            exactAlarmCapabilityGateway =
                exactCapabilityGateway,
            alarmGateway =
                alarmGateway,
            notificationGateway =
                notificationGateway,
            clock =
                Clock.fixed(
                    DefaultReminderCoordinatorTest
                        .FIXED_NOW,
                    ZoneOffset.UTC,
                ),
            diagnosticSink =
                diagnosticSink,
        )
}

private class FakeReminderScheduleSource(
    private val allSeriesIds: Set<String>,
    private val nextTargets: List<ReminderTarget>,
    private val eligibleTargets: Map<String, ReminderTarget>,
) : ReminderScheduleSource {

    override suspend fun getAllScheduleSeriesIds():
            Set<String> =
        allSeriesIds

    override suspend fun hasActiveSchedule():
            Boolean =
        allSeriesIds.isNotEmpty()

    override suspend fun getNextEligibleTargets(
        now: Instant,
    ): List<ReminderTarget> =
        nextTargets

    override suspend fun getEligibleOccurrence(
        occurrenceId: String,
    ): ReminderTarget? =
        eligibleTargets[occurrenceId]
}

private class FakeReminderPreferenceStore(
    initialState: ReminderPreferenceState,
) : ReminderPreferenceStore {

    private val mutableState =
        MutableStateFlow(
            initialState,
        )

    override val state:
            Flow<ReminderPreferenceState> =
        mutableState

    override suspend fun setRemindersEnabled(
        enabled: Boolean,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    remindersEnabled =
                        enabled,
                )
    }

    override suspend fun observeDeviceZone(
        zoneId: String,
    ): TimezoneObservation =
        TimezoneObservation.Unchanged

    override suspend fun dismissTimezoneWarning() {
        mutableState.value =
            mutableState
                .value
                .copy(
                    timezoneWarning =
                        null,
                )
    }
}

private class FakeSnoozedReminderStore :
    SnoozedReminderStore {

    private val mutableReminders =
        MutableStateFlow<List<SnoozedReminder>>(
            emptyList(),
        )

    override val reminders:
            Flow<List<SnoozedReminder>> =
        mutableReminders

    override suspend fun upsert(
        reminder: SnoozedReminder,
    ) {
        mutableReminders.value =
            mutableReminders
                .value
                .filterNot {
                    it.occurrenceId ==
                            reminder.occurrenceId
                } + reminder
    }

    override suspend fun delete(
        occurrenceId: String,
    ) {
        mutableReminders.value =
            mutableReminders
                .value
                .filterNot {
                    it.occurrenceId ==
                            occurrenceId
                }
    }

    override suspend fun clear() {
        mutableReminders.value =
            emptyList()
    }
}

private class MutableNotificationPermissionGateway(
    private val permissionGranted: Boolean,
) : NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean =
        permissionGranted

    override fun requiresRuntimePermission():
            Boolean =
        true
}

private class MutableExactAlarmCapabilityGateway(
    private val capabilityGranted: Boolean,
) : ExactAlarmCapabilityGateway {

    override fun canScheduleExactAlarms():
            Boolean =
        capabilityGranted
}

private class RecordingAlarmGateway(
    private val failedDeliveryModes: Set<AlarmDeliveryMode>,
) : AlarmGateway {

    val ownedRequests =
        linkedMapOf<AlarmKey, AlarmRequest>()

    val cancelledKeys =
        mutableListOf<AlarmKey>()

    override fun schedule(
        request: AlarmRequest,
    ) {
        if (
            request.deliveryMode in
            failedDeliveryModes
        ) {
            throw IllegalStateException(
                "Forced alarm scheduling failure.",
            )
        }

        ownedRequests[request.alarmKey] =
            request
    }

    override fun cancel(
        alarmKey: AlarmKey,
    ) {
        cancelledKeys += alarmKey
        ownedRequests.remove(alarmKey)
    }
}

private class RecordingNotificationGateway(
    private val postFails: Boolean,
) : NotificationGateway {

    val postedNotifications =
        mutableListOf<ReminderNotification>()

    var cancelAllCount =
        0

    override fun post(
        notification: ReminderNotification,
    ) {
        if (postFails) {
            throw IllegalStateException(
                "Forced notification posting failure.",
            )
        }

        postedNotifications += notification
    }

    override fun cancel(
        occurrenceId: String,
    ) {
        Unit
    }

    override fun cancelAll() {
        cancelAllCount += 1
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

    fun eventTypes(): List<ReminderDiagnosticEventType> =
        events.map {
            it.type
        }
}
