package ir.carepack.reminder.receiver

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemReconciliationRetrySchedulerContractTest {

    private lateinit var context: Context

    private val clock =
        Clock.fixed(
            Instant.parse("2026-06-24T08:00:00Z"),
            ZoneOffset.UTC,
        )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearRetryPreferences()
        cancelExistingPendingIntent()
    }

    @After
    fun tearDown() {
        clearRetryPreferences()
        cancelExistingPendingIntent()
    }

    @Test
    fun alarmSchedulingFailure_doesNotConsumeAttempt() {
        val gateway =
            RecordingRetryAlarmGateway(
                failSchedule = true,
            )
        val scheduler =
            SystemReconciliationRetryScheduler(
                context = context,
                clock = clock,
                alarmGateway = gateway,
            )

        var failed = false
        try {
            scheduler.scheduleNextRetry()
        } catch (_: IOException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(0, retryAttempt())
        assertEquals(1, gateway.scheduleCalls)
    }

    @Test
    fun successfulScheduling_persistsAttemptAfterGatewaySuccess() {
        val gateway = RecordingRetryAlarmGateway()
        val scheduler =
            SystemReconciliationRetryScheduler(
                context = context,
                clock = clock,
                alarmGateway = gateway,
            )

        assertTrue(scheduler.scheduleNextRetry())
        assertEquals(1, retryAttempt())
        assertEquals(1, gateway.scheduleCalls)

        scheduler.markSuccessful()

        assertEquals(0, retryAttempt())
        assertEquals(1, gateway.cancelCalls)
    }

    @Test
    fun maxRetryContract_isBounded() {
        val gateway = RecordingRetryAlarmGateway()
        val scheduler =
            SystemReconciliationRetryScheduler(
                context = context,
                clock = clock,
                alarmGateway = gateway,
            )

        assertTrue(scheduler.scheduleNextRetry())
        assertTrue(scheduler.scheduleNextRetry())
        assertTrue(scheduler.scheduleNextRetry())
        assertFalse(scheduler.scheduleNextRetry())
        assertEquals(3, gateway.scheduleCalls)
        assertEquals(3, retryAttempt())
    }

    @Test
    fun clearAll_removesRetryStateAndRealPendingIntent() {
        val scheduler =
            SystemReconciliationRetryScheduler(
                context = context,
                clock = clock,
            )

        assertTrue(scheduler.scheduleNextRetry())
        assertTrue(retryAttempt() > 0)

        scheduler.clearAll()

        assertEquals(0, retryAttempt())
        assertNull(findExistingPendingIntent())
    }

    private fun retryAttempt(): Int =
        context
            .getSharedPreferences(
                PREFERENCE_FILE,
                Context.MODE_PRIVATE,
            )
            .getInt(KEY_ATTEMPT, 0)

    private fun clearRetryPreferences() {
        context
            .getSharedPreferences(
                PREFERENCE_FILE,
                Context.MODE_PRIVATE,
            )
            .edit()
            .clear()
            .commit()
    }

    private fun cancelExistingPendingIntent() {
        findExistingPendingIntent()?.cancel()
    }

    private fun findExistingPendingIntent(): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(
                context,
                SystemReconciliationReceiver::class.java,
            ).apply {
                action =
                    SystemReconciliationReceiver
                        .ACTION_RETRY_RECONCILIATION
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_NO_CREATE or
                PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val PREFERENCE_FILE =
            "carepack_reconciliation_retry"
        const val KEY_ATTEMPT = "attempt"
        const val REQUEST_CODE = 0x6612
    }
}

private class RecordingRetryAlarmGateway(
    private val failSchedule: Boolean = false,
) : SystemReconciliationRetryAlarmGateway {
    var scheduleCalls = 0
    var cancelCalls = 0

    override fun schedule(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        require(triggerAtMillis > 0)
        scheduleCalls += 1
        if (failSchedule) {
            throw IOException("injected retry scheduling failure")
        }
    }

    override fun cancel(pendingIntent: PendingIntent) {
        cancelCalls += 1
    }
}
