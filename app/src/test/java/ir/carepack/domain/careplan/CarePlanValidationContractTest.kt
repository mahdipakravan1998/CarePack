package ir.carepack.domain.careplan

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarePlanValidationContractTest {

    @Test
    fun recipientNameLength120_isAccepted() {
        val value =
            "ر".repeat(120)

        val result =
            CarePlanValidation
                .validateRecipientName(value)

        assertTrue(
            result is ValidationResult.Valid,
        )

        assertEquals(
            value,
            result.valueOrNull(),
        )
    }

    @Test
    fun recipientNameLength121_isRejected() {
        val value =
            "ر".repeat(121)

        val result =
            CarePlanValidation
                .validateRecipientName(value)

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
    fun medicationNameLength120_isAccepted() {
        val value =
            "د".repeat(120)

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
    fun medicationNameLength121_isRejected() {
        val value =
            "د".repeat(121)

        val result =
            CarePlanValidation
                .validateMedicationText(
                    rawName = value,
                    rawInstruction = "دستور",
                )

        assertTrue(
            result is ValidationResult.Invalid,
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
    fun instructionLength1000_isAccepted() {
        val value =
            "ت".repeat(1000)

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
    fun instructionLength1001_isRejected() {
        val value =
            "ت".repeat(1001)

        val result =
            CarePlanValidation
                .validateMedicationText(
                    rawName = "دارو",
                    rawInstruction = value,
                )

        assertTrue(
            result is ValidationResult.Invalid,
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
    fun medicationRecordingFieldLimits_areEnforcedWithoutMedicalMeaningChecks() {
        val accepted =
            CarePlanValidation
                .validateMedicationText(
                    rawName =
                        "دارو",
                    rawInstruction =
                        "دستور",
                    rawMedicationType =
                        "ن".repeat(
                            CarePlanLimits
                                .MEDICATION_TYPE_MAX_LENGTH,
                        ),
                    rawDosageText =
                        "د".repeat(
                            CarePlanLimits
                                .DOSAGE_TEXT_MAX_LENGTH,
                        ),
                    rawDoseUnit =
                        "و".repeat(
                            CarePlanLimits
                                .DOSE_UNIT_MAX_LENGTH,
                        ),
                )

        assertTrue(
            accepted is ValidationResult.Valid,
        )

        val rejected =
            CarePlanValidation
                .validateMedicationText(
                    rawName =
                        "دارو",
                    rawInstruction =
                        "دستور",
                    rawMedicationType =
                        "ن".repeat(
                            CarePlanLimits
                                .MEDICATION_TYPE_MAX_LENGTH + 1,
                        ),
                    rawDosageText =
                        "د".repeat(
                            CarePlanLimits
                                .DOSAGE_TEXT_MAX_LENGTH + 1,
                        ),
                    rawDoseUnit =
                        "و".repeat(
                            CarePlanLimits
                                .DOSE_UNIT_MAX_LENGTH + 1,
                        ),
                )

        val fields =
            rejected
                .errorsOrEmpty()
                .map {
                    it.field
                }
                .toSet()

        assertEquals(
            setOf(
                CarePlanField.MEDICATION_TYPE,
                CarePlanField.DOSAGE_TEXT,
                CarePlanField.DOSE_UNIT,
            ),
            fields,
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
            "😀".repeat(120)

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
