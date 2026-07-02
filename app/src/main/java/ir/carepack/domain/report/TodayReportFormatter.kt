package ir.carepack.domain.report

import ir.carepack.domain.calendar.JalaliPresentationDate
import ir.carepack.domain.model.CaregiverReportState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@JvmInline
value class TodayReportText(
    val value: String,
) {
    init {
        require(
            value.isNotBlank(),
        )
    }
}

interface TodayReportFormatter {

    suspend fun createTodayReport(
        date: LocalDate,
        includeRecipientName: Boolean,
    ): TodayReportText
}

internal data class TodayReportEntry(
    val occurrenceId: String,
    val localTime: LocalTime,
    val medicationName: String,
    val medicationInstruction: String,
    val reportState:
    CaregiverReportState?,
)

internal class TodayReportTextBuilder {

    fun build(
        date: LocalDate,
        recipientName: String?,
        entries: List<TodayReportEntry>,
    ): TodayReportText {
        val orderedEntries =
            entries.sortedWith(
                compareBy<TodayReportEntry>(
                    TodayReportEntry::localTime,
                ).thenBy(
                    TodayReportEntry::occurrenceId,
                ),
            )

        val headerLines =
            buildList {
                add(REPORT_TITLE)

                add(
                    "$DATE_LABEL: " +
                            JalaliPresentationDate
                                .from(date)
                                .formatNumeric(),
                )

                recipientName
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { displayName ->
                        add(
                            "$RECIPIENT_LABEL: $displayName",
                        )
                    }
            }

        val givenCount =
            orderedEntries.count {
                it.reportState ==
                        CaregiverReportState.GIVEN
            }

        val notGivenCount =
            orderedEntries.count {
                it.reportState ==
                        CaregiverReportState.NOT_GIVEN
            }

        val unknownCount =
            orderedEntries.count {
                it.reportState ==
                        CaregiverReportState.UNKNOWN
            }

        val noReportCount =
            orderedEntries.count {
                it.reportState == null
            }

        val summary =
            listOf(
                SUMMARY_TITLE,
                "$TOTAL_LABEL: ${orderedEntries.size}",
                "$GIVEN_COUNT_LABEL: $givenCount",
                "$NOT_GIVEN_COUNT_LABEL: $notGivenCount",
                "$UNKNOWN_COUNT_LABEL: $unknownCount",
                "$NO_REPORT_COUNT_LABEL: $noReportCount",
            ).joinToString(
                separator = "\n",
            )

        val occurrences =
            if (orderedEntries.isEmpty()) {
                EMPTY_REPORT_MESSAGE
            } else {
                orderedEntries
                    .mapIndexed { index, entry ->
                        entry.toReportBlock(
                            number = index + 1,
                        )
                    }
                    .joinToString(
                        separator = "\n\n",
                    )
            }

        val report =
            listOf(
                headerLines.joinToString(
                    separator = "\n",
                ),
                summary,
                "$OCCURRENCES_TITLE\n$occurrences",
                DISCLAIMER,
            ).joinToString(
                separator = "\n\n",
            )

        return TodayReportText(
            value = report,
        )
    }

    private fun TodayReportEntry.toReportBlock(
        number: Int,
    ): String =
        buildString {
            append(number)
            append(". ")
            append(
                localTime.format(
                    HOUR_MINUTE_FORMATTER,
                ),
            )
            append(" — ")
            append(medicationName)
            append(" — ")
            append(
                reportStateText(
                    reportState,
                ),
            )

            if (medicationInstruction.isNotBlank()) {
                append('\n')
                append(INSTRUCTION_LABEL)
                append(": ")
                append(medicationInstruction)
            }
        }

    private fun reportStateText(
        state: CaregiverReportState?,
    ): String =
        when (state) {
            CaregiverReportState.GIVEN -> {
                "مصرف شد"
            }

            CaregiverReportState.NOT_GIVEN -> {
                "مصرف نشد"
            }

            CaregiverReportState.UNKNOWN -> {
                "نامشخص"
            }

            null -> {
                "ثبت نشده"
            }
        }

    private companion object {
        const val REPORT_TITLE =
            "گزارش امروز CarePack"

        const val DATE_LABEL =
            "تاریخ"

        const val RECIPIENT_LABEL =
            "فرد تحت مراقبت"

        const val SUMMARY_TITLE =
            "خلاصه"

        const val TOTAL_LABEL =
            "مجموع نوبت‌ها"

        const val GIVEN_COUNT_LABEL =
            "مصرف شد"

        const val NOT_GIVEN_COUNT_LABEL =
            "مصرف نشد"

        const val UNKNOWN_COUNT_LABEL =
            "نامشخص"

        const val NO_REPORT_COUNT_LABEL =
            "ثبت نشده"

        const val OCCURRENCES_TITLE =
            "جزئیات"

        const val INSTRUCTION_LABEL =
            "توضیح"

        const val EMPTY_REPORT_MESSAGE =
            "موردی برای امروز وجود ندارد."

        const val DISCLAIMER =
            "این گزارش بر اساس ثبت‌های مراقب تهیه شده است و تأیید پزشکی مصرف دارو نیست."

        val HOUR_MINUTE_FORMATTER:
                DateTimeFormatter =
            DateTimeFormatter.ofPattern(
                "HH:mm",
            )
    }
}
