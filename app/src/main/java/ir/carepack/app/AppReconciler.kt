package ir.carepack.app

import ir.carepack.core.concurrency.AppOperationGate
import ir.carepack.core.error.AppFailureKind
import ir.carepack.core.error.AppOperationStage
import ir.carepack.core.error.SafeAppFailure
import ir.carepack.core.error.rethrowIfCancellation
import ir.carepack.core.error.toSafeAppFailure
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.occurrence.GenerationSummary
import ir.carepack.domain.occurrence.OccurrenceGenerator
import ir.carepack.domain.reminder.ReconciliationReason
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.ReminderReconciliationResult
import ir.carepack.domain.reminder.recoverableFailureOrNull
import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.DataDeletionResult
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionRecoveryResult
import java.time.Clock

sealed interface AppReconciliationOutcome {
    data class Completed(
        val generation: GenerationSummary,
        val reminderResult: ReminderReconciliationResult,
    ) : AppReconciliationOutcome

    data class Failed(
        val stage: AppOperationStage,
        val failure: SafeAppFailure,
    ) : AppReconciliationOutcome
}

class AppReconciler(
    private val medicationDeletionCoordinator:
        MedicationDeletionCoordinator,
    private val dataDeletionCoordinator:
        DataDeletionCoordinator,
    private val occurrenceGenerator: OccurrenceGenerator,
    private val reminderCoordinator: ReminderCoordinator,
    private val reminderPreferenceStore:
        ReminderPreferenceStore,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val operationGate: AppOperationGate,
) {

    suspend fun reconcile(
        reason: ReconciliationReason,
    ): AppReconciliationOutcome =
        operationGate.withGate {
            reconcileLocked(reason)
        }

    private suspend fun reconcileLocked(
        reason: ReconciliationReason,
    ): AppReconciliationOutcome {
        val medicationRecovery =
            medicationDeletionCoordinator
                .resumeIncompleteDeletionIfNeeded()

        if (
            medicationRecovery is
                MedicationDeletionRecoveryResult.Failed
        ) {
            return AppReconciliationOutcome.Failed(
                stage =
                    AppOperationStage
                        .RECOVERING_MEDICATION_DELETION,
                failure =
                    medicationRecovery.failure
                        ?: SafeAppFailure(
                            kind = AppFailureKind.UNKNOWN,
                            stage =
                                AppOperationStage
                                    .RECOVERING_MEDICATION_DELETION,
                            retryable = true,
                        ),
            )
        }

        val dataRecovery =
            dataDeletionCoordinator
                .resumeIncompleteDeletionIfNeeded()

        if (dataRecovery is DataDeletionResult.Failed) {
            return AppReconciliationOutcome.Failed(
                stage =
                    AppOperationStage.RECOVERING_DELETE_ALL,
                failure =
                    dataRecovery.failure
                        ?: SafeAppFailure(
                            kind = AppFailureKind.UNKNOWN,
                            stage =
                                AppOperationStage
                                    .RECOVERING_DELETE_ALL,
                            retryable = true,
                        ),
            )
        }

        val now = clock.instant()
        val currentZone = zoneProvider.currentZone()

        val generation: GenerationSummary =
            try {
                occurrenceGenerator
                    .guaranteeMaintenanceWindowForAll(
                        anchorDate =
                            now.atZone(currentZone)
                                .toLocalDate(),
                        now = now,
                    )
            } catch (throwable: Throwable) {
                throwable.rethrowIfCancellation()

                return AppReconciliationOutcome.Failed(
                    stage =
                        AppOperationStage
                            .MAINTAINING_OCCURRENCES,
                    failure =
                        throwable.toSafeAppFailure(
                            AppOperationStage
                                .MAINTAINING_OCCURRENCES,
                        ),
                )
            }

        try {
            reminderPreferenceStore.observeDeviceZone(
                currentZone.id,
            )
        } catch (throwable: Throwable) {
            throwable.rethrowIfCancellation()

            return AppReconciliationOutcome.Failed(
                stage =
                    AppOperationStage
                        .RECONCILING_REMINDERS,
                failure =
                    throwable.toSafeAppFailure(
                        AppOperationStage
                            .RECONCILING_REMINDERS,
                    ),
            )
        }

        return try {
            val reminderResult =
                reminderCoordinator.reconcile(reason)
            val partialFailure =
                reminderResult.recoverableFailureOrNull()

            if (partialFailure != null) {
                reminderPreferenceStore.markFailure(
                    failure = partialFailure,
                    failedAtEpochMillis = now.toEpochMilli(),
                )

                AppReconciliationOutcome.Failed(
                    stage =
                        AppOperationStage.RECONCILING_REMINDERS,
                    failure = partialFailure,
                )
            } else {
                reminderPreferenceStore.markHealthy()

                AppReconciliationOutcome.Completed(
                    generation = generation,
                    reminderResult = reminderResult,
                )
            }
        } catch (throwable: Throwable) {
            throwable.rethrowIfCancellation()

            val failure =
                throwable.toSafeAppFailure(
                    AppOperationStage
                        .RECONCILING_REMINDERS,
                )

            reminderPreferenceStore.markFailure(
                failure = failure,
                failedAtEpochMillis = now.toEpochMilli(),
            )

            AppReconciliationOutcome.Failed(
                stage =
                    AppOperationStage
                        .RECONCILING_REMINDERS,
                failure = failure,
            )
        }
    }
}
