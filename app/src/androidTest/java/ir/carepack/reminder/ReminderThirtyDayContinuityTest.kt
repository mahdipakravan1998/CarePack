package ir.carepack.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderThirtyDayContinuityTest {

    @Test
    fun fixedSchedule_keepsTargetsAvailableForThirtyDaysWithoutForeground() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(initialInstant = START_INSTANT)
                .use { fixture ->
                    val plan =
                        fixture.createPlan(
                            weekdays = DayOfWeek.entries.toSet(),
                            minutesOfDay = listOf(9 * 60),
                            schedulePattern =
                                FixedTimeSchedule(
                                    minutesOfDay = listOf(9 * 60),
                                ),
                            startDate = ANCHOR_DATE,
                            endDate = null,
                            zoneId = "UTC",
                        )

                    repeat(31) { dayIndex ->
                        val date = ANCHOR_DATE.plusDays(dayIndex.toLong())
                        val now =
                            date
                                .atTime(8, 55)
                                .toInstant(ZoneOffset.UTC)

                        fixture.moveTo(now)
                        fixture.occurrenceGenerator
                            .guaranteeMaintenanceWindowForAll(
                                anchorDate = date,
                                now = now,
                            )

                        val target =
                            fixture.reminderScheduleSource
                                .getNextEligibleTargets(now)
                                .singleOrNull { candidate ->
                                    candidate.alarmKey.scheduleSeriesId ==
                                        plan.scheduleSeriesId
                                }

                        assertNotNull(
                            "No reminder target on day $dayIndex",
                            target,
                        )
                        assertEquals(
                            date,
                            target?.scheduledAt
                                ?.atZone(ZoneOffset.UTC)
                                ?.toLocalDate(),
                        )
                    }

                    val occurrences =
                        fixture.occurrencesForMedication(plan.medicationId)

                    assertTrue(
                        occurrences.any { occurrence ->
                            occurrence.localEpochDay >=
                                ANCHOR_DATE.plusDays(30).toEpochDay()
                        },
                    )
                }
        }

    @Test
    fun intervalSchedule_keepsTargetsAvailableForThirtyDaysWithoutForeground() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(initialInstant = START_INSTANT)
                .use { fixture ->
                    val pattern =
                        IntervalSchedule(
                            intervalHours = 8,
                            anchorMinuteOfDay = 7 * 60,
                        )

                    val plan =
                        fixture.createPlan(
                            weekdays = DayOfWeek.entries.toSet(),
                            minutesOfDay =
                                pattern.representativeMinutesOfDay,
                            schedulePattern = pattern,
                            startDate = ANCHOR_DATE,
                            endDate = null,
                            zoneId = "UTC",
                        )

                    repeat(31) { dayIndex ->
                        val date = ANCHOR_DATE.plusDays(dayIndex.toLong())
                        val now =
                            date
                                .atTime(6, 55)
                                .toInstant(ZoneOffset.UTC)

                        fixture.moveTo(now)
                        fixture.occurrenceGenerator
                            .guaranteeMaintenanceWindowForAll(
                                anchorDate = date,
                                now = now,
                            )

                        val target =
                            fixture.reminderScheduleSource
                                .getNextEligibleTargets(now)
                                .singleOrNull { candidate ->
                                    candidate.alarmKey.scheduleSeriesId ==
                                        plan.scheduleSeriesId
                                }

                        assertNotNull(
                            "No interval reminder target on day $dayIndex",
                            target,
                        )
                    }
                }
        }

    private companion object {
        val ANCHOR_DATE: LocalDate = LocalDate.parse("2026-06-24")
        val START_INSTANT: Instant = Instant.parse("2026-06-24T06:55:00Z")
    }
}
