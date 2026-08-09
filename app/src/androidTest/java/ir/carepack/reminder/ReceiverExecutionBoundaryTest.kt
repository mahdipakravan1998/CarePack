package ir.carepack.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.core.error.AppFailureKind
import ir.carepack.reminder.receiver.ReceiverCompletion
import ir.carepack.reminder.receiver.ReceiverExecutionBoundary
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiverExecutionBoundaryTest {

    @Test
    fun timeout_finishesOnceAndReportsTimeout() {
        val finishCount = AtomicInteger(0)
        val completed = CountDownLatch(1)
        val failureKinds = mutableListOf<AppFailureKind>()
        val boundary =
            boundary(
                timeoutMillis = 25L,
                finishCount = finishCount,
                completed = completed,
            )

        boundary.launch(
            receiver = EmptyReceiver(),
            operation = {
                delay(5_000L)
            },
            onFailure = { failure ->
                synchronized(failureKinds) {
                    failureKinds += failure.kind
                }
            },
        )

        assertTrue(completed.await(5L, TimeUnit.SECONDS))
        assertEquals(1, finishCount.get())
        assertEquals(listOf(AppFailureKind.TIMEOUT), failureKinds)
    }

    @Test
    fun exception_finishesOnceAndReportsSafeStorageFailure() {
        val finishCount = AtomicInteger(0)
        val completed = CountDownLatch(1)
        val failureKinds = mutableListOf<AppFailureKind>()
        val boundary =
            boundary(
                timeoutMillis = 2_000L,
                finishCount = finishCount,
                completed = completed,
            )

        boundary.launch(
            receiver = EmptyReceiver(),
            operation = {
                throw IOException("sensitive raw message")
            },
            onFailure = { failure ->
                synchronized(failureKinds) {
                    failureKinds += failure.kind
                }
            },
        )

        assertTrue(completed.await(5L, TimeUnit.SECONDS))
        assertEquals(1, finishCount.get())
        assertEquals(listOf(AppFailureKind.STORAGE), failureKinds)
    }

    @Test
    fun cancellation_finishesOnceAndDoesNotMapToFailure() {
        val finishCount = AtomicInteger(0)
        val completed = CountDownLatch(1)
        val failureCount = AtomicInteger(0)
        val boundary =
            boundary(
                timeoutMillis = 2_000L,
                finishCount = finishCount,
                completed = completed,
            )

        boundary.launch(
            receiver = EmptyReceiver(),
            operation = {
                throw CancellationException("cancelled")
            },
            onFailure = {
                failureCount.incrementAndGet()
            },
        )

        assertTrue(completed.await(5L, TimeUnit.SECONDS))
        assertEquals(1, finishCount.get())
        assertEquals(0, failureCount.get())
    }

    private fun boundary(
        timeoutMillis: Long,
        finishCount: AtomicInteger,
        completed: CountDownLatch,
    ): ReceiverExecutionBoundary =
        ReceiverExecutionBoundary(
            dispatcher = Dispatchers.Default,
            timeoutMillis = timeoutMillis,
            completionProvider = {
                ReceiverCompletion {
                    finishCount.incrementAndGet()
                    completed.countDown()
                }
            },
        )
}

private class EmptyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = Unit
}
