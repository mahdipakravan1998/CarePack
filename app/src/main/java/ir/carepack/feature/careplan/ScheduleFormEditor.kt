package ir.carepack.feature.careplan

import ir.carepack.core.time.ZoneProvider
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class ScheduleFormUpdate(
    val schedule: ScheduleFormUiState,
    val previewAnchorDate: LocalDate,
)

internal enum class SchedulePreviewTimestampPolicy {
    SAME_AS_EFFECTIVE_FROM,
    FRESH_CLOCK_READ,
}

internal class ScheduleFormEditor(
    private val clock: Clock,
    zoneProvider: ZoneProvider,
    private val previewTimestampPolicy: SchedulePreviewTimestampPolicy =
        SchedulePreviewTimestampPolicy.SAME_AS_EFFECTIVE_FROM,
) {
    val currentZone: ZoneId = zoneProvider.currentZone()

    fun toggleWeekday(
        schedule: ScheduleFormUiState,
        day: DayOfWeek,
    ): ScheduleFormUpdate = update(schedule) { it.toggleWeekday(day) }

    fun selectInputMode(
        schedule: ScheduleFormUiState,
        mode: ScheduleInputMode,
    ): ScheduleFormUpdate = update(schedule) { it.withInputMode(mode) }

    fun changeTimeDraft(
        schedule: ScheduleFormUiState,
        value: String,
    ): ScheduleFormUpdate = update(schedule) { it.withTimeDraft(value) }

    fun addTime(schedule: ScheduleFormUiState): ScheduleFormUpdate =
        update(schedule, ScheduleFormUiState::addDraftTime)

    fun removeTime(
        schedule: ScheduleFormUiState,
        minuteOfDay: Int,
    ): ScheduleFormUpdate = update(schedule) { it.removeTime(minuteOfDay) }

    fun selectIntervalHours(
        schedule: ScheduleFormUiState,
        hours: Int,
    ): ScheduleFormUpdate = update(schedule) { it.withIntervalHours(hours) }

    fun changeIntervalAnchor(
        schedule: ScheduleFormUiState,
        value: String,
    ): ScheduleFormUpdate = update(schedule) { it.withIntervalAnchorDraft(value) }

    fun changeStartDate(
        schedule: ScheduleFormUiState,
        value: String,
    ): ScheduleFormUpdate = update(schedule) { it.withStartDate(value) }

    fun changeEndDate(
        schedule: ScheduleFormUiState,
        value: String,
    ): ScheduleFormUpdate = update(schedule) { it.withEndDate(value) }

    fun stamp(schedule: ScheduleFormUiState): ScheduleFormUpdate = update(schedule) { it }

    fun currentEffectiveFrom(): Instant = clock.instant()

    fun currentPreviewDate(): LocalDate = currentEffectiveFrom().atZone(currentZone).toLocalDate()

    private fun update(
        schedule: ScheduleFormUiState,
        transform: (ScheduleFormUiState) -> ScheduleFormUiState,
    ): ScheduleFormUpdate {
        val effectiveFrom = currentEffectiveFrom()
        return ScheduleFormUpdate(
            schedule = transform(schedule).withPreviewEffectiveFrom(effectiveFrom),
            previewAnchorDate = when (previewTimestampPolicy) {
                    SchedulePreviewTimestampPolicy.SAME_AS_EFFECTIVE_FROM ->
                        effectiveFrom.atZone(currentZone).toLocalDate()
                    SchedulePreviewTimestampPolicy.FRESH_CLOCK_READ ->
                        currentPreviewDate()
                },
        )
    }
}
