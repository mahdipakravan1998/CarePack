package ir.carepack.reporting

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.data.local.OccurrenceEntity
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.report.RangeReportPeriod
import ir.carepack.domain.report.ReportDateRange
import ir.carepack.data.service.RoomDateRangeSummaryService
import ir.carepack.data.service.RoomRangeReportFormatter
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RangeReportingIntegrationTest {

    @Test
    fun boundedRangeQueryUsesLocalEpochDayExcludesCancelledAndKeepsStableOrder() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        Instant.parse(
                            "2026-03-21T08:00:00Z",
                        ),
                    idPrefix =
                        "range-reporting",
                )
                .use { fixture ->
                    val recipientId =
                        fixture.createOrGetRecipient(
                            displayName =
                                "مادر",
                        )

                    val firstPlan =
                        fixture.createPlan(
                            recipientId = recipientId,
                            medicationName =
                                "داروی اول",
                            instruction =
                                "بعد از غذا",
                            minutesOfDay =
                                listOf(8 * 60),
                            startDate = TODAY,
                            endDate = TODAY,
                        )

                    val secondPlan =
                        fixture.createPlan(
                            recipientId = recipientId,
                            medicationName =
                                "داروی دوم",
                            instruction =
                                "با آب",
                            minutesOfDay =
                                listOf(9 * 60),
                            startDate = TODAY,
                            endDate = TODAY,
                        )

                    val firstTodayOccurrence =
                        fixture.occurrenceOn(
                            medicationId =
                                firstPlan.medicationId,
                            date = TODAY,
                            minuteOfDay = 8 * 60,
                        )

                    val secondTodayOccurrence =
                        fixture.occurrenceOn(
                            medicationId =
                                secondPlan.medicationId,
                            date = TODAY,
                            minuteOfDay = 9 * 60,
                        )

                    val start =
                        TODAY.minusDays(6)

                    insertOccurrence(
                        fixture = fixture,
                        occurrenceId = "given-start",
                        scheduleVersionId =
                            firstPlan.scheduleVersionId,
                        medicationId =
                            firstPlan.medicationId,
                        localDate = start,
                        minuteOfDay = 8 * 60,
                        medicationName =
                            "داروی اول",
                    )

                    insertOccurrence(
                        fixture = fixture,
                        occurrenceId = "not-given-middle",
                        scheduleVersionId =
                            secondPlan.scheduleVersionId,
                        medicationId =
                            secondPlan.medicationId,
                        localDate =
                            TODAY.minusDays(3),
                        minuteOfDay = 9 * 60,
                        medicationName =
                            "داروی دوم",
                    )

                    insertOccurrence(
                        fixture = fixture,
                        occurrenceId = "cancelled-inside",
                        scheduleVersionId =
                            firstPlan.scheduleVersionId,
                        medicationId =
                            firstPlan.medicationId,
                        localDate =
                            TODAY.minusDays(1),
                        minuteOfDay = 11 * 60,
                        medicationName =
                            "داروی لغوشده",
                        lifecycle =
                            OccurrenceLifecycle
                                .CANCELLED,
                    )

                    insertOccurrence(
                        fixture = fixture,
                        occurrenceId = "outside-range",
                        scheduleVersionId =
                            secondPlan.scheduleVersionId,
                        medicationId =
                            secondPlan.medicationId,
                        localDate =
                            start.minusDays(1),
                        minuteOfDay = 12 * 60,
                        medicationName =
                            "داروی بیرون بازه",
                    )

                    fixture.report(
                        occurrenceId = "given-start",
                        state =
                            CaregiverReportState.GIVEN,
                    )

                    fixture.report(
                        occurrenceId =
                            "not-given-middle",
                        state =
                            CaregiverReportState
                                .NOT_GIVEN,
                    )

                    fixture.report(
                        occurrenceId =
                            firstTodayOccurrence.id,
                        state =
                            CaregiverReportState.UNKNOWN,
                    )

                    val service =
                        RoomDateRangeSummaryService(
                            database = fixture.database,
                        )

                    val summary =
                        service.getSummary(
                            ReportDateRange(
                                startDate = start,
                                endDate = TODAY,
                            ),
                        )

                    assertEquals(4, summary.totalOccurrenceCount)
                    assertEquals(1, summary.givenCount)
                    assertEquals(1, summary.notGivenCount)
                    assertEquals(1, summary.unknownCount)
                    assertEquals(1, summary.noReportCount)

                    assertEquals(
                        listOf(
                            "given-start",
                            "not-given-middle",
                            firstTodayOccurrence.id,
                            secondTodayOccurrence.id,
                        ),
                        summary.entries.map {
                            it.occurrenceId
                        },
                    )

                    assertFalse(
                        summary.entries.any {
                            it.occurrenceId ==
                                    "cancelled-inside"
                        },
                    )

                    assertFalse(
                        summary.entries.any {
                            it.occurrenceId ==
                                    "outside-range"
                        },
                    )
                }
        }

    @Test
    fun formatterCreatesSevenAndThirtyDayJalaliTextWithoutMutatingReports() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        Instant.parse(
                            "2026-03-21T08:00:00Z",
                        ),
                    idPrefix =
                        "range-formatter",
                )
                .use { fixture ->
                    val recipientId =
                        fixture.createOrGetRecipient(
                            displayName =
                                "پدر",
                        )

                    val plan =
                        fixture.createPlan(
                            recipientId = recipientId,
                            medicationName =
                                "داروی فشار",
                            instruction =
                                "صبح",
                            medicationType =
                                "قرص",
                            dosageText = "یک",
                            doseUnit = "عدد",
                            minutesOfDay =
                                listOf(8 * 60),
                            startDate = TODAY,
                            endDate = TODAY,
                        )

                    insertOccurrence(
                        fixture = fixture,
                        occurrenceId =
                            "thirty-day-start",
                        scheduleVersionId =
                            plan.scheduleVersionId,
                        medicationId =
                            plan.medicationId,
                        localDate =
                            TODAY.minusDays(29),
                        minuteOfDay = 7 * 60,
                        medicationName =
                            "داروی فشار",
                    )

                    fixture.report(
                        occurrenceId =
                            "thirty-day-start",
                        state =
                            CaregiverReportState.GIVEN,
                    )

                    val reportsBefore =
                        fixture
                            .database
                            .reportingDao()
                            .countReports()

                    val summaryService =
                        RoomDateRangeSummaryService(
                            database = fixture.database,
                        )

                    val formatter =
                        RoomRangeReportFormatter(
                            database = fixture.database,
                            summaryService =
                                summaryService,
                        )

                    val sevenDay =
                        formatter.createRangeReport(
                            period =
                                RangeReportPeriod
                                    .SEVEN_DAYS,
                            today = TODAY,
                            includeRecipientName =
                                false,
                        )

                    val thirtyDay =
                        formatter.createRangeReport(
                            period =
                                RangeReportPeriod
                                    .THIRTY_DAYS,
                            today = TODAY,
                            includeRecipientName =
                                true,
                        )

                    assertEquals(
                        TODAY.minusDays(6),
                        sevenDay
                            .summary
                            .range
                            .startDate,
                    )

                    assertEquals(
                        TODAY.minusDays(29),
                        thirtyDay
                            .summary
                            .range
                            .startDate,
                    )

                    assertTrue(
                        sevenDay.text.value.contains(
                            "گزارش ۷ روزه",
                        ),
                    )

                    assertTrue(
                        thirtyDay.text.value.contains(
                            "گزارش ۳۰ روزه",
                        ),
                    )

                    assertTrue(
                        thirtyDay.text.value.contains(
                            "فرد تحت مراقبت: پدر",
                        ),
                    )

                    assertFalse(
                        sevenDay.text.value.contains(
                            "فرد تحت مراقبت: پدر",
                        ),
                    )

                    assertTrue(
                        thirtyDay.text.value.contains(
                            "۱۴۰۵/۰۱/۰۱",
                        ),
                    )

                    assertEquals(
                        reportsBefore,
                        fixture
                            .database
                            .reportingDao()
                            .countReports(),
                    )
                }
        }

    private suspend fun insertOccurrence(
        fixture: CarePlanRoomTestFixture,
        occurrenceId: String,
        scheduleVersionId: String,
        medicationId: String,
        localDate: LocalDate,
        minuteOfDay: Int,
        medicationName: String,
        lifecycle: OccurrenceLifecycle =
            OccurrenceLifecycle.ACTIVE,
    ) {
        val scheduledAt =
            localDate
                .atTime(
                    minuteOfDay / 60,
                    minuteOfDay % 60,
                )
                .toInstant(
                    ZoneOffset.UTC,
                )

        val inserted =
            fixture
                .database
                .occurrenceDao()
                .insertIgnoringLogicalConflict(
                    OccurrenceEntity(
                        id = occurrenceId,
                        scheduleVersionId =
                            scheduleVersionId,
                        medicationId = medicationId,
                        localEpochDay =
                            localDate.toEpochDay(),
                        minuteOfDay = minuteOfDay,
                        zoneIdSnapshot = "UTC",
                        scheduledAtEpochMillis =
                            scheduledAt.toEpochMilli(),
                        medicationNameSnapshot =
                            medicationName,
                        instructionSnapshot =
                            "دستور",
                        medicationTypeSnapshot =
                            "قرص",
                        dosageTextSnapshot = "یک",
                        doseUnitSnapshot = "عدد",
                        lifecycle = lifecycle.name,
                        cancelledAtEpochMillis =
                            if (
                                lifecycle ==
                                OccurrenceLifecycle.CANCELLED
                            ) {
                                scheduledAt.toEpochMilli()
                            } else {
                                null
                            },
                        cancellationReason =
                            if (
                                lifecycle ==
                                OccurrenceLifecycle.CANCELLED
                            ) {
                                "TEST"
                            } else {
                                null
                            },
                        createdAtEpochMillis =
                            fixture
                                .clock
                                .instant()
                                .toEpochMilli(),
                    ),
                )

        check(inserted != -1L) {
            "The test occurrence conflicts with an existing logical row."
        }
    }

    private companion object {
        val TODAY: LocalDate =
            LocalDate.parse(
                "2026-03-21",
            )
    }
}
