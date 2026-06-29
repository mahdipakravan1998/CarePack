package ir.carepack.settings.deletion

import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.reminder.notification.NotificationGateway
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultDataDeletionCoordinator(
    private val privacyPreferenceStore:
    PrivacyPreferenceStore,
    private val reminderCoordinator:
    ReminderCoordinator,
    private val notificationGateway:
    NotificationGateway,
    private val domainDataCleaner:
    DomainDataCleaner,
    private val temporaryDataCleaner:
    TemporaryDataCleaner,
    private val ioDispatcher:
    CoroutineDispatcher =
        Dispatchers.IO,
) : DataDeletionCoordinator {

    private val deletionMutex =
        Mutex()

    override suspend fun deleteEverything():
            DataDeletionResult =
        deletionMutex.withLock {
            performDeletion(
                markDeletionFirst = true,
            )
        }

    override suspend fun resumeIncompleteDeletionIfNeeded():
            DataDeletionResult =
        deletionMutex.withLock {
            val deletionInProgress =
                privacyPreferenceStore
                    .state
                    .first()
                    .deletionInProgress

            if (!deletionInProgress) {
                DataDeletionResult
                    .NoDeletionPending
            } else {
                performDeletion(
                    markDeletionFirst = false,
                )
            }
        }

    private suspend fun performDeletion(
        markDeletionFirst: Boolean,
    ): DataDeletionResult =
        withContext(ioDispatcher) {
            var currentStage =
                if (markDeletionFirst) {
                    DataDeletionStage
                        .MARKING_DELETION_IN_PROGRESS
                } else {
                    DataDeletionStage
                        .CANCELLING_REMINDERS
                }

            try {
                if (markDeletionFirst) {
                    privacyPreferenceStore
                        .markDeletionInProgress()
                }

                currentStage =
                    DataDeletionStage
                        .CANCELLING_REMINDERS

                reminderCoordinator
                    .cancelAllOwnedReminderState()

                currentStage =
                    DataDeletionStage
                        .CANCELLING_NOTIFICATIONS

                notificationGateway.cancelAll()

                currentStage =
                    DataDeletionStage
                        .CLEARING_DOMAIN_DATA

                domainDataCleaner
                    .clearAllDomainData()

                currentStage =
                    DataDeletionStage
                        .CLEARING_PREFERENCES

                privacyPreferenceStore
                    .clearAllPreservingDeletionMarker()

                currentStage =
                    DataDeletionStage
                        .CLEARING_TEMPORARY_DATA

                temporaryDataCleaner
                    .clearAllTemporaryData()

                currentStage =
                    DataDeletionStage
                        .COMPLETING_DELETION

                privacyPreferenceStore
                    .completeDeletion()

                DataDeletionResult.Completed
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                DataDeletionResult.Failed(
                    stage = currentStage,
                )
            }
        }
}
