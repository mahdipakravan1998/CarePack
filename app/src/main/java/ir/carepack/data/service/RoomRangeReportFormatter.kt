package ir.carepack.data.service

import ir.carepack.domain.report.*
import ir.carepack.data.local.CarePackDatabase
import java.time.LocalDate

class RoomRangeReportFormatter(
    private val database: CarePackDatabase,
    private val summaryService: DateRangeSummaryService,
    private val textBuilder: RangeReportTextBuilder =
        RangeReportTextBuilder(),
) : RangeReportFormatter {

    override suspend fun createRangeReport(
        period: RangeReportPeriod,
        today: LocalDate,
        includeRecipientName: Boolean,
    ): RangeReportContent {
        val summary = summaryService.getSummary(
                range = period.rangeEndingAt(
                        today,
                    ),
            )

        val recipientName = if (includeRecipientName) {
                database.careRecipientDao()
                    .getSingleton()?.displayName
            } else {
                null
            }

        return RangeReportContent(
            period = period,
            summary = summary,
            text = textBuilder.build(
                    period = period,
                    summary = summary,
                    recipientName = recipientName,
                ),
        )
    }
}
