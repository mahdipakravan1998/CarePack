package ir.carepack.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.careplan.StopMedicationOutcome
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderRoomIntegrationTest {

    private val anchorDate =
        LocalDate.parse(
            "2026-06-24",
        )

    @Test
    fun hasActiveSchedule_tracksCarePlanLifecycle() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                assertEquals(
                    false,
                    fixture
                        .reminderScheduleSource
                        .hasActiveSchedule(),
                )

                val plan =
                    fixture.createPlan(
                        minutesOfDay =
                            listOf(
                                9 * 60,
                            ),
                    )

                assertEquals(
                    true,
                    fixture
                        .reminderScheduleSource
                        .hasActiveSchedule(),
                )

                assertEquals(
                    StopMedicationOutcome.Stopped,
                    fixture
                        .carePlanService
                        .stopMedication(
                            plan.medicationId,
                        ),
                )

                assertEquals(
                    false,
                    fixture
                        .reminderScheduleSource
                        .hasActiveSchedule(),
                )
            }
        }

    @Test
    fun nextEligibleTargets_returnsEarliestFutureUnreportedOccurrencePerSeries() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val firstPlan =
                    fixture.createPlan(
                        medicationName =
                            "داروی ساعت نه",
                        instruction =
                            "دستور اول",
                        minutesOfDay =
                            listOf(
                                9 * 60,
                                10 * 60,
                            ),
                    )

                val secondPlan =
                    fixture.createPlan(
                        medicationName =
                            "داروی ساعت یازده",
                        instruction =
                            "دستور دوم",
                        minutesOfDay =
                            listOf(
                                11 * 60,
                            ),
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
                        .sortedBy {
                            it.localTime
                        }

                assertEquals(
                    2,
                    targets.size,
                )

                assertEquals(
                    firstPlan.scheduleSeriesId,
                    targets[0]
                        .alarmKey
                        .scheduleSeriesId,
                )

                assertEquals(
                    LocalTime.of(
                        9,
                        0,
                    ),
                    targets[0].localTime,
                )

                assertEquals(
                    "داروی ساعت نه",
                    targets[0].medicationName,
                )

                assertEquals(
                    secondPlan.scheduleSeriesId,
                    targets[1]
                        .alarmKey
                        .scheduleSeriesId,
                )

                assertEquals(
                    LocalTime.of(
                        11,
                        0,
                    ),
                    targets[1].localTime,
                )
            }
        }

    @Test
    fun reportedOccurrence_isExcludedFromReminderEligibility() =
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
                                10 * 60,
                            ),
                    )

                val firstOccurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date = anchorDate,
                        minuteOfDay =
                            9 * 60,
                    )

                assertNotNull(
                    fixture
                        .reminderScheduleSource
                        .getEligibleOccurrence(
                            firstOccurrence.id,
                        ),
                )

                fixture.report(
                    occurrenceId =
                        firstOccurrence.id,
                    state =
                        CaregiverReportState.GIVEN,
                )

                assertNull(
                    fixture
                        .reminderScheduleSource
                        .getEligibleOccurrence(
                            firstOccurrence.id,
                        ),
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
                    1,
                    targets.size,
                )

                assertEquals(
                    LocalTime.of(
                        10,
                        0,
                    ),
                    targets.single().localTime,
                )
            }
        }

    @Test
    fun cancelledOccurrence_isNotReminderEligible() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                    )

                val occurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date = anchorDate,
                        minuteOfDay =
                            12 * 60,
                    )

                assertNotNull(
                    fixture
                        .reminderScheduleSource
                        .getEligibleOccurrence(
                            occurrence.id,
                        ),
                )

                fixture
                    .carePlanService
                    .stopMedication(
                        plan.medicationId,
                    )

                val stoppedOccurrence =
                    fixture
                        .database
                        .occurrenceDao()
                        .getById(
                            occurrence.id,
                        )

                assertEquals(
                    OccurrenceLifecycle
                        .CANCELLED
                        .name,
                    stoppedOccurrence?.lifecycle,
                )

                assertNull(
                    fixture
                        .reminderScheduleSource
                        .getEligibleOccurrence(
                            occurrence.id,
                        ),
                )
            }
        }

    @Test
    fun allScheduleSeriesIds_includeStoppedHistoricalSeries() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        minutesOfDay =
                            listOf(
                                12 * 60,
                            ),
                    )

                fixture
                    .carePlanService
                    .stopMedication(
                        plan.medicationId,
                    )

                assertTrue(
                    fixture
                        .reminderScheduleSource
                        .getAllScheduleSeriesIds()
                        .contains(
                            plan.scheduleSeriesId,
                        ),
                )
            }
        }
}
