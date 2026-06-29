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
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultReminderCoordinatorTest {

    @Test
    fun disabledReminders_cancelOwnedAlarms_andScheduleNothing() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId =
                        "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            3_600L,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled =
                        false,
                    permissionGranted =
                        true,
                    exactCapabilityGranted =
                        true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(target),
                )

            val result =
                fixture.coordinator
                    .reconcile(
                        reason =
                            ReconciliationReason
                                .REMINDER_PREFERENCE_CHANGED,
                    )

            assertEquals(
                ReminderAvailability.DISABLED,
                result.status.availability,
            )

            assertTrue(
                fixture.alarmGateway
                    .scheduledRequests
                    .isEmpty(),
            )

            assertEquals(
                listOf(
                    target.alarmKey,
                ),
                fixture.alarmGateway
                    .cancelledKeys,
            )
        }

    @Test
    fun deniedNotificationPermission_schedulesNothing_andKeepsUnavailableStatus() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId =
                        "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            3_600L,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled =
                        true,
                    permissionGranted =
                        false,
                    exactCapabilityGranted =
                        true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(target),
                )

            val result =
                fixture.coordinator
                    .reconcile(
                        reason =
                            ReconciliationReason
                                .NOTIFICATION_PERMISSION_CHANGED,
                    )

            assertEquals(
                ReminderAvailability
                    .NOTIFICATION_PERMISSION_REQUIRED,
                result.status.availability,
            )

            assertFalse(
                result.status
                    .notificationPermissionGranted,
            )

            assertTrue(
                fixture.alarmGateway
                    .scheduledRequests
                    .isEmpty(),
            )
        }

    @Test
    fun exactCapability_registersExactAlarm() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId =
                        "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            3_600L,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled =
                        true,
                    permissionGranted =
                        true,
                    exactCapabilityGranted =
                        true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(target),
                )

            val result =
                fixture.coordinator
                    .reconcile(
                        reason =
                            ReconciliationReason
                                .APPLICATION_FOREGROUND,
                    )

            assertEquals(
                ReminderAvailability.EXACT,
                result.status.availability,
            )

            assertEquals(
                listOf(
                    AlarmDeliveryMode.EXACT,
                ),
                fixture.alarmGateway
                    .scheduledRequests
                    .map(
                        AlarmRequest::deliveryMode,
                    ),
            )

            assertEquals(
                target.occurrenceId,
                fixture.alarmGateway
                    .scheduledRequests
                    .single()
                    .occurrenceId,
            )
        }

    @Test
    fun unavailableExactCapability_registersApproximateAlarm() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId =
                        "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            3_600L,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled =
                        true,
                    permissionGranted =
                        true,
                    exactCapabilityGranted =
                        false,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(target),
                )

            val result =
                fixture.coordinator
                    .reconcile(
                        reason =
                            ReconciliationReason
                                .APPLICATION_FOREGROUND,
                    )

            assertEquals(
                ReminderAvailability
                    .APPROXIMATE,
                result.status.availability,
            )

            assertEquals(
                listOf(
                    AlarmDeliveryMode
                        .APPROXIMATE,
                ),
                fixture.alarmGateway
                    .scheduledRequests
                    .map(
                        AlarmRequest::deliveryMode,
                    ),
            )
        }

    @Test
    fun exactRegistrationSecurityFailure_fallsBackToApproximate() =
        runTest {
            val target =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId =
                        "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            3_600L,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled =
                        true,
                    permissionGranted =
                        true,
                    exactCapabilityGranted =
                        true,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(target),
                )

            fixture.alarmGateway
                .failExactRegistration =
                true

            val result =
                fixture.coordinator
                    .reconcile(
                        reason =
                            ReconciliationReason
                                .EXACT_ALARM_CAPABILITY_CHANGED,
                    )

            assertEquals(
                ReminderAvailability
                    .APPROXIMATE,
                result.status.availability,
            )

            assertEquals(
                listOf(
                    AlarmDeliveryMode.EXACT,
                    AlarmDeliveryMode
                        .APPROXIMATE,
                ),
                fixture.alarmGateway
                    .attemptedModes,
            )

            assertEquals(
                AlarmDeliveryMode.APPROXIMATE,
                fixture.alarmGateway
                    .scheduledRequests
                    .single()
                    .deliveryMode,
            )

            assertTrue(
                result is
                        ReminderReconciliationResult
                        .Reconciled,
            )
        }

    @Test
    fun reconciliation_replacesSameAlarmKey_withoutCreatingDuplicateOwnedIdentity() =
        runTest {
            val firstTarget =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId =
                        "occurrence-1",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            3_600L,
                        ),
                )

            val replacementTarget =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId =
                        "occurrence-2",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            7_200L,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled =
                        true,
                    permissionGranted =
                        true,
                    exactCapabilityGranted =
                        false,
                    allSeriesIds =
                        setOf(
                            "series-1",
                        ),
                    nextTargets =
                        listOf(
                            firstTarget,
                        ),
                )

            fixture.coordinator.reconcile(
                reason =
                    ReconciliationReason
                        .CARE_PLAN_CHANGED,
            )

            fixture.scheduleSource
                .nextTargets =
                listOf(
                    replacementTarget,
                )

            fixture.coordinator.reconcile(
                reason =
                    ReconciliationReason
                        .REPORT_CHANGED,
            )

            assertEquals(
                1,
                fixture.alarmGateway
                    .ownedRequests
                    .size,
            )

            assertEquals(
                replacementTarget
                    .occurrenceId,
                fixture.alarmGateway
                    .ownedRequests
                    .getValue(
                        replacementTarget
                            .alarmKey,
                    )
                    .occurrenceId,
            )
        }

    @Test
    fun alarmFire_postsPrivatePayload_andAdvancesToNextTarget() =
        runTest {
            val firedTarget =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId =
                        "occurrence-fired",
                    scheduledAt =
                        FIXED_NOW.minusSeconds(
                            1L,
                        ),
                )

            val nextTarget =
                reminderTarget(
                    seriesId = "series-1",
                    occurrenceId =
                        "occurrence-next",
                    scheduledAt =
                        FIXED_NOW.plusSeconds(
                            3_600L,
                        ),
                )

            val fixture =
                CoordinatorFixture(
                    remindersEnabled =
                        true,
                    permissionGranted =
                        true,
                    exactCapabilityGranted =
                        false,
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
                            firedTarget
                                .occurrenceId to
                                    firedTarget,
                        ),
                )

            val result =
                fixture.coordinator
                    .handleAlarmFired(
                        occurrenceId =
                            firedTarget
                                .occurrenceId,
                    )

            assertTrue(
                result is
                        AlarmFireResult
                        .NotificationPosted,
            )

            assertEquals(
                firedTarget.occurrenceId,
                fixture.notificationGateway
                    .postedNotifications
                    .single()
                    .occurrenceId,
            )

            assertEquals(
                firedTarget.medicationName,
                fixture.notificationGateway
                    .postedNotifications
                    .single()
                    .medicationName,
            )

            assertEquals(
                nextTarget.occurrenceId,
                fixture.alarmGateway
                    .ownedRequests
                    .getValue(
                        nextTarget.alarmKey,
                    )
                    .occurrenceId,
            )
        }

    @Test
    fun nonexistentAlarmOccurrence_isIgnored_withoutNotification() =
        runTest {
            val fixture =
                CoordinatorFixture(
                    remindersEnabled =
                        true,
                    permissionGranted =
                        true,
                    exactCapabilityGranted =
                        false,
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
                fixture.coordinator
                    .handleAlarmFired(
                        occurrenceId =
                            "missing-occurrence",
                    )

            assertEquals(
                AlarmFireIgnoreReason
                    .OCCURRENCE_NOT_ELIGIBLE,
                (
                        result as
                                AlarmFireResult
                                .Ignored
                        ).reason,
            )

            assertTrue(
                fixture.notificationGateway
                    .postedNotifications
                    .isEmpty(),
            )
        }

    private fun reminderTarget(
        seriesId: String,
        occurrenceId: String,
        scheduledAt: Instant,
    ): ReminderTarget {
        return ReminderTarget(
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
                "داروی نمونه",
        )
    }

    private companion object {
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
    eligibleTargets:
    Map<String, ReminderTarget> =
        emptyMap(),
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
        RecordingAlarmGateway()

    val notificationGateway =
        RecordingNotificationGateway()

    val coordinator =
        DefaultReminderCoordinator(
            scheduleSource =
                scheduleSource,
            preferenceStore =
                preferenceStore,
            notificationPermissionGateway =
                permissionGateway,
            exactAlarmCapabilityGateway =
                exactCapabilityGateway,
            alarmGateway =
                alarmGateway,
            notificationGateway =
                notificationGateway,
            clock =
                MutableReminderClock(
                    currentInstant =
                        Instant.parse(
                            "2026-06-24T08:00:00Z",
                        ),
                ),
        )
}

private class FakeReminderScheduleSource(
    private val allSeriesIds:
    Set<String>,
    var nextTargets:
    List<ReminderTarget>,
    var eligibleTargets:
    Map<String, ReminderTarget>,
) : ReminderScheduleSource {

    override suspend fun getAllScheduleSeriesIds():
            Set<String> {
        return allSeriesIds
    }

    override suspend fun hasActiveSchedule():
            Boolean {
        return allSeriesIds.isNotEmpty()
    }

    override suspend fun getNextEligibleTargets(
        now: Instant,
    ): List<ReminderTarget> {
        return nextTargets
    }

    override suspend fun getEligibleOccurrence(
        occurrenceId: String,
    ): ReminderTarget? {
        return eligibleTargets[
            occurrenceId
        ]
    }
}

private class FakeReminderPreferenceStore(
    initialState:
    ReminderPreferenceState,
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
    ): TimezoneObservation {
        val previousZone =
            mutableState
                .value
                .lastObservedZoneId

        return when {
            previousZone == null -> {
                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            lastObservedZoneId =
                                zoneId,
                        )

                TimezoneObservation.Initialized
            }

            previousZone == zoneId -> {
                TimezoneObservation.Unchanged
            }

            else -> {
                val warning =
                    TimezoneWarning(
                        previousZoneId =
                            previousZone,
                        currentZoneId =
                            zoneId,
                    )

                mutableState.value =
                    mutableState
                        .value
                        .copy(
                            lastObservedZoneId =
                                zoneId,
                            timezoneWarning =
                                warning,
                        )

                TimezoneObservation
                    .Changed(warning)
            }
        }
    }

    override suspend fun dismissTimezoneWarning() {
        mutableState.value =
            mutableState
                .value
                .copy(
                    timezoneWarning = null,
                )
    }
}

