package ir.carepack.reporting.share

import ir.carepack.domain.calendar.toPersianDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareDescriptorTest {

    @Test
    fun todaySevenDayAndThirtyDay_haveCorrectGenericMetadata() {
        val today = ShareDescriptor(ShareReportKind.TODAY)
        val sevenDay = ShareDescriptor(ShareReportKind.SEVEN_DAY)
        val thirtyDay = ShareDescriptor(ShareReportKind.THIRTY_DAY)

        assertEquals(
            "اشتراک‌گذاری گزارش امروز CarePack",
            today.chooserTitle,
        )
        assertEquals(
            "اشتراک‌گذاری گزارش ۷ روزه CarePack",
            sevenDay.chooserTitle,
        )
        assertEquals(
            "اشتراک‌گذاری گزارش ۳۰ روزه CarePack",
            thirtyDay.chooserTitle,
        )

        listOf(today, sevenDay, thirtyDay).forEach { descriptor ->
            assertFalse(descriptor.chooserTitle.contains("دارو"))
            assertFalse(descriptor.clipboardLabel.contains("نام فرد"))
        }
    }

    @Test
    fun persianNumberFormatting_hasParityForCountsAndRanges() {
        assertEquals("۰", "0".toPersianDigits())
        assertEquals("۷", "7".toPersianDigits())
        assertEquals("۳۰", "30".toPersianDigits())
        assertEquals("۱۲۳۴۵۶۷۸۹۰", "1234567890".toPersianDigits())
        assertTrue(
            ShareDescriptor(ShareReportKind.THIRTY_DAY)
                .chooserTitle
                .contains("۳۰"),
        )
    }
}
