package ir.carepack.core.error

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

enum class AppFailureKind {
    STORAGE,
    PLATFORM,
    TIMEOUT,
    PERMISSION,
    CORRUPTION,
    UNKNOWN,
}

enum class AppOperationStage {
    READING_OPERATION_MARKER,
    WRITING_OPERATION_MARKER,
    RECOVERING_MEDICATION_DELETION,
    RECOVERING_DELETE_ALL,
    MAINTAINING_OCCURRENCES,
    RECONCILING_REMINDERS,
    RECEIVER_EXECUTION,
    CANCELLING_ALARMS,
    CANCELLING_NOTIFICATIONS,
    CLEARING_SNOOZES,
    DELETING_DATABASE_GRAPH,
    CLEARING_DATABASE,
    CLEARING_PREFERENCES,
    CLEARING_TEMPORARY_DATA,
    SHARING_TEXT,
    COPYING_TEXT,
    UNKNOWN,
}

data class SafeAppFailure(
    val kind: AppFailureKind,
    val stage: AppOperationStage,
    val retryable: Boolean,
)

fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) {
        throw this
    }
}

fun Throwable.toSafeAppFailure(
    stage: AppOperationStage,
): SafeAppFailure {
    if (this is TimeoutCancellationException) {
        return SafeAppFailure(
            kind = AppFailureKind.TIMEOUT,
            stage = stage,
            retryable = true,
        )
    }

    rethrowIfCancellation()

    return when (this) {
        is IOException ->
            SafeAppFailure(
                kind = AppFailureKind.STORAGE,
                stage = stage,
                retryable = true,
            )

        is SecurityException ->
            SafeAppFailure(
                kind = AppFailureKind.PERMISSION,
                stage = stage,
                retryable = true,
            )

        is IllegalArgumentException ->
            SafeAppFailure(
                kind = AppFailureKind.CORRUPTION,
                stage = stage,
                retryable = false,
            )

        is IllegalStateException ->
            SafeAppFailure(
                kind = AppFailureKind.CORRUPTION,
                stage = stage,
                retryable = false,
            )

        else ->
            SafeAppFailure(
                kind = AppFailureKind.UNKNOWN,
                stage = stage,
                retryable = true,
            )
    }
}
