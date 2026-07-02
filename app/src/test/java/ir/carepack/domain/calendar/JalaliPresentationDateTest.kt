package ir.carepack.domain.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JalaliPresentationDateTest {

    @Test
    fun fromGregorian_convertsKnownNowruzDates() {
        assertEquals(
            JalaliPresentationDate(
                year = JalaliYear(1403),
                month = JalaliMonth(1),
                dayOfMonth =
                    JalaliDayOfMonth(1),
            ),
            JalaliPresentationDate.from(
                LocalDate.parse(
                    "2024-03-20",
                ),
            ),
        )

        assertEquals(
            JalaliPresentationDate(
                year = JalaliYear(1404),
                month = JalaliMonth(1),
                dayOfMonth =
                    JalaliDayOfMonth(1),
            ),
            JalaliPresentationDate.from(
                LocalDate.parse(
                    "2025-03-21",
                ),
            ),
        )

        assertEquals(
            JalaliPresentationDate(
                year = JalaliYear(1405),
                month = JalaliMonth(1),
                dayOfMonth =
                    JalaliDayOfMonth(1),
            ),
            JalaliPresentationDate.from(
                LocalDate.parse(
                    "2026-03-21",
                ),
            ),
        )
    }

    @Test
    fun toGregorian_convertsKnownNowruzDates() {
        assertEquals(
            LocalDate.parse(
                "2024-03-20",
            ),
            JalaliPresentationDate(
                year = JalaliYear(1403),
                month = JalaliMonth(1),
                dayOfMonth =
                    JalaliDayOfMonth(1),
            ).toLocalDate(),
        )

        assertEquals(
            LocalDate.parse(
                "2025-03-21",
            ),
            JalaliPresentationDate(
                year = JalaliYear(1404),
                month = JalaliMonth(1),
                dayOfMonth =
                    JalaliDayOfMonth(1),
            ).toLocalDate(),
        )

        assertEquals(
            LocalDate.parse(
                "2026-03-21",
            ),
            JalaliPresentationDate(
                year = JalaliYear(1405),
                month = JalaliMonth(1),
                dayOfMonth =
                    JalaliDayOfMonth(1),
            ).toLocalDate(),
        )
    }

    @Test
    fun nowruzBoundary_previousGregorianDayIsLastDayOfPreviousJalaliYear() {
        assertEquals(
            JalaliPresentationDate(
                year = JalaliYear(1404),
                month = JalaliMonth(12),
                dayOfMonth =
                    JalaliDayOfMonth(29),
            ),
            JalaliPresentationDate.from(
                LocalDate.parse(
                    "2026-03-20",
                ),
            ),
        )

        assertEquals(
            JalaliPresentationDate(
                year = JalaliYear(1405),
                month = JalaliMonth(1),
                dayOfMonth =
                    JalaliDayOfMonth(1),
            ),
            JalaliPresentationDate.from(
                LocalDate.parse(
                    "2026-03-21",
                ),
            ),
        )
    }

    @Test
    fun roundTrip_preservesRepresentativeDatesAcrossMultipleYears() {
        val dates =
            listOf(
                LocalDate.parse(
                    "2023-03-21",
                ),
                LocalDate.parse(
                    "2024-02-29",
                ),
                LocalDate.parse(
                    "2024-03-20",
                ),
                LocalDate.parse(
                    "2025-03-20",
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
                LocalDate.parse(
                    "2030-12-31",
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
        assertEquals(
            "1405/04/03",
            JalaliPresentationDate(
                year = JalaliYear(1405),
                month = JalaliMonth(4),
                dayOfMonth =
                    JalaliDayOfMonth(3),
            ).formatNumeric(),
        )
    }

    @Test
    fun parseNumeric_acceptsSlashDashPersianDigitsAndArabicDigits() {
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

        assertEquals(
            LocalDate.parse(
                "2026-06-24",
            ),
            JalaliPresentationDate
                .parseNumeric(
                    "۱۴۰۵/۰۴/۰۳",
                )
                ?.toLocalDate(),
        )

        assertEquals(
            LocalDate.parse(
                "2026-06-24",
            ),
            JalaliPresentationDate
                .parseNumeric(
                    "١٤٠٥-٠٤-٠٣",
                )
                ?.toLocalDate(),
        )
    }

    @Test
    fun parseNumeric_acceptsMixedSingleDigitMonthAndDay() {
        assertEquals(
            LocalDate.parse(
                "2026-06-24",
            ),
            JalaliPresentationDate
                .parseNumeric(
                    "۱۴۰۵/4/۳",
                )
                ?.toLocalDate(),
        )
    }

    @Test
    fun parseNumeric_rejectsGregorianLookingUserInput() {
        assertNull(
            JalaliPresentationDate
                .parseNumeric(
                    "2026/06/24",
                ),
        )

        assertNull(
            JalaliPresentationDate
                .parseNumeric(
                    "۲۰۲۶/۰۶/۲۴",
                ),
        )
    }

    @Test
    fun parseNumeric_rejectsNonLeapEsfandThirty() {
        assertNull(
            JalaliPresentationDate
                .parseNumeric(
                    "1405/12/30",
                ),
        )
    }

    @Test
    fun leapYear_supportsEsfandThirty() {
        assertTrue(
            JalaliPresentationDate
                .isLeapYear(
                    1403,
                ),
        )

        assertEquals(
            LocalDate.parse(
                "2025-03-20",
            ),
            JalaliPresentationDate
                .parseNumeric(
                    "1403/12/30",
                )
                ?.toLocalDate(),
        )
    }

    @Test
    fun lengthOfMonth_matchesJalaliMonthRules() {
        assertEquals(
            31,
            JalaliPresentationDate
                .lengthOfMonth(
                    year = 1405,
                    month = 1,
                ),
        )

        assertEquals(
            30,
            JalaliPresentationDate
                .lengthOfMonth(
                    year = 1405,
                    month = 7,
                ),
        )

        assertEquals(
            29,
            JalaliPresentationDate
                .lengthOfMonth(
                    year = 1405,
                    month = 12,
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
