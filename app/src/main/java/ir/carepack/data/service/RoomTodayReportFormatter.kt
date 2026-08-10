package ir.carepack.data.service

import ir.carepack.core.time.requireLocalTime
import ir.carepack.domain.report.*
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.ReportingOccurrenceRow
import ir.carepack.domain.model.CaregiverReportState
import java.time.LocalDate

internal class RoomTodayReportFormatter(
    private val database: CarePackDatabase,
    private val textBuilder: TodayReportTextBuilder =
        TodayReportTextBuilder(),
) : TodayReportFormatter {

    override suspend fun createTodayReport(
        date: LocalDate,
        includeRecipientName: Boolean,
    ): TodayReportText {
        val recipientName = if (includeRecipientName) {
                database.careRecipientDao()
                    .getSingleton()?.displayName
            } else {
                null
            }

        val entries = database
                .reportingDao().getTodayForReport(
                    localEpochDay = date.toEpochDay(),
                ).map(
                    ReportingOccurrenceRow::toTodayReportEntry,
                )

        return textBuilder.build(
            date = date,
            recipientName = recipientName,
            entries = entries,
        )
    }
}

private fun ReportingOccurrenceRow.toTodayReportEntry(): TodayReportEntry =
    TodayReportEntry(
        occurrenceId = occurrenceId,
        localTime = minuteOfDay.requireLocalTime(),
        medicationName = medicationNameSnapshot,
        medicationInstruction = instructionSnapshot,
        reportState = reportState?.let(
                CaregiverReportState::valueOf,
            ),
        medicationType = medicationTypeSnapshot,
        dosageText = dosageTextSnapshot,
        doseUnit = doseUnitSnapshot,
    )
