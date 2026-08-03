package ir.carepack.settings.deletion

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.careplan.UpdateMedicationTextCommand
import ir.carepack.domain.careplan.UpdateMedicationTextOutcome
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.report.ReportDateRange
import ir.carepack.domain.report.RoomDateRangeSummaryService
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationDeletionIntegrationTest {

    @Test
    fun previewCountsOwnedGraphAndStalePreviewMustBeReviewedAgain() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val target =
                    fixture.createPlan(
                        medicationName =
                            "داروی هدف",
                        instruction = "صبح",
                        minutesOfDay =
                            listOf(
                                12 * 60,
                                16 * 60,
                            ),
                        startDate = TODAY,
                        endDate =
                            TODAY.plusDays(1),
                    )

                fixture.addSchedule(
                    medicationId =
                        target.medicationId,
                    minutesOfDay =
                        listOf(20 * 60),
                    startDate = TODAY,
                    endDate =
                        TODAY.plusDays(1),
                )

                val reportable =
                    fixture.occurrenceOn(
                        medicationId =
                            target.medicationId,
                        date = TODAY,
                        minuteOfDay = 12 * 60,
                    )

                fixture.report(
                    occurrenceId =
                        reportable.id,
                    state =
                        CaregiverReportState.GIVEN,
                )

                val dataSource =
                    RoomMedicationDeletionDataSource(
                        database = fixture.database,
                    )

                val originalPreview =
                    checkNotNull(
                        dataSource.loadPreview(
                            target.medicationId,
                        ),
                    )

                assertEquals(2, originalPreview.scheduleSeriesCount)
                assertEquals(2, originalPreview.scheduleVersionCount)
                assertEquals(3, originalPreview.scheduleTimeCount)
                assertTrue(originalPreview.occurrenceCount > 0)
                assertEquals(1, originalPreview.caregiverReportCount)

                fixture.moveTo(
                    Instant.parse(
                        "2026-06-24T09:00:00Z",
                    ),
                )

                assertEquals(
                    UpdateMedicationTextOutcome.Updated,
                    fixture
                        .carePlanService
                        .updateMedicationText(
                            UpdateMedicationTextCommand(
                                medicationId =
                                    target.medicationId,
                                medicationName =
                                    "داروی هدف جدید",
                                instruction =
                                    "بعد از صبحانه",
                            ),
                        ),
                )

                val staleResult =
                    dataSource.deleteGraph(
                        medicationId =
                            target.medicationId,
                        expectedPreview =
                            originalPreview,
                    )

                assertTrue(
                    staleResult is
                            MedicationGraphDeletionResult
                            .ChangedSincePreview,
                )

                assertNotNull(
                    fixture
                        .database
                        .medicationDao()
                        .getById(
                            target.medicationId,
                        ),
                )

                assertTrue(
                    fixture
                        .occurrencesForMedication(
                            target.medicationId,
                        )
                        .isNotEmpty(),
                )
            }
        }

    @Test
    fun explicitChildToParentDeletionRemovesOnlyTargetGraphAndRefreshesReports() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val recipientId =
                    fixture.createOrGetRecipient(
                        displayName = "مادر",
                    )

                val target =
                    fixture.createPlan(
                        recipientId = recipientId,
                        medicationName =
                            "داروی حذف‌شونده",
                        instruction = "صبح",
                        minutesOfDay =
                            listOf(12 * 60),
                        startDate = TODAY,
                        endDate = TODAY,
                    )

                fixture.addSchedule(
                    medicationId =
                        target.medicationId,
                    minutesOfDay =
                        listOf(20 * 60),
                    startDate = TODAY,
                    endDate = TODAY,
                )

                val preserved =
                    fixture.createPlan(
                        recipientId = recipientId,
                        medicationName =
                            "داروی باقی‌مانده",
                        instruction = "شب",
                        minutesOfDay =
                            listOf(22 * 60),
                        startDate = TODAY,
                        endDate = TODAY,
                    )

                val targetOccurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            target.medicationId,
                        date = TODAY,
                        minuteOfDay = 12 * 60,
                    )

                val preservedOccurrence =
                    fixture.occurrenceOn(
                        medicationId =
                            preserved.medicationId,
                        date = TODAY,
                        minuteOfDay = 22 * 60,
                    )

                fixture.report(
                    occurrenceId =
                        targetOccurrence.id,
                    state =
                        CaregiverReportState
                            .NOT_GIVEN,
                )

                fixture.report(
                    occurrenceId =
                        preservedOccurrence.id,
                    state =
                        CaregiverReportState.UNKNOWN,
                )

                val dataSource =
                    RoomMedicationDeletionDataSource(
                        database = fixture.database,
                    )

                val preview =
                    checkNotNull(
                        dataSource.loadPreview(
                            target.medicationId,
                        ),
                    )

                val result =
                    dataSource.deleteGraph(
                        medicationId =
                            target.medicationId,
                        expectedPreview = preview,
                    )

                assertTrue(
                    result is
                            MedicationGraphDeletionResult
                            .Deleted,
                )

                val counts =
                    (
                            result as
                                    MedicationGraphDeletionResult
                                    .Deleted
                            ).counts

                assertEquals(1, counts.medicationCount)
                assertEquals(
                    preview.scheduleSeriesCount,
                    counts.scheduleSeriesCount,
                )
                assertEquals(
                    preview.scheduleVersionCount,
                    counts.scheduleVersionCount,
                )
                assertEquals(
                    preview.scheduleTimeCount,
                    counts.scheduleTimeCount,
                )
                assertEquals(
                    preview.occurrenceCount,
                    counts.occurrenceCount,
                )
                assertEquals(
                    preview.caregiverReportCount,
                    counts.caregiverReportCount,
                )

                assertNull(
                    fixture
                        .database
                        .medicationDao()
                        .getById(
                            target.medicationId,
                        ),
                )

                assertTrue(
                    fixture
                        .occurrencesForMedication(
                            target.medicationId,
                        )
                        .isEmpty(),
                )

                assertNull(
                    fixture
                        .database
                        .reportingDao()
                        .getReport(
                            targetOccurrence.id,
                        ),
                )

                assertNotNull(
                    fixture
                        .database
                        .medicationDao()
                        .getById(
                            preserved.medicationId,
                        ),
                )

                assertNotNull(
                    fixture
                        .database
                        .occurrenceDao()
                        .getById(
                            preservedOccurrence.id,
                        ),
                )

                assertEquals(
                    CaregiverReportState
                        .UNKNOWN
                        .name,
                    fixture
                        .database
                        .reportingDao()
                        .getReport(
                            preservedOccurrence.id,
                        )
                        ?.state,
                )

                assertEquals(
                    1,
                    fixture
                        .database
                        .careRecipientDao()
                        .count(),
                )

                val summary =
                    RoomDateRangeSummaryService(
                        database = fixture.database,
                    ).getSummary(
                        ReportDateRange(
                            startDate = TODAY,
                            endDate = TODAY,
                        ),
                    )

                assertEquals(1, summary.totalOccurrenceCount)
                assertEquals(
                    listOf(
                        preservedOccurrence.id,
                    ),
                    summary.entries.map {
                        it.occurrenceId
                    },
                )
            }
        }

    @Test
    fun duplicateDeleteIsSafeAndDeterministic() =
        runBlocking {
            CarePlanRoomTestFixture.create().use { fixture ->
                val target =
                    fixture.createPlan(
                        medicationName =
                            "داروی یک‌بار حذف",
                        instruction = "دستور",
                        minutesOfDay =
                            listOf(12 * 60),
                        startDate = TODAY,
                        endDate = TODAY,
                    )

                val dataSource =
                    RoomMedicationDeletionDataSource(
                        database = fixture.database,
                    )

                val preview =
                    checkNotNull(
                        dataSource.loadPreview(
                            target.medicationId,
                        ),
                    )

                assertTrue(
                    dataSource.deleteGraph(
                        medicationId =
                            target.medicationId,
                        expectedPreview = preview,
                    ) is MedicationGraphDeletionResult.Deleted,
                )

                assertEquals(
                    MedicationGraphDeletionResult.NotFound,
                    dataSource.deleteGraph(
                        medicationId =
                            target.medicationId,
                        expectedPreview = preview,
                    ),
                )
            }
        }

    private companion object {
        val TODAY: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )
    }
}
