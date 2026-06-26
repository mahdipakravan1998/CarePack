package ir.carepack.reporting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.core.id.IdSource
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.careplan.RoomCarePlanService
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.TodayEmptyState
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.report.SetReportOutcome
import ir.carepack.domain.report.UndoReportOutcome
import ir.carepack.domain.today.RoomTodayQueryService
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportingIntegrationTest {

    private lateinit var database:
            CarePackDatabase

    private lateinit var clock:
            MutableReportingClock

    private lateinit var idSource:
            ReportingIdSource

    private lateinit var generator:
            RoomOccurrenceGenerator

    private lateinit var carePlanService:
            RoomCarePlanService

    private lateinit var reportService:
            RoomCaregiverReportService

    private lateinit var todayQueryService:
            RoomTodayQueryService

    private lateinit var recipientId:
            String

    @Before
    fun setUp() {
        val context =
            ApplicationProvider
                .getApplicationContext<
                        Context
                        >()

        database =
            Room
                .inMemoryDatabaseBuilder(
                    context,
                    CarePackDatabase::class.java,
                )
                .build()

        clock =
            MutableReportingClock(
                initialInstant =
                    HISTORY_START_INSTANT,
            )

        idSource =
            ReportingIdSource()

        generator =
            RoomOccurrenceGenerator(
                database = database,
                idSource = idSource,
                candidateResolver =
                    OccurrenceCandidateResolver(),
            )

        carePlanService =
            RoomCarePlanService(
                database = database,
                occurrenceGenerator =
                    generator,
                clock = clock,
                idSource = idSource,
            )

        reportService =
            RoomCaregiverReportService(
                database = database,
                clock = clock,
            )

        todayQueryService =
            RoomTodayQueryService(
                database = database,
                clock = clock,
            )

        runBlocking {
            val outcome =
                carePlanService
                    .createRecipient(
                        CreateRecipientCommand(
                            displayName =
                                "فرد نمونه",
                        ),
                    )

            recipientId =
                (
                        outcome as
                                CreateRecipientOutcome
                                .Created
                        ).recipientId
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun noReportAndExplicitUnknown_areDifferentRepresentations() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
                        listOf(9 * 60),
                )

            moveToAnchorAndGuarantee()

            val occurrence =
                occurrenceForDate(
                    scheduleVersionId =
                        plan
                            .scheduleVersionId,
                    date = ANCHOR_DATE,
                )

            val before =
                todayQueryService
                    .observeOccurrence(
                        occurrenceId =
                            occurrence.id,
                        now =
                            flowOf(
                                clock.instant(),
                            ),
                    )
                    .first()

            assertNull(
                before?.reportState,
            )

            val outcome =
                reportService.setReport(
                    occurrenceId =
                        occurrence.id,
                    newState =
                        CaregiverReportState
                            .UNKNOWN,
                )

            assertTrue(
                outcome is
                        SetReportOutcome
                        .Changed,
            )

            val after =
                todayQueryService
                    .observeOccurrence(
                        occurrenceId =
                            occurrence.id,
                        now =
                            flowOf(
                                clock.instant(),
                            ),
                    )
                    .first()

            assertEquals(
                CaregiverReportState.UNKNOWN,
                after?.reportState,
            )

            assertEquals(
                1,
                database
                    .reportingDao()
                    .countReportsForOccurrence(
                        occurrence.id,
                    ),
            )
        }

    @Test
    fun explicitTransitions_keepOneRowAndPreserveRecordedAt() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
                        listOf(9 * 60),
                )

            moveToAnchorAndGuarantee()

            val occurrence =
                occurrenceForDate(
                    scheduleVersionId =
                        plan
                            .scheduleVersionId,
                    date = ANCHOR_DATE,
                )

            reportService.setReport(
                occurrenceId =
                    occurrence.id,
                newState =
                    CaregiverReportState.GIVEN,
            )

            val firstReport =
                checkNotNull(
                    database
                        .reportingDao()
                        .getReport(
                            occurrence.id,
                        ),
                )

            reportService.setReport(
                occurrenceId =
                    occurrence.id,
                newState =
                    CaregiverReportState
                        .NOT_GIVEN,
            )

            reportService.setReport(
                occurrenceId =
                    occurrence.id,
                newState =
                    CaregiverReportState
                        .UNKNOWN,
            )

            val finalReport =
                checkNotNull(
                    database
                        .reportingDao()
                        .getReport(
                            occurrence.id,
                        ),
                )

            assertEquals(
                CaregiverReportState
                    .UNKNOWN
                    .name,
                finalReport.state,
            )

            assertEquals(
                firstReport
                    .recordedAtEpochMillis,
                finalReport
                    .recordedAtEpochMillis,
            )

            assertTrue(
                finalReport
                    .updatedAtEpochMillis >
                        firstReport
                            .updatedAtEpochMillis,
            )

            assertEquals(
                1,
                database
                    .reportingDao()
                    .countReportsForOccurrence(
                        occurrence.id,
                    ),
            )
        }

    @Test
    fun sameStateSelection_isIdempotent() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
                        listOf(9 * 60),
                )

            moveToAnchorAndGuarantee()

            val occurrence =
                occurrenceForDate(
                    scheduleVersionId =
                        plan
                            .scheduleVersionId,
                    date = ANCHOR_DATE,
                )

            reportService.setReport(
                occurrenceId =
                    occurrence.id,
                newState =
                    CaregiverReportState.GIVEN,
            )

            val persistedBefore =
                checkNotNull(
                    database
                        .reportingDao()
                        .getReport(
                            occurrence.id,
                        ),
                )

            val outcome =
                reportService.setReport(
                    occurrenceId =
                        occurrence.id,
                    newState =
                        CaregiverReportState.GIVEN,
                )

            assertTrue(
                outcome is
                        SetReportOutcome
                        .Unchanged,
            )

            val persistedAfter =
                checkNotNull(
                    database
                        .reportingDao()
                        .getReport(
                            occurrence.id,
                        ),
                )

            assertEquals(
                persistedBefore,
                persistedAfter,
            )

            assertEquals(
                1,
                database
                    .reportingDao()
                    .countReportsForOccurrence(
                        occurrence.id,
                    ),
            )
        }

    @Test
    fun undoToNoReport_removesOnlyReportRow() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
                        listOf(9 * 60),
                )

            moveToAnchorAndGuarantee()

            val occurrence =
                occurrenceForDate(
                    scheduleVersionId =
                        plan
                            .scheduleVersionId,
                    date = ANCHOR_DATE,
                )

            val changed =
                reportService.setReport(
                    occurrenceId =
                        occurrence.id,
                    newState =
                        CaregiverReportState.GIVEN,
                ) as
                        SetReportOutcome
                        .Changed

            val undoOutcome =
                reportService
                    .restorePrevious(
                        changed.change,
                    )

            assertEquals(
                UndoReportOutcome.Restored(
                    occurrenceId =
                        occurrence.id,
                    restoredState = null,
                ),
                undoOutcome,
            )

            assertNull(
                database
                    .reportingDao()
                    .getReport(
                        occurrence.id,
                    ),
            )

            assertNotNull(
                database
                    .occurrenceDao()
                    .getById(
                        occurrence.id,
                    ),
            )
        }

    @Test
    fun staleUndo_cannotOverwriteNewerReport() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
                        listOf(9 * 60),
                )

            moveToAnchorAndGuarantee()

            val occurrence =
                occurrenceForDate(
                    scheduleVersionId =
                        plan
                            .scheduleVersionId,
                    date = ANCHOR_DATE,
                )

            val firstChange =
                reportService.setReport(
                    occurrenceId =
                        occurrence.id,
                    newState =
                        CaregiverReportState.GIVEN,
                ) as
                        SetReportOutcome
                        .Changed

            reportService.setReport(
                occurrenceId =
                    occurrence.id,
                newState =
                    CaregiverReportState.UNKNOWN,
            )

            val undoOutcome =
                reportService
                    .restorePrevious(
                        firstChange.change,
                    )

            assertEquals(
                UndoReportOutcome
                    .NoLongerCurrent,
                undoOutcome,
            )

            assertEquals(
                CaregiverReportState
                    .UNKNOWN
                    .name,
                database
                    .reportingDao()
                    .getReport(
                        occurrence.id,
                    )
                    ?.state,
            )
        }

    @Test
    fun cancelledOccurrence_rejectsNewReport() =
        runBlocking {
            val plan =
                createPlan(
                    minutesOfDay =
                        listOf(9 * 60),
                )

            moveToAnchorAndGuarantee()

            val occurrence =
                occurrenceForDate(
                    scheduleVersionId =
                        plan
                            .scheduleVersionId,
                    date = ANCHOR_DATE,
                )

            database
                .occurrenceDao()
                .cancelFutureUnreportedForVersion(
                    scheduleVersionId =
                        plan
                            .scheduleVersionId,
                    nowEpochMillis =
                        clock
                            .instant()
                            .minusSeconds(1)
                            .toEpochMilli(),
                    cancelledAtEpochMillis =
                        clock
                            .instant()
                            .toEpochMilli(),
                    cancellationReason =
                        OccurrenceCancellationReason
                            .SCHEDULE_REPLACED
                            .name,
                )

            val outcome =
                reportService.setReport(
                    occurrenceId =
                        occurrence.id,
                    newState =
                        CaregiverReportState.GIVEN,
                )

            assertEquals(
                SetReportOutcome
                    .CancelledOccurrenceRejected,
                outcome,
            )

            assertNull(
                database
                    .reportingDao()
                    .getReport(
                        occurrence.id,
                    ),
            )
        }

    @Test
    fun today_isOrderedAndExcludesCancelledOccurrences() =
        runBlocking {
            val activePlan =
                createPlan(
                    medicationName =
                        "داروی فعال",
                    minutesOfDay =
                        listOf(
                            11 * 60,
                            9 * 60,
                        ),
                )

            val cancelledPlan =
                createPlan(
                    medicationName =
                        "داروی لغوشده",
                    minutesOfDay =
                        listOf(10 * 60),
                )

            moveToAnchorAndGuarantee()

            database
                .occurrenceDao()
                .cancelFutureUnreportedForVersion(
                    scheduleVersionId =
                        cancelledPlan
                            .scheduleVersionId,
                    nowEpochMillis =
                        clock
                            .instant()
                            .minusSeconds(1)
                            .toEpochMilli(),
                    cancelledAtEpochMillis =
                        clock
                            .instant()
                            .toEpochMilli(),
                    cancellationReason =
                        OccurrenceCancellationReason
                            .SCHEDULE_REPLACED
                            .name,
                )

            val model =
                todayQueryService
                    .observeToday(
                        localDate =
                            ANCHOR_DATE,
                        now =
                            flowOf(
                                clock.instant(),
                            ),
                    )
                    .first()

            assertEquals(
                listOf(
                    9 * 60,
                    11 * 60,
                ),
                model.items.map {
                    it.localTime.hour * 60 +
                            it.localTime.minute
                },
            )

            assertTrue(
                model.items.all {
                    it.lifecycle ==
                            OccurrenceLifecycle
                                .ACTIVE
                },
            )

            assertTrue(
                model.items.all {
                    it.medicationName ==
                            "داروی فعال"
                },
            )

            assertNull(
                model.emptyState,
            )

            assertEquals(
                2,
                database
                    .occurrenceDao()
                    .getForVersion(
                        activePlan
                            .scheduleVersionId,
                    )
                    .count {
                        it.localDateEpochDay ==
                                ANCHOR_DATE
                                    .toEpochDay()
                    },
            )
        }

    @Test
    fun today_distinguishesNoMedicationsFromNoOccurrences() =
        runBlocking {
            val dateWithoutOccurrences =
                ANCHOR_DATE.plusDays(30)

            createPlan(
                minutesOfDay =
                    listOf(9 * 60),
            )

            moveToAnchorAndGuarantee()

            val noOccurrences =
                todayQueryService
                    .observeToday(
                        localDate =
                            dateWithoutOccurrences,
                        now =
                            flowOf(
                                clock.instant(),
                            ),
                    )
                    .first()

            assertEquals(
                TodayEmptyState
                    .NO_OCCURRENCES,
                noOccurrences.emptyState,
            )

            database.clearAllTables()

            val noMedications =
                todayQueryService
                    .observeToday(
                        localDate =
                            ANCHOR_DATE,
                        now =
                            flowOf(
                                clock.instant(),
                            ),
                    )
                    .first()

            assertEquals(
                TodayEmptyState
                    .NO_MEDICATIONS,
                noMedications.emptyState,
            )
        }

    @Test
    fun recentHistory_isInclusiveEightDaysAndDoesNotDeleteOlderRows() =
        runBlocking {
            createPlan(
                minutesOfDay =
                    listOf(9 * 60),
            )

            moveToAnchorAndGuarantee()

            val totalBefore =
                database
                    .occurrenceDao()
                    .count()

            val history =
                todayQueryService
                    .observeRecentHistory(
                        anchorDate =
                            ANCHOR_DATE,
                        now =
                            flowOf(
                                clock.instant(),
                            ),
                    )
                    .first()

            val dates =
                history.map {
                    it.localDate
                }

            assertTrue(
                dates.contains(
                    ANCHOR_DATE,
                ),
            )

            assertTrue(
                dates.contains(
                    ANCHOR_DATE
                        .minusDays(7),
                ),
            )

            assertFalse(
                dates.contains(
                    ANCHOR_DATE
                        .minusDays(8),
                ),
            )

            assertEquals(
                totalBefore,
                database
                    .occurrenceDao()
                    .count(),
            )

            assertTrue(
                database
                    .occurrenceDao()
                    .countBetweenDates(
                        startEpochDay =
                            ANCHOR_DATE
                                .minusDays(8)
                                .toEpochDay(),
                        endEpochDay =
                            ANCHOR_DATE
                                .minusDays(8)
                                .toEpochDay(),
                    ) > 0,
            )
        }

    private suspend fun createPlan(
        medicationName: String =
            "داروی نمونه",
        minutesOfDay: List<Int>,
    ): CreatedReportingPlan {
        val outcome =
            carePlanService
                .createMedicationAndSchedule(
                    CreateMedicationScheduleCommand(
                        recipientId =
                            recipientId,
                        medicationName =
                            medicationName,
                        instruction =
                            "دستور نمونه",
                        weekdays =
                            DayOfWeek
                                .entries
                                .toSet(),
                        minutesOfDay =
                            minutesOfDay,
                        startDate =
                            HISTORY_START_DATE,
                        endDate = null,
                        zoneId = "UTC",
                    ),
                )

        val created =
            outcome as
                    CreateMedicationScheduleOutcome
                    .Created

        return CreatedReportingPlan(
            medicationId =
                created.medicationId,
            scheduleVersionId =
                created.scheduleVersionId,
        )
    }

    private suspend fun moveToAnchorAndGuarantee() {
        clock.currentInstant =
            ANCHOR_INSTANT

        generator.guaranteeWindowForAll(
            anchorDate =
                ANCHOR_DATE,
            now = ANCHOR_INSTANT,
        )
    }

    private suspend fun occurrenceForDate(
        scheduleVersionId: String,
        date: LocalDate,
    ) =
        checkNotNull(
            database
                .occurrenceDao()
                .getForVersion(
                    scheduleVersionId,
                )
                .firstOrNull {
                    it.localDateEpochDay ==
                            date.toEpochDay()
                },
        )

    private companion object {
        val HISTORY_START_DATE:
                LocalDate =
            LocalDate.parse(
                "2026-06-16",
            )

        val HISTORY_START_INSTANT:
                Instant =
            Instant.parse(
                "2026-06-16T08:00:00Z",
            )

        val ANCHOR_DATE:
                LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )

        val ANCHOR_INSTANT:
                Instant =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )
    }
}

private data class CreatedReportingPlan(
    val medicationId: String,
    val scheduleVersionId: String,
)

private class ReportingIdSource :
    IdSource {

    private val counter =
        AtomicInteger(0)

    override fun nextId(): String {
        return "reporting-id-" +
                counter.incrementAndGet()
    }
}

private class MutableReportingClock(
    initialInstant: Instant,
) : Clock() {

    var currentInstant:
            Instant =
        initialInstant

    override fun getZone():
            ZoneId {
        return ZoneOffset.UTC
    }

    override fun withZone(
        zone: ZoneId,
    ): Clock {
        return this
    }

    override fun instant():
            Instant {
        return currentInstant
    }
}
