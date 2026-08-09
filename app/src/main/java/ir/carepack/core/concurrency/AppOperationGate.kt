package ir.carepack.core.concurrency

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppOperationGate {
    private val mutex = Mutex()

    suspend fun <T> withGate(
        operation: suspend () -> T,
    ): T {
        val currentOwner =
            currentCoroutineContext()[GateOwner]

        if (currentOwner?.gate === this) {
            return operation()
        }

        return mutex.withLock {
            withContext(
                GateOwner(
                    gate = this,
                ),
            ) {
                operation()
            }
        }
    }

    private class GateOwner(
        val gate: AppOperationGate,
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key :
            CoroutineContext.Key<GateOwner>
    }
}
