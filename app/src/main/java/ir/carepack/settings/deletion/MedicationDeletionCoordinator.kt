package ir.carepack.settings.deletion

enum class MedicationDeletionStage {
    LOADING_PREVIEW,
    CHECKING_PENDING_OPERATION,
    VALIDATING_PREVIEW,
    SAVING_RECOVERY_MARKER,
    CANCELLING_SCHEDULE_ALARMS,
    CANCELLING_DELAYED_ALARMS,
    REMOVING_SNOOZED_REMINDERS,
    CANCELLING_NOTIFICATIONS,
    DELETING_DATABASE_GRAPH,
    MARKING_DATABASE_DELETED,
    CANCELLING_ALL_OWNED_REMINDERS,
    RECONCILING_REMAINING_REMINDERS,
    MARKING_CHANGED_PREVIEW,
    CLEARING_RECOVERY_MARKER,
}

sealed interface MedicationDeletionPreviewResult {

    data class Available(
        val preview: MedicationDeletionPreview,
    ) : MedicationDeletionPreviewResult

    data object NotFound :
        MedicationDeletionPreviewResult

    data class Failed(
        val stage: MedicationDeletionStage =
            MedicationDeletionStage
                .LOADING_PREVIEW,
    ) : MedicationDeletionPreviewResult
}

sealed interface MedicationDeletionResult {

    data class Completed(
        val counts: MedicationDeletionCounts?,
    ) : MedicationDeletionResult

    data object AlreadyDeleted :
        MedicationDeletionResult

    data class ChangedSincePreview(
        val latestPreview:
        MedicationDeletionPreview,
    ) : MedicationDeletionResult

    data class Failed(
        val stage: MedicationDeletionStage,
        val databaseDeleted: Boolean,
    ) : MedicationDeletionResult
}

sealed interface MedicationDeletionRecoveryResult {

    data object NoDeletionPending :
        MedicationDeletionRecoveryResult

    data class Completed(
        val medicationId: String,
    ) : MedicationDeletionRecoveryResult

    data class AbortedChangedPreview(
        val medicationId: String,
    ) : MedicationDeletionRecoveryResult

    data class Failed(
        val medicationId: String,
        val stage: MedicationDeletionStage,
        val databaseDeleted: Boolean,
    ) : MedicationDeletionRecoveryResult
}

interface MedicationDeletionCoordinator {

    suspend fun loadPreview(
        medicationId: String,
    ): MedicationDeletionPreviewResult

    suspend fun deleteMedication(
        expectedPreview:
        MedicationDeletionPreview,
    ): MedicationDeletionResult

    suspend fun resumeIncompleteDeletionIfNeeded():
            MedicationDeletionRecoveryResult
}
