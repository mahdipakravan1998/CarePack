package ir.carepack.settings.deletion

import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.error.AppOperationStage
import ir.carepack.core.error.SafeAppFailure
import ir.carepack.core.error.rethrowIfCancellation
import ir.carepack.core.error.toSafeAppFailure
import ir.carepack.domain.reminder.AlarmKey
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.SnoozedReminderStore
import ir.carepack.reminder.alarm.AlarmGateway
import ir.carepack.reminder.notification.NotificationGateway
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DefaultMedicationDeletionCoordinator(
    private val dataSource: MedicationDeletionDataSource,
    private val markerStore: MedicationDeletionMarkerStore,
    private val alarmGateway: AlarmGateway,
    private val notificationGateway: NotificationGateway,
    private val snoozedReminderStore: SnoozedReminderStore,
    private val reminderCoordinator: ReminderCoordinator,
    private val operationGate: AppOperationGate,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MedicationDeletionCoordinator {

    override suspend fun loadPreview(
        medicationId: String,
    ): MedicationDeletionPreviewResult = withContext(ioDispatcher) {
            val trimmedMedicationId = medicationId.trim()

            if (trimmedMedicationId.isBlank()) {
                return@withContext (
                    MedicationDeletionPreviewResult.NotFound)
            }

            try {
                val preview = dataSource.loadPreview(
                        trimmedMedicationId,
                    )

                if (preview == null) {
                    MedicationDeletionPreviewResult.NotFound
                } else {
                    MedicationDeletionPreviewResult.Available(
                        preview,
                    )
                }
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                MedicationDeletionPreviewResult.Failed(
                    failure = throwable.toSafeAppFailure(
                            AppOperationStage.DELETING_DATABASE_GRAPH,
                        ),
                )
            }
        }
    override suspend fun deleteMedication(
        expectedPreview: MedicationDeletionPreview,
    ): MedicationDeletionResult = operationGate.withGate {
            withContext(ioDispatcher) {
                deleteInsideGate(expectedPreview)
            }
        }

    override suspend fun resumeIncompleteDeletionIfNeeded(): MedicationDeletionRecoveryResult =
        operationGate.withGate {
            withContext(ioDispatcher) {
                when (val readResult = markerStore.state.first()) {
                    MedicationDeletionMarkerReadResult.Absent ->
                        MedicationDeletionRecoveryResult.NoDeletionPending

                    is MedicationDeletionMarkerReadResult.Corrupted ->
                        MedicationDeletionRecoveryResult.Failed(
                            medicationId = null,
                            stage = MedicationDeletionStage
                                    .CHECKING_PENDING_OPERATION,
                            databaseDeleted = false,
                            failure = SafeAppFailure(
                                    kind = ir.carepack.core.error
                                            .AppFailureKind.CORRUPTION,
                                    stage = AppOperationStage
                                            .READING_OPERATION_MARKER,
                                    retryable = false,
                                ),
                        )

                    is MedicationDeletionMarkerReadResult.Valid ->
                        when (
                            val result = resumeMarker(readResult.marker)
                        ) {
                            is MedicationDeletionResult.Completed,
                            MedicationDeletionResult.AlreadyDeleted,
                                -> MedicationDeletionRecoveryResult.Completed(
                                        medicationId = readResult.marker
                                                .expectedPreview.medicationId,
                                    )

                            is MedicationDeletionResult.ChangedSincePreview ->
                                MedicationDeletionRecoveryResult.Failed(
                                    medicationId = readResult.marker
                                            .expectedPreview.medicationId,
                                    stage = MedicationDeletionStage
                                            .VALIDATING_PREVIEW,
                                    databaseDeleted = false,
                                    failure = SafeAppFailure(
                                            kind = ir.carepack.core.error
                                                    .AppFailureKind.CORRUPTION,
                                            stage = AppOperationStage
                                                    .RECOVERING_MEDICATION_DELETION,
                                            retryable = false,
                                        ),
                                )

                            is MedicationDeletionResult.Failed ->
                                MedicationDeletionRecoveryResult.Failed(
                                    medicationId = readResult.marker
                                            .expectedPreview.medicationId,
                                    stage = result.stage,
                                    databaseDeleted = result.databaseDeleted,
                                    failure = result.failure,
                                )
                        }
                }
            }
        }

    private suspend fun deleteInsideGate(
        expectedPreview: MedicationDeletionPreview,
    ): MedicationDeletionResult {
        return when (val readResult = markerStore.state.first()) {
            is MedicationDeletionMarkerReadResult.Corrupted ->
                failed(
                    stage = MedicationDeletionStage
                            .CHECKING_PENDING_OPERATION,
                    databaseDeleted = false,
                    failure = SafeAppFailure(
                            kind = ir.carepack.core.error
                                    .AppFailureKind.CORRUPTION,
                            stage = AppOperationStage
                                    .READING_OPERATION_MARKER,
                            retryable = false,
                        ),
                )

            is MedicationDeletionMarkerReadResult.Valid -> {
                if (
                    readResult.marker.expectedPreview.medicationId != expectedPreview.medicationId
                ) {
                    failed(
                        stage = MedicationDeletionStage
                                .CHECKING_PENDING_OPERATION,
                        databaseDeleted = readResult.marker.stage >=
                                MedicationDeletionMarkerStage.DATABASE_DELETED,
                        failure = SafeAppFailure(
                                kind = ir.carepack.core.error
                                        .AppFailureKind.CORRUPTION,
                                stage = AppOperationStage
                                        .RECOVERING_MEDICATION_DELETION,
                                retryable = false,
                            ),
                    )
                } else {
                    resumeMarker(readResult.marker)
                }
            }

            MedicationDeletionMarkerReadResult.Absent -> {
                val graph = try {
                        dataSource.loadGraph(
                            expectedPreview.medicationId,
                        )
                    } catch (throwable: Throwable) {
                        throwable.rethrowIfCancellation()
                        return failed(
                            stage = MedicationDeletionStage
                                    .VALIDATING_PREVIEW,
                            databaseDeleted = false,
                            failure = throwable.toSafeAppFailure(
                                    AppOperationStage.DELETING_DATABASE_GRAPH,
                                ),
                        )
                    }

                if (graph == null) {
                    return MedicationDeletionResult.AlreadyDeleted
                }

                if (graph.preview != expectedPreview) {
                    return MedicationDeletionResult.ChangedSincePreview(graph.preview)
                }

                val marker = MedicationDeletionMarker.create(
                        expectedPreview = expectedPreview,
                        scheduleSeriesIds = graph.scheduleSeriesIds.toSet(),
                        occurrenceIds = graph.occurrenceIds.toSet(),
                        stage = MedicationDeletionMarkerStage
                                .PLATFORM_CLEANUP_PENDING,
                        startedAtEpochMillis = clock.instant().toEpochMilli(),
                    )

                try {
                    markerStore.save(marker)
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    return failed(
                        stage = MedicationDeletionStage
                                .SAVING_RECOVERY_MARKER,
                        databaseDeleted = false,
                        failure = throwable.toSafeAppFailure(
                                AppOperationStage.WRITING_OPERATION_MARKER,
                            ),
                    )
                }

                resumeMarker(marker)
            }
        }
    }

    private suspend fun resumeMarker(
        initialMarker: MedicationDeletionMarker,
    ): MedicationDeletionResult {
        var marker = initialMarker
        val medicationId = marker.expectedPreview.medicationId

        if (
            marker.stage == MedicationDeletionMarkerStage
                .PLATFORM_CLEANUP_PENDING) {
            val platformFailure = performTargetPlatformCleanup(marker)

            if (platformFailure != null) {
                return failed(
                    stage = platformFailure.first,
                    databaseDeleted = false,
                    failure = platformFailure.second,
                )
            }

            marker = updateStage(
                    marker,
                    MedicationDeletionMarkerStage.DATABASE_DELETE_PENDING,
                ) ?: return markerWriteFailure(
                    databaseDeleted = false,
                )
        }

        var counts: MedicationDeletionCounts? = null

        if (
            marker.stage == MedicationDeletionMarkerStage
                .DATABASE_DELETE_PENDING) {
            val deletionResult = try {
                    dataSource.deleteGraph(
                        medicationId = medicationId,
                        expectedPreview = marker.expectedPreview,
                    )
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    return failed(
                        stage = MedicationDeletionStage
                                .DELETING_DATABASE_GRAPH,
                        databaseDeleted = false,
                        failure = throwable.toSafeAppFailure(
                                AppOperationStage.DELETING_DATABASE_GRAPH,
                            ),
                    )
                }

            when (deletionResult) {
                is MedicationGraphDeletionResult.Deleted ->
                    counts = deletionResult.counts

                MedicationGraphDeletionResult.NotFound ->
                    Unit

                is MedicationGraphDeletionResult.ChangedSincePreview ->
                    return MedicationDeletionResult.ChangedSincePreview(
                            deletionResult.latestPreview,
                        )
            }

            marker = updateStage(
                    marker,
                    MedicationDeletionMarkerStage.DATABASE_DELETED,
                ) ?: return markerWriteFailure(
                    databaseDeleted = true,
                )
        }

        if (
            marker.stage == MedicationDeletionMarkerStage.DATABASE_DELETED
        ) {
            marker = updateStage(
                    marker,
                    MedicationDeletionMarkerStage.FINAL_RECONCILIATION_PENDING,
                ) ?: return markerWriteFailure(
                    databaseDeleted = true,
                )
        }

        if (
            marker.stage == MedicationDeletionMarkerStage
                .FINAL_RECONCILIATION_PENDING) {
            val platformFailure = performTargetPlatformCleanup(marker)

            if (platformFailure != null) {
                return failed(
                    stage = platformFailure.first,
                    databaseDeleted = true,
                    failure = platformFailure.second,
                )
            }

            val reconciliationResult = try {
                    reminderCoordinator.reconcile(
                        ReconciliationReason.CARE_PLAN_CHANGED,
                    )
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    return failed(
                        stage = MedicationDeletionStage
                                .RECONCILING_REMAINING_REMINDERS,
                        databaseDeleted = true,
                        failure = throwable.toSafeAppFailure(
                                AppOperationStage.RECONCILING_REMINDERS,
                            ),
                    )
                }

            if (
                reconciliationResult is ReminderReconciliationResult.PartialFailure
            ) {
                return failed(
                    stage = MedicationDeletionStage
                            .RECONCILING_REMAINING_REMINDERS,
                    databaseDeleted = true,
                    failure = SafeAppFailure(
                            kind = ir.carepack.core.error
                                    .AppFailureKind.PLATFORM,
                            stage = AppOperationStage
                                    .RECONCILING_REMINDERS,
                            retryable = true,
                        ),
                )
            }

            try {
                markerStore.clear(medicationId)
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return failed(
                    stage = MedicationDeletionStage
                            .CLEARING_RECOVERY_MARKER,
                    databaseDeleted = true,
                    failure = throwable.toSafeAppFailure(
                            AppOperationStage.WRITING_OPERATION_MARKER,
                        ),
                )
            }
        }

        return MedicationDeletionResult.Completed(counts)
    }

    private suspend fun performTargetPlatformCleanup(
        marker: MedicationDeletionMarker,
    ): Pair<MedicationDeletionStage, SafeAppFailure>? {
        marker.scheduleSeriesIds.forEach { scheduleSeriesId ->
            try {
                alarmGateway.cancel(
                    AlarmKey.forScheduleSeries(
                        scheduleSeriesId,
                    ),
                )
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return MedicationDeletionStage.CANCELLING_SCHEDULE_ALARMS to
                    throwable.toSafeAppFailure(
                        AppOperationStage.CANCELLING_ALARMS,
                    )
            }
        }

        val targetSnoozedOccurrenceIds = try {
                snoozedReminderStore.reminders
                    .first().asSequence()
                    .map { it.occurrenceId }.filter { it in marker.occurrenceIds }
                    .distinct().toSet()
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return MedicationDeletionStage.REMOVING_SNOOZED_REMINDERS to
                    throwable.toSafeAppFailure(
                        AppOperationStage.CLEARING_SNOOZES,
                    )
            }

        marker.occurrenceIds.forEach { occurrenceId ->
            try {
                alarmGateway.cancel(
                    AlarmKey.forDelayedOccurrence(
                        occurrenceId,
                    ),
                )
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return MedicationDeletionStage.CANCELLING_DELAYED_ALARMS to
                    throwable.toSafeAppFailure(
                        AppOperationStage.CANCELLING_ALARMS,
                    )
            }

            if (occurrenceId in targetSnoozedOccurrenceIds) {
                try {
                    snoozedReminderStore.delete(occurrenceId)
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    return MedicationDeletionStage.REMOVING_SNOOZED_REMINDERS to
                        throwable.toSafeAppFailure(
                            AppOperationStage.CLEARING_SNOOZES,
                        )
                }
            }

            try {
                notificationGateway.cancel(occurrenceId)
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()
                return MedicationDeletionStage.CANCELLING_NOTIFICATIONS to
                    throwable.toSafeAppFailure(
                        AppOperationStage.CANCELLING_NOTIFICATIONS,
                    )
            }
        }

        return null
    }

    private suspend fun updateStage(
        marker: MedicationDeletionMarker,
        stage: MedicationDeletionMarkerStage,
    ): MedicationDeletionMarker? = try {
            markerStore.updateStage(
                medicationId = marker.expectedPreview.medicationId,
                stage = stage,
            )
            marker.withStage(stage)
        } catch (throwable: Throwable) {
            throwable.rethrowIfCancellation()
            null
        }

    private fun markerWriteFailure(
        databaseDeleted: Boolean,
    ): MedicationDeletionResult.Failed = failed(
            stage = MedicationDeletionStage
                    .MARKING_DATABASE_DELETED,
            databaseDeleted = databaseDeleted,
            failure = SafeAppFailure(
                    kind = ir.carepack.core.error
                            .AppFailureKind.STORAGE,
                    stage = AppOperationStage
                            .WRITING_OPERATION_MARKER,
                    retryable = true,
                ),
        )

    private fun failed(
        stage: MedicationDeletionStage,
        databaseDeleted: Boolean,
        failure: SafeAppFailure,
    ): MedicationDeletionResult.Failed = MedicationDeletionResult.Failed(
            stage = stage,
            databaseDeleted = databaseDeleted,
            failure = failure,
        )
}
