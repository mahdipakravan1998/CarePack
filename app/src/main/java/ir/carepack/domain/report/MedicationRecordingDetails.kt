package ir.carepack.domain.report

data class MedicationRecordingDetails(
    val medicationType: String,
    val dosageText: String,
    val doseUnit: String,
) {
    fun toDisplayText(): String = buildList {
            medicationType.trim().takeIf(String::isNotEmpty)?.let {
                add("نوع: $it")
            }
            dosageText.trim().takeIf(String::isNotEmpty)?.let {
                add("مقدار ثبت‌شده: $it")
            }
            doseUnit.trim().takeIf(String::isNotEmpty)?.let {
                add("واحد: $it")
            }
        }.joinToString(separator = "، ")
}
