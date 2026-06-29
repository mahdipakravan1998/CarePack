package ir.carepack.settings.deletion

enum class DataDeletionStage {
    MARKING_DELETION_IN_PROGRESS,
    CANCELLING_REMINDERS,
    CANCELLING_NOTIFICATIONS,
    CLEARING_DOMAIN_DATA,
    CLEARING_PREFERENCES,
    CLEARING_TEMPORARY_DATA,
    COMPLETING_DELETION,
}

sealed interface DataDeletionResult {

    data object Completed :
        DataDeletionResult

    data object NoDeletionPending :
        DataDeletionResult

    data class Failed(
        val stage: DataDeletionStage,
    ) : DataDeletionResult
}

interface DataDeletionCoordinator {

    suspend fun deleteEverything():
            DataDeletionResult

    suspend fun resumeIncompleteDeletionIfNeeded():
            DataDeletionResult
}
