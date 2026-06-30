package ir.carepack.testing

import android.content.Intent
import ir.carepack.data.preferences.PrivacyPreferenceState
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.reminder.AlarmFireResult
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderAvailability
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderNotification
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.ReminderStatus
import ir.carepack.platform.ExternalIntentLaunchResult
import ir.carepack.platform.ExternalIntentLauncher
import ir.carepack.reminder.notification.NotificationGateway
import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.DataDeletionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class InstrumentedPrivacyPreferenceStore(
    initialState: PrivacyPreferenceState =
        PrivacyPreferenceState(),
) : PrivacyPreferenceStore {

    private val mutableState =
        MutableStateFlow(
            initialState,
        )

    override val state:
            Flow<PrivacyPreferenceState> =
        mutableState

    override suspend fun setIncludeRecipientName(
        includeRecipientName: Boolean,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    includeRecipientName =
                        includeRecipientName,
                )
    }

    override suspend fun markDeletionInProgress() {
        mutableState.value =
            mutableState
                .value
                .copy(
                    deletionInProgress = true,
                )
    }

    override suspend fun clearAllPreservingDeletionMarker() {
        mutableState.value =
            PrivacyPreferenceState(
                includeRecipientName = false,
                deletionInProgress = true,
            )
    }

    override suspend fun completeDeletion() {
        mutableState.value =
            PrivacyPreferenceState()
    }
}

internal class RecordingExternalIntentLauncher(
    var result:
    ExternalIntentLaunchResult =
        ExternalIntentLaunchResult.Launched,
) : ExternalIntentLauncher {

    val launchedIntents =
        mutableListOf<Intent>()

    override fun launch(
        intent: Intent,
    ): ExternalIntentLaunchResult {
        launchedIntents +=
            Intent(intent)

        return result
    }
}

internal class RecordingTextShareGateway(
    var shareResult:
    ShareTextResult =
        ShareTextResult.ChooserOpened,
    var copyResult:
    CopyTextResult =
        CopyTextResult.Copied,
) : TextShareGateway {

    val sharedTexts =
        mutableListOf<String>()

    val copiedTexts =
        mutableListOf<String>()

    override fun share(
        text: String,
    ): ShareTextResult {
        sharedTexts += text

        return shareResult
    }

    override fun copy(
        text: String,
    ): CopyTextResult {
        copiedTexts += text

        return copyResult
    }
}

internal class RecordingDataDeletionCoordinator(
    var deleteResult:
    DataDeletionResult =
        DataDeletionResult.Completed,
    var resumeResult:
    DataDeletionResult =
        DataDeletionResult.NoDeletionPending,
) : DataDeletionCoordinator {

    var deleteCount =
        0

    var resumeCount =
        0

    override suspend fun deleteEverything():
            DataDeletionResult {
        deleteCount += 1

        return deleteResult
    }

    override suspend fun resumeIncompleteDeletionIfNeeded():
            DataDeletionResult {
        resumeCount += 1

        return resumeResult
    }
}

internal class RecordingReminderCoordinator :
    ReminderCoordinator {

    var cancelAllCount =
        0

    val reconciliationReasons =
        mutableListOf<ReconciliationReason>()

    override suspend fun currentStatus():
            ReminderStatus =
        ReminderStatus(
            remindersEnabled = false,
            notificationPermissionGranted = true,
            hasActiveSchedule = false,
            exactAlarmCapabilityGranted = false,
            availability =
                ReminderAvailability.DISABLED,
        )

    override suspend fun reconcile(
        reason: ReconciliationReason,
    ): ReminderReconciliationResult {
        reconciliationReasons += reason

        return ReminderReconciliationResult
            .Reconciled(
                reason = reason,
                status = currentStatus(),
                scheduledCount = 0,
                cancelledCount = 0,
            )
    }

    override suspend fun handleAlarmFired(
        occurrenceId: String,
    ): AlarmFireResult {
        error(
            "Alarm firing is not used by this test double.",
        )
    }

    override suspend fun cancelAllOwnedReminderState() {
        cancelAllCount += 1
    }
}

internal class RecordingNotificationGateway :
    NotificationGateway {

    val postedNotifications =
        mutableListOf<ReminderNotification>()

    val cancelledOccurrenceIds =
        mutableListOf<String>()

    var cancelAllCount =
        0

    override fun post(
        notification: ReminderNotification,
    ) {
        postedNotifications += notification
    }

    override fun cancel(
        occurrenceId: String,
    ) {
        cancelledOccurrenceIds += occurrenceId
    }

    override fun cancelAll() {
        cancelAllCount += 1
    }
}
