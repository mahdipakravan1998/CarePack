package ir.carepack.app

import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.error.SafeAppFailure
import ir.carepack.domain.model.CaregiverReportState
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
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderAwareHealthRecoveryTest {

    @Test
    fun databaseCommitSurvivesReminderFailure_andLaterRetryRestoresHealthyState() =
        runTest {
            val delegate = RecordingReportService()
            val coordinator = FailingThenHealthyReminderCoordinator()
            val preferenceStore = RecordingHealthPreferenceStore()
            val service =
                ReminderAwareCaregiverReportService(
                    delegate = delegate,
                    reminderCoordinator = coordinator,
                    reminderPreferenceStore = preferenceStore,
                    operationGate = AppOperationGate(),
                    clock =
                        Clock.fixed(
                            Instant.parse("2026-06-24T08:00:00Z"),
                            ZoneOffset.UTC,
                        ),
                )

            val first =
                service.setReport(
                    occurrenceId = "occurrence-1",
                    newState = CaregiverReportState.GIVEN,
                )

            assertTrue(first is SetReportOutcome.Changed)
            assertEquals(1, delegate.commitCount)
            assertTrue(
                preferenceStore.state.value.health is
                    ReminderHealth.PendingRetry,
            )

            coordinator.fail = false

            val second =
                service.setReport(
                    occurrenceId = "occurrence-1",
                    newState = CaregiverReportState.UNKNOWN,
                )

            assertTrue(second is SetReportOutcome.Changed)
            assertEquals(2, delegate.commitCount)
            assertEquals(
                ReminderHealth.Healthy,
                preferenceStore.state.value.health,
            )
        }
}

private class RecordingReportService : CaregiverReportService {
    var commitCount = 0

    override suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome {
        commitCount += 1
        return SetReportOutcome.Changed(
            ReportChange(
                occurrenceId = occurrenceId,
                previousState = null,
                newState = newState,
                changedAtEpochMillis = 1_750_752_000_000L,
            ),
        )
    }

    override suspend fun restorePrevious(
        change: ReportChange,
    ): UndoReportOutcome =
        UndoReportOutcome.Restored(
            occurrenceId = change.occurrenceId,
            restoredState = change.previousState,
        )
}

private class FailingThenHealthyReminderCoordinator : ReminderCoordinator {
    var fail = true

    override suspend fun currentStatus(): ReminderStatus = status()

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        if (fail) {
            throw IOException("raw platform failure")
        }
        return ReminderReconciliationResult.Reconciled(
            reason = reason,
            status = status(),
            scheduledCount = 1,
            cancelledCount = 0,
        )
    }

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult = error("Not used")

    override suspend fun remindLater(
        occurrenceId: String,
        delayMinutes: Long,
    ): RemindLaterOutcome = RemindLaterOutcome.SchedulingFailed

    override suspend fun cancelReminderDelay(
        occurrenceId: String,
    ) = Unit

    override suspend fun cancelAllOwnedReminderState() = Unit

    private fun status() =
        ReminderStatus(
            remindersEnabled = true,
            notificationPermissionGranted = true,
            hasActiveSchedule = true,
            exactAlarmCapabilityGranted = true,
            availability = ReminderAvailability.EXACT,
        )
}

private class RecordingHealthPreferenceStore : ReminderPreferenceStore {
    private val mutableState =
        MutableStateFlow(ReminderPreferenceState())
    override val state = mutableState

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        mutableState.update { it.copy(remindersEnabled = enabled) }
    }

    override suspend fun observeDeviceZone(
        zoneId: String,
    ): TimezoneObservation = TimezoneObservation.Initialized

    override suspend fun dismissTimezoneWarning() = Unit

    override suspend fun markHealthy() {
        mutableState.update { it.copy(health = ReminderHealth.Healthy) }
    }

    override suspend fun markFailure(
        failure: SafeAppFailure,
        failedAtEpochMillis: Long,
    ) {
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
