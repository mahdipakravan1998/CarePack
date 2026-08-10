package ir.carepack.domain.report

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationRecordingDetailsTest {

    @Test
    fun displayText_preservesLabelsOrderTrimmingAndSeparator() {
        assertEquals(
            "نوع: قرص، مقدار ثبت‌شده: ۲، واحد: عدد",
            MedicationRecordingDetails(
                medicationType = " قرص ",
                dosageText = "۲",
                doseUnit = "عدد",
            ).toDisplayText(),
        )
    }

    @Test
    fun displayText_omitsBlankValues() {
        assertEquals(
            "واحد: میلی‌لیتر",
            MedicationRecordingDetails(
                medicationType = " ",
                dosageText = "",
                doseUnit = " میلی‌لیتر ",
            ).toDisplayText(),
        )
    }
}
