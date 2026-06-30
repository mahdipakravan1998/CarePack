package ir.carepack.reporting

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.report.RoomTodayReportFormatter
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayReportIntegrationTest {

    private lateinit var fixture:
            CarePlanRoomTestFixture

    @Before
    fun setUp() {
        fixture =
            CarePlanRoomTestFixture.create(
                initialInstant =
                    INITIAL_INSTANT,
                idPrefix =
                    "today-report-contract",
                clockZone =
                    ZoneOffset.UTC,
            )
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun formatter_usesOnlyRequestedTodayExcludesCancelledAndOrdersRows() =
        runBlocking {
            val recipientId =
                fixture.createOrGetRecipient(
                    displayName =
                        RECIPIENT_NAME,
                )

            val noReportPlan =
                fixture.createPlan(
                    recipientId =
                        recipientId,
                    medicationName =
                        NO_REPORT_MEDICATION,
                    instruction =
                        "دستور بدون گزارش",
                    weekdays =
                        DayOfWeek
                            .entries
                            .toSet(),
                    minutesOfDay =
                        listOf(8 * 60),
                    startDate =
                        REPORT_DATE,
                    endDate =
                        REPORT_DATE,
                )

            val givenPlan =
                fixture.createPlan(
                    recipientId =
                        recipientId,
                    medicationName =
                        GIVEN_MEDICATION,
                    instruction =
                        "دستور داده‌شده",
                    weekdays =
                        DayOfWeek
                            .entries
                            .toSet(),
                    minutesOfDay =
                        listOf(9 * 60),
                    startDate =
                        REPORT_DATE,
                    endDate =
                        REPORT_DATE,
                )

            val notGivenPlan =
                fixture.createPlan(
                    recipientId =
                        recipientId,
                    medicationName =
                        NOT_GIVEN_MEDICATION,
                    instruction =
                        "دستور داده‌نشده",
                    weekdays =
                        DayOfWeek
                            .entries
                            .toSet(),
                    minutesOfDay =
                        listOf(10 * 60),
                    startDate =
                        REPORT_DATE,
                    endDate =
                        REPORT_DATE,
                )

            val unknownPlan =
                fixture.createPlan(
                    recipientId =
                        recipientId,
                    medicationName =
                        UNKNOWN_MEDICATION,
                    instruction =
                        "دستور نامشخص",
                    weekdays =
                        DayOfWeek
                            .entries
                            .toSet(),
                    minutesOfDay =
                        listOf(11 * 60),
                    startDate =
                        REPORT_DATE,
                    endDate =
                        REPORT_DATE,
                )

            val cancelledPlan =
                fixture.createPlan(
                    recipientId =
                        recipientId,
                    medicationName =
                        CANCELLED_MEDICATION,
                    instruction =
                        "دستور لغوشده",
                    weekdays =
                        DayOfWeek
                            .entries
                            .toSet(),
                    minutesOfDay =
                        listOf(12 * 60),
                    startDate =
                        REPORT_DATE,
                    endDate =
                        REPORT_DATE,
                )

            fixture.createPlan(
                recipientId =
                    recipientId,
                medicationName =
                    ADJACENT_DATE_MEDICATION,
                instruction =
                    "دستور فردا",
                weekdays =
                    DayOfWeek
                        .entries
                        .toSet(),
                minutesOfDay =
                    listOf(7 * 60),
                startDate =
                    REPORT_DATE
                        .plusDays(1),
                endDate =
                    REPORT_DATE
                        .plusDays(1),
            )

            setReport(
                scheduleVersionId =
                    givenPlan.scheduleVersionId,
                reportState =
                    CaregiverReportState.GIVEN,
            )

            setReport(
                scheduleVersionId =
                    notGivenPlan.scheduleVersionId,
                reportState =
                    CaregiverReportState
                        .NOT_GIVEN,
            )

            setReport(
                scheduleVersionId =
                    unknownPlan.scheduleVersionId,
                reportState =
                    CaregiverReportState.UNKNOWN,
            )

            fixture
                .database
                .occurrenceDao()
                .cancelFutureUnreportedForVersion(
                    scheduleVersionId =
                        cancelledPlan
                            .scheduleVersionId,
                    nowEpochMillis =
                        INITIAL_INSTANT
                            .minusSeconds(1)
                            .toEpochMilli(),
                    cancelledAtEpochMillis =
                        INITIAL_INSTANT
                            .toEpochMilli(),
                    cancellationReason =
                        OccurrenceCancellationReason
                            .MEDICATION_STOPPED
                            .name,
                )

            val formatter =
                RoomTodayReportFormatter(
                    database =
                        fixture.database,
                )

            val report =
                formatter
                    .createTodayReport(
                        date =
                            REPORT_DATE,
                        includeRecipientName =
                            true,
                    )
                    .value

            assertTrue(
                report.contains(
                    RECIPIENT_NAME,
                ),
            )

            assertTrue(
                report.contains(
                    NO_REPORT_MEDICATION,
                ),
            )

            assertTrue(
                report.contains(
                    GIVEN_MEDICATION,
                ),
            )

            assertTrue(
                report.contains(
                    NOT_GIVEN_MEDICATION,
                ),
            )

            assertTrue(
                report.contains(
                    UNKNOWN_MEDICATION,
                ),
            )

            assertFalse(
                report.contains(
                    CANCELLED_MEDICATION,
                ),
            )

            assertFalse(
                report.contains(
                    ADJACENT_DATE_MEDICATION,
                ),
            )

            val noReportIndex =
                report.indexOf(
                    NO_REPORT_MEDICATION,
                )

            val givenIndex =
                report.indexOf(
                    GIVEN_MEDICATION,
                )

            val notGivenIndex =
                report.indexOf(
                    NOT_GIVEN_MEDICATION,
                )

            val unknownIndex =
                report.indexOf(
                    UNKNOWN_MEDICATION,
                )

            assertTrue(
                givenIndex >
                        noReportIndex,
            )

            assertTrue(
                notGivenIndex >
                        givenIndex,
            )

            assertTrue(
                unknownIndex >
                        notGivenIndex,
            )

            val omittedNameReport =
                formatter
                    .createTodayReport(
                        date =
                            REPORT_DATE,
                        includeRecipientName =
                            false,
                    )
                    .value

            assertFalse(
                omittedNameReport.contains(
                    RECIPIENT_NAME,
                ),
            )

            assertTrue(
                omittedNameReport.contains(
                    FIXED_DISCLAIMER,
                ),
            )

            assertTrue(
                noReportPlan
                    .occurrenceIds
                    .isNotEmpty(),
            )
        }

    private suspend fun setReport(
        scheduleVersionId: String,
        reportState:
        CaregiverReportState,
    ) {
        val occurrence =
            fixture.occurrenceForDate(
                scheduleVersionId =
                    scheduleVersionId,
                date =
                    REPORT_DATE,
            )

        fixture.reportService.setReport(
            occurrenceId =
                occurrence.id,
            newState =
                reportState,
        )
    }

    private companion object {

        val INITIAL_INSTANT:
                Instant =
            Instant.parse(
                "2026-06-24T00:00:00Z",
            )

        val REPORT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                6,
                24,
            )

        const val RECIPIENT_NAME =
            "سارا"

        const val NO_REPORT_MEDICATION =
            "داروی بدون گزارش"

        const val GIVEN_MEDICATION =
            "داروی داده‌شده"

        const val NOT_GIVEN_MEDICATION =
            "داروی داده‌نشده"

        const val UNKNOWN_MEDICATION =
            "داروی نامشخص"

        const val CANCELLED_MEDICATION =
            "داروی لغوشده"

        const val ADJACENT_DATE_MEDICATION =
            "داروی تاریخ مجاور"

        const val FIXED_DISCLAIMER =
            "این گزارش بر اساس ثبت‌های مراقب تهیه شده است و تأیید پزشکی مصرف دارو نیست."
    }
}
