package ir.carepack.core.time

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

fun tickingNow(
    clock: Clock,
    intervalMillis: Long = 1_000L,
): Flow<Instant> {
    require(intervalMillis > 0L)

    return flow {
        while (
            currentCoroutineContext()
                .isActive
        ) {
            emit(clock.instant())
            delay(intervalMillis)
        }
    }.distinctUntilChanged()
}
