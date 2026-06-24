package ir.carepack.domain.careplan

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarePlanValidationTest {

    @Test
    fun requiredText_isTrimmed() {
        val result =
            CarePlanValidation.normalizeRequiredText(
                "  Sample medication  ",
            )

        assertEquals(
            "Sample medication",
            result,
        )
    }

    @Test
    fun blankRequiredText_isRejected() {
        val result =
            CarePlanValidation.normalizeRequiredText(
                "   ",
            )

        assertNull(result)
    }

    @Test
    fun validZoneId_isAccepted() {
        val result =
            CarePlanValidation.parseZoneId(
                "Asia/Tehran",
            )

        assertEquals(
            "Asia/Tehran",
            result?.id,
        )
    }

    @Test
    fun invalidZoneId_isRejected() {
        val result =
            CarePlanValidation.parseZoneId(
                "Not/A_Real_Zone",
            )

        assertNull(result)
    }

    @Test
    fun weekdayMask_containsSelectedDay() {
        val mask =
            CarePlanValidation.weekdayMask(
                DayOfWeek.WEDNESDAY,
            )

        val wednesdayBit =
            1 shl (
                    DayOfWeek.WEDNESDAY.value - 1
                    )

        assertTrue(
            mask and wednesdayBit != 0,
        )
    }
}
