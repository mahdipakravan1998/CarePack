package ir.carepack.domain.occurrence

import ir.carepack.domain.model.ScheduleDefinition
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val candidates =
            resolver.resolveAll(
                definition =
                    definition(
                        weekdays =
                            setOf(
                                DayOfWeek.THURSDAY,
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
            emptyList<OccurrenceCandidate>(),
            candidates,
        )
    }

    @Test
    fun fixedTimeSchedule_resolvesAllRepresentativeTimes() {
        val candidates =
            resolver.resolveAll(
                definition =
                    definition(
                        minuteOfDay =
                            8 * 60,
                        schedulePattern =
                            FixedTimeSchedule(
                                minutesOfDay =
                                    listOf(
                                        8 * 60,
                                        20 * 60,
                                    ),
                            ),
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertEquals(
            listOf(
                8 * 60,
                20 * 60,
            ),
            candidates.map {
                it.minuteOfDay
            },
        )
    }

    @Test
    fun intervalSchedule_resolvesAllTimesWithinDay() {
        val candidates =
            resolver.resolveAll(
                definition =
                    definition(
                        minuteOfDay =
                            7 * 60,
                        schedulePattern =
                            IntervalSchedule(
                                intervalHours = 8,
                                anchorMinuteOfDay =
                                    7 * 60,
                            ),
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertEquals(
            listOf(
                7 * 60,
                15 * 60,
                23 * 60,
            ),
            candidates.map {
                it.minuteOfDay
            },
        )
    }

    @Test
    fun intervalSchedule_crossingMidnightIsRepresentedByLocalDateAndTime() {
        val candidates =
            resolver.resolveAll(
                definition =
                    definition(
                        minuteOfDay =
                            23 * 60,
                        schedulePattern =
                            IntervalSchedule(
                                intervalHours = 6,
                                anchorMinuteOfDay =
                                    23 * 60,
                            ),
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        assertEquals(
            listOf(
                5 * 60,
                11 * 60,
                17 * 60,
                23 * 60,
            ),
            candidates.map {
                it.minuteOfDay
            },
        )

        assertEquals(
            List(
                size = 4,
            ) {
                LocalDate.parse(
                    "2026-06-24",
                )
            },
            candidates.map {
                it.localDate
            },
        )
    }

    @Test
    fun startAndEndDatesLimitCandidates() {
        val definition =
            definition(
                weekdays =
                    setOf(
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                    ),
                minuteOfDay =
                    9 * 60,
                startDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
                endDate =
                    LocalDate.parse(
                        "2026-06-25",
                    ),
            )

        assertEquals(
            emptyList<OccurrenceCandidate>(),
            resolver.resolveAll(
                definition =
                    definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-23",
                    ),
            ),
        )

        assertEquals(
            1,
            resolver.resolveAll(
                definition =
                    definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            ).size,
        )

        assertEquals(
            1,
            resolver.resolveAll(
                definition =
                    definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-25",
                    ),
            ).size,
        )

        assertEquals(
            emptyList<OccurrenceCandidate>(),
            resolver.resolveAll(
                definition =
                    definition,
                anchorDate =
                    LocalDate.parse(
                        "2026-06-26",
                    ),
            ),
        )
    }

    private fun definition(
        weekdays: Set<DayOfWeek> =
            setOf(
                DayOfWeek.WEDNESDAY,
            ),
        minuteOfDay: Int,
        schedulePattern:
        ir.carepack.domain.schedule.SchedulePattern =
            FixedTimeSchedule(
                minutesOfDay =
                    listOf(
                        minuteOfDay,
                    ),
            ),
        effectiveFrom: Instant =
            Instant.parse(
                "2026-06-24T00:00:00Z",
            ),
        effectiveUntil: Instant? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        zoneId: String = "Asia/Tehran",
    ): ScheduleDefinition =
        ScheduleDefinition(
            scheduleVersionId =
                "version-1",
            scheduleSeriesId =
                "series-1",
            medicationId =
                "medication-1",
            weekdayMask =
                weekdays.toWeekdayMask(),
            minuteOfDay =
                minuteOfDay,
            schedulePattern =
                schedulePattern,
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
                "داروی نمونه",
            medicationInstructionSnapshot =
                "دستور نمونه",
        )

    private fun Set<DayOfWeek>.toWeekdayMask():
            Int =
        fold(0) {
                mask,
                day ->
            mask or
                    (1 shl (day.value - 1))
        }
}
