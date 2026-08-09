package ir.carepack.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.app.AppReconciler
import ir.carepack.app.AppReconciliationOutcome
import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.error.SafeAppFailure
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.DefaultReminderCoordinator
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderHealth
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.SnoozedReminder
import ir.carepack.domain.reminder.SnoozedReminderStore
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.reminder.alarm.AlarmDeliveryMode
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.alarm.AlarmRequest
import ir.carepack.reminder.notification.NotificationGateway
import ir.carepack.reminder.permission.ExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.DataDeletionResult
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionPreview
import ir.carepack.settings.deletion.MedicationDeletionPreviewResult
import ir.carepack.settings.deletion.MedicationDeletionRecoveryResult
import ir.carepack.settings.deletion.MedicationDeletionResult
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderLongHorizonOrchestrationTest {

    @Test
    fun fixedSchedule_alarmMaintenanceChainContinuesPastThirtyOneDaysInExactMode() =
        runBlocking {
            runChain(
                exactCapability = true,
                interval = false,
            )
        }

    @Test
    fun intervalSchedule_alarmMaintenanceChainContinuesPastThirtyOneDaysWithApproximateFallback() =
        runBlocking {
            runChain(
                exactCapability = false,
                interval = true,
            )
        }

    private suspend fun runChain(
        exactCapability: Boolean,
        interval: Boolean,
    ) {
        val start = Instant.parse("2026-01-01T06:00:00Z")
        CarePlanRoomTestFixture.create(
            initialInstant = start,
            idPrefix = if (interval) "interval-chain" else "fixed-chain",
        ).use { fixture ->
            if (interval) {
                fixture.createPlan(
                    weekdays = DayOfWeek.entries.toSet(),
                    minutesOfDay = listOf(7 * 60, 15 * 60, 23 * 60),
                    schedulePattern =
                        IntervalSchedule(
                            intervalHours = 8,
                            anchorMinuteOfDay = 7 * 60,
                        ),
                    startDate = start.atZone(ZoneOffset.UTC).toLocalDate(),
                    endDate = null,
                    zoneId = "UTC",
                )
            } else {
                val plan =
                    fixture.createPlan(
                        weekdays = DayOfWeek.entries.toSet(),
                        minutesOfDay = listOf(8 * 60),
                        schedulePattern = FixedTimeSchedule(listOf(8 * 60)),
                        startDate = start.atZone(ZoneOffset.UTC).toLocalDate(),
                        endDate = null,
                        zoneId = "UTC",
                    )
                fixture.addSchedule(
                    medicationId = plan.medicationId,
                    weekdays = DayOfWeek.entries.toSet(),
                    minutesOfDay = listOf(20 * 60),
                    schedulePattern = FixedTimeSchedule(listOf(20 * 60)),
                    startDate = start.atZone(ZoneOffset.UTC).toLocalDate(),
                    endDate = null,
                    zoneId = "UTC",
                )
            }

            val gate = AppOperationGate()
            val preferences = EnabledPreferenceStore()
            val alarmGateway = RecordingAlarmGateway()
            val notificationGateway = RecordingNotificationGateway()
            val snoozes = InMemorySnoozeStore()
            val reminderCoordinator =
                DefaultReminderCoordinator(
                    scheduleSource = fixture.reminderScheduleSource,
                    preferenceStore = preferences,
                    snoozedReminderStore = snoozes,
                    notificationPermissionGateway =
                        NotificationPermissionGatewayStub(granted = true),
                    exactAlarmCapabilityGateway =
                        ExactAlarmCapabilityGatewayStub(exactCapability),
                    alarmGateway = alarmGateway,
                    notificationGateway = notificationGateway,
                    clock = fixture.clock,
                    operationLock = gate,
                )
            val zoneProvider = MutableZoneProvider()
            val reconciler =
                AppReconciler(
                    medicationDeletionCoordinator = NoMedicationDeletion(),
                    dataDeletionCoordinator = NoDataDeletion(),
                    occurrenceGenerator = fixture.occurrenceGenerator,
                    reminderCoordinator = reminderCoordinator,
                    reminderPreferenceStore = preferences,
                    clock = fixture.clock,
                    zoneProvider = zoneProvider,
                    operationGate = gate,
                )

            assertTrue(
                reconciler.reconcile(ReconciliationReason.APPLICATION_FOREGROUND)
                    is AppReconciliationOutcome.Completed,
            )

            val firedIds = linkedSetOf<String>()
            var injectedBoot = false
            var injectedTimezone = false
            val targetEnd = start.plus(Duration.ofDays(31))
            var safetyCounter = 0

            while (fixture.clock.instant().isBefore(targetEnd)) {
                safetyCounter += 1
                check(safetyCounter < 200) {
                    "Reminder chain did not advance within the bounded test budget."
                }

                val request =
                    checkNotNull(
                        alarmGateway.requests.values
                            .minByOrNull { it.triggerAt },
                    ) {
                        "No scheduled reminder target is available."
                    }

                fixture.moveTo(request.triggerAt.plusSeconds(1))

                if (!injectedBoot && safetyCounter >= 10) {
                    assertTrue(
                        reconciler.reconcile(ReconciliationReason.BOOT_COMPLETED)
                            is AppReconciliationOutcome.Completed,
                    )
                    injectedBoot = true
                }
                if (!injectedTimezone && safetyCounter >= 20) {
                    zoneProvider.zone = java.time.ZoneId.of("Europe/Berlin")
                    assertTrue(
                        reconciler.reconcile(ReconciliationReason.TIMEZONE_CHANGED)
                            is AppReconciliationOutcome.Completed,
                    )
                    val remainingTargets =
                        fixture.reminderScheduleSource.getNextEligibleTargets(
                            fixture.clock.instant(),
                        )
                    assertTrue(remainingTargets.all { it.zoneId == "UTC" })
                    injectedTimezone = true
                }

                assertTrue(
                    reconciler.reconcile(ReconciliationReason.ALARM_FIRED)
                        is AppReconciliationOutcome.Completed,
                )

                val fireResult =
                    reminderCoordinator.handleAlarmFired(request.occurrenceId)
                assertTrue(fireResult is AlarmFireResult.NotificationPosted)
                assertTrue(firedIds.add(request.occurrenceId))
            }

            assertTrue(injectedBoot)
            assertTrue(injectedTimezone)
            assertTrue(fixture.clock.instant() >= targetEnd)
            assertEquals(0, fixture.database.reportingDao().countReports())
            assertTrue(notificationGateway.postedOccurrenceIds.isNotEmpty())
            assertEquals(firedIds, notificationGateway.postedOccurrenceIds.toSet())
            assertTrue(fixture.database.occurrenceDao().count() < 500)

            val expectedMode =
                if (exactCapability) {
                    AlarmDeliveryMode.EXACT
                } else {
                    AlarmDeliveryMode.APPROXIMATE
                }
            assertTrue(alarmGateway.allScheduledModes.all { it == expectedMode })
        }
    }

    private class RecordingAlarmGateway : AlarmGateway {
        val requests = linkedMapOf<ir.carepack.domain.reminder.AlarmKey, AlarmRequest>()
        val allScheduledModes = mutableListOf<AlarmDeliveryMode>()

        override fun schedule(request: AlarmRequest) {
            requests[request.alarmKey] = request
            allScheduledModes += request.deliveryMode
        }

        override fun cancel(alarmKey: ir.carepack.domain.reminder.AlarmKey) {
            requests.remove(alarmKey)
        }
    }

    private class RecordingNotificationGateway : NotificationGateway {
        val postedOccurrenceIds = mutableListOf<String>()
        override fun post(notification: ir.carepack.domain.reminder.ReminderNotification) {
            postedOccurrenceIds += notification.occurrenceId
        }
        override fun cancel(occurrenceId: String) = Unit
        override fun cancelAll() = Unit
    }

    private class InMemorySnoozeStore : SnoozedReminderStore {
        private val mutable = MutableStateFlow<List<SnoozedReminder>>(emptyList())
        override val reminders: Flow<List<SnoozedReminder>> = mutable
        override suspend fun upsert(reminder: SnoozedReminder) {
            mutable.value = mutable.value.filterNot { it.occurrenceId == reminder.occurrenceId } + reminder
        }
        override suspend fun delete(occurrenceId: String) {
            mutable.value = mutable.value.filterNot { it.occurrenceId == occurrenceId }
        }
        override suspend fun clear() {
            mutable.value = emptyList()
        }
    }

    private class EnabledPreferenceStore : ReminderPreferenceStore {
        private val mutable =
            MutableStateFlow(
                ReminderPreferenceState(
                    remindersEnabled = true,
                    health = ReminderHealth.Healthy,
                ),
            )
        override val state: Flow<ReminderPreferenceState> = mutable
        override suspend fun setRemindersEnabled(enabled: Boolean) {
            mutable.value = mutable.value.copy(remindersEnabled = enabled)
        }
        override suspend fun observeDeviceZone(zoneId: String): TimezoneObservation =
            TimezoneObservation.Unchanged
        override suspend fun dismissTimezoneWarning() = Unit
        override suspend fun markHealthy() {
            mutable.value = mutable.value.copy(health = ReminderHealth.Healthy)
        }
        override suspend fun markFailure(failure: SafeAppFailure, failedAtEpochMillis: Long) {
            mutable.value =
                mutable.value.copy(
                    health = ReminderHealth.PendingRetry(failure, failedAtEpochMillis),
                )
        }
    }

    private class MutableZoneProvider : ZoneProvider {
        var zone: java.time.ZoneId = ZoneOffset.UTC
        override fun currentZone(): java.time.ZoneId = zone
    }

    private class NotificationPermissionGatewayStub(
        private val granted: Boolean,
    ) : NotificationPermissionGateway {
        override fun isPermissionGranted(): Boolean = granted
        override fun requiresRuntimePermission(): Boolean = true
    }

    private class ExactAlarmCapabilityGatewayStub(
        private val exact: Boolean,
    ) : ExactAlarmCapabilityGateway {
        override fun canScheduleExactAlarms(): Boolean = exact
    }

    private class NoMedicationDeletion : MedicationDeletionCoordinator {
        override suspend fun loadPreview(medicationId: String): MedicationDeletionPreviewResult =
            MedicationDeletionPreviewResult.NotFound
        override suspend fun deleteMedication(expectedPreview: MedicationDeletionPreview): MedicationDeletionResult =
            MedicationDeletionResult.AlreadyDeleted
        override suspend fun resumeIncompleteDeletionIfNeeded(): MedicationDeletionRecoveryResult =
            MedicationDeletionRecoveryResult.NoDeletionPending
    }

    private class NoDataDeletion : DataDeletionCoordinator {
        override suspend fun deleteEverything(): DataDeletionResult = DataDeletionResult.Completed
        override suspend fun resumeIncompleteDeletionIfNeeded(): DataDeletionResult =
            DataDeletionResult.NoDeletionPending
    }
}
