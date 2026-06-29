package ir.carepack.domain.report

import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.ReportingOccurrenceRow
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceLifecycle
import java.time.LocalDate
import java.time.LocalTime

internal class RoomTodayReportFormatter(
    private val database:
    CarePackDatabase,
    private val textBuilder:
    TodayReportTextBuilder =
        TodayReportTextBuilder(),
) : TodayReportFormatter {

    override suspend fun createTodayReport(
        date: LocalDate,
        includeRecipientName: Boolean,
    ): TodayReportText {
        val recipientName =
            if (includeRecipientName) {
                database
                    .careRecipientDao()
                    .getSingleton()
                    ?.displayName
            } else {
                null
            }

        val entries =
            database
                .reportingDao()
                .getTodayForReport(
                    localDateEpochDay =
                        date.toEpochDay(),
                )
                .asSequence()
                .filter { row ->
                    row.lifecycle !=
                            OccurrenceLifecycle
                                .CANCELLED
                                .name
                }
                .map(
                    ReportingOccurrenceRow::toTodayReportEntry,
                )
                .toList()

        return textBuilder.build(
            date = date,
            recipientName =
                recipientName,
            entries = entries,
        )
    }
}

private fun ReportingOccurrenceRow.toTodayReportEntry():
        TodayReportEntry =
    TodayReportEntry(
        occurrenceId =
            occurrenceId,
        localTime =
            minuteOfDay.toLocalTime(),
        medicationName =
            medicationNameSnapshot,
        medicationInstruction =
            medicationInstructionSnapshot,
        reportState =
            reportState?.let(
                CaregiverReportState::valueOf,
            ),
    )

private fun Int.toLocalTime():
        LocalTime {
    require(
        this in 0 until MINUTES_PER_DAY,
    )

    return LocalTime.of(
        this / MINUTES_PER_HOUR,
        this % MINUTES_PER_HOUR,
    )
}

private const val MINUTES_PER_HOUR =
    60

private const val MINUTES_PER_DAY =
    24 * MINUTES_PER_HOUR
