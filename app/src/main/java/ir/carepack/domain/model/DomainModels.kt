package ir.carepack.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class OccurrenceLifecycle {
    ACTIVE,
    CANCELLED,
}

enum class OccurrenceCancellationReason {
    SCHEDULE_REPLACED,
    MEDICATION_UPDATED,
    MEDICATION_STOPPED,
}

enum class CaregiverReportState {
    GIVEN,
    NOT_GIVEN,
    UNKNOWN,
}

enum class MedicationStatus {
    ACTIVE,
    STOPPED,
}

enum class TemporalPhase {
    UPCOMING,
    DUE,
    PAST,
}

enum class TodayEmptyState {
    NO_MEDICATIONS,
    NO_OCCURRENCES,
}

data class ScheduleDefinition(
    val scheduleVersionId: String,
    val scheduleSeriesId: String,
    val medicationId: String,
    val weekdayMask: Int,
    val minuteOfDay: Int,
    val zoneId: String,
    val effectiveFrom: Instant,
    val effectiveUntil: Instant?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val medicationNameSnapshot: String,
    val medicationInstructionSnapshot: String,
)

data class TodayItem(
    val occurrenceId: String,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val medicationName: String,
    val medicationInstruction: String,
    val lifecycle: OccurrenceLifecycle,
    val reportState: CaregiverReportState?,
    val scheduledAt: Instant = Instant.EPOCH,
    val temporalPhase: TemporalPhase =
        TemporalPhase.UPCOMING,
    val isOverdue: Boolean = false,
)

data class TodayModel(
    val localDate: LocalDate,
    val items: List<TodayItem>,
    val emptyState: TodayEmptyState?,
)

data class OccurrenceDetail(
    val occurrenceId: String,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val scheduledAt: Instant,
    val medicationName: String,
    val medicationInstruction: String,
    val lifecycle: OccurrenceLifecycle,
    val reportState: CaregiverReportState?,
    val zoneId: String = "UTC",
    val temporalPhase: TemporalPhase =
        TemporalPhase.UPCOMING,
    val isOverdue: Boolean = false,
    val cancellationReason:
    OccurrenceCancellationReason? = null,
)

data class HistoryItem(
    val occurrenceId: String,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val scheduledAt: Instant,
    val medicationName: String,
    val medicationInstruction: String,
    val lifecycle: OccurrenceLifecycle,
    val reportState: CaregiverReportState?,
    val temporalPhase: TemporalPhase,
    val isOverdue: Boolean,
)

data class HistoryDay(
    val localDate: LocalDate,
    val items: List<HistoryItem>,
)
