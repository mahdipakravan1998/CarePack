package ir.carepack.domain.report

import ir.carepack.domain.calendar.PersianDateText
import ir.carepack.domain.calendar.toPersianDigits
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@JvmInline
value class RangeReportText(
    val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}


data class RangeReportContent(
    val period: RangeReportPeriod,
    val summary: DateRangeSummary,
    val text: RangeReportText,
)

interface RangeReportFormatter {

    suspend fun createRangeReport(
        period: RangeReportPeriod,
        today: LocalDate,
        includeRecipientName: Boolean,
    ): RangeReportContent
}

class RangeReportTextBuilder {

    fun build(
        period: RangeReportPeriod,
        summary: DateRangeSummary,
        recipientName: String?,
    ): RangeReportText {
        require(
            summary.range.dayCount == period.dayCount,
        )

        val header = buildList {
                add(
                    reportTitle(
                        period,
                    ),
                )

                add(
                    "$PERIOD_LABEL: " + period.dayCount
                                .toString().toPersianDigits() +
                            " روز",
                )

                add(
                    "$DATE_RANGE_LABEL: " + PersianDateText.formatNumeric(
                                summary.range
                                    .startDate,
                            ) + " تا " +
                            PersianDateText.formatNumeric(
                                summary.range
                                    .endDate,
                            ),
                )

                recipientName?.trim()
                    ?.takeIf(String::isNotEmpty)?.let { displayName ->
                        add(
                            "$RECIPIENT_LABEL: $displayName",
                        )
                    }
            }.joinToString(
                separator = "\n",
            )

        val summaryText = listOf(
                SUMMARY_TITLE,
                countLine(
                    label = TOTAL_LABEL,
                    count = summary
                            .totalOccurrenceCount,
                ),
                countLine(
                    label = GIVEN_LABEL,
                    count = summary.givenCount,
                ),
                countLine(
                    label = NOT_GIVEN_LABEL,
                    count = summary.notGivenCount,
                ),
                countLine(
                    label = UNKNOWN_LABEL,
                    count = summary.unknownCount,
                ),
                countLine(
                    label = NO_REPORT_LABEL,
                    count = summary.noReportCount,
                ),
            ).joinToString(
                separator = "\n",
            )

        val details = if (
                summary.totalOccurrenceCount == 0) {
                EMPTY_RANGE_MESSAGE
            } else {
                summary.daySummaries
                    .filter {
                        it.totalOccurrenceCount > 0
                    }.joinToString(
                        separator = "\n\n",
                    ) { daySummary ->
                        daySummary.toTextBlock()
                    }
            }

        return RangeReportText(
            value = listOf(
                    header,
                    summaryText,
                    "$DETAILS_TITLE\n$details",
                    DISCLAIMER,
                ).joinToString(
                    separator = "\n\n",
                ),
        )
    }

    private fun DayRangeSummary.toTextBlock(): String =
        buildString {
            append(
                PersianDateText.formatFull(
                    date,
                ),
            )
            append('\n')

            entries.forEachIndexed {
                    index,
                    entry ->
                if (index > 0) {
                    append('\n')
                }

                append(
                    index.plus(1)
                        .toString().toPersianDigits(),
                )
                append(". ")
                append(
                    entry.localTime
                        .format(
                            HOUR_MINUTE_FORMATTER,
                        ).toPersianDigits(),
                )
                append(" — ")
                append(entry.medicationName)
                append(" — ")
                append(
                    reportStateText(
                        entry.reportState,
                    ),
                )

                val recordingDetails = entry.recordingDetailsText()

                if (recordingDetails.isNotBlank()) {
                    append('\n')
                    append(recordingDetails)
                }

                if (entry.instruction.isNotBlank()) {
                    append('\n')
                    append(INSTRUCTION_LABEL)
                    append(": ")
                    append(entry.instruction)
                }
            }
        }

    private fun RangeOccurrenceEntry.recordingDetailsText(): String = MedicationRecordingDetails(
            medicationType = medicationType,
            dosageText = dosageText,
            doseUnit = doseUnit,
        ).toDisplayText()

    private fun reportTitle(
        period: RangeReportPeriod,
    ): String = when (period) {
            RangeReportPeriod.SEVEN_DAYS ->
                "گزارش ۷ روزه CarePack"

            RangeReportPeriod.THIRTY_DAYS ->
                "گزارش ۳۰ روزه CarePack"
        }

    private fun countLine(
        label: String,
        count: Int,
    ): String = "$label: " +
                count.toString()
                    .toPersianDigits()

    private fun reportStateText(
        state: RangeOccurrenceReportState,
    ): String = when (state) {
            RangeOccurrenceReportState.GIVEN ->
                "مراقب: داده شد"

            RangeOccurrenceReportState.NOT_GIVEN ->
                "مراقب: داده نشد"

            RangeOccurrenceReportState.UNKNOWN ->
                "نامشخص"

            RangeOccurrenceReportState.NO_REPORT ->
                "ثبت نشده"
        }

    private companion object {
        const val PERIOD_LABEL = "دوره"

        const val DATE_RANGE_LABEL = "بازه"

        const val RECIPIENT_LABEL = "فرد تحت مراقبت"

        const val SUMMARY_TITLE = "خلاصه"

        const val TOTAL_LABEL = "مجموع نوبت‌ها"

        const val GIVEN_LABEL = "مراقب: داده شد"

        const val NOT_GIVEN_LABEL = "مراقب: داده نشد"

        const val UNKNOWN_LABEL = "نامشخص"

        const val NO_REPORT_LABEL = "ثبت نشده"

        const val DETAILS_TITLE = "جزئیات"

        const val INSTRUCTION_LABEL = "توضیح"

        const val MEDICATION_TYPE_LABEL = "نوع"

        const val DOSAGE_LABEL = "مقدار ثبت‌شده"

        const val DOSE_UNIT_LABEL = "واحد"

        const val EMPTY_RANGE_MESSAGE = "در این بازه نوبتی وجود ندارد."

        const val DISCLAIMER =
            "این خلاصه فقط بر اساس نوبت‌ها و ثبت‌های موجود در CarePack تهیه شده است و ارزیابی پزشکی یا تضمین مصرف دارو نیست."

        val HOUR_MINUTE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern(
                "HH:mm",
            )
    }
}
