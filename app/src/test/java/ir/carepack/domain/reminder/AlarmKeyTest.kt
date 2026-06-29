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
    fun stableToken_containsOnlyLowercaseHexCharacters() {
        val alarmKey =
            AlarmKey.forScheduleSeries(
                scheduleSeriesId =
                    "series/with spaces/و/فارسی",
            )

        assertTrue(
            alarmKey.stableToken.all {
                    character ->
                character in '0'..'9' ||
                        character in 'a'..'f'
            },
        )
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

    private companion object {
        const val SHA_256_HEX_LENGTH =
            64
    }
}
