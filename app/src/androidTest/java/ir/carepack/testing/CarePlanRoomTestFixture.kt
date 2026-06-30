package ir.carepack.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.OccurrenceEntity
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import ir.carepack.domain.careplan.RoomCarePlanService
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.report.RoomCaregiverReportService
import ir.carepack.domain.reminder.RoomReminderScheduleSource
import ir.carepack.domain.today.RoomTodayQueryService
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal class CarePlanRoomTestFixture private constructor(
    val database: CarePackDatabase,
    val clock: MutableTestClock,
    val idSource: IncrementingIdSource,
) : AutoCloseable {

    val occurrenceGenerator =
        RoomOccurrenceGenerator(
            database = database,
            idSource = idSource,
            candidateResolver =
                OccurrenceCandidateResolver(),
        )

    val carePlanService =
        RoomCarePlanService(
            database = database,
            occurrenceGenerator =
                occurrenceGenerator,
            clock = clock,
            idSource = idSource,
        )

    val reportService =
        RoomCaregiverReportService(
            database = database,
            clock = clock,
        )

    val todayQueryService =
        RoomTodayQueryService(
            database = database,
        )

    val reminderScheduleSource =
        RoomReminderScheduleSource(
            database = database,
        )

    suspend fun createOrGetRecipient(
        displayName: String =
            DEFAULT_RECIPIENT_NAME,
    ): String =
        when (
            val outcome =
                carePlanService.createRecipient(
                    CreateRecipientCommand(
                        displayName =
                            displayName,
                    ),
                )
        ) {
            is CreateRecipientOutcome.Created -> {
                outcome.recipientId
            }

            is CreateRecipientOutcome.AlreadyExists -> {
                outcome.recipientId
            }

            is CreateRecipientOutcome.Invalid -> {
                error(
                    "Test recipient creation failed: ${outcome.errors}",
                )
            }
        }

    suspend fun createPlan(
        recipientId: String? = null,
        medicationName: String =
            DEFAULT_MEDICATION_NAME,
        instruction: String =
            DEFAULT_INSTRUCTION,
        weekdays: Set<DayOfWeek> =
            DayOfWeek.entries.toSet(),
        minutesOfDay: List<Int>,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        zoneId: String = DEFAULT_ZONE_ID,
    ): CreatedTestPlan {
        val resolvedRecipientId =
            recipientId ?: createOrGetRecipient()

        val outcome =
            carePlanService
                .createMedicationAndSchedule(
                    CreateMedicationScheduleCommand(
                        recipientId =
                            resolvedRecipientId,
                        medicationName =
                            medicationName,
                        instruction =
                            instruction,
                        weekdays =
                            weekdays,
                        minutesOfDay =
                            minutesOfDay,
                        startDate =
                            startDate,
                        endDate =
                            endDate,
                        zoneId =
                            zoneId,
                    ),
                )

        return when (outcome) {
            is CreateMedicationScheduleOutcome.Created -> {
                CreatedTestPlan(
                    recipientId =
                        resolvedRecipientId,
                    medicationId =
                        outcome.medicationId,
                    scheduleSeriesId =
                        outcome.scheduleSeriesId,
                    scheduleVersionId =
                        outcome.scheduleVersionId,
                    occurrenceIds =
                        outcome.occurrenceIds,
                )
            }

            CreateMedicationScheduleOutcome.RecipientNotFound -> {
                error(
                    "Test care-plan creation failed: recipient not found.",
                )
            }

            is CreateMedicationScheduleOutcome.Invalid -> {
                error(
                    "Test care-plan creation failed: ${outcome.errors}",
                )
            }
        }
    }

    suspend fun occurrencesForMedication(
        medicationId: String,
    ): List<OccurrenceEntity> =
        database
            .occurrenceDao()
            .getForMedication(
                medicationId,
            )

    suspend fun occurrenceOn(
        medicationId: String,
        date: LocalDate,
        minuteOfDay: Int,
    ): OccurrenceEntity =
        occurrencesForMedication(
            medicationId,
        )
            .first {
                it.localEpochDay ==
                        date.toEpochDay() &&
                        it.minuteOfDay ==
                        minuteOfDay
            }

    suspend fun occurrenceForDate(
        scheduleVersionId: String,
        date: LocalDate,
    ): OccurrenceEntity =
        checkNotNull(
            database
                .occurrenceDao()
                .getForVersion(
                    scheduleVersionId,
                )
                .firstOrNull {
                    it.localEpochDay ==
                            date.toEpochDay()
                },
        ) {
            "No occurrence exists for schedule version $scheduleVersionId on $date."
        }

    suspend fun report(
        occurrenceId: String,
        state: CaregiverReportState,
    ) {
        reportService.setReport(
            occurrenceId =
                occurrenceId,
            newState =
                state,
        )
    }

    suspend fun generateAll(
        anchorDate: LocalDate =
            clock
                .instant()
                .atZone(
                    ZoneOffset.UTC,
                )
                .toLocalDate(),
    ) {
        occurrenceGenerator
            .guaranteeWindowForAll(
                anchorDate = anchorDate,
                now = clock.instant(),
            )
    }

    suspend fun guaranteeWindow(
        anchorDate: LocalDate,
    ) {
        generateAll(
            anchorDate =
                anchorDate,
        )
    }

    fun advanceTo(
        instant: Instant,
    ) {
        clock.currentInstant =
            instant
    }

    fun moveTo(
        instant: Instant,
    ) {
        advanceTo(
            instant,
        )
    }

    override fun close() {
        database.close()
    }

    companion object {

        const val DEFAULT_RECIPIENT_NAME =
            "مادر"

        const val DEFAULT_MEDICATION_NAME =
            "داروی صبح"

        const val DEFAULT_INSTRUCTION =
            "بعد از صبحانه"

        const val DEFAULT_ZONE_ID =
            "UTC"

        val DEFAULT_INSTANT: Instant =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        fun create(
            initialInstant: Instant =
                DEFAULT_INSTANT,
            idPrefix: String =
                "room-fixture-id",
            clockZone: ZoneId =
                ZoneOffset.UTC,
            context: Context =
                ApplicationProvider
                    .getApplicationContext(),
        ): CarePlanRoomTestFixture {
            val database =
                Room
                    .inMemoryDatabaseBuilder(
                        context,
                        CarePackDatabase::class.java,
                    )
                    .build()

            return CarePlanRoomTestFixture(
                database = database,
                clock =
                    MutableTestClock(
                        initialInstant =
                            initialInstant,
                        zone =
                            clockZone,
                    ),
                idSource =
                    IncrementingIdSource(
                        prefix =
                            idPrefix,
                    ),
            )
        }
    }
}

internal data class CreatedTestPlan(
    val recipientId: String,
    val medicationId: String,
    val scheduleSeriesId: String,
    val scheduleVersionId: String,
    val occurrenceIds: List<String>,
)
