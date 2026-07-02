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
        generateSequence(
            firstDate,
        ) { currentDate ->
            currentDate
                .plusDays(1)
                .takeIf {
                    !it.isAfter(lastDate)
                }
        }
}

object OccurrenceGenerationWindow {
    const val RADIUS_DAYS: Long = 7L

    fun around(
        anchorDate: LocalDate,
    ): OccurrenceGenerationDateWindow =
        OccurrenceGenerationDateWindow(
            firstDate =
                anchorDate.minusDays(
                    RADIUS_DAYS,
                ),
            lastDate =
                anchorDate.plusDays(
                    RADIUS_DAYS,
                ),
        )

    fun exactForward(
        anchorDate: LocalDate,
        dayCount: Int,
    ): OccurrenceGenerationDateWindow {
        require(dayCount > 0)

        return OccurrenceGenerationDateWindow(
            firstDate =
                anchorDate,
            lastDate =
                anchorDate.plusDays(
                    dayCount.toLong() - 1L,
                ),
        )
    }
}
