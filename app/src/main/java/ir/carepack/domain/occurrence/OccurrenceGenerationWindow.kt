package ir.carepack.domain.occurrence

import java.time.LocalDate

data class OccurrenceGenerationDateWindow(
    val firstDate: LocalDate,
    val lastDate: LocalDate,
) {
    init {
        require(!firstDate.isAfter(lastDate))
    }

    fun dates(): Sequence<LocalDate> =
        generateSequence(firstDate) { currentDate ->
            currentDate
                .plusDays(1L)
                .takeIf { nextDate ->
                    !nextDate.isAfter(lastDate)
                }
        }
}

object OccurrenceGenerationWindow {
    const val EDITING_RADIUS_DAYS: Long = 7L
    const val MAINTENANCE_LOOKBACK_DAYS: Long = 1L
    const val MAINTENANCE_FORWARD_DAYS: Long = 35L

    fun around(
        anchorDate: LocalDate,
    ): OccurrenceGenerationDateWindow =
        OccurrenceGenerationDateWindow(
            firstDate =
                anchorDate.minusDays(
                    EDITING_RADIUS_DAYS,
                ),
            lastDate =
                anchorDate.plusDays(
                    EDITING_RADIUS_DAYS,
                ),
        )

    fun maintenance(
        anchorDate: LocalDate,
    ): OccurrenceGenerationDateWindow =
        OccurrenceGenerationDateWindow(
            firstDate =
                anchorDate.minusDays(
                    MAINTENANCE_LOOKBACK_DAYS,
                ),
            lastDate =
                anchorDate.plusDays(
                    MAINTENANCE_FORWARD_DAYS,
                ),
        )

    fun exactForward(
        anchorDate: LocalDate,
        dayCount: Int,
    ): OccurrenceGenerationDateWindow {
        require(dayCount > 0)

        return OccurrenceGenerationDateWindow(
            firstDate = anchorDate,
            lastDate =
                anchorDate.plusDays(
                    dayCount.toLong() - 1L,
                ),
        )
    }
}
