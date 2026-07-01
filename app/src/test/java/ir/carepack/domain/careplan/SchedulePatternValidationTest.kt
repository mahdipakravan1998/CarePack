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

        assertEquals(
            CarePlanField.TIMES,
            (
                    result as
                            ValidationResult.Invalid
                    ).errors.single().field,
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
    fun intervalSchedule_acceptsAllowedPreset() {
        val result =
            CarePlanValidation
                .validateSchedulePattern(
                    IntervalSchedule(
                        intervalHours = 8,
                        anchorMinuteOfDay =
                            7 * 60,
                    ),
                )

        assertTrue(
            result is ValidationResult.Valid,
        )
    }

    @Test
    fun intervalSchedule_rejectsUnsupportedInterval() {
        val result =
            CarePlanValidation
                .validateSchedulePattern(
                    IntervalSchedule(
                        intervalHours = 5,
                        anchorMinuteOfDay =
                            7 * 60,
                    ),
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )

        assertEquals(
            CarePlanField.TIMES,
            (
                    result as
                            ValidationResult.Invalid
                    ).errors.single().field,
        )
    }

    @Test
    fun intervalSchedule_rejectsInvalidAnchorTime() {
        val result =
            CarePlanValidation
                .validateSchedulePattern(
                    IntervalSchedule(
                        intervalHours = 8,
                        anchorMinuteOfDay =
                            24 * 60,
                    ),
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )
    }

    @Test
    fun intervalSchedule_calculatesRepresentativeTimesWithinOneDay() {
        assertEquals(
            listOf(
                7 * 60,
                15 * 60,
                23 * 60,
            ),
            SchedulePatternRules
                .intervalMinutesWithinDay(
                    intervalHours = 8,
                    anchorMinuteOfDay =
                        7 * 60,
                ),
        )
    }

    @Test
    fun minuteOfDayRoundTripsLocalTime() {
        val localTime =
            LocalTime.of(
                14,
                30,
            )

        val minuteOfDay =
            SchedulePatternRules
                .minuteOfDayFrom(
                    localTime,
                )

        assertEquals(
            14 * 60 + 30,
            minuteOfDay,
        )

        assertEquals(
            localTime,
            SchedulePatternRules
                .localTimeFrom(
                    minuteOfDay,
                ),
        )
    }
}
