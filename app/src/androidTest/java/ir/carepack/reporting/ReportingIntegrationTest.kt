package ir.carepack.reporting

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.report.RoomTodayReportFormatter
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportingIntegrationTest {

    private val anchorDate =
        LocalDate.parse(
            "2026-06-24",
        )

    @Test
    fun todayWithoutMedication_usesNoMedicationsEmptyState() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                fixture.createOrGetRecipient()

                val today =
                    fixture
                        .todayQueryService
                        .observeToday(
                            localDate =
                                anchorDate,
                            now =
                                flowOf(
                                    fixture.clock.instant(),
                                ),
                        )
                        .first()

                assertTrue(
                    today.items.isEmpty(),
                )

                assertEquals(
                    TodayEmptyState.NO_MEDICATIONS,
                    today.emptyState,
                )
            }
        }

    @Test
    fun reportChangesRoundTripAndUndoRestoresPreviousState() =
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

                val first =
                    fixture
                        .reportService
                        .setReport(
                            occurrenceId =
                                occurrence.id,
                            newState =
                                CaregiverReportState.GIVEN,
                        )

                assertTrue(
                    first is SetReportOutcome.Changed,
                )

                val second =
                    fixture
                        .reportService
                        .setReport(
                            occurrenceId =
                                occurrence.id,
                            newState =
                                CaregiverReportState.UNKNOWN,
                        )

                assertTrue(
                    second is SetReportOutcome.Changed,
                )

                val secondChange =
                    (
                            second as
                                    SetReportOutcome.Changed
                            ).change

                val undo =
                    fixture
                        .reportService
                        .restorePrevious(
                            secondChange,
                        )

                assertTrue(
                    undo is UndoReportOutcome.Restored,
                )

                val detail =
                    fixture
                        .todayQueryService
                        .observeOccurrence(
                            occurrenceId =
                                occurrence.id,
                            now =
                                flowOf(
                                    fixture.clock.instant(),
                                ),
                        )
                        .first()

                assertEquals(
                    CaregiverReportState.GIVEN,
                    detail?.reportState,
                )
            }
        }

    @Test
    fun noReportAndUnknownRemainDistinctInToday() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val plan =
                    fixture.createPlan(
                        minutesOfDay =
                            listOf(
                                12 * 60,
                                13 * 60,
                            ),
                    )

                val unknownOccurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            plan.medicationId,
                        date = anchorDate,
                        minuteOfDay =
                            13 * 60,
                    )

                fixture.report(
                    occurrenceId =
                        unknownOccurrence.id,
                    state =
                        CaregiverReportState.UNKNOWN,
                )

                val today =
                    fixture
                        .todayQueryService
                        .observeToday(
                            localDate =
                                anchorDate,
                            now =
                                flowOf(
                                    fixture.clock.instant(),
                                ),
                        )
                        .first()

                val noon =
                    today.items.single {
                        it.localTime.hour == 12
                    }

                val onePm =
                    today.items.single {
                        it.localTime.hour == 13
                    }

                assertNull(
                    noon.reportState,
                )

                assertEquals(
                    CaregiverReportState.UNKNOWN,
                    onePm.reportState,
                )
            }
        }

    @Test
    fun recentHistoryIncludesPreviousDaysAndCaregiverReports() =
        runBlocking {
            CarePlanRoomTestFixture
                .create(
                    initialInstant =
                        Instant.parse(
                            "2026-06-17T08:00:00Z",
                        ),
                )
                .use { fixture ->
                    val plan =
                        fixture.createPlan(
                            minutesOfDay =
                                listOf(
                                    12 * 60,
                                ),
                        )

                    fixture.advanceTo(
                        Instant.parse(
                            "2026-06-24T08:00:00Z",
                        ),
                    )

                    fixture.generateAll(
                        anchorDate =
                            anchorDate,
                    )

                    val yesterday =
                        LocalDate.parse(
                            "2026-06-23",
                        )

                    val occurrence =
                        fixture.occurrenceOn(
                            medicationId =
                                plan.medicationId,
                            date = yesterday,
                            minuteOfDay =
                                12 * 60,
                        )

                    fixture.report(
                        occurrenceId =
                            occurrence.id,
                        state =
                            CaregiverReportState
                                .NOT_GIVEN,
                    )

                    val history =
                        fixture
                            .todayQueryService
                            .observeRecentHistory(
                                anchorDate =
                                    anchorDate,
                                now =
                                    flowOf(
                                        fixture
                                            .clock
                                            .instant(),
                                    ),
                            )
                            .first()

                    val yesterdayHistory =
                        history.single {
                            it.localDate ==
                                    yesterday
                        }

                    assertEquals(
                        CaregiverReportState.NOT_GIVEN,
                        yesterdayHistory
                            .items
                            .single()
                            .reportState,
                    )
                }
        }

    @Test
    fun todayReportFormatterIncludesRecipientNameOnlyWhenRequested() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                fixture.createPlan(
                    medicationName =
                        "داروی گزارش",
                    instruction =
                        "دستور گزارش",
                    minutesOfDay =
                        listOf(
                            12 * 60,
                        ),
                )

                val formatter =
                    RoomTodayReportFormatter(
                        database =
                            fixture.database,
                    )

                val withRecipient =
                    formatter
                        .createTodayReport(
                            date = anchorDate,
                            includeRecipientName = true,
                        )
                        .value

                val withoutRecipient =
                    formatter
                        .createTodayReport(
                            date = anchorDate,
                            includeRecipientName = false,
                        )
                        .value

                assertTrue(
                    withRecipient.contains(
                        "فرد تحت مراقبت: مادر",
                    ),
                )

                assertFalse(
                    withoutRecipient.contains(
                        "فرد تحت مراقبت:",
                    ),
                )

                assertTrue(
                    withRecipient.contains(
                        "داروی گزارش",
                    ),
                )

                assertTrue(
                    withRecipient.contains(
                        "این گزارش بر اساس ثبت‌های مراقب تهیه شده است",
                    ),
                )
            }
        }
}
