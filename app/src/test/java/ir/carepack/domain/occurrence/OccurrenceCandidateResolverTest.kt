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
        val definition = definition(
            effectiveFrom =
                Instant.parse(
                    "2026-06-24T08:00:00Z",
                ),
            minuteOfDay = 12 * 60,
        )

        val candidate =
            resolver.resolve(
                definition = definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertEquals(
            LocalDate.parse(
                "2026-06-24",
            ),
            candidate?.localDate,
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
        val definition = definition(
            effectiveFrom =
                Instant.parse(
                    "2026-06-24T09:00:00Z",
                ),
            minuteOfDay = 12 * 60,
        )

        val candidate =
            resolver.resolve(
                definition = definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertNull(candidate)
    }

    @Test
    fun nonMatchingWeekday_isExcluded() {
        val definition = definition(
            effectiveFrom =
                Instant.parse(
                    "2026-06-24T00:00:00Z",
                ),
            minuteOfDay = 12 * 60,
        )

        val candidate =
            resolver.resolve(
                definition = definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-25",
                    ),
            )

        assertNull(candidate)
    }

    @Test
    fun candidateAtEffectiveUntil_isExcluded() {
        val definition = definition(
            effectiveFrom =
                Instant.parse(
                    "2026-06-24T00:00:00Z",
                ),
            effectiveUntil =
                Instant.parse(
                    "2026-06-24T08:30:00Z",
                ),
            minuteOfDay = 12 * 60,
        )

        val candidate =
            resolver.resolve(
                definition = definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertNull(candidate)
    }

    @Test
    fun occurrenceIdentityInputs_remainStable() {
        val definition = definition(
            effectiveFrom =
                Instant.parse(
                    "2026-06-24T00:00:00Z",
                ),
            minuteOfDay = 12 * 60,
        )

        val first =
            resolver.resolve(
                definition = definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        val second =
            resolver.resolve(
                definition = definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertTrue(first == second)
    }

    private fun definition(
        effectiveFrom: Instant,
        effectiveUntil: Instant? = null,
        minuteOfDay: Int,
    ): ScheduleDefinition {
        return ScheduleDefinition(
            scheduleVersionId = "version-1",
            scheduleSeriesId = "series-1",
            medicationId = "medication-1",
            weekdayMask =
                1 shl (
                        DayOfWeek.WEDNESDAY.value - 1
                        ),
            minuteOfDay = minuteOfDay,
            zoneId = "Asia/Tehran",
            effectiveFrom = effectiveFrom,
            effectiveUntil = effectiveUntil,
            startDate = null,
            endDate = null,
            medicationNameSnapshot =
                "Sample medication",
            medicationInstructionSnapshot =
                "Sample instruction",
        )
    }
}
