package ir.carepack.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmKeyTest {

    @Test
    fun sameScheduleSeries_producesStableIdentity() {
        val first =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "schedule-series-1",
            )

        val second =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "schedule-series-1",
            )

        assertEquals(first, second)
        assertEquals(
            first.stableToken,
            second.stableToken,
        )
        assertEquals(
            SHA_256_HEX_LENGTH,
            first.stableToken.length,
        )
    }

    @Test
    fun differentScheduleSeries_doNotCollide() {
        val first =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "schedule-series-1",
            )

        val second =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "schedule-series-2",
            )

        assertNotEquals(first, second)
        assertNotEquals(
            first.stableToken,
            second.stableToken,
        )
    }

    @Test
    fun delayedOccurrenceIdentity_isStableAndDistinctFromScheduleSeries() {
        val delayed =
            AlarmKey.forDelayedOccurrence(
                occurrenceId =
                    "shared-identifier",
            )

        val delayedAgain =
            AlarmKey.forDelayedOccurrence(
                occurrenceId =
                    "shared-identifier",
            )

        val schedule =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "shared-identifier",
            )

        assertEquals(delayed, delayedAgain)
        assertEquals(
            delayed.stableToken,
            delayedAgain.stableToken,
        )
        assertNotEquals(delayed, schedule)
        assertNotEquals(
            delayed.stableToken,
            schedule.stableToken,
        )
    }

    @Test
    fun testReminderIdentity_isStableAndCollisionSafe() {
        val first =
            AlarmKey.forTestReminder()

        val second =
            AlarmKey.forTestReminder()

        val schedule =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId = "single",
            )

        val delayed =
            AlarmKey.forDelayedOccurrence(
                occurrenceId = "single",
            )

        assertEquals(first, second)
        assertEquals(
            first.stableToken,
            second.stableToken,
        )
        assertNotEquals(first, schedule)
        assertNotEquals(first, delayed)
        assertNotEquals(
            first.stableToken,
            schedule.stableToken,
        )
        assertNotEquals(
            first.stableToken,
            delayed.stableToken,
        )
    }

    @Test
    fun stableTokensContainOnlyLowercaseHexCharacters() {
        val keys =
            listOf(
                AlarmKey.forScheduleSeries(
                    scheduleSeriesId =
                        "series/with spaces/و/فارسی",
                ),
                AlarmKey.forDelayedOccurrence(
                    occurrenceId =
                        "occurrence/with spaces/و/فارسی",
                ),
                AlarmKey.forTestReminder(),
            )

        keys.forEach { alarmKey ->
            assertEquals(
                SHA_256_HEX_LENGTH,
                alarmKey.stableToken.length,
            )

            assertTrue(
                alarmKey.stableToken.all { character ->
                    character in '0'..'9' ||
                            character in 'a'..'f'
                },
            )
        }
    }

    @Test(
        expected =
            IllegalArgumentException::class,
    )
    fun blankScheduleSeries_isRejected() {
        AlarmKey.forScheduleSeries(
            scheduleSeriesId = " ",
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class,
    )
    fun blankDelayedOccurrence_isRejected() {
        AlarmKey.forDelayedOccurrence(
            occurrenceId = " ",
        )
    }

    private companion object {
        const val SHA_256_HEX_LENGTH =
            64
    }
}
