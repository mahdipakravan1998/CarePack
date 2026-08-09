package ir.carepack.data.service

import ir.carepack.domain.report.*
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.ReportingOccurrenceRow
import ir.carepack.domain.model.CaregiverReportState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDateRangeSummaryService(
    private val database: CarePackDatabase,
) : DateRangeSummaryService {

    override fun observeSummary(
        range: ReportDateRange,
    ): Flow<DateRangeSummary> =
        database
            .reportingDao()
            .observeRange(
                startEpochDay =
                    range
                        .startDate
                        .toEpochDay(),
                endEpochDay =
                    range
                        .endDate
                        .toEpochDay(),
            )
            .map { rows ->
                RangeSummaryBuilder.build(
                    range = range,
                    entries =
                        rows.map(
                            ReportingOccurrenceRow::toRangeOccurrenceEntry,
                        ),
                )
            }

    override suspend fun getSummary(
        range: ReportDateRange,
    ): DateRangeSummary =
        RangeSummaryBuilder.build(
            range = range,
            entries =
                database
                    .reportingDao()
                    .getRange(
                        startEpochDay =
                            range
                                .startDate
                                .toEpochDay(),
                        endEpochDay =
                            range
                                .endDate
                                .toEpochDay(),
                    )
                    .map(
                        ReportingOccurrenceRow::toRangeOccurrenceEntry,
                    ),
        )
}

private fun ReportingOccurrenceRow.toRangeOccurrenceEntry():
        RangeOccurrenceEntry =
    RangeOccurrenceEntry(
        occurrenceId = occurrenceId,
        localDate =
            LocalDate.ofEpochDay(
                localEpochDay,
            ),
        localTime =
            minuteOfDay.toLocalTime(),
        zoneIdSnapshot =
            zoneIdSnapshot,
        scheduledAt =
            Instant.ofEpochMilli(
                scheduledAtEpochMillis,
            ),
        medicationName =
            medicationNameSnapshot,
        instruction =
            instructionSnapshot,
        medicationType =
            medicationTypeSnapshot,
        dosageText =
            dosageTextSnapshot,
        doseUnit =
            doseUnitSnapshot,
        reportState =
            reportState.toRangeReportState(),
    )

private fun String?.toRangeReportState():
        RangeOccurrenceReportState =
    when (this) {
        CaregiverReportState.GIVEN.name ->
            RangeOccurrenceReportState.GIVEN

        CaregiverReportState.NOT_GIVEN.name ->
            RangeOccurrenceReportState.NOT_GIVEN

        CaregiverReportState.UNKNOWN.name ->
            RangeOccurrenceReportState.UNKNOWN

        null ->
            RangeOccurrenceReportState.NO_REPORT

        else ->
            error(
                "Unsupported caregiver report state: $this",
            )
    }

private fun Int.toLocalTime(): LocalTime {
    require(this in 0 until MINUTES_PER_DAY)

    return LocalTime.of(
        this / MINUTES_PER_HOUR,
        this % MINUTES_PER_HOUR,
    )
}

private const val MINUTES_PER_HOUR =
    60

private const val MINUTES_PER_DAY =
    24 * MINUTES_PER_HOUR
