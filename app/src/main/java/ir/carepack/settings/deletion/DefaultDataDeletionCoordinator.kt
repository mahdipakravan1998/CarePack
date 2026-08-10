package ir.carepack.settings.deletion

import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.error.AppFailureKind
import ir.carepack.core.error.AppOperationStage
import ir.carepack.core.error.SafeAppFailure
import ir.carepack.core.error.rethrowIfCancellation
import ir.carepack.core.error.toSafeAppFailure
import ir.carepack.core.id.IdSource
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.reminder.notification.NotificationGateway
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DefaultDataDeletionCoordinator(
    private val markerStore: DataDeletionMarkerStore,
    private val reminderCoordinator: ReminderCoordinator,
    private val notificationGateway: NotificationGateway,
    private val domainDataCleaner: DomainDataCleaner,
    private val preferenceDataCleaner: PreferenceDataCleaner,
    private val temporaryDataCleaner: TemporaryDataCleaner,
    private val auxiliaryDeletionStateCleaner: AuxiliaryDeletionStateCleaner,
    private val operationGate: AppOperationGate,
    private val idSource: IdSource,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataDeletionCoordinator {

    override suspend fun deleteEverything(): DataDeletionResult = operationGate.withGate {
            withContext(ioDispatcher) {
                when (val readResult = markerStore.state.first()) {
                    is DataDeletionMarkerReadResult.Corrupted ->
                        corruptionFailure()

                    is DataDeletionMarkerReadResult.Valid ->
                        resumeMarker(readResult.marker)

                    DataDeletionMarkerReadResult.Absent -> {
                        val marker = DataDeletionMarker.create(
                                operationId = idSource.nextId(),
                                stage = DataDeletionMarkerStage
                                        .PLATFORM_CLEANUP_PENDING,
                                startedAtEpochMillis = clock.instant().toEpochMilli(),
                            )

                        try {
                            markerStore.save(marker)
                        } catch (throwable: Throwable) {
                            throwable.rethrowIfCancellation()
                            return@withContext failed(
                                stage = DataDeletionStage
                                        .MARKING_DELETION_IN_PROGRESS,
                                throwable = throwable,
                                operationStage = AppOperationStage
                                        .WRITING_OPERATION_MARKER,
                            )
                        }

                        resumeMarker(marker)
                    }
                }
            }
        }

    override suspend fun resumeIncompleteDeletionIfNeeded(): DataDeletionResult =
        operationGate.withGate {
            withContext(ioDispatcher) {
                when (val readResult = markerStore.state.first()) {
                    DataDeletionMarkerReadResult.Absent ->
                        DataDeletionResult.NoDeletionPending

                    is DataDeletionMarkerReadResult.Corrupted ->
                        corruptionFailure()

                    is DataDeletionMarkerReadResult.Valid ->
                        resumeMarker(readResult.marker)
                }
            }
        }

    private suspend fun resumeMarker(
        initialMarker: DataDeletionMarker,
    ): DataDeletionResult {
        var marker = initialMarker

        if (
            marker.stage == DataDeletionMarkerStage.PLATFORM_CLEANUP_PENDING
        ) {
            try {
                reminderCoordinator.cancelAllOwnedReminderState()
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return failed(
                    stage = DataDeletionStage.CANCELLING_REMINDERS,
                    throwable = throwable,
                    operationStage = AppOperationStage.CANCELLING_ALARMS,
                )
            }

            try {
                notificationGateway.cancelAll()
                auxiliaryDeletionStateCleaner.clearAllAuxiliaryState()
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return failed(
                    stage = DataDeletionStage
                            .CANCELLING_NOTIFICATIONS,
                    throwable = throwable,
                    operationStage = AppOperationStage
                            .CANCELLING_NOTIFICATIONS,
                )
            }

            marker = updateStage(
                    marker,
                    DataDeletionMarkerStage.DOMAIN_DATA_PENDING,
                ) ?: return markerFailure(
                    DataDeletionStage.CLEARING_DOMAIN_DATA,
                )
        }

        if (
            marker.stage == DataDeletionMarkerStage.DOMAIN_DATA_PENDING
        ) {
            try {
                domainDataCleaner.clearAllDomainData()
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return failed(
                    stage = DataDeletionStage.CLEARING_DOMAIN_DATA,
                    throwable = throwable,
                    operationStage = AppOperationStage.CLEARING_DATABASE,
                )
            }

            marker = updateStage(
                    marker,
                    DataDeletionMarkerStage.PREFERENCES_PENDING,
                ) ?: return markerFailure(
                    DataDeletionStage.CLEARING_PREFERENCES,
                )
        }

        if (
            marker.stage == DataDeletionMarkerStage.PREFERENCES_PENDING
        ) {
            try {
                preferenceDataCleaner.clearAllPreservingOperationMarkers()
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return failed(
                    stage = DataDeletionStage.CLEARING_PREFERENCES,
                    throwable = throwable,
                    operationStage = AppOperationStage.CLEARING_PREFERENCES,
                )
            }

            marker = updateStage(
                    marker,
                    DataDeletionMarkerStage.TEMPORARY_DATA_PENDING,
                ) ?: return markerFailure(
                    DataDeletionStage.CLEARING_TEMPORARY_DATA,
                )
        }

        if (
            marker.stage == DataDeletionMarkerStage.TEMPORARY_DATA_PENDING
        ) {
            try {
                temporaryDataCleaner.clearAllTemporaryData()
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return failed(
                    stage = DataDeletionStage
                            .CLEARING_TEMPORARY_DATA,
                    throwable = throwable,
                    operationStage = AppOperationStage
                            .CLEARING_TEMPORARY_DATA,
                )
            }

            marker = updateStage(
                    marker,
                    DataDeletionMarkerStage.FINAL_PLATFORM_VERIFICATION_PENDING,
                ) ?: return markerFailure(
                    DataDeletionStage.VERIFYING_PLATFORM_CLEANUP,
                )
        }

        if (
            marker.stage == DataDeletionMarkerStage
                .FINAL_PLATFORM_VERIFICATION_PENDING) {
            try {
                reminderCoordinator.cancelAllOwnedReminderState()
                notificationGateway.cancelAll()
                auxiliaryDeletionStateCleaner.clearAllAuxiliaryState()
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return failed(
                    stage = DataDeletionStage
                            .VERIFYING_PLATFORM_CLEANUP,
                    throwable = throwable,
                    operationStage = AppOperationStage.CANCELLING_ALARMS,
                )
            }

            marker = updateStage(
                    marker,
                    DataDeletionMarkerStage.COMPLETION_PENDING,
                ) ?: return markerFailure(
                    DataDeletionStage.COMPLETING_DELETION,
                )
        }

        if (
            marker.stage == DataDeletionMarkerStage.COMPLETION_PENDING
        ) {
            try {
                markerStore.clear(marker.operationId)
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return failed(
                    stage = DataDeletionStage.COMPLETING_DELETION,
                    throwable = throwable,
                    operationStage = AppOperationStage
                            .WRITING_OPERATION_MARKER,
                )
            }
        }

        return DataDeletionResult.Completed
    }

    private suspend fun updateStage(
        marker: DataDeletionMarker,
        stage: DataDeletionMarkerStage,
    ): DataDeletionMarker? = try {
            markerStore.updateStage(
                operationId = marker.operationId,
                stage = stage,
            )
            marker.withStage(stage)
        } catch (throwable: Throwable) {
            throwable.rethrowIfCancellation()
            null
        }

    private fun markerFailure(
        stage: DataDeletionStage,
    ): DataDeletionResult.Failed = DataDeletionResult.Failed(
            stage = stage,
            failure = SafeAppFailure(
                    kind = AppFailureKind.STORAGE,
                    stage = AppOperationStage
                            .WRITING_OPERATION_MARKER,
                    retryable = true,
                ),
        )

    private fun corruptionFailure(): DataDeletionResult.Failed =
        DataDeletionResult.Failed(
            stage = DataDeletionStage
                    .CHECKING_PENDING_OPERATION,
            failure = SafeAppFailure(
                    kind = AppFailureKind.CORRUPTION,
                    stage = AppOperationStage
                            .READING_OPERATION_MARKER,
                    retryable = false,
                ),
        )

    private fun failed(
        stage: DataDeletionStage,
        throwable: Throwable,
        operationStage: AppOperationStage,
    ): DataDeletionResult.Failed = DataDeletionResult.Failed(
            stage = stage,
            failure = throwable.toSafeAppFailure(operationStage),
        )
}
