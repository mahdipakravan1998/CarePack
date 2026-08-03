package ir.carepack.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JalaliMonthModelTest {

    @Test
    fun allTwelvePersianMonthNames_areExposedInOrder() {
        val names =
            (1..12).map { month ->
                JalaliYearMonth(
                    year = 1405,
                    month = month,
                ).monthName
            }

        assertEquals(
            listOf(
                "فروردین",
                "اردیبهشت",
                "خرداد",
                "تیر",
                "مرداد",
                "شهریور",
                "مهر",
                "آبان",
                "آذر",
                "دی",
                "بهمن",
                "اسفند",
            ),
            names,
        )
    }

    @Test
    fun monthLengths_followJalaliRules() {
        (1..6).forEach { month ->
            assertEquals(
                31,
                JalaliYearMonth(
                    year = 1405,
                    month = month,
                ).lengthOfMonth,
            )
        }

        (7..11).forEach { month ->
            assertEquals(
                30,
                JalaliYearMonth(
                    year = 1405,
                    month = month,
                ).lengthOfMonth,
            )
        }

        assertEquals(
            30,
            JalaliYearMonth(
                year = 1403,
                month = 12,
            ).lengthOfMonth,
        )

        assertEquals(
            29,
            JalaliYearMonth(
                year = 1405,
                month = 12,
            ).lengthOfMonth,
        )
    }

    @Test
    fun previousAndNext_crossJalaliYearBoundary() {
        assertEquals(
            JalaliYearMonth(
                year = 1404,
                month = 12,
            ),
            JalaliYearMonth(
                year = 1405,
                month = 1,
            ).previous(),
        )

        assertEquals(
            JalaliYearMonth(
                year = 1406,
                month = 1,
            ),
            JalaliYearMonth(
                year = 1405,
                month = 12,
            ).next(),
        )
    }

    @Test
    fun nowruzTransition_mapsAdjacentGregorianDatesToDifferentJalaliYears() {
        val esfandEnd =
            JalaliYearMonth.from(
                LocalDate.parse(
                    "2025-03-20",
                ),
            )

        val nowruz =
            JalaliYearMonth.from(
                LocalDate.parse(
                    "2025-03-21",
                ),
            )

        assertEquals(
            JalaliYearMonth(
                year = 1403,
                month = 12,
            ),
            esfandEnd,
        )

        assertEquals(
            JalaliYearMonth(
                year = 1404,
                month = 1,
            ),
            nowruz,
        )
    }

    @Test
    fun saturdayFirstModel_hasExpectedWeekdayOrderAndRectangularGrid() {
        val model =
            model(
                displayedMonth =
                    JalaliYearMonth(
                        year = 1404,
                        month = 1,
                    ),
                firstDayOfWeek =
                    DayOfWeek.SATURDAY,
            )

        assertEquals(
            listOf(
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            model.weekdayOrder,
        )

        assertTrue(
            model.weeks.all {
                it.size == 7
            },
        )

        assertEquals(
            model.firstVisibleDate,
            model.cells.first().localDate,
        )

        assertEquals(
            model.lastVisibleDate,
            model.cells.last().localDate,
        )
    }

    @Test
    fun mondayFirstModel_hasExpectedWeekdayOrder() {
        val model =
            model(
                displayedMonth =
                    JalaliYearMonth(
                        year = 1404,
                        month = 1,
                    ),
                firstDayOfWeek =
                    DayOfWeek.MONDAY,
            )

        assertEquals(
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY,
            ),
            model.weekdayOrder,
        )
    }

    @Test
    fun modelSupportsBothFiveWeekAndSixWeekLayouts() {
        val models =
            (1..12).map { month ->
                model(
                    displayedMonth =
                        JalaliYearMonth(
                            year = 1404,
                            month = month,
                        ),
                    firstDayOfWeek =
                        DayOfWeek.SATURDAY,
                )
            }

        assertTrue(
            models.any {
                it.weeks.size == 5
            },
        )

        assertTrue(
            models.any {
                it.weeks.size == 6
            },
        )
    }

    @Test
    fun todaySelectedAndAdjacentMonthCells_areMarkedIndependently() {
        val today =
            LocalDate.parse(
                "2025-03-21",
            )

        val selected =
            today.plusDays(10)

        val model =
            JalaliMonthModelFactory.create(
                displayedMonth =
                    JalaliYearMonth(
                        year = 1404,
                        month = 1,
                    ),
                today = today,
                selectedDate = selected,
                firstDayOfWeek =
                    DayOfWeek.SATURDAY,
            )

        assertEquals(
            today,
            model.cells.single {
                it.isToday
            }.localDate,
        )

        assertEquals(
            selected,
            model.cells.single {
                it.isSelected
            }.localDate,
        )

        assertTrue(
            model.cells.any {
                !it.belongsToDisplayedMonth
            },
        )

        assertFalse(
            model.cells
                .filter {
                    !it.belongsToDisplayedMonth
                }
                .all {
                    it.isToday || it.isSelected
                },
        )
    }

    @Test
    fun displayedMonthContainsEveryDayExactlyOnce() {
        val displayedMonth =
            JalaliYearMonth(
                year = 1403,
                month = 12,
            )

        val model =
            model(
                displayedMonth = displayedMonth,
                firstDayOfWeek =
                    DayOfWeek.SATURDAY,
            )

        val displayedDays =
            model.cells
                .filter {
                    it.belongsToDisplayedMonth
                }
                .map {
                    it.jalaliDate
                        .dayOfMonth
                        .value
                }

        assertEquals(
            (1..30).toList(),
            displayedDays,
        )
    }

    private fun model(
        displayedMonth: JalaliYearMonth,
        firstDayOfWeek: DayOfWeek,
    ): JalaliMonthModel =
        JalaliMonthModelFactory.create(
            displayedMonth = displayedMonth,
            today =
                LocalDate.parse(
                    "2025-03-21",
                ),
            selectedDate =
                LocalDate.parse(
                    "2025-03-21",
                ),
            firstDayOfWeek = firstDayOfWeek,
        )
}
