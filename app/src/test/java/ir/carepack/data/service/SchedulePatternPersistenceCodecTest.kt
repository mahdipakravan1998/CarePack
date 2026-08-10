package ir.carepack.data.service

import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchedulePatternPersistenceCodecTest {

    @Test
    fun fixedPattern_roundTripsWithoutIntervalColumns() {
        val pattern = FixedTimeSchedule(listOf(480, 1_200))

        val encoded = SchedulePatternPersistenceCodec.encode(pattern)

        assertEquals("FIXED_TIMES", encoded.patternType)
        assertNull(encoded.intervalHours)
        assertNull(encoded.anchorMinuteOfDay)
        assertEquals(
            pattern,
            SchedulePatternPersistenceCodec.decode(
                patternType = encoded.patternType,
                intervalHours = encoded.intervalHours,
                anchorMinuteOfDay = encoded.anchorMinuteOfDay,
                fixedMinutesOfDay = pattern.minutesOfDay,
            ),
        )
    }

    @Test
    fun intervalPattern_roundTripsAnchorAndInterval() {
        val pattern = IntervalSchedule(intervalHours = 8, anchorMinuteOfDay = 510)
        val encoded = SchedulePatternPersistenceCodec.encode(pattern)

        assertEquals("EVERY_X_HOURS", encoded.patternType)
        assertEquals(
            pattern,
            SchedulePatternPersistenceCodec.decode(
                patternType = encoded.patternType,
                intervalHours = encoded.intervalHours,
                anchorMinuteOfDay = encoded.anchorMinuteOfDay,
                fixedMinutesOfDay = emptyList(),
            ),
        )
    }

    @Test
    fun unknownType_preservesLegacyFixedFallback() {
        assertEquals(
            FixedTimeSchedule(listOf(600)),
            SchedulePatternPersistenceCodec.decode(
                patternType = "UNKNOWN",
                intervalHours = 8,
                anchorMinuteOfDay = 510,
                fixedMinutesOfDay = listOf(600),
            ),
        )
    }
}
