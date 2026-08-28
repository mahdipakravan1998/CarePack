package ir.carepack.feature.occurrence

import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.reminder.RemindLaterOutcome
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.ReportChange
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class OccurrenceActionUiState(
    val snackbarMessage: String? = null,
    val undoChange: ReportChange? = null,
)

internal class OccurrenceActionController(
    private val caregiverReportService: CaregiverReportService,
    private val reminderCoordinator: ReminderCoordinator,
    private val scope: CoroutineScope,
    private val onReminderAttemptCompleted: () -> Unit = {},
) {
    private val mutableState = MutableStateFlow(OccurrenceActionUiState())
    val state = mutableState.asStateFlow()

    private var undoJob: Job? = null

    fun setReport(
        occurrenceId: String,
        state: CaregiverReportState,
    ) {
        require(occurrenceId.isNotBlank())

        scope.launch {
            try {
                when (
                    val outcome = caregiverReportService.setReport(
                        occurrenceId = occurrenceId,
                        newState = state,
                    )) {
                    is SetReportOutcome.Changed -> showReportChanged(outcome.change)
                    is SetReportOutcome.Unchanged ->
                        showSnackbar("این وضعیت قبلاً ثبت شده است.")
                    SetReportOutcome.CancelledOccurrenceRejected ->
                        showSnackbar("برای نوبت لغوشده نمی‌توان گزارش ثبت کرد.")
                    SetReportOutcome.BeforeScheduledTimeRejected ->
                        showSnackbar("ثبت گزارش از زمان برنامه‌ریزی‌شده نوبت در دسترس است.")
                    SetReportOutcome.OccurrenceNotFound ->
                        showSnackbar("نوبت پیدا نشد.")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                showSnackbar("ثبت گزارش انجام نشد.")
            }
        }
    }

    fun remindLater(occurrenceId: String) {
        require(occurrenceId.isNotBlank())

        scope.launch {
            try {
                when (reminderCoordinator.remindLater(occurrenceId)) {
                    is RemindLaterOutcome.Scheduled ->
                        showSnackbar("یادآوری دوباره ثبت شد.")
                    is RemindLaterOutcome.Ignored ->
                        showSnackbar("برای این نوبت امکان یادآوری دوباره وجود ندارد.")
                    RemindLaterOutcome.SchedulingFailed ->
                        showSnackbar("ثبت یادآوری دوباره انجام نشد.")
                }

                onReminderAttemptCompleted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                showSnackbar("ثبت یادآوری دوباره انجام نشد.")
            }
        }
    }

    fun undoReportChange() {
        val change = mutableState.value.undoChange ?: return

        scope.launch {
            try {
                when (caregiverReportService.restorePrevious(change)) {
                    is UndoReportOutcome.Restored -> {
                        undoJob?.cancel()
                        mutableState.value = OccurrenceActionUiState(
                            snackbarMessage = "تغییر گزارش برگردانده شد.",
                        )
                    }

                    UndoReportOutcome.NoLongerCurrent,
                    UndoReportOutcome.OccurrenceNotFound,
                    -> {
                        undoJob?.cancel()
                        mutableState.value = OccurrenceActionUiState(
                            snackbarMessage = "واگرد دیگر در دسترس نیست.",
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                showSnackbar("واگرد انجام نشد.")
            }
        }
    }

    fun consumeSnackbar() {
        mutableState.update { it.copy(snackbarMessage = null) }
    }

    fun close() {
        undoJob?.cancel()
    }

    private fun showReportChanged(change: ReportChange) {
        undoJob?.cancel()
        mutableState.value = OccurrenceActionUiState(
            snackbarMessage = "گزارش ثبت شد.",
            undoChange = change,
        )
        undoJob = scope.launch {
            delay(UNDO_WINDOW_MILLIS)
            mutableState.update { it.copy(undoChange = null) }
        }
    }

    private fun showSnackbar(message: String) {
        mutableState.update { it.copy(snackbarMessage = message) }
    }

    private companion object {
        const val UNDO_WINDOW_MILLIS = 8_000L
    }
}
