package ir.carepack.domain.report

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeReportTextBuilderTest {

    private val builder =
        RangeReportTextBuilder()

    @Test
    fun sevenDayReport_hasStablePersianTextAndAllCounts() {
        val today =
            LocalDate.parse(
                "2025-03-21",
            )

        val range =
            RangeReportPeriod
                .SEVEN_DAYS
                .rangeEndingAt(today)

        val summary =
            RangeSummaryBuilder.build(
                range = range,
                entries =
                    listOf(
                        entry(
                            id = "given",
                            date =
                                LocalDate.parse(
                                    "2025-03-20",
                                ),
                            time = LocalTime.of(8, 0),
                            state =
                                RangeOccurrenceReportState.GIVEN,
                            medicationName = "داروی صبح",
                        ),
                        entry(
                            id = "unknown",
                            date = today,
                            time = LocalTime.of(9, 30),
                            state =
                                RangeOccurrenceReportState.UNKNOWN,
                            medicationName = "داروی ظهر",
                        ),
                        entry(
                            id = "no-report",
                            date = today,
                            time = LocalTime.of(20, 0),
                            state =
                                RangeOccurrenceReportState.NO_REPORT,
                            medicationName = "داروی شب",
                        ),
                    ),
            )

        val text =
            builder.build(
                period =
                    RangeReportPeriod.SEVEN_DAYS,
                summary = summary,
                recipientName = "مادر",
            ).value

        val expected =
            """
            گزارش ۷ روزه CarePack
            دوره: ۷ روز
            بازه: ۱۴۰۳/۱۲/۲۵ تا ۱۴۰۴/۰۱/۰۱
            فرد تحت مراقبت: مادر

            خلاصه
            مجموع نوبت‌ها: ۳
            مراقب: داده شد: ۱
            مراقب: داده نشد: ۰
            نامشخص: ۱
            ثبت نشده: ۱

            جزئیات
            پنجشنبه، ۳۰ اسفند ۱۴۰۳
            ۱. ۰۸:۰۰ — داروی صبح — مراقب: داده شد
            نوع: قرص، مقدار ثبت‌شده: یک، واحد: عدد
            توضیح: بعد از غذا

            جمعه، ۱ فروردین ۱۴۰۴
            ۱. ۰۹:۳۰ — داروی ظهر — نامشخص
            نوع: قرص، مقدار ثبت‌شده: یک، واحد: عدد
            توضیح: بعد از غذا
            ۲. ۲۰:۰۰ — داروی شب — ثبت نشده
            نوع: قرص، مقدار ثبت‌شده: یک، واحد: عدد
            توضیح: بعد از غذا

            این خلاصه فقط بر اساس نوبت‌ها و ثبت‌های موجود در CarePack تهیه شده است و ارزیابی پزشکی یا تضمین مصرف دارو نیست.
            """.trimIndent()

        assertEquals(expected, text)
    }

    @Test
    fun recipientName_isIncludedOnlyWhenExplicitlyProvided() {
        val summary =
            emptySummary(
                RangeReportPeriod.SEVEN_DAYS,
                LocalDate.parse(
                    "2026-06-24",
                ),
            )

        val withName =
            builder.build(
                period =
                    RangeReportPeriod.SEVEN_DAYS,
                summary = summary,
                recipientName = "پدر",
            ).value

        val withoutName =
            builder.build(
                period =
                    RangeReportPeriod.SEVEN_DAYS,
                summary = summary,
                recipientName = null,
            ).value

        val blankName =
            builder.build(
                period =
                    RangeReportPeriod.SEVEN_DAYS,
                summary = summary,
                recipientName = "   ",
            ).value

        assertTrue(
            withName.contains(
                "فرد تحت مراقبت: پدر",
            ),
        )
        assertFalse(
            withoutName.contains(
                "فرد تحت مراقبت:",
            ),
        )
        assertFalse(
            blankName.contains(
                "فرد تحت مراقبت:",
            ),
        )
    }

    @Test
    fun thirtyDayReport_statesPeriodAndInclusiveJalaliRange() {
        val today =
            LocalDate.parse(
                "2026-01-05",
            )

        val summary =
            emptySummary(
                period =
                    RangeReportPeriod.THIRTY_DAYS,
                today = today,
            )

        val text =
            builder.build(
                period =
                    RangeReportPeriod.THIRTY_DAYS,
                summary = summary,
                recipientName = null,
            ).value

        assertTrue(
            text.startsWith(
                "گزارش ۳۰ روزه CarePack",
            ),
        )
        assertTrue(
            text.contains("دوره: ۳۰ روز"),
        )
        assertTrue(
            text.contains(
                "بازه: ۱۴۰۴/۰۹/۱۶ تا ۱۴۰۴/۱۰/۱۵",
            ),
        )
    }

    @Test
    fun emptyRange_hasZeroCountsAndExplicitEmptyMessage() {
        val summary =
            emptySummary(
                period =
                    RangeReportPeriod.SEVEN_DAYS,
                today =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        val text =
            builder.build(
                period =
                    RangeReportPeriod.SEVEN_DAYS,
                summary = summary,
                recipientName = null,
            ).value

        assertTrue(
            text.contains("مجموع نوبت‌ها: ۰"),
        )
        assertTrue(
            text.contains("مراقب: داده شد: ۰"),
        )
        assertTrue(
            text.contains("مراقب: داده نشد: ۰"),
        )
        assertTrue(
            text.contains("نامشخص: ۰"),
        )
        assertTrue(
            text.contains("ثبت نشده: ۰"),
        )
        assertTrue(
            text.contains(
                "در این بازه نوبتی وجود ندارد.",
            ),
        )
    }

    @Test
    fun formatterUsesJalaliPresentationAcrossNowruzAndLeapEsfand() {
        val today =
            LocalDate.parse(
                "2025-03-21",
            )

        val summary =
            RangeSummaryBuilder.build(
                range =
                    RangeReportPeriod
                        .SEVEN_DAYS
                        .rangeEndingAt(today),
                entries =
                    listOf(
                        entry(
                            id = "esfand-thirty",
                            date =
                                LocalDate.parse(
                                    "2025-03-20",
                                ),
                            time = LocalTime.NOON,
                            state =
                                RangeOccurrenceReportState
                                    .NO_REPORT,
                            medicationName = "داروی مرزی",
                        ),
                        entry(
                            id = "farvardin-one",
                            date = today,
                            time = LocalTime.NOON,
                            state =
                                RangeOccurrenceReportState
                                    .NO_REPORT,
                            medicationName = "داروی مرزی",
                        ),
                    ),
            )

        val text =
            builder.build(
                period =
                    RangeReportPeriod.SEVEN_DAYS,
                summary = summary,
                recipientName = null,
            ).value

        assertTrue(
            text.contains("۳۰ اسفند ۱۴۰۳"),
        )
        assertTrue(
            text.contains("۱ فروردین ۱۴۰۴"),
        )
    }

    @Test
    fun reportAvoidsMedicalAssessmentOrAdherencePercentage() {
        val summary =
            emptySummary(
                period =
                    RangeReportPeriod.SEVEN_DAYS,
                today =
                    LocalDate.parse(
                        "2026-06-24",
                    ),
            )

        val text =
            builder.build(
                period =
                    RangeReportPeriod.SEVEN_DAYS,
                summary = summary,
                recipientName = null,
            ).value

        assertFalse(text.contains('%'))
        assertFalse(
            text.contains(
                "پایبندی",
                ignoreCase = true,
            ),
        )
        assertTrue(
            text.contains(
                "ارزیابی پزشکی یا تضمین مصرف دارو نیست",
            ),
        )
    }

    private fun emptySummary(
        period: RangeReportPeriod,
        today: LocalDate,
    ): DateRangeSummary =
        RangeSummaryBuilder.build(
            range = period.rangeEndingAt(today),
            entries = emptyList(),
        )

    private fun entry(
        id: String,
        date: LocalDate,
        time: LocalTime,
        state: RangeOccurrenceReportState,
        medicationName: String,
    ): RangeOccurrenceEntry =
        RangeOccurrenceEntry(
            occurrenceId = id,
            localDate = date,
            localTime = time,
            zoneIdSnapshot = "Asia/Tehran",
            scheduledAt =
                Instant.parse(
                    "2025-03-20T08:30:00Z",
                ),
            medicationName = medicationName,
            instruction = "بعد از غذا",
            medicationType = "قرص",
            dosageText = "یک",
            doseUnit = "عدد",
            reportState = state,
        )
}
