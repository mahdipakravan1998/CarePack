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
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.RoomOccurrenceGenerator
import ir.carepack.domain.report.RoomCaregiverReportService
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

    val occurrenceGenerator = RoomOccurrenceGenerator(
        database = database,
        idSource = idSource,
        candidateResolver = OccurrenceCandidateResolver(),
    )

    val carePlanService = RoomCarePlanService(
        database = database,
        occurrenceGenerator = occurrenceGenerator,
        clock = clock,
        idSource = idSource,
    )

    val reportService = RoomCaregiverReportService(
        database = database,
        clock = clock,
    )

    val todayQueryService = RoomTodayQueryService(database)

    suspend fun createOrGetRecipient(
        displayName: String = DEFAULT_RECIPIENT_NAME,
    ): String = when (
        val outcome = carePlanService.createRecipient(
            CreateRecipientCommand(displayName = displayName),
        )
    ) {
        is CreateRecipientOutcome.Created -> outcome.recipientId
        is CreateRecipientOutcome.AlreadyExists -> outcome.recipientId
        is CreateRecipientOutcome.Invalid -> error(
            "Test recipient creation failed: ${outcome.errors}",
        )
    }

    suspend fun createPlan(
        recipientId: String? = null,
        medicationName: String = DEFAULT_MEDICATION_NAME,
        instruction: String = DEFAULT_INSTRUCTION,
        weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        minutesOfDay: List<Int>,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        zoneId: String = DEFAULT_ZONE_ID,
    ): CreatedTestPlan {
        val resolvedRecipientId = recipientId ?: createOrGetRecipient()
        val outcome = carePlanService.createMedicationAndSchedule(
            CreateMedicationScheduleCommand(
                recipientId = resolvedRecipientId,
                medicationName = medicationName,
                instruction = instruction,
                weekdays = weekdays,
                minutesOfDay = minutesOfDay,
                startDate = startDate,
                endDate = endDate,
                zoneId = zoneId,
            ),
        )
        val created = outcome as? CreateMedicationScheduleOutcome.Created
            ?: error("Test care-plan creation failed: $outcome")

        return CreatedTestPlan(
            recipientId = resolvedRecipientId,
            medicationId = created.medicationId,
            scheduleSeriesId = created.scheduleSeriesId,
            scheduleVersionId = created.scheduleVersionId,
            occurrenceIds = created.occurrenceIds,
        )
    }

    fun moveTo(instant: Instant) {
        clock.currentInstant = instant
    }

    suspend fun guaranteeWindow(anchorDate: LocalDate) {
        occurrenceGenerator.guaranteeWindowForAll(
            anchorDate = anchorDate,
            now = clock.instant(),
        )
    }

    suspend fun occurrenceForDate(
        scheduleVersionId: String,
        date: LocalDate,
    ): OccurrenceEntity = checkNotNull(
        database.occurrenceDao()
            .getForVersion(scheduleVersionId)
            .firstOrNull { it.localDateEpochDay == date.toEpochDay() },
    ) {
        "No occurrence exists for schedule version $scheduleVersionId on $date."
    }

    override fun close() {
        database.close()
    }

    companion object {
        fun create(
            initialInstant: Instant,
            idPrefix: String = "test-id",
            clockZone: ZoneId = ZoneOffset.UTC,
            context: Context = ApplicationProvider.getApplicationContext(),
        ): CarePlanRoomTestFixture {
            val database = Room.inMemoryDatabaseBuilder(
                context,
                CarePackDatabase::class.java,
            ).build()

            return CarePlanRoomTestFixture(
                database = database,
                clock = MutableTestClock(initialInstant, clockZone),
                idSource = IncrementingIdSource(idPrefix),
            )
        }

        private const val DEFAULT_RECIPIENT_NAME = "فرد نمونه"
        private const val DEFAULT_MEDICATION_NAME = "داروی نمونه"
        private const val DEFAULT_INSTRUCTION = "دستور نمونه"
        private const val DEFAULT_ZONE_ID = "Asia/Tehran"
    }
}

internal data class CreatedTestPlan(
    val recipientId: String,
    val medicationId: String,
    val scheduleSeriesId: String,
    val scheduleVersionId: String,
    val occurrenceIds: List<String>,
)
