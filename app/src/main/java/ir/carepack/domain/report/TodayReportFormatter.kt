package ir.carepack.domain.report

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
                    "$DATE_LABEL: $date",
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
                    .mapIndexed {
                            index,
                            entry ->
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
    ): String {
        val normalizedInstruction =
            medicationInstruction
                .trim()
                .lineSequence()
                .joinToString(
                    separator = "\n",
                ) { line ->
                    "   ${line.trimEnd()}"
                }

        return buildString {
            append(number)
            append(". ")
            append(
                localTime.format(
                    TIME_FORMATTER,
                ),
            )
            append(" — ")
            append(
                medicationName.trim(),
            )
            append('\n')
            append(INSTRUCTION_LABEL)
            append('\n')
            append(normalizedInstruction)
            append('\n')
            append(REPORT_STATE_LABEL)
            append(": ")
            append(
                reportState.toReportStateText(),
            )
        }
    }

    private fun CaregiverReportState?.toReportStateText():
            String =
        when (this) {
            null -> {
                NO_REPORT_TEXT
            }

            CaregiverReportState.GIVEN -> {
                GIVEN_TEXT
            }

            CaregiverReportState.NOT_GIVEN -> {
                NOT_GIVEN_TEXT
            }

            CaregiverReportState.UNKNOWN -> {
                UNKNOWN_TEXT
            }
        }

    private companion object {

        val TIME_FORMATTER:
                DateTimeFormatter =
            DateTimeFormatter.ofPattern(
                "HH:mm",
            )

        const val REPORT_TITLE =
            "گزارش امروز کرپک"

        const val DATE_LABEL =
            "تاریخ"

        const val RECIPIENT_LABEL =
            "فرد تحت مراقبت"

        const val SUMMARY_TITLE =
            "خلاصه"

        const val TOTAL_LABEL =
            "تعداد نوبت‌ها"

        const val GIVEN_COUNT_LABEL =
            "داده شد"

        const val NOT_GIVEN_COUNT_LABEL =
            "داده نشد"

        const val UNKNOWN_COUNT_LABEL =
            "مشخص نیست"

        const val NO_REPORT_COUNT_LABEL =
            "بدون گزارش"

        const val OCCURRENCES_TITLE =
            "نوبت‌ها"

        const val INSTRUCTION_LABEL =
            "   دستور:"

        const val REPORT_STATE_LABEL =
            "   گزارش"

        const val NO_REPORT_TEXT =
            "هنوز گزارشی ثبت نشده است"

        const val GIVEN_TEXT =
            "مراقب ثبت کرده است که دارو داده شده است"

        const val NOT_GIVEN_TEXT =
            "مراقب ثبت کرده است که دارو داده نشده است"

        const val UNKNOWN_TEXT =
            "مراقب صریحاً ثبت کرده است که نتیجه مشخص نیست"

        const val EMPTY_REPORT_MESSAGE =
            "برای امروز نوبتی ثبت نشده است."

        const val DISCLAIMER =
            "این گزارش بر اساس ثبت‌های مراقب تهیه شده است و تأیید پزشکی مصرف دارو نیست."
    }
}
