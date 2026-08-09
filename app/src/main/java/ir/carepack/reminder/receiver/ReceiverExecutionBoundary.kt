package ir.carepack.reminder.receiver

import android.content.BroadcastReceiver
import ir.carepack.core.error.AppOperationStage
import ir.carepack.core.error.SafeAppFailure
import ir.carepack.core.error.toSafeAppFailure
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

fun interface ReceiverCompletion {
    fun finish()
}

class ReceiverExecutionBoundary(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val completionProvider:
        (BroadcastReceiver) -> ReceiverCompletion = { receiver ->
        val pendingResult = receiver.goAsync()
        ReceiverCompletion {
            pendingResult.finish()
        }
    },
) {
    init {
        require(timeoutMillis > 0L)
    }

    fun launch(
        receiver: BroadcastReceiver,
        operation: suspend () -> Unit,
        onFailure: suspend (SafeAppFailure) -> Unit = {},
    ) {
        val completion = completionProvider(receiver)
        val finished = AtomicBoolean(false)

        CoroutineScope(
            SupervisorJob() + dispatcher,
        ).launch {
            try {
                withTimeout(timeoutMillis) {
                    operation()
                }
            } catch (throwable: Throwable) {
                onFailure(
                    throwable.toSafeAppFailure(
                        AppOperationStage.RECEIVER_EXECUTION,
                    ),
                )
            } finally {
                if (finished.compareAndSet(false, true)) {
                    completion.finish()
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 8_000L
    }
}
