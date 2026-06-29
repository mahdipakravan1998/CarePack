package ir.carepack.domain.report

import ir.carepack.domain.model.CaregiverReportState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayReportTextBuilderTest {

    private val builder =
        TodayReportTextBuilder()

    @Test
    fun entries_areOrderedByTimeAndOccurrenceId() {
        val report =
            builder.build(
                date = REPORT_DATE,
                recipientName = null,
                entries =
                    listOf(
                        entry(
                            occurrenceId =
                                "occurrence-late",
                            time =
                                LocalTime.of(
                                    11,
                                    30,
                                ),
                            medicationName =
                                "داروی دیرتر",
                        ),
                        entry(
                            occurrenceId =
                                "occurrence-early-b",
                            time =
                                LocalTime.of(
                                    8,
                                    0,
                                ),
                            medicationName =
                                "داروی دوم",
                        ),
                        entry(
                            occurrenceId =
                                "occurrence-early-a",
                            time =
                                LocalTime.of(
                                    8,
                                    0,
                                ),
                            medicationName =
                                "داروی اول",
                        ),
                    ),
            )
                .value

        val firstIndex =
            report.indexOf(
                "داروی اول",
            )

        val secondIndex =
            report.indexOf(
                "داروی دوم",
            )

        val lateIndex =
            report.indexOf(
                "داروی دیرتر",
            )

        assertTrue(
            firstIndex >= 0,
        )

        assertTrue(
            secondIndex >
                    firstIndex,
        )

        assertTrue(
            lateIndex >
                    secondIndex,
        )
    }

    @Test
    fun everyReportState_hasDistinctOutput() {
        val reportTexts =
            listOf(
                null,
                CaregiverReportState.GIVEN,
                CaregiverReportState.NOT_GIVEN,
                CaregiverReportState.UNKNOWN,
            )
                .map { reportState ->
                    builder.build(
                        date = REPORT_DATE,
                        recipientName = null,
                        entries =
                            listOf(
                                entry(
                                    occurrenceId =
                                        "same-occurrence",
                                    time =
                                        LocalTime.of(
                                            9,
                                            15,
                                        ),
                                    medicationName =
                                        "داروی ثابت",
                                    reportState =
                                        reportState,
                                ),
                            ),
                    )
                        .value
                }

        assertEquals(
            4,
            reportTexts
                .toSet()
                .size,
        )
    }

    @Test
    fun recipientName_isIncludedOnlyWhenProvided() {
        val entry =
            entry(
                occurrenceId =
                    "occurrence-name",
                time =
                    LocalTime.of(
                        10,
                        0,
                    ),
                medicationName =
                    "داروی نمونه",
            )

        val included =
            builder.build(
                date = REPORT_DATE,
                recipientName =
                    RECIPIENT_NAME,
                entries =
                    listOf(entry),
            )
                .value

        val omitted =
            builder.build(
                date = REPORT_DATE,
                recipientName = null,
                entries =
                    listOf(entry),
            )
                .value

        assertTrue(
            included.contains(
                RECIPIENT_NAME,
            ),
        )

        assertFalse(
            omitted.contains(
                RECIPIENT_NAME,
            ),
        )
    }

    @Test
    fun report_alwaysContainsRequestedDateAndFixedDisclaimer() {
        val report =
            builder.build(
                date = REPORT_DATE,
                recipientName = null,
                entries = emptyList(),
            )
                .value

        assertTrue(
            report.contains(
                REPORT_DATE.toString(),
            ),
        )

        assertTrue(
            report.contains(
                FIXED_DISCLAIMER,
            ),
        )
    }

    @Test
    fun emptyToday_isStillACompleteDeterministicReport() {
        val first =
            builder.build(
                date = REPORT_DATE,
                recipientName = null,
                entries = emptyList(),
            )

        val second =
            builder.build(
                date = REPORT_DATE,
                recipientName = null,
                entries = emptyList(),
            )

        assertEquals(
            first,
            second,
        )

        assertTrue(
            first.value.isNotBlank(),
        )
    }

    private fun entry(
        occurrenceId: String,
        time: LocalTime,
        medicationName: String,
        reportState:
        CaregiverReportState? = null,
    ): TodayReportEntry =
        TodayReportEntry(
            occurrenceId =
                occurrenceId,
            localTime =
                time,
            medicationName =
                medicationName,
            medicationInstruction =
                "دستور مصرف",
            reportState =
                reportState,
        )

    private companion object {

        val REPORT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                6,
                24,
            )

        const val RECIPIENT_NAME =
            "مینا"

        const val FIXED_DISCLAIMER =
            "این گزارش بر اساس ثبت‌های مراقب تهیه شده است و تأیید پزشکی مصرف دارو نیست."
    }
}
