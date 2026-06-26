package ir.carepack.domain.careplan

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarePlanValidationContractTest {

    @Test
    fun medicationNameAtMaximum_isAccepted() {
        val value =
            "د".repeat(
                CarePlanLimits
                    .MEDICATION_NAME_MAX_LENGTH,
            )

        val result =
            CarePlanValidation
                .validateMedicationText(
                    rawName = value,
                    rawInstruction = "دستور",
                )

        assertTrue(
            result is ValidationResult.Valid,
        )

        assertEquals(
            value,
            result.valueOrNull()?.name,
        )
    }

    @Test
    fun medicationNameAboveMaximum_isRejected() {
        val value =
            "د".repeat(
                CarePlanLimits
                    .MEDICATION_NAME_MAX_LENGTH + 1,
            )

        val result =
            CarePlanValidation
                .validateMedicationText(
                    rawName = value,
                    rawInstruction = "دستور",
                )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.MEDICATION_NAME
                },
        )
    }

    @Test
    fun instructionAtMaximum_isAccepted() {
        val value =
            "ت".repeat(
                CarePlanLimits
                    .INSTRUCTION_MAX_LENGTH,
            )

        val result =
            CarePlanValidation
                .validateMedicationText(
                    rawName = "دارو",
                    rawInstruction = value,
                )

        assertTrue(
            result is ValidationResult.Valid,
        )

        assertEquals(
            value,
            result.valueOrNull()?.instruction,
        )
    }

    @Test
    fun instructionAboveMaximum_isRejected() {
        val value =
            "ت".repeat(
                CarePlanLimits
                    .INSTRUCTION_MAX_LENGTH + 1,
            )

        val result =
            CarePlanValidation
                .validateMedicationText(
                    rawName = "دارو",
                    rawInstruction = value,
                )

        assertTrue(
            result.errorsOrEmpty()
                .any {
                    it.field ==
                            CarePlanField.INSTRUCTION
                },
        )
    }

    @Test
    fun validSevenBitWeekdayMasks_areAccepted() {
        DayOfWeek.entries.forEach { day ->
            val mask =
                1 shl (day.value - 1)

            assertTrue(
                CarePlanValidation
                    .isValidWeekdayMask(mask),
            )
        }

        assertTrue(
            CarePlanValidation
                .isValidWeekdayMask(
                    0b1111111,
                ),
        )
    }

    @Test
    fun zeroAndOutsideSevenBitMasks_areRejected() {
        assertFalse(
            CarePlanValidation
                .isValidWeekdayMask(0),
        )

        assertFalse(
            CarePlanValidation
                .isValidWeekdayMask(
                    0b10000000,
                ),
        )

        assertFalse(
            CarePlanValidation
                .isValidWeekdayMask(
                    0b11111111,
                ),
        )
    }

    @Test
    fun supplementaryUnicodeCharacters_areCountedAsCharacters() {
        val value =
            "😀".repeat(
                CarePlanLimits
                    .RECIPIENT_NAME_MAX_LENGTH,
            )

        val accepted =
            CarePlanValidation
                .validateRecipientName(value)

        assertTrue(
            accepted is ValidationResult.Valid,
        )

        val rejected =
            CarePlanValidation
                .validateRecipientName(
                    value + "😀",
                )

        assertTrue(
            rejected is ValidationResult.Invalid,
        )
    }
}
