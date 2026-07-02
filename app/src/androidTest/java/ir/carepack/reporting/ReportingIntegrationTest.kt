package ir.carepack.reporting

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.careplan.UpdateScheduleCommand
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.report.RoomTodayReportFormatter
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportingIntegrationTest {

    @Test
    fun todayReportIncludesOccurrencesFromMultipleSchedules() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        medicationName =
                            "داروی صبح",
                        instruction =
                            "بعد از صبحانه",
                        minutesOfDay =
                            listOf(
                                8 * 60,
                            ),
                        startDate =
                            REPORT_DATE,
                        endDate =
                            REPORT_DATE,
                    )

                fixture.addSchedule(
                    medicationId =
                        plan.medicationId,
                    minutesOfDay =
                        listOf(
                            20 * 60,
                        ),
                    startDate =
                        REPORT_DATE,
                    endDate =
                        REPORT_DATE,
                )

                val morning =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date =
                            REPORT_DATE,
                        minuteOfDay =
                            8 * 60,
                    )

                val evening =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date =
                            REPORT_DATE,
                        minuteOfDay =
                            20 * 60,
                    )

                fixture.report(
                    occurrenceId =
                        morning.id,
                    state =
                        CaregiverReportState.GIVEN,
                )

                fixture.report(
                    occurrenceId =
                        evening.id,
                    state =
                        CaregiverReportState.NOT_GIVEN,
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
                        "داروی صبح",
                    ),
                )

                assertTrue(
                    report.contains(
                        "08:00",
                    ),
                )

                assertTrue(
                    report.contains(
                        "20:00",
                    ),
                )

                assertTrue(
                    report.contains(
                        "مصرف شد",
                    ),
                )

                assertTrue(
                    report.contains(
                        "مصرف نشد",
                    ),
                )
            }
        }

    @Test
    fun historyReportRemainsStableAfterEditingOneSchedule() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        medicationName =
                            "داروی سابقه",
                        instruction =
                            "دستور",
                        minutesOfDay =
                            listOf(
                                8 * 60,
                            ),
                        startDate =
                            REPORT_DATE,
                        endDate =
                            REPORT_DATE.plusDays(
                                1,
                            ),
                    )

                fixture.addSchedule(
                    medicationId =
                        plan.medicationId,
                    minutesOfDay =
                        listOf(
                            20 * 60,
                        ),
                    startDate =
                        REPORT_DATE,
                    endDate =
                        REPORT_DATE.plusDays(
                            1,
                        ),
                )

                val occurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date =
                            REPORT_DATE,
                        minuteOfDay =
                            8 * 60,
                    )

                fixture.report(
                    occurrenceId =
                        occurrence.id,
                    state =
                        CaregiverReportState.GIVEN,
                )

                fixture.moveTo(
                    Instant.parse(
                        "2026-06-24T09:00:00Z",
                    ),
                )

                fixture.carePlanService.updateSchedule(
                    UpdateScheduleCommand(
                        scheduleSeriesId =
                            plan.scheduleSeriesId,
                        weekdays =
                            setOf(
                                DayOfWeek.WEDNESDAY,
                                DayOfWeek.THURSDAY,
                            ),
                        minutesOfDay =
                            listOf(
                                10 * 60,
                            ),
                        schedulePattern =
                            FixedTimeSchedule(
                                minutesOfDay =
                                    listOf(
                                        10 * 60,
                                    ),
                            ),
                        startDate =
                            REPORT_DATE,
                        endDate =
                            REPORT_DATE.plusDays(
                                1,
                            ),
                        zoneId =
                            "UTC",
                    ),
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
                                false,
                        )
                        .value

                assertTrue(
                    report.contains(
                        "08:00",
                    ),
                )

                assertTrue(
                    report.contains(
                        "مصرف شد",
                    ),
                )
            }
        }

    private companion object {
        val REPORT_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )
    }
}
