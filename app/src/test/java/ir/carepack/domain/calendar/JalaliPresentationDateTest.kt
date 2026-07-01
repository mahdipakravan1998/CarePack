package ir.carepack.domain.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JalaliPresentationDateTest {

    @Test
    fun fromGregorian_convertsKnownNowruzDate() {
        val jalali =
            JalaliPresentationDate.from(
                LocalDate.parse(
                    "2026-03-21",
                ),
            )

        assertEquals(
            1405,
            jalali.year.value,
        )

        assertEquals(
            1,
            jalali.month.value,
        )

        assertEquals(
            1,
            jalali.dayOfMonth.value,
        )
    }

    @Test
    fun toGregorian_convertsKnownNowruzDate() {
        val gregorian =
            JalaliPresentationDate(
                year =
                    JalaliYear(1405),
                month =
                    JalaliMonth(1),
                dayOfMonth =
                    JalaliDayOfMonth(1),
            ).toLocalDate()

        assertEquals(
            LocalDate.parse(
                "2026-03-21",
            ),
            gregorian,
        )
    }

    @Test
    fun roundTrip_preservesRepresentativeDates() {
        val dates =
            listOf(
                LocalDate.parse(
                    "2026-03-21",
                ),
                LocalDate.parse(
                    "2026-06-24",
                ),
                LocalDate.parse(
                    "2027-03-20",
                ),
                LocalDate.parse(
                    "2028-02-29",
                ),
            )

        dates.forEach { date ->
            assertEquals(
                date,
                JalaliPresentationDate
                    .from(date)
                    .toLocalDate(),
            )
        }
    }

    @Test
    fun numericFormatting_usesPersianCalendarOrder() {
        val formatted =
            JalaliPresentationDate(
                year =
                    JalaliYear(1405),
                month =
                    JalaliMonth(4),
                dayOfMonth =
                    JalaliDayOfMonth(3),
            ).formatNumeric()

        assertEquals(
            "1405/04/03",
            formatted,
        )
    }

    @Test
    fun parseNumeric_acceptsSlashAndDashSeparators() {
        assertEquals(
            LocalDate.parse(
                "2026-06-24",
            ),
            JalaliPresentationDate
                .parseNumeric(
                    "1405/04/03",
                )
                ?.toLocalDate(),
        )

        assertEquals(
            LocalDate.parse(
                "2026-06-24",
            ),
            JalaliPresentationDate
                .parseNumeric(
                    "1405-04-03",
                )
                ?.toLocalDate(),
        )
    }

    @Test
    fun parseNumeric_rejectsInvalidDate() {
        assertNull(
            JalaliPresentationDate
                .parseNumeric(
                    "1405/12/30",
                ),
        )
    }

    @Test
    fun leapYear_supportsLastDayOfEsfand() {
        assertTrue(
            JalaliPresentationDate
                .isLeapYear(
                    1403,
                ),
        )

        assertEquals(
            30,
            JalaliPresentationDate
                .lengthOfMonth(
                    year = 1403,
                    month = 12,
                ),
        )
    }
}
