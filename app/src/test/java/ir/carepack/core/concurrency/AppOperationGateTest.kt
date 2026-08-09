package ir.carepack.core.concurrency

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOperationGateTest {

    @Test
    fun concurrentDestructiveOperations_andReconciliation_areSerialized() =
        runTest {
            val gate = AppOperationGate()
            val active = AtomicInteger(0)
            val maximumActive = AtomicInteger(0)
            val releaseFirst = CompletableDeferred<Unit>()
            val firstEntered = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()

            val first =
                async {
                    gate.withGate {
                        order += "delete-all-enter"
                        val current = active.incrementAndGet()
                        maximumActive.updateAndGet { previous ->
                            maxOf(previous, current)
                        }
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                        active.decrementAndGet()
                        order += "delete-all-exit"
                    }
                }

            firstEntered.await()

            val second =
                async {
                    gate.withGate {
                        order += "medication-delete-enter"
                        val current = active.incrementAndGet()
                        maximumActive.updateAndGet { previous ->
                            maxOf(previous, current)
                        }
                        active.decrementAndGet()
                        order += "medication-delete-exit"
                    }
                }

            val third =
                async {
                    gate.withGate {
                        order += "reconcile-enter"
                        val current = active.incrementAndGet()
                        maximumActive.updateAndGet { previous ->
                            maxOf(previous, current)
                        }
                        active.decrementAndGet()
                        order += "reconcile-exit"
                    }
                }

            releaseFirst.complete(Unit)
            awaitAll(first, second, third)

            assertEquals(1, maximumActive.get())
            assertEquals("delete-all-enter", order.first())
            assertEquals("delete-all-exit", order[1])
            assertTrue(order.indexOf("medication-delete-enter") > 1)
            assertTrue(order.indexOf("reconcile-enter") > 1)
        }

    @Test
    fun nestedCallOnSameCoroutine_isReentrantAndDoesNotDeadlock() =
        runTest {
            val gate = AppOperationGate()
            val calls = mutableListOf<String>()

            gate.withGate {
                calls += "outer"
                gate.withGate {
                    calls += "inner"
                }
            }

            assertEquals(listOf("outer", "inner"), calls)
        }
}
