package ir.carepack.feature.occurrence

import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.RemindLaterOutcome
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OccurrenceActionControllerTest {

    @Test
    fun changedReport_exposesUndoForExactlyEightSeconds() = runTest {
        val change = ReportChange(
            occurrenceId = "occurrence-1",
            previousState = null,
            newState = CaregiverReportState.GIVEN,
            changedAtEpochMillis = 10L,
        )
        val controller = OccurrenceActionController(
            caregiverReportService = FakeReportService(change),
            reminderCoordinator = FakeReminderCoordinator(),
            scope = this,
        )

        controller.setReport("occurrence-1", CaregiverReportState.GIVEN)
        runCurrent()

        assertEquals("گزارش ثبت شد.", controller.state.value.snackbarMessage)
        assertEquals(change, controller.state.value.undoChange)

        advanceTimeBy(8_000L)
        runCurrent()
        assertNull(controller.state.value.undoChange)
    }

    @Test
    fun remindLater_preservesSuccessMessageAndCompletionCallback() = runTest {
        var callbackCount = 0
        val controller = OccurrenceActionController(
            caregiverReportService = FakeReportService(),
            reminderCoordinator = FakeReminderCoordinator(),
            scope = this,
            onReminderAttemptCompleted = { callbackCount += 1 },
        )

        controller.remindLater("occurrence-1")
        runCurrent()

        assertEquals("یادآوری دوباره ثبت شد.", controller.state.value.snackbarMessage)
        assertEquals(1, callbackCount)
    }
}

private class FakeReportService(
    private val change: ReportChange? = null,
) : CaregiverReportService {
    override suspend fun setReport(
        occurrenceId: String,
        newState: CaregiverReportState,
    ): SetReportOutcome =
        change?.let(SetReportOutcome::Changed)
            ?: SetReportOutcome.Unchanged(occurrenceId, newState)

    override suspend fun restorePrevious(change: ReportChange): UndoReportOutcome =
        UndoReportOutcome.Restored(change.occurrenceId, change.previousState)
}

private class FakeReminderCoordinator : ReminderCoordinator {
    override suspend fun remindLater(
        occurrenceId: String,
        delayMinutes: Long,
    ): RemindLaterOutcome =
        RemindLaterOutcome.Scheduled(
            ir.carepack.domain.reminder.SnoozedReminder(
                occurrenceId = occurrenceId,
                remindAt = Instant.parse("2026-08-10T00:10:00Z"),
                createdAt = Instant.parse("2026-08-10T00:00:00Z"),
            ),
        )

    override suspend fun currentStatus(): ReminderStatus = error("Not used")

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult = error("Not used")

    override suspend fun handleAlarmFired(occurrenceId: String): AlarmFireResult =
        error("Not used")

    override suspend fun cancelAllOwnedReminderState() = Unit
}
