package ir.carepack.settings.deletion

import ir.carepack.domain.reminder.AlarmKey
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderOperationLock
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.SnoozedReminderStore
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.notification.NotificationGateway
import java.time.Clock
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultMedicationDeletionCoordinator(
    private val dataSource:
    MedicationDeletionDataSource,
    private val markerStore:
    MedicationDeletionMarkerStore,
    private val alarmGateway:
    AlarmGateway,
    private val notificationGateway:
    NotificationGateway,
    private val snoozedReminderStore:
    SnoozedReminderStore,
    private val reminderCoordinator:
    ReminderCoordinator,
    private val reminderOperationLock:
    ReminderOperationLock,
    private val clock: Clock,
    private val ioDispatcher:
    CoroutineDispatcher =
        Dispatchers.IO,
) : MedicationDeletionCoordinator {

    private val deletionMutex =
        Mutex()

    override suspend fun loadPreview(
        medicationId: String,
    ): MedicationDeletionPreviewResult =
        withContext(ioDispatcher) {
            val trimmedMedicationId =
                medicationId.trim()

            if (trimmedMedicationId.isBlank()) {
                return@withContext (
                        MedicationDeletionPreviewResult
                            .NotFound
                        )
            }

            try {
                val preview =
                    dataSource.loadPreview(
                        medicationId =
                            trimmedMedicationId,
                    )

                if (preview == null) {
                    MedicationDeletionPreviewResult
                        .NotFound
                } else {
                    MedicationDeletionPreviewResult
                        .Available(
                            preview = preview,
                        )
                }
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                MedicationDeletionPreviewResult
                    .Failed()
            }
        }

    override suspend fun deleteMedication(
        expectedPreview:
        MedicationDeletionPreview,
    ): MedicationDeletionResult =
        deletionMutex.withLock {
            withContext(ioDispatcher) {
                deleteMedicationLocked(
                    expectedPreview =
                        expectedPreview,
                )
            }
        }

    override suspend fun resumeIncompleteDeletionIfNeeded():
            MedicationDeletionRecoveryResult =
        deletionMutex.withLock {
            withContext(ioDispatcher) {
                val marker =
                    try {
                        markerStore
                            .marker
                            .first()
                    } catch (
                        cancellationException:
                        CancellationException,
                    ) {
                        throw cancellationException
                    } catch (_: Exception) {
                        return@withContext (
                                MedicationDeletionRecoveryResult
                                    .Failed(
                                        medicationId = "unknown",
                                        stage =
                                            MedicationDeletionStage
                                                .CHECKING_PENDING_OPERATION,
                                        databaseDeleted = false,
                                    )
                                )
                    }

                if (marker == null) {
                    MedicationDeletionRecoveryResult
                        .NoDeletionPending
                } else {
                    when (
                        val outcome =
                            resumeMarkedDeletion(
                                marker = marker,
                            )
                    ) {
                        is InternalDeletionOutcome.Completed ->
                            MedicationDeletionRecoveryResult
                                .Completed(
                                    medicationId =
                                        marker
                                            .expectedPreview
                                            .medicationId,
                                )

                        is InternalDeletionOutcome.AlreadyDeleted ->
                            MedicationDeletionRecoveryResult
                                .Completed(
                                    medicationId =
                                        marker
                                            .expectedPreview
                                            .medicationId,
                                )

                        is InternalDeletionOutcome.Changed ->
                            MedicationDeletionRecoveryResult
                                .AbortedChangedPreview(
                                    medicationId =
                                        marker
                                            .expectedPreview
                                            .medicationId,
                                )

                        is InternalDeletionOutcome.Failed ->
                            MedicationDeletionRecoveryResult
                                .Failed(
                                    medicationId =
                                        marker
                                            .expectedPreview
                                            .medicationId,
                                    stage =
                                        outcome.stage,
                                    databaseDeleted =
                                        outcome.databaseDeleted,
                                )
                    }
                }
            }
        }

    private suspend fun deleteMedicationLocked(
        expectedPreview:
        MedicationDeletionPreview,
    ): MedicationDeletionResult {
        val existingMarker =
            try {
                markerStore
                    .marker
                    .first()
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                return MedicationDeletionResult
                    .Failed(
                        stage =
                            MedicationDeletionStage
                                .CHECKING_PENDING_OPERATION,
                        databaseDeleted = false,
                    )
            }

        if (existingMarker != null) {
            if (
                existingMarker
                    .expectedPreview
                    .medicationId !=
                expectedPreview.medicationId
            ) {
                return MedicationDeletionResult
                    .Failed(
                        stage =
                            MedicationDeletionStage
                                .CHECKING_PENDING_OPERATION,
                        databaseDeleted =
                            existingMarker.stage ==
                                    MedicationDeletionMarkerStage
                                        .DATABASE_DELETED,
                    )
            }

            return resumeMarkedDeletion(
                marker = existingMarker,
            ).toPublicResult()
        }

        val graph =
            try {
                dataSource.loadGraph(
                    medicationId =
                        expectedPreview
                            .medicationId,
                )
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                return MedicationDeletionResult
                    .Failed(
                        stage =
                            MedicationDeletionStage
                                .VALIDATING_PREVIEW,
                        databaseDeleted = false,
                    )
            }

        if (graph == null) {
            return MedicationDeletionResult
                .AlreadyDeleted
        }

        if (
            graph.preview !=
            expectedPreview
        ) {
            return MedicationDeletionResult
                .ChangedSincePreview(
                    latestPreview =
                        graph.preview,
                )
        }

        val marker =
            MedicationDeletionMarker(
                expectedPreview =
                    expectedPreview,
                scheduleSeriesIds =
                    graph
                        .scheduleSeriesIds
                        .toSet(),
                stage =
                    MedicationDeletionMarkerStage
                        .PLATFORM_CLEANUP_PENDING,
                startedAtEpochMillis =
                    clock
                        .instant()
                        .toEpochMilli(),
            )

        try {
            markerStore.save(
                marker = marker,
            )
        } catch (
            cancellationException:
            CancellationException,
        ) {
            throw cancellationException
        } catch (_: Exception) {
            return MedicationDeletionResult
                .Failed(
                    stage =
                        MedicationDeletionStage
                            .SAVING_RECOVERY_MARKER,
                    databaseDeleted = false,
                )
        }

        return resumeMarkedDeletion(
            marker = marker,
        ).toPublicResult()
    }

    private suspend fun resumeMarkedDeletion(
        marker: MedicationDeletionMarker,
    ): InternalDeletionOutcome {
        val medicationId =
            marker
                .expectedPreview
                .medicationId

        if (
            marker.stage ==
            MedicationDeletionMarkerStage
                .ABORTED_CHANGED_PREVIEW
        ) {
            val latestPreview =
                try {
                    dataSource.loadPreview(
                        medicationId =
                            medicationId,
                    )
                } catch (
                    cancellationException:
                    CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    return InternalDeletionOutcome
                        .Failed(
                            stage =
                                MedicationDeletionStage
                                    .VALIDATING_PREVIEW,
                            databaseDeleted = false,
                        )
                }

            if (latestPreview == null) {
                return finishMissingTargetRecovery(
                    marker = marker,
                )
            }

            return finishChangedPreviewAbort(
                medicationId =
                    medicationId,
                latestPreview =
                    latestPreview,
            )
        }

        val graph =
            try {
                dataSource.loadGraph(
                    medicationId =
                        medicationId,
                )
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                return InternalDeletionOutcome
                    .Failed(
                        stage =
                            MedicationDeletionStage
                                .VALIDATING_PREVIEW,
                        databaseDeleted =
                            marker.stage ==
                                    MedicationDeletionMarkerStage
                                        .DATABASE_DELETED,
                    )
            }

        if (graph == null) {
            return finishMissingTargetRecovery(
                marker = marker,
            )
        }

        if (
            graph.preview !=
            marker.expectedPreview
        ) {
            return finishChangedPreviewAbort(
                medicationId =
                    medicationId,
                latestPreview =
                    graph.preview,
            )
        }

        var currentStage =
            MedicationDeletionStage
                .VALIDATING_PREVIEW

        val graphDeletionResult =
            try {
                reminderOperationLock.withLock {
                    val lockedGraph =
                        dataSource.loadGraph(
                            medicationId =
                                medicationId,
                        )
                            ?: return@withLock (
                                    LockedDeletionResult
                                        .TargetAlreadyMissing
                                    )

                    if (
                        lockedGraph.preview !=
                        marker.expectedPreview
                    ) {
                        return@withLock (
                                LockedDeletionResult
                                    .Changed(
                                        latestPreview =
                                            lockedGraph.preview,
                                    )
                                )
                    }

                    currentStage =
                        MedicationDeletionStage
                            .CANCELLING_SCHEDULE_ALARMS

                    alarmGateway.cancelAll(
                        alarmKeys =
                            lockedGraph
                                .scheduleSeriesIds
                                .map(
                                    AlarmKey::forScheduleSeries,
                                )
                                .toSet(),
                    )

                    currentStage =
                        MedicationDeletionStage
                            .CANCELLING_DELAYED_ALARMS

                    alarmGateway.cancelAll(
                        alarmKeys =
                            lockedGraph
                                .occurrenceIds
                                .map(
                                    AlarmKey::forDelayedOccurrence,
                                )
                                .toSet(),
                    )

                    currentStage =
                        MedicationDeletionStage
                            .REMOVING_SNOOZED_REMINDERS

                    val targetOccurrenceIds =
                        lockedGraph
                            .occurrenceIds
                            .toSet()

                    snoozedReminderStore
                        .reminders
                        .first()
                        .asSequence()
                        .map {
                            it.occurrenceId
                        }
                        .filter {
                            it in targetOccurrenceIds
                        }
                        .distinct()
                        .forEach { occurrenceId ->
                            snoozedReminderStore.delete(
                                occurrenceId =
                                    occurrenceId,
                            )
                        }

                    currentStage =
                        MedicationDeletionStage
                            .CANCELLING_NOTIFICATIONS

                    lockedGraph
                        .occurrenceIds
                        .forEach { occurrenceId ->
                            notificationGateway.cancel(
                                occurrenceId =
                                    occurrenceId,
                            )
                        }

                    currentStage =
                        MedicationDeletionStage
                            .DELETING_DATABASE_GRAPH

                    when (
                        val deletion =
                            dataSource.deleteGraph(
                                medicationId =
                                    medicationId,
                                expectedPreview =
                                    marker.expectedPreview,
                            )
                    ) {
                        is MedicationGraphDeletionResult
                        .Deleted ->
                            LockedDeletionResult
                                .Deleted(
                                    counts =
                                        deletion.counts,
                                )

                        MedicationGraphDeletionResult
                            .NotFound ->
                            LockedDeletionResult
                                .TargetAlreadyMissing

                        is MedicationGraphDeletionResult
                        .ChangedSincePreview ->
                            LockedDeletionResult
                                .Changed(
                                    latestPreview =
                                        deletion
                                            .latestPreview,
                                )
                    }
                }
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                return InternalDeletionOutcome
                    .Failed(
                        stage = currentStage,
                        databaseDeleted = false,
                    )
            }

        return when (graphDeletionResult) {
            is LockedDeletionResult.Changed ->
                finishChangedPreviewAbort(
                    medicationId =
                        medicationId,
                    latestPreview =
                        graphDeletionResult
                            .latestPreview,
                )

            LockedDeletionResult
                .TargetAlreadyMissing ->
                finishMissingTargetRecovery(
                    marker = marker,
                )

            is LockedDeletionResult.Deleted ->
                finishDeletedGraph(
                    marker = marker,
                    counts =
                        graphDeletionResult.counts,
                )
        }
    }

    private suspend fun finishDeletedGraph(
        marker: MedicationDeletionMarker,
        counts: MedicationDeletionCounts,
    ): InternalDeletionOutcome {
        val medicationId =
            marker
                .expectedPreview
                .medicationId

        try {
            markerStore.updateStage(
                medicationId =
                    medicationId,
                stage =
                    MedicationDeletionMarkerStage
                        .DATABASE_DELETED,
            )
        } catch (
            cancellationException:
            CancellationException,
        ) {
            throw cancellationException
        } catch (_: Exception) {
            return InternalDeletionOutcome
                .Failed(
                    stage =
                        MedicationDeletionStage
                            .MARKING_DATABASE_DELETED,
                    databaseDeleted = true,
                )
        }

        val reconciled =
            reconcileRemainingReminders()

        if (!reconciled) {
            return InternalDeletionOutcome
                .Failed(
                    stage =
                        MedicationDeletionStage
                            .RECONCILING_REMAINING_REMINDERS,
                    databaseDeleted = true,
                )
        }

        return try {
            markerStore.clear(
                medicationId =
                    medicationId,
            )

            InternalDeletionOutcome
                .Completed(
                    counts = counts,
                )
        } catch (
            cancellationException:
            CancellationException,
        ) {
            throw cancellationException
        } catch (_: Exception) {
            InternalDeletionOutcome
                .Failed(
                    stage =
                        MedicationDeletionStage
                            .CLEARING_RECOVERY_MARKER,
                    databaseDeleted = true,
                )
        }
    }

    private suspend fun finishMissingTargetRecovery(
        marker: MedicationDeletionMarker,
    ): InternalDeletionOutcome {
        val medicationId =
            marker
                .expectedPreview
                .medicationId

        try {
            alarmGateway.cancelAll(
                alarmKeys =
                    marker
                        .scheduleSeriesIds
                        .map(
                            AlarmKey::forScheduleSeries,
                        )
                        .toSet(),
            )

            reminderCoordinator
                .cancelAllOwnedReminderState()

            notificationGateway.cancelAll()
        } catch (
            cancellationException:
            CancellationException,
        ) {
            throw cancellationException
        } catch (_: Exception) {
            return InternalDeletionOutcome
                .Failed(
                    stage =
                        MedicationDeletionStage
                            .CANCELLING_ALL_OWNED_REMINDERS,
                    databaseDeleted = true,
                )
        }

        if (!reconcileRemainingReminders()) {
            return InternalDeletionOutcome
                .Failed(
                    stage =
                        MedicationDeletionStage
                            .RECONCILING_REMAINING_REMINDERS,
                    databaseDeleted = true,
                )
        }

        return try {
            markerStore.clear(
                medicationId =
                    medicationId,
            )

            InternalDeletionOutcome
                .AlreadyDeleted
        } catch (
            cancellationException:
            CancellationException,
        ) {
            throw cancellationException
        } catch (_: Exception) {
            InternalDeletionOutcome
                .Failed(
                    stage =
                        MedicationDeletionStage
                            .CLEARING_RECOVERY_MARKER,
                    databaseDeleted = true,
                )
        }
    }

    private suspend fun finishChangedPreviewAbort(
        medicationId: String,
        latestPreview:
        MedicationDeletionPreview?,
    ): InternalDeletionOutcome {
        try {
            markerStore.updateStage(
                medicationId =
                    medicationId,
                stage =
                    MedicationDeletionMarkerStage
                        .ABORTED_CHANGED_PREVIEW,
            )
        } catch (
            cancellationException:
            CancellationException,
        ) {
            throw cancellationException
        } catch (_: Exception) {
            Unit
        }

        if (!reconcileRemainingReminders()) {
            return InternalDeletionOutcome
                .Failed(
                    stage =
                        MedicationDeletionStage
                            .RECONCILING_REMAINING_REMINDERS,
                    databaseDeleted = false,
                )
        }

        return try {
            markerStore.clear(
                medicationId =
                    medicationId,
            )

            InternalDeletionOutcome
                .Changed(
                    latestPreview =
                        latestPreview,
                )
        } catch (
            cancellationException:
            CancellationException,
        ) {
            throw cancellationException
        } catch (_: Exception) {
            InternalDeletionOutcome
                .Failed(
                    stage =
                        MedicationDeletionStage
                            .CLEARING_RECOVERY_MARKER,
                    databaseDeleted = false,
                )
        }
    }

    private suspend fun reconcileRemainingReminders():
            Boolean {
        return try {
            when (
                reminderCoordinator.reconcile(
                    reason =
                        ReconciliationReason
                            .CARE_PLAN_CHANGED,
                )
            ) {
                is ReminderReconciliationResult
                .Reconciled -> true

                is ReminderReconciliationResult
                .PartialFailure -> false
            }
        } catch (
            cancellationException:
            CancellationException,
        ) {
            throw cancellationException
        } catch (_: Exception) {
            false
        }
    }

    private fun InternalDeletionOutcome.toPublicResult():
            MedicationDeletionResult =
        when (this) {
            is InternalDeletionOutcome.Completed ->
                MedicationDeletionResult
                    .Completed(
                        counts = counts,
                    )

            InternalDeletionOutcome
                .AlreadyDeleted ->
                MedicationDeletionResult
                    .AlreadyDeleted

            is InternalDeletionOutcome.Changed -> {
                val preview =
                    latestPreview

                if (preview == null) {
                    MedicationDeletionResult
                        .Failed(
                            stage =
                                MedicationDeletionStage
                                    .MARKING_CHANGED_PREVIEW,
                            databaseDeleted = false,
                        )
                } else {
                    MedicationDeletionResult
                        .ChangedSincePreview(
                            latestPreview =
                                preview,
                        )
                }
            }

            is InternalDeletionOutcome.Failed ->
                MedicationDeletionResult
                    .Failed(
                        stage = stage,
                        databaseDeleted =
                            databaseDeleted,
                    )
        }

    private sealed interface LockedDeletionResult {

        data class Deleted(
            val counts: MedicationDeletionCounts,
        ) : LockedDeletionResult

        data object TargetAlreadyMissing :
            LockedDeletionResult

        data class Changed(
            val latestPreview:
            MedicationDeletionPreview,
        ) : LockedDeletionResult
    }

    private sealed interface InternalDeletionOutcome {

        data class Completed(
            val counts: MedicationDeletionCounts,
        ) : InternalDeletionOutcome

        data object AlreadyDeleted :
            InternalDeletionOutcome

        data class Changed(
            val latestPreview:
            MedicationDeletionPreview?,
        ) : InternalDeletionOutcome

        data class Failed(
            val stage: MedicationDeletionStage,
            val databaseDeleted: Boolean,
        ) : InternalDeletionOutcome
    }
}
