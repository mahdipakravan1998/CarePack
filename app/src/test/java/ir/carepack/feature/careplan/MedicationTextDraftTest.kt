package ir.carepack.feature.careplan

import ir.carepack.domain.careplan.CarePlanField
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationTextDraftTest {

    @Test
    fun withField_changesOnlyTheRequestedMedicationTextField() {
        val original = MedicationTextDraft(
            medicationName = "A",
            instruction = "B",
            medicationType = "C",
            dosageText = "D",
            doseUnit = "E",
        )

        assertEquals(
            original.copy(dosageText = "new"),
            original.withField(CarePlanField.DOSAGE_TEXT, "new"),
        )
        assertEquals(
            original,
            original.withField(CarePlanField.WEEKDAYS, "ignored"),
        )
    }
}
