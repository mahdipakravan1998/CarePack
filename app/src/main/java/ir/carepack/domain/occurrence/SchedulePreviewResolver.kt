package ir.carepack.domain.occurrence

import ir.carepack.domain.model.ScheduleDefinition
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.domain.schedule.SchedulePattern
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val DEFAULT_SCHEDULE_PREVIEW_DAY_COUNT = 14

data class SchedulePreviewRequest(
    val weekdays: Set<DayOfWeek>,
    val schedulePattern: SchedulePattern,
    val zoneId: String,
    val effectiveFrom: Instant,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val anchorDate: LocalDate? = null,
    val dayCount: Int = DEFAULT_SCHEDULE_PREVIEW_DAY_COUNT,
) {
    init {
        require(dayCount > 0)
    }
}

data class SchedulePreviewOccurrence(
    val localDate: LocalDate,
    val dayOfWeek: DayOfWeek,
    val minuteOfDay: Int,
    val zoneId: String,
    val scheduledAt: Instant,
)

class SchedulePreviewResolver(
    private val candidateResolver:
    OccurrenceCandidateResolver = OccurrenceCandidateResolver(),
) {

    fun resolve(
        request: SchedulePreviewRequest,
    ): List<SchedulePreviewOccurrence> {
        val zone =
            runCatching {
                ZoneId.of(
                    request.zoneId,
                )
            }.getOrNull() ?: return emptyList()

        val minutesOfDay =
            request
                .schedulePattern
                .representativeMinutesOfDay
                .distinct()
                .sorted()

        if (
            request.weekdays.isEmpty() ||
            minutesOfDay.isEmpty()
        ) {
            return emptyList()
        }

        val effectiveLocalDate =
            request
                .effectiveFrom
                .atZone(
                    zone,
                )
                .toLocalDate()

        val previewAnchorDate =
            request
                .anchorDate
                ?.takeIf {
                    it.isAfter(
                        effectiveLocalDate,
                    )
                }
                ?: effectiveLocalDate

        val window =
            OccurrenceGenerationWindow
                .exactForward(
                    anchorDate =
                        previewAnchorDate,
                    dayCount =
                        request.dayCount,
                )

        return window
            .dates()
            .flatMap { localDate ->
                minutesOfDay
                    .asSequence()
                    .mapNotNull { minuteOfDay ->
                        candidateResolver.resolve(
                            definition =
                                request.toDefinition(
                                    minuteOfDay,
                                ),
                            anchorDate =
                                localDate,
                        )
                    }
            }
            .sortedWith(
                compareBy(
                    { it.scheduledAt },
                    { it.localDate },
                    { it.minuteOfDay },
                ),
            )
            .map { candidate ->
                SchedulePreviewOccurrence(
                    localDate =
                        candidate.localDate,
                    dayOfWeek =
                        candidate
                            .localDate
                            .dayOfWeek,
                    minuteOfDay =
                        candidate.minuteOfDay,
                    zoneId =
                        candidate.zoneId,
                    scheduledAt =
                        candidate.scheduledAt,
                )
            }
            .toList()
    }

    private fun SchedulePreviewRequest.toDefinition(
        minuteOfDay: Int,
    ): ScheduleDefinition =
        ScheduleDefinition(
            scheduleVersionId =
                PREVIEW_VERSION_ID,
            scheduleSeriesId =
                PREVIEW_SERIES_ID,
            medicationId =
                PREVIEW_MEDICATION_ID,
            weekdayMask =
                weekdays.toWeekdayMask(),
            minuteOfDay =
                minuteOfDay,
            schedulePattern =
                schedulePattern.forPersistedDefinitionMinute(
                    minuteOfDay,
                ),
            zoneId =
                zoneId,
            effectiveFrom =
                effectiveFrom,
            effectiveUntil = null,
            startDate =
                startDate,
            endDate =
                endDate,
            medicationNameSnapshot =
                "",
            medicationInstructionSnapshot =
                "",
        )

    private fun SchedulePattern.forPersistedDefinitionMinute(
        minuteOfDay: Int,
    ): SchedulePattern =
        when (this) {
            is FixedTimeSchedule ->
                FixedTimeSchedule(
                    minutesOfDay =
                        listOf(
                            minuteOfDay,
                        ),
                )

            is IntervalSchedule ->
                this
        }

    private fun Set<DayOfWeek>.toWeekdayMask(): Int =
        fold(0) { mask, day ->
            mask or
                    (1 shl (day.value - 1))
        }

    private companion object {
        const val PREVIEW_VERSION_ID = "preview-version"
        const val PREVIEW_SERIES_ID = "preview-series"
        const val PREVIEW_MEDICATION_ID = "preview-medication"
    }
}
