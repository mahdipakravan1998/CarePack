package ir.carepack.domain.reminder

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderOperationLock {
    private val mutex =
        Mutex()

    suspend fun <T> withLock(
        operation: suspend () -> T,
    ): T =
        mutex.withLock {
            operation()
        }
}
