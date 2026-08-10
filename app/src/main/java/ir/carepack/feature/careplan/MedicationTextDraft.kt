package ir.carepack.feature.careplan

import ir.carepack.domain.careplan.CarePlanField

data class MedicationTextDraft(
    val medicationName: String = "",
    val instruction: String = "",
    val medicationType: String = "",
    val dosageText: String = "",
    val doseUnit: String = "",
) {
    fun withField(field: CarePlanField, value: String): MedicationTextDraft = when (field) {
            CarePlanField.MEDICATION_NAME -> copy(medicationName = value)
            CarePlanField.INSTRUCTION -> copy(instruction = value)
            CarePlanField.MEDICATION_TYPE -> copy(medicationType = value)
            CarePlanField.DOSAGE_TEXT -> copy(dosageText = value)
            CarePlanField.DOSE_UNIT -> copy(doseUnit = value)
            else -> this
        }
}
