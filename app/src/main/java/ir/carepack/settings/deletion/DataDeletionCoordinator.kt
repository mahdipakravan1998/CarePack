package ir.carepack.settings.deletion

import ir.carepack.core.error.SafeAppFailure

enum class DataDeletionStage {
    CHECKING_PENDING_OPERATION,
    MARKING_DELETION_IN_PROGRESS,
    CANCELLING_REMINDERS,
    CANCELLING_NOTIFICATIONS,
    CLEARING_DOMAIN_DATA,
    CLEARING_PREFERENCES,
    CLEARING_TEMPORARY_DATA,
    VERIFYING_PLATFORM_CLEANUP,
    COMPLETING_DELETION,
}

sealed interface DataDeletionResult {
    data object Completed : DataDeletionResult
    data object NoDeletionPending : DataDeletionResult

    data class Failed(
        val stage: DataDeletionStage,
        val failure: SafeAppFailure? = null,
    ) : DataDeletionResult
}

interface DataDeletionCoordinator {
    suspend fun deleteEverything(): DataDeletionResult

    suspend fun resumeIncompleteDeletionIfNeeded(): DataDeletionResult
}
