package ir.carepack.domain.careplan

import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.domain.schedule.SchedulePatternRules
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePatternValidationTest {

    @Test
    fun fixedTimeSchedule_acceptsDistinctValidTimes() {
        val result =
            CarePlanValidation
                .validateSchedulePattern(
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                                20 * 60,
                            ),
                    ),
                )

        assertTrue(
            result is ValidationResult.Valid,
        )
    }

    @Test
    fun fixedTimeSchedule_rejectsDuplicateTimes() {
        val result =
            CarePlanValidation
                .validateSchedulePattern(
                    FixedTimeSchedule(
                        minutesOfDay =
                            listOf(
                                8 * 60,
                                8 * 60,
                            ),
                    ),
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )
    }

    @Test
    fun fixedTimeSchedule_rejectsEmptyTimes() {
        val result =
            CarePlanValidation
                .validateSchedulePattern(
                    FixedTimeSchedule(
                        minutesOfDay =
                            emptyList(),
                    ),
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )
    }

    @Test
    fun intervalSchedule_acceptsEverySixEightAndTwelveHours() {
        listOf(
            6,
            8,
            12,
        ).forEach { hours ->
            val result =
                CarePlanValidation
                    .validateSchedulePattern(
                        IntervalSchedule(
                            intervalHours = hours,
                            anchorMinuteOfDay =
                                8 * 60,
                        ),
                    )

            assertTrue(
                result is ValidationResult.Valid,
            )
        }
    }

    @Test
    fun intervalSchedule_rejectsUnsupportedHourValue() {
        val result =
            CarePlanValidation
                .validateSchedulePattern(
                    IntervalSchedule(
                        intervalHours = 7,
                        anchorMinuteOfDay =
                            8 * 60,
                    ),
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )
    }

    @Test
    fun intervalSchedule_rejectsInvalidAnchorMinute() {
        val result =
            CarePlanValidation
                .validateSchedulePattern(
                    IntervalSchedule(
                        intervalHours = 8,
                        anchorMinuteOfDay = -1,
                    ),
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )
    }

    @Test
    fun intervalPatternDerivesSortedDailyTimesFromAnchor() {
        assertEquals(
            listOf(
                60,
                9 * 60,
                17 * 60,
            ),
            IntervalSchedule(
                intervalHours = 8,
                anchorMinuteOfDay = 60,
            ).representativeMinutesOfDay,
        )
    }

    @Test
    fun localTimeConversion_isStable() {
        val minuteOfDay =
            SchedulePatternRules
                .minuteOfDayFrom(
                    LocalTime.of(
                        23,
                        45,
                    ),
                )

        assertEquals(
            23 * 60 + 45,
            minuteOfDay,
        )

        assertEquals(
            LocalTime.of(
                23,
                45,
            ),
            SchedulePatternRules
                .localTimeFrom(
                    minuteOfDay,
                ),
        )
    }
}
