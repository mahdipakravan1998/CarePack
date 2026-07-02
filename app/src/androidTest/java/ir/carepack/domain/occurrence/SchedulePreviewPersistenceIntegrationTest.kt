package ir.carepack.domain.occurrence

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.careplan.UpdateScheduleCommand
import ir.carepack.domain.careplan.UpdateScheduleOutcome
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.domain.schedule.SchedulePattern
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchedulePreviewPersistenceIntegrationTest {

    @Test
    fun fixedTimePreviewEqualsGeneratedOccurrencesInsideExactFourteenDayPreviewWindow() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        START_OF_ANCHOR_DAY,
                )
                .use { fixture ->
                    val expected =
                        listOf(
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-24T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-25",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-25T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-26",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-26T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-27",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-27T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-28",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-28T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-29",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-29T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-30",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-30T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-01",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-07-01T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-02",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-07-02T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-03",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-07-03T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-04",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-07-04T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-05",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-07-05T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-06",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-07-06T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-07",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-07-07T10:00:00Z",
                            ),
                        )

                    val preview =
                        previewOccurrences(
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
                            zoneId = UTC_ZONE_ID,
                            effectiveFrom =
                                fixture.clock.instant(),
                            startDate = null,
                            endDate = null,
                            anchorDate = null,
                            dayCount =
                                DEFAULT_SCHEDULE_PREVIEW_DAY_COUNT,
                        )

                    val plan =
                        fixture.createPlan(
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            minutesOfDay =
                                listOf(
                                    10 * 60,
                                ),
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay =
                                        listOf(
                                            10 * 60,
                                        ),
                                ),
                            startDate = null,
                            endDate = null,
                            zoneId = UTC_ZONE_ID,
                        )

                    fixture.guaranteeWindow(
                        anchorDate =
                            ANCHOR_DATE
                                .plusDays(
                                    6,
                                ),
                    )

                    val generated =
                        generatedOccurrences(
                            fixture = fixture,
                            scheduleVersionId =
                                plan.scheduleVersionId,
                            anchorDate = ANCHOR_DATE,
                            dayCount =
                                DEFAULT_SCHEDULE_PREVIEW_DAY_COUNT,
                        )

                    assertEquals(
                        expected,
                        preview,
                    )

                    assertEquals(
                        expected,
                        generated,
                    )

                    assertEquals(
                        generated,
                        preview,
                    )
                }
        }

    @Test
    fun intervalPreviewEqualsGeneratedOccurrencesInsideExactPreviewRange() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        START_OF_ANCHOR_DAY,
                )
                .use { fixture ->
                    val pattern =
                        IntervalSchedule(
                            intervalHours = 8,
                            anchorMinuteOfDay =
                                7 * 60,
                        )

                    val expected =
                        listOf(
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 7 * 60,
                                scheduledAt =
                                    "2026-06-24T07:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 15 * 60,
                                scheduledAt =
                                    "2026-06-24T15:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 23 * 60,
                                scheduledAt =
                                    "2026-06-24T23:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-25",
                                minuteOfDay = 7 * 60,
                                scheduledAt =
                                    "2026-06-25T07:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-25",
                                minuteOfDay = 15 * 60,
                                scheduledAt =
                                    "2026-06-25T15:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-25",
                                minuteOfDay = 23 * 60,
                                scheduledAt =
                                    "2026-06-25T23:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-26",
                                minuteOfDay = 7 * 60,
                                scheduledAt =
                                    "2026-06-26T07:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-26",
                                minuteOfDay = 15 * 60,
                                scheduledAt =
                                    "2026-06-26T15:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-26",
                                minuteOfDay = 23 * 60,
                                scheduledAt =
                                    "2026-06-26T23:00:00Z",
                            ),
                        )

                    val preview =
                        previewOccurrences(
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            schedulePattern = pattern,
                            zoneId = UTC_ZONE_ID,
                            effectiveFrom =
                                fixture.clock.instant(),
                            startDate = null,
                            endDate = null,
                            anchorDate =
                                ANCHOR_DATE,
                            dayCount = 3,
                        )

                    val plan =
                        fixture.createPlan(
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            minutesOfDay =
                                pattern.representativeMinutesOfDay,
                            schedulePattern = pattern,
                            startDate = null,
                            endDate = null,
                            zoneId = UTC_ZONE_ID,
                        )

                    val generated =
                        generatedOccurrences(
                            fixture = fixture,
                            scheduleVersionId =
                                plan.scheduleVersionId,
                            anchorDate = ANCHOR_DATE,
                            dayCount = 3,
                        )

                    assertEquals(
                        expected,
                        preview,
                    )

                    assertEquals(
                        expected,
                        generated,
                    )

                    assertEquals(
                        generated,
                        preview,
                    )
                }
        }

    @Test
    fun crossingMidnightIntervalPreviewEqualsGeneratedOccurrencesInsideExactFourteenDayWindow() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        START_OF_ANCHOR_DAY,
                )
                .use { fixture ->
                    val pattern =
                        IntervalSchedule(
                            intervalHours = 6,
                            anchorMinuteOfDay =
                                23 * 60,
                        )

                    val preview =
                        previewOccurrences(
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            schedulePattern = pattern,
                            zoneId = UTC_ZONE_ID,
                            effectiveFrom =
                                fixture.clock.instant(),
                            startDate = null,
                            endDate = null,
                            anchorDate = null,
                            dayCount =
                                DEFAULT_SCHEDULE_PREVIEW_DAY_COUNT,
                        )

                    val plan =
                        fixture.createPlan(
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            minutesOfDay =
                                pattern.representativeMinutesOfDay,
                            schedulePattern = pattern,
                            startDate = null,
                            endDate = null,
                            zoneId = UTC_ZONE_ID,
                        )

                    fixture.guaranteeWindow(
                        anchorDate =
                            ANCHOR_DATE
                                .plusDays(
                                    6,
                                ),
                    )

                    val generated =
                        generatedOccurrences(
                            fixture = fixture,
                            scheduleVersionId =
                                plan.scheduleVersionId,
                            anchorDate = ANCHOR_DATE,
                            dayCount =
                                DEFAULT_SCHEDULE_PREVIEW_DAY_COUNT,
                        )

                    assertEquals(
                        56,
                        preview.size,
                    )

                    assertEquals(
                        listOf(
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 5 * 60,
                                scheduledAt =
                                    "2026-06-24T05:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 11 * 60,
                                scheduledAt =
                                    "2026-06-24T11:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 17 * 60,
                                scheduledAt =
                                    "2026-06-24T17:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 23 * 60,
                                scheduledAt =
                                    "2026-06-24T23:00:00Z",
                            ),
                        ),
                        preview.take(
                            4,
                        ),
                    )

                    assertEquals(
                        listOf(
                            expectedOccurrence(
                                date = "2026-07-07",
                                minuteOfDay = 5 * 60,
                                scheduledAt =
                                    "2026-07-07T05:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-07",
                                minuteOfDay = 11 * 60,
                                scheduledAt =
                                    "2026-07-07T11:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-07",
                                minuteOfDay = 17 * 60,
                                scheduledAt =
                                    "2026-07-07T17:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-07-07",
                                minuteOfDay = 23 * 60,
                                scheduledAt =
                                    "2026-07-07T23:00:00Z",
                            ),
                        ),
                        preview.takeLast(
                            4,
                        ),
                    )

                    assertEquals(
                        preview,
                        generated,
                    )
                }
        }

    @Test
    fun sameDayPastTimeIsExcludedByPreviewAndGeneratedOccurrencesInsideExactRange() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        CREATE_EFFECTIVE_FROM,
                )
                .use { fixture ->
                    val minutesOfDay =
                        listOf(
                            7 * 60,
                            9 * 60,
                        )

                    val expected =
                        listOf(
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 9 * 60,
                                scheduledAt =
                                    "2026-06-24T09:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-25",
                                minuteOfDay = 7 * 60,
                                scheduledAt =
                                    "2026-06-25T07:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-25",
                                minuteOfDay = 9 * 60,
                                scheduledAt =
                                    "2026-06-25T09:00:00Z",
                            ),
                        )

                    val preview =
                        previewOccurrences(
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay =
                                        minutesOfDay,
                                ),
                            zoneId = UTC_ZONE_ID,
                            effectiveFrom =
                                fixture.clock.instant(),
                            startDate = null,
                            endDate = null,
                            anchorDate = null,
                            dayCount = 2,
                        )

                    val plan =
                        fixture.createPlan(
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            minutesOfDay =
                                minutesOfDay,
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay =
                                        minutesOfDay,
                                ),
                            startDate = null,
                            endDate = null,
                            zoneId = UTC_ZONE_ID,
                        )

                    val generated =
                        generatedOccurrences(
                            fixture = fixture,
                            scheduleVersionId =
                                plan.scheduleVersionId,
                            anchorDate = ANCHOR_DATE,
                            dayCount = 2,
                        )

                    assertEquals(
                        expected,
                        preview,
                    )

                    assertEquals(
                        expected,
                        generated,
                    )

                    assertEquals(
                        generated,
                        preview,
                    )
                }
        }

    @Test
    fun startDateAndEndDateAreRespectedByPreviewAndGeneratedOccurrencesInsideExactRange() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        START_OF_ANCHOR_DAY,
                )
                .use { fixture ->
                    val startDate =
                        LocalDate.parse(
                            "2026-06-26",
                        )

                    val endDate =
                        LocalDate.parse(
                            "2026-06-27",
                        )

                    val expected =
                        listOf(
                            expectedOccurrence(
                                date = "2026-06-26",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-26T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-27",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-27T10:00:00Z",
                            ),
                        )

                    val preview =
                        previewOccurrences(
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
                            zoneId = UTC_ZONE_ID,
                            effectiveFrom =
                                fixture.clock.instant(),
                            startDate = startDate,
                            endDate = endDate,
                            anchorDate = ANCHOR_DATE,
                            dayCount = 7,
                        )

                    val plan =
                        fixture.createPlan(
                            weekdays =
                                DayOfWeek
                                    .entries
                                    .toSet(),
                            minutesOfDay =
                                listOf(
                                    10 * 60,
                                ),
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay =
                                        listOf(
                                            10 * 60,
                                        ),
                                ),
                            startDate = startDate,
                            endDate = endDate,
                            zoneId = UTC_ZONE_ID,
                        )

                    val generated =
                        generatedOccurrences(
                            fixture = fixture,
                            scheduleVersionId =
                                plan.scheduleVersionId,
                            anchorDate = ANCHOR_DATE,
                            dayCount = 7,
                        )

                    assertEquals(
                        expected,
                        preview,
                    )

                    assertEquals(
                        expected,
                        generated,
                    )

                    assertEquals(
                        generated,
                        preview,
                    )
                }
        }

    @Test
    fun selectedWeekdaysAreRespectedByPreviewAndGeneratedOccurrencesInsideExactRange() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        START_OF_ANCHOR_DAY,
                )
                .use { fixture ->
                    val weekdays =
                        setOf(
                            DayOfWeek.THURSDAY,
                            DayOfWeek.SATURDAY,
                        )

                    val expected =
                        listOf(
                            expectedOccurrence(
                                date = "2026-06-25",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-25T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-27",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-27T10:00:00Z",
                            ),
                        )

                    val preview =
                        previewOccurrences(
                            weekdays = weekdays,
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay =
                                        listOf(
                                            10 * 60,
                                        ),
                                ),
                            zoneId = UTC_ZONE_ID,
                            effectiveFrom =
                                fixture.clock.instant(),
                            startDate = null,
                            endDate = null,
                            anchorDate = ANCHOR_DATE,
                            dayCount = 7,
                        )

                    val plan =
                        fixture.createPlan(
                            weekdays = weekdays,
                            minutesOfDay =
                                listOf(
                                    10 * 60,
                                ),
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay =
                                        listOf(
                                            10 * 60,
                                        ),
                                ),
                            startDate = null,
                            endDate = null,
                            zoneId = UTC_ZONE_ID,
                        )

                    val generated =
                        generatedOccurrences(
                            fixture = fixture,
                            scheduleVersionId =
                                plan.scheduleVersionId,
                            anchorDate = ANCHOR_DATE,
                            dayCount = 7,
                        )

                    assertEquals(
                        expected,
                        preview,
                    )

                    assertEquals(
                        expected,
                        generated,
                    )

                    assertEquals(
                        generated,
                        preview,
                    )
                }
        }

    @Test
    fun scheduleZoneIsRespectedByPreviewAndGeneratedOccurrencesInsideExactRange() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        TEHRAN_EFFECTIVE_FROM,
                )
                .use { fixture ->
                    val expected =
                        listOf(
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 2 * 60 + 30,
                                zoneId = TEHRAN_ZONE_ID,
                                scheduledAt =
                                    "2026-06-23T23:00:00Z",
                            ),
                        )

                    val preview =
                        previewOccurrences(
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
                            zoneId = TEHRAN_ZONE_ID,
                            effectiveFrom =
                                fixture.clock.instant(),
                            startDate = null,
                            endDate = null,
                            anchorDate = null,
                            dayCount = 1,
                        )

                    val plan =
                        fixture.createPlan(
                            weekdays =
                                setOf(
                                    DayOfWeek.WEDNESDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    2 * 60 + 30,
                                ),
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay =
                                        listOf(
                                            2 * 60 + 30,
                                        ),
                                ),
                            startDate = null,
                            endDate = null,
                            zoneId = TEHRAN_ZONE_ID,
                        )

                    val generated =
                        generatedOccurrences(
                            fixture = fixture,
                            scheduleVersionId =
                                plan.scheduleVersionId,
                            anchorDate = ANCHOR_DATE,
                            dayCount = 1,
                        )

                    assertEquals(
                        expected,
                        preview,
                    )

                    assertEquals(
                        expected,
                        generated,
                    )

                    assertEquals(
                        generated,
                        preview,
                    )
                }
        }

    @Test
    fun editingSchedulePreviewUsesSameEffectiveFromBehaviorAsActualUpdateInsideExactRange() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        START_OF_ANCHOR_DAY,
                )
                .use { fixture ->
                    val plan =
                        fixture.createPlan(
                            weekdays =
                                setOf(
                                    DayOfWeek.WEDNESDAY,
                                    DayOfWeek.THURSDAY,
                                ),
                            minutesOfDay =
                                listOf(
                                    9 * 60,
                                ),
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay =
                                        listOf(
                                            9 * 60,
                                        ),
                                ),
                            startDate = ANCHOR_DATE,
                            endDate =
                                ANCHOR_DATE
                                    .plusDays(
                                        1,
                                    ),
                            zoneId = UTC_ZONE_ID,
                        )

                    fixture.moveTo(
                        EDIT_EFFECTIVE_FROM,
                    )

                    val updatedMinutes =
                        listOf(
                            10 * 60,
                            14 * 60,
                        )

                    val expected =
                        listOf(
                            expectedOccurrence(
                                date = "2026-06-24",
                                minuteOfDay = 14 * 60,
                                scheduledAt =
                                    "2026-06-24T14:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-25",
                                minuteOfDay = 10 * 60,
                                scheduledAt =
                                    "2026-06-25T10:00:00Z",
                            ),
                            expectedOccurrence(
                                date = "2026-06-25",
                                minuteOfDay = 14 * 60,
                                scheduledAt =
                                    "2026-06-25T14:00:00Z",
                            ),
                        )

                    val preview =
                        previewOccurrences(
                            weekdays =
                                setOf(
                                    DayOfWeek.WEDNESDAY,
                                    DayOfWeek.THURSDAY,
                                ),
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay =
                                        updatedMinutes,
                                ),
                            zoneId = UTC_ZONE_ID,
                            effectiveFrom =
                                fixture.clock.instant(),
                            startDate = ANCHOR_DATE,
                            endDate =
                                ANCHOR_DATE
                                    .plusDays(
                                        1,
                                    ),
                            anchorDate = ANCHOR_DATE,
                            dayCount = 2,
                        )

                    assertEquals(
                        UpdateScheduleOutcome.Updated,
                        fixture
                            .carePlanService
                            .updateSchedule(
                                UpdateScheduleCommand(
                                    scheduleSeriesId =
                                        plan.scheduleSeriesId,
                                    weekdays =
                                        setOf(
                                            DayOfWeek.WEDNESDAY,
                                            DayOfWeek.THURSDAY,
                                        ),
                                    minutesOfDay =
                                        updatedMinutes,
                                    schedulePattern =
                                        FixedTimeSchedule(
                                            minutesOfDay =
                                                updatedMinutes,
                                        ),
                                    startDate = ANCHOR_DATE,
                                    endDate =
                                        ANCHOR_DATE
                                            .plusDays(
                                                1,
                                            ),
                                    zoneId = UTC_ZONE_ID,
                                ),
                            ),
                    )

                    val newVersionId =
                        checkNotNull(
                            fixture
                                .database
                                .scheduleDao()
                                .getOpenVersionForScheduleSeries(
                                    plan.scheduleSeriesId,
                                ),
                        ).scheduleVersionId

                    val generated =
                        generatedOccurrences(
                            fixture = fixture,
                            scheduleVersionId =
                                newVersionId,
                            anchorDate = ANCHOR_DATE,
                            dayCount = 2,
                        )

                    assertEquals(
                        expected,
                        preview,
                    )

                    assertEquals(
                        expected,
                        generated,
                    )

                    assertEquals(
                        generated,
                        preview,
                    )
                }
        }

    private fun previewOccurrences(
        weekdays: Set<DayOfWeek>,
        schedulePattern: SchedulePattern,
        zoneId: String,
        effectiveFrom: Instant,
        startDate: LocalDate?,
        endDate: LocalDate?,
        anchorDate: LocalDate?,
        dayCount: Int,
    ): List<ComparableOccurrence> =
        SchedulePreviewResolver()
            .resolve(
                SchedulePreviewRequest(
                    weekdays = weekdays,
                    schedulePattern = schedulePattern,
                    zoneId = zoneId,
                    effectiveFrom = effectiveFrom,
                    startDate = startDate,
                    endDate = endDate,
                    anchorDate = anchorDate,
                    dayCount = dayCount,
                ),
            )
            .map { occurrence ->
                ComparableOccurrence(
                    localDate =
                        occurrence.localDate,
                    minuteOfDay =
                        occurrence.minuteOfDay,
                    zoneId =
                        occurrence.zoneId,
                    scheduledAtEpochMillis =
                        occurrence
                            .scheduledAt
                            .toEpochMilli(),
                )
            }
            .sorted()

    private suspend fun generatedOccurrences(
        fixture: CarePlanRoomTestFixture,
        scheduleVersionId: String,
        anchorDate: LocalDate,
        dayCount: Int,
    ): List<ComparableOccurrence> {
        val lastDate =
            anchorDate
                .plusDays(
                    dayCount.toLong() - 1L,
                )

        return fixture
            .occurrencesForSchedule(
                scheduleVersionId,
            )
            .filter { occurrence ->
                val localDate =
                    LocalDate.ofEpochDay(
                        occurrence.localEpochDay,
                    )

                occurrence.lifecycle ==
                        OccurrenceLifecycle
                            .ACTIVE
                            .name &&
                        !localDate.isBefore(
                            anchorDate,
                        ) &&
                        !localDate.isAfter(
                            lastDate,
                        )
            }
            .map { occurrence ->
                ComparableOccurrence(
                    localDate =
                        LocalDate.ofEpochDay(
                            occurrence.localEpochDay,
                        ),
                    minuteOfDay =
                        occurrence.minuteOfDay,
                    zoneId =
                        occurrence.zoneIdSnapshot,
                    scheduledAtEpochMillis =
                        occurrence.scheduledAtEpochMillis,
                )
            }
            .sorted()
    }

    private fun expectedOccurrence(
        date: String,
        minuteOfDay: Int,
        zoneId: String = UTC_ZONE_ID,
        scheduledAt: String,
    ): ComparableOccurrence =
        ComparableOccurrence(
            localDate =
                LocalDate.parse(
                    date,
                ),
            minuteOfDay =
                minuteOfDay,
            zoneId =
                zoneId,
            scheduledAtEpochMillis =
                Instant
                    .parse(
                        scheduledAt,
                    )
                    .toEpochMilli(),
        )

    private data class ComparableOccurrence(
        val localDate: LocalDate,
        val minuteOfDay: Int,
        val zoneId: String,
        val scheduledAtEpochMillis: Long,
    ) : Comparable<ComparableOccurrence> {

        override fun compareTo(
            other: ComparableOccurrence,
        ): Int =
            compareValuesBy(
                this,
                other,
                ComparableOccurrence::scheduledAtEpochMillis,
                ComparableOccurrence::localDate,
                ComparableOccurrence::minuteOfDay,
                ComparableOccurrence::zoneId,
            )
    }

    private companion object {
        const val UTC_ZONE_ID = "UTC"
        const val TEHRAN_ZONE_ID = "Asia/Tehran"

        val ANCHOR_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )

        val START_OF_ANCHOR_DAY: Instant =
            Instant.parse(
                "2026-06-24T00:00:00Z",
            )

        val CREATE_EFFECTIVE_FROM: Instant =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        val EDIT_EFFECTIVE_FROM: Instant =
            Instant.parse(
                "2026-06-24T13:00:00Z",
            )

        val TEHRAN_EFFECTIVE_FROM: Instant =
            Instant.parse(
                "2026-06-23T22:30:00Z",
            )
    }
}
