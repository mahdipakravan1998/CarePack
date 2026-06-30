package ir.carepack.domain.careplan

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarePlanValidationTest {

    @Test
    fun recipientName_isTrimmed() {
        val result =
            CarePlanValidation
                .validateRecipientName(
                    "  Sample recipient  ",
                )

        assertTrue(
            result is ValidationResult.Valid,
        )

        assertEquals(
            "Sample recipient",
            result.valueOrNull(),
        )
    }

    @Test
    fun blankRecipientName_isRejected() {
        val result =
            CarePlanValidation
                .validateRecipientName(
                    "   ",
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.RECIPIENT_NAME
                },
        )
    }

    @Test
    fun medicationText_isTrimmed() {
        val result =
            CarePlanValidation
                .validateMedicationText(
                    rawName =
                        "  Medication  ",
                    rawInstruction =
                        "  Instruction  ",
                )

        val value =
            result.valueOrNull()

        assertEquals(
            "Medication",
            value?.name,
        )

        assertEquals(
            "Instruction",
            value?.instruction,
        )
    }

    @Test
    fun blankMedicationFields_areRejected() {
        val result =
            CarePlanValidation
                .validateMedicationText(
                    rawName = " ",
                    rawInstruction = " ",
                )

        val errors =
            result.errorsOrEmpty()

        assertEquals(
            2,
            errors.size,
        )

        assertTrue(
            errors.any {
                it.field ==
                        CarePlanField.MEDICATION_NAME
            },
        )

        assertTrue(
            errors.any {
                it.field ==
                        CarePlanField.INSTRUCTION
            },
        )
    }

    @Test
    fun scheduleWithoutWeekday_isRejected() {
        val result =
            CarePlanValidation
                .validateSchedule(
                    weekdays = emptySet(),
                    minutesOfDay =
                        listOf(600),
                    startDate = null,
                    endDate = null,
                    rawZoneId =
                        "Asia/Tehran",
                )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.WEEKDAYS
                },
        )
    }

    @Test
    fun scheduleWithoutTime_isRejected() {
        val result =
            CarePlanValidation
                .validateSchedule(
                    weekdays =
                        setOf(
                            DayOfWeek.MONDAY,
                        ),
                    minutesOfDay = emptyList(),
                    startDate = null,
                    endDate = null,
                    rawZoneId =
                        "Asia/Tehran",
                )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.TIMES
                },
        )
    }

    @Test
    fun duplicateTimes_areRejected() {
        val result =
            CarePlanValidation
                .validateSchedule(
                    weekdays =
                        setOf(
                            DayOfWeek.MONDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            600,
                            600,
                        ),
                    startDate = null,
                    endDate = null,
                    rawZoneId =
                        "Asia/Tehran",
                )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.TIMES
                },
        )
    }

    @Test
    fun outOfRangeMinutes_areRejected() {
        val result =
            CarePlanValidation
                .validateSchedule(
                    weekdays =
                        setOf(
                            DayOfWeek.MONDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            -1,
                            1440,
                        ),
                    startDate = null,
                    endDate = null,
                    rawZoneId =
                        "Asia/Tehran",
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.TIMES
                },
        )
    }

    @Test
    fun reversedDates_areRejected() {
        val result =
            CarePlanValidation
                .validateSchedule(
                    weekdays =
                        setOf(
                            DayOfWeek.MONDAY,
                        ),
                    minutesOfDay =
                        listOf(600),
                    startDate =
                        LocalDate.parse(
                            "2026-06-25",
                        ),
                    endDate =
                        LocalDate.parse(
                            "2026-06-24",
                        ),
                    rawZoneId =
                        "Asia/Tehran",
                )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.END_DATE
                },
        )
    }

    @Test
    fun sameStartAndEndDate_isAccepted() {
        val date =
            LocalDate.parse(
                "2026-06-24",
            )

        val result =
            CarePlanValidation
                .validateSchedule(
                    weekdays =
                        setOf(
                            DayOfWeek.WEDNESDAY,
                        ),
                    minutesOfDay =
                        listOf(600),
                    startDate = date,
                    endDate = date,
                    rawZoneId =
                        "Asia/Tehran",
                )

        assertTrue(
            result is ValidationResult.Valid,
        )
    }

    @Test
    fun invalidZone_isRejected() {
        val result =
            CarePlanValidation
                .validateSchedule(
                    weekdays =
                        setOf(
                            DayOfWeek.MONDAY,
                        ),
                    minutesOfDay =
                        listOf(600),
                    startDate = null,
                    endDate = null,
                    rawZoneId =
                        "Not/A_Zone",
                )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.ZONE_ID
                },
        )
    }

    @Test
    fun validSchedule_isCanonicalized() {
        val result =
            CarePlanValidation
                .validateSchedule(
                    weekdays =
                        setOf(
                            DayOfWeek.FRIDAY,
                            DayOfWeek.MONDAY,
                        ),
                    minutesOfDay =
                        listOf(
                            1200,
                            300,
                        ),
                    startDate = null,
                    endDate = null,
                    rawZoneId =
                        "Asia/Tehran",
                )

        val value =
            result.valueOrNull()

        assertEquals(
            listOf(
                300,
                1200,
            ),
            value?.minutesOfDay,
        )

        assertEquals(
            "Asia/Tehran",
            value?.zoneId?.id,
        )
    }

    @Test
    fun scheduleTimeParser_acceptsTwentyFourHourTime() {
        val result =
            CarePlanValidation
                .validateScheduleTime(
                    rawValue = "08:45",
                    existingMinutesOfDay =
                        emptyList(),
                )

        assertEquals(
            525,
            result.valueOrNull(),
        )
    }

    @Test
    fun scheduleTimeParser_rejectsInvalidText() {
        val result =
            CarePlanValidation
                .validateScheduleTime(
                    rawValue = "8:45",
                    existingMinutesOfDay =
                        emptyList(),
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )
    }

    @Test
    fun scheduleTimeParser_rejectsDuplicateTime() {
        val result =
            CarePlanValidation
                .validateScheduleTime(
                    rawValue = "10:00",
                    existingMinutesOfDay =
                        listOf(600),
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.TIMES
                },
        )
    }

    @Test
    fun optionalDatesParser_acceptsBlankValues() {
        val result =
            CarePlanValidation
                .parseScheduleDates(
                    rawStartDate = "",
                    rawEndDate = "   ",
                )

        assertTrue(
            result is ValidationResult.Valid,
        )

        assertEquals(
            null,
            result.valueOrNull()?.startDate,
        )

        assertEquals(
            null,
            result.valueOrNull()?.endDate,
        )
    }

    @Test
    fun optionalDatesParser_rejectsInvalidDateText() {
        val result =
            CarePlanValidation
                .parseScheduleDates(
                    rawStartDate = "2026/06/24",
                    rawEndDate = "not-a-date",
                )

        assertTrue(
            result is ValidationResult.Invalid,
        )

        assertEquals(
            2,
            result.errorsOrEmpty().size,
        )
    }
}
