package ir.carepack.domain.calendar

import java.time.DayOfWeek
import java.time.ZoneId
import java.util.Locale

enum class FirstDayOfWeekPreference {
    SYSTEM_DEFAULT,
    SATURDAY,
    MONDAY,
}

object FirstDayOfWeekPolicy {
    fun resolve(
        preference: FirstDayOfWeekPreference,
        zoneId: ZoneId,
        locale: Locale,
    ): DayOfWeek =
        when (preference) {
            FirstDayOfWeekPreference.SATURDAY ->
                DayOfWeek.SATURDAY

            FirstDayOfWeekPreference.MONDAY ->
                DayOfWeek.MONDAY

            FirstDayOfWeekPreference.SYSTEM_DEFAULT ->
                if (usesIranianWeekStart(zoneId, locale)) {
                    DayOfWeek.SATURDAY
                } else {
                    DayOfWeek.MONDAY
                }
        }

    private fun usesIranianWeekStart(
        zoneId: ZoneId,
        locale: Locale,
    ): Boolean =
        zoneId.id == IRAN_ZONE_ID ||
                locale.country.equals(
                    IRAN_COUNTRY_CODE,
                    ignoreCase = true,
                ) ||
                locale.language.equals(
                    PERSIAN_LANGUAGE_CODE,
                    ignoreCase = true,
                )

    private const val IRAN_ZONE_ID = "Asia/Tehran"
    private const val IRAN_COUNTRY_CODE = "IR"
    private const val PERSIAN_LANGUAGE_CODE = "fa"
}
