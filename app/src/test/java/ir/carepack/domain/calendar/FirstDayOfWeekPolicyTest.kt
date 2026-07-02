package ir.carepack.domain.calendar

import java.time.DayOfWeek
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstDayOfWeekPolicyTest {

    @Test
    fun systemDefault_usesSaturdayForTehranZone() {
        assertEquals(
            DayOfWeek.SATURDAY,
            FirstDayOfWeekPolicy.resolve(
                preference =
                    FirstDayOfWeekPreference
                        .SYSTEM_DEFAULT,
                zoneId =
                    ZoneId.of(
                        "Asia/Tehran",
                    ),
                locale =
                    Locale.US,
            ),
        )
    }

    @Test
    fun systemDefault_usesSaturdayForPersianLocale() {
        assertEquals(
            DayOfWeek.SATURDAY,
            FirstDayOfWeekPolicy.resolve(
                preference =
                    FirstDayOfWeekPreference
                        .SYSTEM_DEFAULT,
                zoneId =
                    ZoneId.of(
                        "Europe/Helsinki",
                    ),
                locale =
                    Locale(
                        "fa",
                        "FI",
                    ),
            ),
        )
    }

    @Test
    fun systemDefault_usesMondayOutsideIranianContext() {
        assertEquals(
            DayOfWeek.MONDAY,
            FirstDayOfWeekPolicy.resolve(
                preference =
                    FirstDayOfWeekPreference
                        .SYSTEM_DEFAULT,
                zoneId =
                    ZoneId.of(
                        "Europe/Helsinki",
                    ),
                locale =
                    Locale.US,
            ),
        )
    }

    @Test
    fun saturdayOverrideWinsOutsideIran() {
        assertEquals(
            DayOfWeek.SATURDAY,
            FirstDayOfWeekPolicy.resolve(
                preference =
                    FirstDayOfWeekPreference
                        .SATURDAY,
                zoneId =
                    ZoneId.of(
                        "Europe/Helsinki",
                    ),
                locale =
                    Locale.US,
            ),
        )
    }

    @Test
    fun mondayOverrideWinsInsideIran() {
        assertEquals(
            DayOfWeek.MONDAY,
            FirstDayOfWeekPolicy.resolve(
                preference =
                    FirstDayOfWeekPreference
                        .MONDAY,
                zoneId =
                    ZoneId.of(
                        "Asia/Tehran",
                    ),
                locale =
                    Locale(
                        "fa",
                        "IR",
                    ),
            ),
        )
    }
}
