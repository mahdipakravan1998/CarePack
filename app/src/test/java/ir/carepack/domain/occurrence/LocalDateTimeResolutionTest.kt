package ir.carepack.domain.occurrence

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDateTimeResolutionTest {

    private val resolver = CarePackLocalDateTimeResolver()

    @Test
    fun berlinSpringGap_movesToFirstValidInstantAfterGap() {
        val result =
            resolver.resolve(
                localDateTime =
                    LocalDateTime.of(
                        LocalDate.of(2026, 3, 29),
                        LocalTime.of(2, 30),
                    ),
                zoneId = ZoneId.of("Europe/Berlin"),
            )

        assertEquals(LocalDateTimeResolutionKind.GAP_ADJUSTED, result.kind)
        assertEquals(
            LocalDateTime.of(2026, 3, 29, 3, 0),
            result.resolvedLocalDateTime,
        )
        assertEquals(
            Instant.parse("2026-03-29T01:00:00Z"),
            result.instant,
        )
    }

    @Test
    fun berlinAutumnOverlap_usesEarlierOffsetAndCreatesOneInstant() {
        val result =
            resolver.resolve(
                localDateTime =
                    LocalDateTime.of(2026, 10, 25, 2, 30),
                zoneId = ZoneId.of("Europe/Berlin"),
            )

        assertEquals(LocalDateTimeResolutionKind.OVERLAP_EARLIER_OFFSET, result.kind)
        assertEquals(ZoneOffset.ofHours(2), result.offset)
        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z"),
            result.instant,
        )
    }

    @Test
    fun newYorkSpringGap_movesToFirstValidInstantAfterGap() {
        val result =
            resolver.resolve(
                localDateTime =
                    LocalDateTime.of(2026, 3, 8, 2, 30),
                zoneId = ZoneId.of("America/New_York"),
            )

        assertEquals(LocalDateTimeResolutionKind.GAP_ADJUSTED, result.kind)
        assertEquals(
            LocalDateTime.of(2026, 3, 8, 3, 0),
            result.resolvedLocalDateTime,
        )
        assertEquals(
            Instant.parse("2026-03-08T07:00:00Z"),
            result.instant,
        )
    }

    @Test
    fun newYorkAutumnOverlap_usesEarlierOffsetAndCreatesOneInstant() {
        val result =
            resolver.resolve(
                localDateTime =
                    LocalDateTime.of(2026, 11, 1, 1, 30),
                zoneId = ZoneId.of("America/New_York"),
            )

        assertEquals(LocalDateTimeResolutionKind.OVERLAP_EARLIER_OFFSET, result.kind)
        assertEquals(ZoneOffset.ofHours(-4), result.offset)
        assertEquals(
            Instant.parse("2026-11-01T05:30:00Z"),
            result.instant,
        )
    }

    @Test
    fun normalDateTime_isUnchanged() {
        val localDateTime = LocalDateTime.of(2026, 2, 15, 11, 45)

        val result =
            resolver.resolve(
                localDateTime = localDateTime,
                zoneId = ZoneId.of("Europe/Berlin"),
            )

        assertEquals(LocalDateTimeResolutionKind.NORMAL, result.kind)
        assertEquals(localDateTime, result.resolvedLocalDateTime)
    }
}
