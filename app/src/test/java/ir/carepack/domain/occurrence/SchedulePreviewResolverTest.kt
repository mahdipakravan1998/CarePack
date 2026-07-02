package ir.carepack.domain.occurrence

import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.domain.schedule.SchedulePattern
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePreviewResolverTest {

    private val resolver =
        SchedulePreviewResolver()

    @Test
    fun defaultPreviewCoversExactlyFourteenCalendarDays() {
        val preview =
            resolve(
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                10 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T00:00:00Z",
                    ),
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-24T10:00:00Z",
                "2026-06-25T10:00:00Z",
                "2026-06-26T10:00:00Z",
                "2026-06-27T10:00:00Z",
                "2026-06-28T10:00:00Z",
                "2026-06-29T10:00:00Z",
                "2026-06-30T10:00:00Z",
                "2026-07-01T10:00:00Z",
                "2026-07-02T10:00:00Z",
                "2026-07-03T10:00:00Z",
                "2026-07-04T10:00:00Z",
                "2026-07-05T10:00:00Z",
                "2026-07-06T10:00:00Z",
                "2026-07-07T10:00:00Z",
            ),
            preview,
        )

        assertEquals(
            LocalDate.parse(
                "2026-06-24",
            ),
            preview
                .first()
                .localDate,
        )

        assertEquals(
            LocalDate.parse(
                "2026-07-07",
            ),
            preview
                .last()
                .localDate,
        )

        assertEquals(
            14,
            preview
                .map {
                    it.localDate
                }
                .distinct()
                .size,
        )
    }

    @Test
    fun customDayCountChangesPreviewRange() {
        val preview =
            resolve(
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                10 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T00:00:00Z",
                    ),
                dayCount = 3,
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-24T10:00:00Z",
                "2026-06-25T10:00:00Z",
                "2026-06-26T10:00:00Z",
            ),
            preview,
        )
    }

    @Test
    fun laterAnchorDateChangesPreviewStartDate() {
        val preview =
            resolve(
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                10 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T00:00:00Z",
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-27",
                    ),
                dayCount = 2,
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-27T10:00:00Z",
                "2026-06-28T10:00:00Z",
            ),
            preview,
        )
    }

    @Test
    fun earlierAnchorDateDoesNotMovePreviewBeforeEffectiveLocalDate() {
        val preview =
            resolve(
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                9 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T08:00:00Z",
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-21",
                    ),
                dayCount = 2,
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-24T09:00:00Z",
                "2026-06-25T09:00:00Z",
            ),
            preview,
        )

        assertTrue(
            preview.none {
                it.localDate.isBefore(
                    LocalDate.parse(
                        "2026-06-24",
                    ),
                )
            },
        )
    }

    @Test
    fun previewDoesNotIncludeDatesAfterResolvedWindowEnd() {
        val preview =
            resolve(
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                10 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T00:00:00Z",
                    ),
                anchorDate =
                    LocalDate.parse(
                        "2026-06-26",
                    ),
                dayCount = 4,
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-26T10:00:00Z",
                "2026-06-27T10:00:00Z",
                "2026-06-28T10:00:00Z",
                "2026-06-29T10:00:00Z",
            ),
            preview,
        )

        assertTrue(
            preview.none {
                it.localDate.isAfter(
                    LocalDate.parse(
                        "2026-06-29",
                    ),
                )
            },
        )
    }

    @Test
    fun fixedTimePreviewUsesConcreteExpectedOccurrencesInsideExactRange() {
        val preview =
            resolve(
                weekdays =
                    setOf(
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.FRIDAY,
                    ),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                9 * 60,
                                21 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T08:00:00Z",
                    ),
                dayCount = 4,
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-24T09:00:00Z",
                "2026-06-24T21:00:00Z",
                "2026-06-26T09:00:00Z",
                "2026-06-26T21:00:00Z",
            ),
            preview,
        )
    }

    @Test
    fun intervalPreviewUsesConcreteExpectedOccurrencesInsideExactRange() {
        val preview =
            resolve(
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
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
                dayCount = 2,
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-24T07:00:00Z",
                "2026-06-24T15:00:00Z",
                "2026-06-24T23:00:00Z",
                "2026-06-25T07:00:00Z",
                "2026-06-25T15:00:00Z",
                "2026-06-25T23:00:00Z",
            ),
            preview,
        )
    }

    @Test
    fun crossingMidnightIntervalIsCorrectInsideExactFourteenDayRange() {
        val preview =
            resolve(
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                schedulePattern =
                    IntervalSchedule(
                        intervalHours = 6,
                        anchorMinuteOfDay =
                            23 * 60,
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T00:00:00Z",
                    ),
            )

        assertEquals(
            56,
            preview.size,
        )

        assertEquals(
            expectedOccurrences(
                "2026-06-24T05:00:00Z",
                "2026-06-24T11:00:00Z",
                "2026-06-24T17:00:00Z",
                "2026-06-24T23:00:00Z",
                "2026-06-25T05:00:00Z",
                "2026-06-25T11:00:00Z",
                "2026-06-25T17:00:00Z",
                "2026-06-25T23:00:00Z",
            ),
            preview.take(8),
        )

        assertEquals(
            expectedOccurrences(
                "2026-07-07T05:00:00Z",
                "2026-07-07T11:00:00Z",
                "2026-07-07T17:00:00Z",
                "2026-07-07T23:00:00Z",
            ),
            preview.takeLast(4),
        )

        assertEquals(
            LocalDate.parse(
                "2026-06-24",
            ),
            preview
                .first()
                .localDate,
        )

        assertEquals(
            LocalDate.parse(
                "2026-07-07",
            ),
            preview
                .last()
                .localDate,
        )
    }

    @Test
    fun sameDayPastTimeIsExcludedButFuturePreviewDaysRemain() {
        val preview =
            resolve(
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                7 * 60,
                                9 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T08:00:00Z",
                    ),
                dayCount = 2,
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-24T09:00:00Z",
                "2026-06-25T07:00:00Z",
                "2026-06-25T09:00:00Z",
            ),
            preview,
        )
    }

    @Test
    fun optionalStartDateAndEndDateAreRespectedInsidePreviewRange() {
        val preview =
            resolve(
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                10 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T00:00:00Z",
                    ),
                startDate =
                    LocalDate.parse(
                        "2026-06-26",
                    ),
                endDate =
                    LocalDate.parse(
                        "2026-06-27",
                    ),
                dayCount = 7,
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-26T10:00:00Z",
                "2026-06-27T10:00:00Z",
            ),
            preview,
        )
    }

    @Test
    fun selectedWeekdaysAreRespectedInsidePreviewRange() {
        val preview =
            resolve(
                weekdays =
                    setOf(
                        DayOfWeek.THURSDAY,
                        DayOfWeek.SATURDAY,
                    ),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                10 * 60,
                            ),
                    ),
                effectiveFrom =
                    Instant.parse(
                        "2026-06-24T00:00:00Z",
                    ),
                dayCount = 7,
            )

        assertEquals(
            expectedOccurrences(
                "2026-06-25T10:00:00Z",
                "2026-06-27T10:00:00Z",
            ),
            preview,
        )
    }

    @Test
    fun scheduleZoneDeterminesEffectiveLocalDateAndScheduledInstant() {
        val preview =
            resolve(
                weekdays =
                    setOf(
                        DayOfWeek.WEDNESDAY,
                    ),
                schedulePattern =
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                2 * 60 + 30,
                            ),
                    ),
                zoneId =
                    "Asia/Tehran",
                effectiveFrom =
                    Instant.parse(
                        "2026-06-23T22:30:00Z",
                    ),
                dayCount = 1,
            )

        assertEquals(
            expectedOccurrences(
                date = "2026-06-24",
                minuteOfDay = 2 * 60 + 30,
                zoneId = "Asia/Tehran",
                scheduledAt =
                    "2026-06-23T23:00:00Z",
            ),
            preview,
        )
    }

    private fun resolve(
        weekdays: Set<DayOfWeek>,
        schedulePattern: SchedulePattern,
        zoneId: String = "UTC",
        effectiveFrom: Instant,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        anchorDate: LocalDate? = null,
        dayCount: Int = DEFAULT_SCHEDULE_PREVIEW_DAY_COUNT,
    ): List<ComparableOccurrence> =
        resolver
            .resolve(
                SchedulePreviewRequest(
                    weekdays =
                        weekdays,
                    schedulePattern =
                        schedulePattern,
                    zoneId =
                        zoneId,
                    effectiveFrom =
                        effectiveFrom,
                    startDate =
                        startDate,
                    endDate =
                        endDate,
                    anchorDate =
                        anchorDate,
                    dayCount =
                        dayCount,
                ),
            )
            .map {
                ComparableOccurrence(
                    localDate =
                        it.localDate,
                    minuteOfDay =
                        it.minuteOfDay,
                    zoneId =
                        it.zoneId,
                    scheduledAt =
                        it.scheduledAt,
                )
            }

    private fun expectedOccurrences(
        vararg scheduledAt: String,
    ): List<ComparableOccurrence> =
        scheduledAt.map { instantText ->
            val instant =
                Instant.parse(
                    instantText,
                )

            ComparableOccurrence(
                localDate =
                    instant
                        .atZone(
                            java.time.ZoneOffset.UTC,
                        )
                        .toLocalDate(),
                minuteOfDay =
                    instant
                        .atZone(
                            java.time.ZoneOffset.UTC,
                        )
                        .toLocalTime()
                        .hour *
                            MINUTES_PER_HOUR +
                            instant
                                .atZone(
                                    java.time.ZoneOffset.UTC,
                                )
                                .toLocalTime()
                                .minute,
                zoneId = "UTC",
                scheduledAt =
                    instant,
            )
        }

    private fun expectedOccurrences(
        date: String,
        minuteOfDay: Int,
        zoneId: String,
        scheduledAt: String,
    ): List<ComparableOccurrence> =
        listOf(
            ComparableOccurrence(
                localDate =
                    LocalDate.parse(
                        date,
                    ),
                minuteOfDay =
                    minuteOfDay,
                zoneId =
                    zoneId,
                scheduledAt =
                    Instant.parse(
                        scheduledAt,
                    ),
            ),
        )

    private data class ComparableOccurrence(
        val localDate: LocalDate,
        val minuteOfDay: Int,
        val zoneId: String,
        val scheduledAt: Instant,
    )

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
