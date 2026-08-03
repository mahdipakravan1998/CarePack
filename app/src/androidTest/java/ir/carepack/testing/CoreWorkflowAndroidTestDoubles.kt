package ir.carepack.testing

import ir.carepack.domain.reminder.ReminderTestCoordinator
import ir.carepack.domain.reminder.ReminderTestFireResult
import ir.carepack.domain.reminder.ReminderTestScheduleResult
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionPreview
import ir.carepack.settings.deletion.MedicationDeletionPreviewResult
import ir.carepack.settings.deletion.MedicationDeletionRecoveryResult
import ir.carepack.settings.deletion.MedicationDeletionResult
import java.time.Instant

internal class InstrumentedReminderTestCoordinator(
    var scheduleResult:
    ReminderTestScheduleResult =
        ReminderTestScheduleResult.Scheduled(
            triggerAt =
                Instant.parse(
                    "2026-06-24T08:00:30Z",
                ),
            deliveryMode =
                ir.carepack.domain.reminder
                    .ReminderDeliveryMode.EXACT,
        ),
    var fireResult:
    ReminderTestFireResult =
        ReminderTestFireResult.NotificationPosted,
) : ReminderTestCoordinator {

    val requestedDelays =
        mutableListOf<Long>()

    var fireCallCount: Int = 0

    var cancelCallCount: Int = 0

    override suspend fun scheduleTestReminder(
        delaySeconds: Long,
    ): ReminderTestScheduleResult {
        requestedDelays += delaySeconds
        return scheduleResult
    }

    override suspend fun handleTestAlarmFired():
            ReminderTestFireResult {
        fireCallCount += 1
        return fireResult
    }

    override suspend fun cancelPendingTest() {
        cancelCallCount += 1
    }
}

internal class InstrumentedMedicationDeletionCoordinator(
    var previewResult:
    MedicationDeletionPreviewResult =
        MedicationDeletionPreviewResult.NotFound,
    var deletionResult:
    MedicationDeletionResult =
        MedicationDeletionResult.AlreadyDeleted,
    var recoveryResult:
    MedicationDeletionRecoveryResult =
        MedicationDeletionRecoveryResult
            .NoDeletionPending,
) : MedicationDeletionCoordinator {

    val previewMedicationIds =
        mutableListOf<String>()

    val deletionPreviews =
        mutableListOf<MedicationDeletionPreview>()

    var recoveryCallCount: Int = 0

    override suspend fun loadPreview(
        medicationId: String,
    ): MedicationDeletionPreviewResult {
        previewMedicationIds += medicationId
        return previewResult
    }

    override suspend fun deleteMedication(
        expectedPreview:
        MedicationDeletionPreview,
    ): MedicationDeletionResult {
        deletionPreviews += expectedPreview
        return deletionResult
    }

    override suspend fun resumeIncompleteDeletionIfNeeded():
            MedicationDeletionRecoveryResult {
        recoveryCallCount += 1
        return recoveryResult
    }
}
