package ir.carepack.domain.occurrence

import ir.carepack.domain.model.ScheduleDefinition
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceCandidateResolverTest {

    private val resolver =
        OccurrenceCandidateResolver()

    @Test
    fun matchingCandidate_afterEffectiveFrom_isIncluded() {
        val candidate =
            resolver.resolve(
                definition =
                    definition(
                        effectiveFrom =
                            Instant.parse(
                                "2026-06-24T08:00:00Z",
                            ),
                        minuteOfDay =
                            12 * 60,
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertEquals(
            Instant.parse(
                "2026-06-24T08:30:00Z",
            ),
            candidate?.scheduledAt,
        )
    }

    @Test
    fun candidate_beforeEffectiveFrom_isExcluded() {
        val candidate =
            resolver.resolve(
                definition =
                    definition(
                        effectiveFrom =
                            Instant.parse(
                                "2026-06-24T09:00:00Z",
                            ),
                        minuteOfDay =
                            12 * 60,
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertNull(candidate)
    }

    @Test
    fun candidateAtEffectiveUntil_isExcluded() {
        val candidate =
            resolver.resolve(
                definition =
                    definition(
                        effectiveFrom =
                            Instant.parse(
                                "2026-06-24T00:00:00Z",
                            ),
                        effectiveUntil =
                            Instant.parse(
                                "2026-06-24T08:30:00Z",
                            ),
                        minuteOfDay =
                            12 * 60,
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertNull(candidate)
    }

    @Test
    fun nonMatchingWeekday_isExcluded() {
        val candidate =
            resolver.resolve(
                definition =
                    definition(
                        effectiveFrom =
                            Instant.parse(
                                "2026-06-24T00:00:00Z",
                            ),
                        minuteOfDay =
                            12 * 60,
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-25",
                    ),
            )

        assertNull(candidate)
    }

    @Test
    fun startDate_isInclusive() {
        val date =
            LocalDate.parse(
                "2026-06-24",
            )

        val candidate =
            resolver.resolve(
                definition =
                    definition(
                        effectiveFrom =
                            Instant.parse(
                                "2026-06-01T00:00:00Z",
                            ),
                        minuteOfDay =
                            12 * 60,
                        startDate = date,
                    ),
                anchorDate = date,
            )

        assertTrue(candidate != null)
    }

    @Test
    fun endDate_isInclusive() {
        val date =
            LocalDate.parse(
                "2026-06-24",
            )

        val candidate =
            resolver.resolve(
                definition =
                    definition(
                        effectiveFrom =
                            Instant.parse(
                                "2026-06-01T00:00:00Z",
                            ),
                        minuteOfDay =
                            12 * 60,
                        endDate = date,
                    ),
                anchorDate = date,
            )

        assertTrue(candidate != null)
    }

    @Test
    fun dateBeforeStart_isExcluded() {
        val candidate =
            resolver.resolve(
                definition =
                    definition(
                        effectiveFrom =
                            Instant.parse(
                                "2026-06-01T00:00:00Z",
                            ),
                        minuteOfDay =
                            12 * 60,
                        startDate =
                            LocalDate.parse(
                                "2026-06-25",
                            ),
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertNull(candidate)
    }

    @Test
    fun dstGap_usesStandardAtZoneResolution() {
        val candidate =
            resolver.resolve(
                definition =
                    definition(
                        effectiveFrom =
                            Instant.parse(
                                "2026-01-01T00:00:00Z",
                            ),
                        minuteOfDay =
                            2 * 60 + 30,
                        zoneId =
                            "America/New_York",
                        weekday =
                            DayOfWeek.SUNDAY,
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-03-08",
                    ),
            )

        assertEquals(
            Instant.parse(
                "2026-03-08T07:30:00Z",
            ),
            candidate?.scheduledAt,
        )
    }

    @Test
    fun dstOverlap_usesEarlierOffset() {
        val candidate =
            resolver.resolve(
                definition =
                    definition(
                        effectiveFrom =
                            Instant.parse(
                                "2026-01-01T00:00:00Z",
                            ),
                        minuteOfDay =
                            1 * 60 + 30,
                        zoneId =
                            "America/New_York",
                        weekday =
                            DayOfWeek.SUNDAY,
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-11-01",
                    ),
            )

        assertEquals(
            Instant.parse(
                "2026-11-01T05:30:00Z",
            ),
            candidate?.scheduledAt,
        )
    }

    @Test
    fun repeatedResolution_isStable() {
        val definition =
            definition(
                effectiveFrom =
                    Instant.parse(
                        "2026-06-01T00:00:00Z",
                    ),
                minuteOfDay =
                    12 * 60,
            )

        val date =
            LocalDate.parse(
                "2026-06-24",
            )

        val first =
            resolver.resolve(
                definition,
                date,
            )

        val second =
            resolver.resolve(
                definition,
                date,
            )

        assertEquals(
            first,
            second,
        )
    }

    private fun definition(
        effectiveFrom: Instant,
        minuteOfDay: Int,
        effectiveUntil:
        Instant? = null,
        startDate:
        LocalDate? = null,
        endDate:
        LocalDate? = null,
        zoneId: String =
            "Asia/Tehran",
        weekday: DayOfWeek =
            DayOfWeek.WEDNESDAY,
    ): ScheduleDefinition {
        return ScheduleDefinition(
            scheduleVersionId =
                "version-1",
            scheduleSeriesId =
                "series-1",
            medicationId =
                "medication-1",
            weekdayMask =
                1 shl (
                        weekday.value - 1
                        ),
            minuteOfDay =
                minuteOfDay,
            zoneId =
                zoneId,
            effectiveFrom =
                effectiveFrom,
            effectiveUntil =
                effectiveUntil,
            startDate =
                startDate,
            endDate =
                endDate,
            medicationNameSnapshot =
                "Medication",
            medicationInstructionSnapshot =
                "Instruction",
        )
    }
}
