package ir.carepack.feature.careplan

import ir.carepack.core.time.ZoneProvider
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleFormEditorTest {

    @Test
    fun sameTimestampPolicy_stampsFormAndPreviewFromOneClockRead() {
        val editor = ScheduleFormEditor(
            clock = SequenceClock(
                Instant.parse("2026-08-10T23:59:59Z"),
                Instant.parse("2026-08-11T00:00:01Z"),
            ),
            zoneProvider = ZoneProvider { ZoneOffset.UTC },
        )

        val update = editor.toggleWeekday(state(), DayOfWeek.MONDAY)

        assertTrue(DayOfWeek.MONDAY in update.schedule.weekdays)
        assertEquals(
            Instant.parse("2026-08-10T23:59:59Z"),
            update.schedule.previewEffectiveFrom,
        )
        assertEquals(LocalDate.parse("2026-08-10"), update.previewAnchorDate)
    }

    @Test
    fun freshClockPolicy_preservesSetupPreviewClockReadContract() {
        val editor = ScheduleFormEditor(
            clock = SequenceClock(
                Instant.parse("2026-08-10T23:59:59Z"),
                Instant.parse("2026-08-11T00:00:01Z"),
            ),
            zoneProvider = ZoneProvider { ZoneOffset.UTC },
            previewTimestampPolicy =
                SchedulePreviewTimestampPolicy.FRESH_CLOCK_READ,
        )

        val update = editor.toggleWeekday(state(), DayOfWeek.MONDAY)

        assertEquals(
            Instant.parse("2026-08-10T23:59:59Z"),
            update.schedule.previewEffectiveFrom,
        )
        assertEquals(LocalDate.parse("2026-08-11"), update.previewAnchorDate)
    }

    private fun state() = ScheduleFormUiState(
        weekdays = emptySet(),
        minutesOfDay = emptyList(),
        timeDraft = "",
        startDateText = "",
        endDateText = "",
        zoneId = "UTC",
        previewEffectiveFrom = Instant.EPOCH,
    )
}

private class SequenceClock(
    vararg instants: Instant,
    private val zoneId: ZoneId = ZoneOffset.UTC,
) : Clock() {
    private val values = ArrayDeque(instants.toList())

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock =
        SequenceClock(*values.toTypedArray(), zoneId = zone)

    override fun instant(): Instant = values.removeFirst()
}
