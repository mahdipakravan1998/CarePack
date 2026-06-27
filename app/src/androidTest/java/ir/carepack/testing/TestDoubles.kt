package ir.carepack.testing

import ir.carepack.core.id.IdSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

internal class SequenceIdSource(vararg ids: String) : IdSource {
    private val remainingIds = ArrayDeque(ids.toList())

    override fun nextId(): String {
        check(remainingIds.isNotEmpty()) { "No test ID remains." }
        return remainingIds.removeFirst()
    }
}

internal class IncrementingIdSource(
    private val prefix: String = "test-id",
) : IdSource {
    private val counter = AtomicInteger(0)

    override fun nextId(): String = "$prefix-${counter.incrementAndGet()}"
}

internal class MutableTestClock(
    initialInstant: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    var currentInstant: Instant = initialInstant

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableTestClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant
}
