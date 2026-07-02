package ir.carepack.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderRoomIntegrationTest {

    @Test
    fun reminderScheduleSource_returnsNextUnreportedOccurrenceForEachActiveSchedule() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        medicationName =
                            "داروی چندزمانه",
                        instruction =
                            "دستور",
                        minutesOfDay =
                            listOf(
                                9 * 60,
                            ),
                        startDate =
                            ANCHOR_DATE,
                        endDate =
                            ANCHOR_DATE,
                    )

                val secondSchedule =
                    fixture.addSchedule(
                        medicationId =
                            plan.medicationId,
                        minutesOfDay =
                            listOf(
                                18 * 60,
                            ),
                        startDate =
                            ANCHOR_DATE,
                        endDate =
                            ANCHOR_DATE,
                    )

                val targets =
                    fixture
                        .reminderScheduleSource
                        .getNextEligibleTargets(
                            now =
                                fixture
                                    .clock
                                    .instant(),
                        )

                assertEquals(
                    setOf(
                        plan.scheduleSeriesId,
                        secondSchedule.scheduleSeriesId,
                    ),
                    targets
                        .map {
                            it.alarmKey.scheduleSeriesId
                        }
                        .toSet(),
                )

                assertEquals(
                    listOf(
                        9 * 60,
                        18 * 60,
                    ),
                    targets
                        .map {
                            it.localTime.hour * 60 +
                                    it.localTime.minute
                        }
                        .sorted(),
                )
            }
        }

    @Test
    fun reminderScheduleSource_skipsReportedOccurrenceButKeepsOtherSchedule() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        medicationName =
                            "داروی گزارش‌شده",
                        instruction =
                            "دستور",
                        minutesOfDay =
                            listOf(
                                9 * 60,
                            ),
                        startDate =
                            ANCHOR_DATE,
                        endDate =
                            ANCHOR_DATE,
                    )

                val secondSchedule =
                    fixture.addSchedule(
                        medicationId =
                            plan.medicationId,
                        minutesOfDay =
                            listOf(
                                18 * 60,
                            ),
                        startDate =
                            ANCHOR_DATE,
                        endDate =
                            ANCHOR_DATE,
                    )

                val reportedOccurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date =
                            ANCHOR_DATE,
                        minuteOfDay =
                            9 * 60,
                    )

                fixture.report(
                    occurrenceId =
                        reportedOccurrence.id,
                    state =
                        CaregiverReportState.GIVEN,
                )

                val targets =
                    fixture
                        .reminderScheduleSource
                        .getNextEligibleTargets(
                            now =
                                fixture
                                    .clock
                                    .instant(),
                        )

                assertEquals(
                    listOf(
                        secondSchedule.scheduleSeriesId,
                    ),
                    targets.map {
                        it.alarmKey.scheduleSeriesId
                    },
                )

                assertEquals(
                    18,
                    targets
                        .single()
                        .localTime
                        .hour,
                )
            }
        }

    @Test
    fun reminderScheduleSource_returnsValidatedNotificationOccurrenceWithoutWritingReport() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        medicationName =
                            "داروی اعلان",
                        instruction =
                            "دستور",
                        minutesOfDay =
                            listOf(
                                11 * 60,
                            ),
                        startDate =
                            ANCHOR_DATE,
                        endDate =
                            ANCHOR_DATE,
                    )

                val occurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date =
                            ANCHOR_DATE,
                        minuteOfDay =
                            11 * 60,
                    )

                val target =
                    fixture
                        .reminderScheduleSource
                        .getEligibleOccurrence(
                            occurrenceId =
                                occurrence.id,
                        )

                assertEquals(
                    occurrence.id,
                    target?.occurrenceId,
                )

                assertEquals(
                    plan.scheduleSeriesId,
                    target
                        ?.alarmKey
                        ?.scheduleSeriesId,
                )

                assertTrue(
                    fixture
                        .database
                        .reportingDao()
                        .getReport(
                            occurrence.id,
                        ) == null,
                )
            }
        }

    private companion object {
        val ANCHOR_DATE: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )
    }
}