private class MutableNotificationPermissionGateway(
    var permissionGranted: Boolean,
    var runtimePermissionRequired:
    Boolean = true,
) : NotificationPermissionGateway {

    override fun isPermissionGranted():
            Boolean {
        return permissionGranted
    }

    override fun requiresRuntimePermission():
            Boolean {
        return runtimePermissionRequired
    }
}

private class MutableExactAlarmCapabilityGateway(
    var capabilityGranted: Boolean,
) : ExactAlarmCapabilityGateway {

    override fun canScheduleExactAlarms():
            Boolean {
        return capabilityGranted
    }
}

private class RecordingAlarmGateway :
    AlarmGateway {

    val attemptedModes =
        mutableListOf<AlarmDeliveryMode>()

    val scheduledRequests =
        mutableListOf<AlarmRequest>()

    val ownedRequests =
        linkedMapOf<AlarmKey, AlarmRequest>()

    val cancelledKeys =
        mutableListOf<AlarmKey>()

    var failExactRegistration:
            Boolean = false

    override fun schedule(
        request: AlarmRequest,
    ) {
        attemptedModes +=
            request.deliveryMode

        if (
            request.deliveryMode ==
            AlarmDeliveryMode.EXACT &&
            failExactRegistration
        ) {
            throw SecurityException(
                "Synthetic exact-alarm denial.",
            )
        }

        scheduledRequests +=
            request

        ownedRequests[
            request.alarmKey
        ] = request
    }

    override fun cancel(
        alarmKey: AlarmKey,
    ) {
        cancelledKeys +=
            alarmKey

        ownedRequests.remove(
            alarmKey,
        )
    }
}

private class RecordingNotificationGateway :
    NotificationGateway {

    val postedNotifications =
        mutableListOf<ReminderNotification>()

    val cancelledOccurrenceIds =
        mutableListOf<String>()

    var cancelAllCalls =
        0
        private set

    override fun post(
        notification:
        ReminderNotification,
    ) {
        postedNotifications +=
            notification
    }

    override fun cancel(
        occurrenceId: String,
    ) {
        cancelledOccurrenceIds +=
            occurrenceId
    }

    override fun cancelAll() {
        cancelAllCalls += 1
    }
}

private class MutableReminderClock(
    var currentInstant: Instant,
    private val zone:
    ZoneId = ZoneOffset.UTC,
) : Clock() {

    override fun getZone():
            ZoneId {
        return zone
    }

    override fun withZone(
        zone: ZoneId,
    ): Clock {
        return MutableReminderClock(
            currentInstant =
                currentInstant,
            zone = zone,
        )
    }

    override fun instant():
            Instant {
        return currentInstant
    }
}
