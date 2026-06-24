package ir.carepack.domain.careplan

import java.time.DayOfWeek
import java.time.ZoneId

internal object CarePlanValidation {
    fun normalizeRequiredText(
        rawValue: String,
    ): String? {
        return rawValue
            .trim()
            .takeIf(String::isNotEmpty)
    }

    fun parseZoneId(
        rawZoneId: String,
    ): ZoneId? {
        return runCatching {
            ZoneId.of(rawZoneId)
        }.getOrNull()
    }

    fun weekdayMask(
        dayOfWeek: DayOfWeek,
    ): Int {
        return 1 shl (dayOfWeek.value - 1)
    }
}