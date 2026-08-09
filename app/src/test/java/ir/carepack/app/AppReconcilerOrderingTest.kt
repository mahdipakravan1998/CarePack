package ir.carepack.app

import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.error.SafeAppFailure
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.occurrence.GenerationSummary
import ir.carepack.domain.occurrence.OccurrenceGenerator
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.RemindLaterOutcome
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderHealth
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.DataDeletionResult
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionPreview
import ir.carepack.settings.deletion.MedicationDeletionPreviewResult
import ir.carepack.settings.deletion.MedicationDeletionRecoveryResult
import ir.carepack.settings.deletion.MedicationDeletionResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReconcilerOrderingTest {

    @Test
    fun bootWithMedicationMarker_recoversBeforeGenerationAndReconciliation() =
        runTest {
            val events = mutableListOf<String>()
            val reconciler = reconciler(events)

            val outcome =
                reconciler.reconcile(
                    ReconciliationReason.BOOT_COMPLETED,
                )

            assertTrue(outcome is AppReconciliationOutcome.Completed)
            assertEquals(
                listOf(
                    "medication-recovery",
                    "delete-all-recovery",
                    "generation",
                    "zone-observation",
                    "reminder-reconciliation:BOOT_COMPLETED",
                    "health-healthy",
                ),
                events,
            )
        }

    @Test
    fun timezoneWithDeleteAllMarker_recoversBeforeGenerationAndReconciliation() =
        runTest {
            val events = mutableListOf<String>()
            val reconciler = reconciler(events)

            val outcome =
                reconciler.reconcile(
                    ReconciliationReason.TIMEZONE_CHANGED,
                )

            assertTrue(outcome is AppReconciliationOutcome.Completed)
            assertEquals("medication-recovery", events[0])
            assertEquals("delete-all-recovery", events[1])
            assertTrue(
                events.indexOf("delete-all-recovery") <
                    events.indexOf("generation"),
            )
            assertTrue(
                events.indexOf("generation") <
                    events.indexOf(
                        "reminder-reconciliation:TIMEZONE_CHANGED",
                    ),
            )
        }

    private fun reconciler(
        events: MutableList<String>,
    ): AppReconciler =
        AppReconciler(
            medicationDeletionCoordinator =
                RecordingMedicationDeletionCoordinator(events),
            dataDeletionCoordinator =
                RecordingDataDeletionCoordinator(events),
            occurrenceGenerator =
                RecordingOccurrenceGenerator(events),
            reminderCoordinator =
                RecordingReminderCoordinator(events),
            reminderPreferenceStore =
                RecordingReminderPreferenceStore(events),
            clock =
                Clock.fixed(
                    Instant.parse("2026-06-24T08:00:00Z"),
                    ZoneOffset.UTC,
                ),
            zoneProvider =
                ZoneProvider {
                    ZoneId.of("Europe/Berlin")
                },
            operationGate = AppOperationGate(),
        )
}

private class RecordingMedicationDeletionCoordinator(
    private val events: MutableList<String>,
) : MedicationDeletionCoordinator {
    override suspend fun loadPreview(
        medicationId: String,
    ): MedicationDeletionPreviewResult =
        MedicationDeletionPreviewResult.NotFound

    override suspend fun deleteMedication(
        expectedPreview: MedicationDeletionPreview,
    ): MedicationDeletionResult =
        MedicationDeletionResult.AlreadyDeleted

    override suspend fun resumeIncompleteDeletionIfNeeded():
        MedicationDeletionRecoveryResult {
        events += "medication-recovery"
        return MedicationDeletionRecoveryResult.NoDeletionPending
    }
}

private class RecordingDataDeletionCoordinator(
    private val events: MutableList<String>,
) : DataDeletionCoordinator {
    override suspend fun deleteEverything(): DataDeletionResult =
        DataDeletionResult.Completed

    override suspend fun resumeIncompleteDeletionIfNeeded():
        DataDeletionResult {
        events += "delete-all-recovery"
        return DataDeletionResult.NoDeletionPending
    }
}

private class RecordingOccurrenceGenerator(
    private val events: MutableList<String>,
) : OccurrenceGenerator {
    override suspend fun guaranteeWindowForSchedule(
        scheduleVersionId: String,
        anchorDate: java.time.LocalDate,
        now: Instant,
    ): GenerationSummary =
        GenerationSummary(emptyList(), 0)

    override suspend fun guaranteeWindowForAll(
        anchorDate: java.time.LocalDate,
        now: Instant,
    ): GenerationSummary =
        GenerationSummary(emptyList(), 0)

    override suspend fun guaranteeMaintenanceWindowForAll(
        anchorDate: java.time.LocalDate,
        now: Instant,
    ): GenerationSummary {
        events += "generation"
        return GenerationSummary(emptyList(), 0)
    }
}

private class RecordingReminderCoordinator(
    private val events: MutableList<String>,
) : ReminderCoordinator {
    override suspend fun currentStatus(): ReminderStatus = status()

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        events += "reminder-reconciliation:${reason.name}"
        return ReminderReconciliationResult.Reconciled(
            reason = reason,
            status = status(),
            scheduledCount = 0,
            cancelledCount = 0,
        )
    }

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult =
        error("Alarm handling is not part of this contract test.")

    override suspend fun remindLater(
        occurrenceId: String,
        delayMinutes: Long,
    ): RemindLaterOutcome =
        RemindLaterOutcome.SchedulingFailed

    override suspend fun cancelAllOwnedReminderState() = Unit

    private fun status(): ReminderStatus =
        ReminderStatus(
            remindersEnabled = true,
            notificationPermissionGranted = true,
            hasActiveSchedule = true,
            exactAlarmCapabilityGranted = true,
            availability = ReminderAvailability.EXACT,
        )
}

private class RecordingReminderPreferenceStore(
    private val events: MutableList<String>,
) : ReminderPreferenceStore {
    private val mutableState =
        MutableStateFlow(ReminderPreferenceState())

    override val state = mutableState

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        mutableState.update { it.copy(remindersEnabled = enabled) }
    }

    override suspend fun observeDeviceZone(
        zoneId: String,
    ): TimezoneObservation {
        events += "zone-observation"
        mutableState.update { it.copy(lastObservedZoneId = zoneId) }
        return TimezoneObservation.Initialized
    }

    override suspend fun dismissTimezoneWarning() {
        mutableState.update { it.copy(timezoneWarning = null) }
    }

    override suspend fun markHealthy() {
        events += "health-healthy"
        mutableState.update { it.copy(health = ReminderHealth.Healthy) }
    }

    override suspend fun markFailure(
        failure: SafeAppFailure,
        failedAtEpochMillis: Long,
    ) {
        events += "health-failed"
        mutableState.update {
            it.copy(
                health =
                    ReminderHealth.PendingRetry(
                        failure = failure,
                        failedAtEpochMillis = failedAtEpochMillis,
                    ),
            )
        }
    }
}
