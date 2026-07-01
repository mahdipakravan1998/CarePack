package ir.carepack.domain.occurrence

import ir.carepack.domain.model.ScheduleDefinition
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceCandidateResolverTest {

    private val resolver =
        OccurrenceCandidateResolver()

    private val anchorDate =
        LocalDate.parse(
            "2026-06-24",
        )

    @Test
    fun fixedTimeSchedule_returnsCandidateWhenDateMatchesRules() {
        val definition =
            scheduleDefinition(
                weekdayMask =
                    weekdayMask(
                        DayOfWeek.WEDNESDAY,
                    ),
                minuteOfDay =
                    8 * 60,
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T00:00:00Z",
                    ),
            )

        val candidates =
            resolver.resolveAll(
                definition = definition,
                anchorDate = anchorDate,
            )

        assertEquals(
            1,
            candidates.size,
        )

        assertEquals(
            anchorDate,
            candidates.single().localDate,
        )

        assertEquals(
            8 * 60,
            candidates.single().minuteOfDay,
        )

        assertEquals(
            Instant.parse(
                "2026-06-24T08:00:00Z",
            ),
            candidates.single().scheduledAt,
        )
    }

    @Test
    fun fixedTimeSchedule_excludesUnselectedWeekday() {
        val definition =
            scheduleDefinition(
                weekdayMask =
                    weekdayMask(
                        DayOfWeek.THURSDAY,
                    ),
                minuteOfDay =
                    8 * 60,
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                            ),
                    ),
            )

        val candidates =
            resolver.resolveAll(
                definition = definition,
                anchorDate = anchorDate,
            )

        assertTrue(
            candidates.isEmpty(),
        )
    }

    @Test
    fun fixedTimeSchedule_excludesDateBeforeStartDate() {
        val definition =
            scheduleDefinition(
                weekdayMask =
                    weekdayMask(
                        DayOfWeek.WEDNESDAY,
                    ),
                minuteOfDay =
                    8 * 60,
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                            ),
                    ),
                startDate =
                    anchorDate.plusDays(
                        1,
                    ),
            )

        val candidates =
            resolver.resolveAll(
                definition = definition,
                anchorDate = anchorDate,
            )

        assertTrue(
            candidates.isEmpty(),
        )
    }

    @Test
    fun fixedTimeSchedule_excludesDateAfterEndDate() {
        val definition =
            scheduleDefinition(
                weekdayMask =
                    weekdayMask(
                        DayOfWeek.WEDNESDAY,
                    ),
                minuteOfDay =
                    8 * 60,
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                            ),
                    ),
                endDate =
                    anchorDate.minusDays(
                        1,
                    ),
            )

        val candidates =
            resolver.resolveAll(
                definition = definition,
                anchorDate = anchorDate,
            )

        assertTrue(
            candidates.isEmpty(),
        )
    }

    @Test
    fun fixedTimeSchedule_doesNotCreateArtificialHistoryBeforeEffectiveInstant() {
        val definition =
            scheduleDefinition(
                weekdayMask =
                    weekdayMask(
                        DayOfWeek.WEDNESDAY,
                    ),
                minuteOfDay =
                    8 * 60,
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                                16 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T08:01:00Z",
                    ),
            )

        val candidates =
            resolver.resolveAll(
                definition = definition,
                anchorDate = anchorDate,
            )

        assertEquals(
            listOf(
                16 * 60,
            ),
            candidates.map {
                it.minuteOfDay
            },
        )
    }

    @Test
    fun fixedTimeSchedule_excludesCandidateAtEffectiveUntilBoundary() {
        val definition =
            scheduleDefinition(
                weekdayMask =
                    weekdayMask(
                        DayOfWeek.WEDNESDAY,
                    ),
                minuteOfDay =
                    8 * 60,
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                                16 * 60,
                            ),
                    ),
                effectiveUntil =
                    Instant.parse(
                        "2026-06-24T16:00:00Z",
                    ),
            )

        val candidates =
            resolver.resolveAll(
                definition = definition,
                anchorDate = anchorDate,
            )

        assertEquals(
            listOf(
                8 * 60,
            ),
            candidates.map {
                it.minuteOfDay
            },
        )
    }

    @Test
    fun intervalSchedule_generatesEveryEightHoursFromAnchor() {
        val definition =
            scheduleDefinition(
                weekdayMask =
                    weekdayMask(
                        DayOfWeek.WEDNESDAY,
                    ),
                minuteOfDay =
                    7 * 60,
                schedulePattern =
                    IntervalSchedule(
                        intervalHours = 8,
                        anchorMinuteOfDay =
                            7 * 60,
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T00:00:00Z",
                    ),
            )

        val candidates =
            resolver.resolveAll(
                definition = definition,
                anchorDate = anchorDate,
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
    fun intervalSchedule_respectsStartEndAndEffectiveRules() {
        val definition =
            scheduleDefinition(
                weekdayMask =
                    weekdayMask(
                        DayOfWeek.WEDNESDAY,
                    ),
                minuteOfDay =
                    6 * 60,
                schedulePattern =
                    IntervalSchedule(
                        intervalHours = 6,
                        anchorMinuteOfDay =
                            6 * 60,
                    ),
                startDate =
                    anchorDate,
                endDate =
                    anchorDate,
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T06:01:00Z",
                    ),
                effectiveUntil =
                    Instant.parse(
                        "2026-06-24T18:00:00Z",
                    ),
            )

        val candidates =
            resolver.resolveAll(
                definition = definition,
                anchorDate = anchorDate,
            )

        assertEquals(
            listOf(
                12 * 60,
            ),
            candidates.map {
                it.minuteOfDay
            },
        )
    }

    private fun scheduleDefinition(
        weekdayMask: Int,
        minuteOfDay: Int,
        schedulePattern:
        ir.carepack.domain.schedule.SchedulePattern,
        effectiveFrom: Instant =
            Instant.parse(
                "2026-06-24T00:00:00Z",
            ),
        effectiveUntil: Instant? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): ScheduleDefinition =
        ScheduleDefinition(
            scheduleVersionId =
                "schedule-version",
            scheduleSeriesId =
                "schedule-series",
            medicationId =
                "medication",
            weekdayMask =
                weekdayMask,
            minuteOfDay =
                minuteOfDay,
            schedulePattern =
                schedulePattern,
            zoneId =
                "UTC",
            effectiveFrom =
                effectiveFrom,
            effectiveUntil =
                effectiveUntil,
            startDate =
                startDate,
            endDate =
                endDate,
            medicationNameSnapshot =
                "دارو",
            medicationInstructionSnapshot =
                "دستور",
        )

    private fun weekdayMask(
        vararg days: DayOfWeek,
    ): Int =
        days.fold(0) { mask, day ->
            mask or
                    (1 shl (day.value - 1))
        }
}
