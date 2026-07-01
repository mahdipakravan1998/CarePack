package ir.carepack.domain.careplan

import ir.carepack.domain.model.MedicationStatus
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.SchedulePattern
import ir.carepack.domain.schedule.SchedulePatternRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

object CarePlanLimits {
    const val RECIPIENT_NAME_MAX_LENGTH = 120
    const val MEDICATION_NAME_MAX_LENGTH = 120
    const val INSTRUCTION_MAX_LENGTH = 1000
}

enum class CarePlanField {
    RECIPIENT_NAME,
    MEDICATION_NAME,
    INSTRUCTION,
    WEEKDAYS,
    TIMES,
    START_DATE,
    END_DATE,
    ZONE_ID,
}

data class CarePlanValidationError(
    val field: CarePlanField,
    val message: String,
)

data class CreateRecipientCommand(
    val displayName: String,
)

sealed interface CreateRecipientOutcome {

    data class Created(
        val recipientId: String,
    ) : CreateRecipientOutcome

    data class AlreadyExists(
        val recipientId: String,
    ) : CreateRecipientOutcome

    data class Invalid(
        val errors: List<CarePlanValidationError>,
    ) : CreateRecipientOutcome
}

data class UpdateRecipientNameCommand(
    val recipientId: String,
    val displayName: String,
)

sealed interface UpdateRecipientNameOutcome {

    data object Updated :
        UpdateRecipientNameOutcome

    data object Unchanged :
        UpdateRecipientNameOutcome

    data object NotFound :
        UpdateRecipientNameOutcome

    data class Invalid(
        val errors: List<CarePlanValidationError>,
    ) : UpdateRecipientNameOutcome
}

data class CreateMedicationScheduleCommand(
    val recipientId: String,
    val medicationName: String,
    val instruction: String,
    val weekdays: Set<DayOfWeek>,
    val minutesOfDay: List<Int>,
    val schedulePattern: SchedulePattern =
        FixedTimeSchedule(
            minutesOfDay =
                minutesOfDay,
        ),
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val zoneId: String,
)

sealed interface CreateMedicationScheduleOutcome {

    data class Created(
        val medicationId: String,
        val scheduleSeriesId: String,
        val scheduleVersionId: String,
        val occurrenceIds: List<String>,
    ) : CreateMedicationScheduleOutcome

    data object RecipientNotFound :
        CreateMedicationScheduleOutcome

    data class Invalid(
        val errors: List<CarePlanValidationError>,
    ) : CreateMedicationScheduleOutcome
}

data class UpdateMedicationTextCommand(
    val medicationId: String,
    val medicationName: String,
    val instruction: String,
)

sealed interface UpdateMedicationTextOutcome {

    data object Updated :
        UpdateMedicationTextOutcome

    data object Unchanged :
        UpdateMedicationTextOutcome

    data object NotFound :
        UpdateMedicationTextOutcome

    data object NotEditable :
        UpdateMedicationTextOutcome

    data class Invalid(
        val errors: List<CarePlanValidationError>,
    ) : UpdateMedicationTextOutcome
}

data class UpdateScheduleCommand(
    val medicationId: String,
    val weekdays: Set<DayOfWeek>,
    val minutesOfDay: List<Int>,
    val schedulePattern: SchedulePattern =
        FixedTimeSchedule(
            minutesOfDay =
                minutesOfDay,
        ),
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val zoneId: String,
)

sealed interface UpdateScheduleOutcome {

    data object Updated :
        UpdateScheduleOutcome

    data object Unchanged :
        UpdateScheduleOutcome

    data object NotFound :
        UpdateScheduleOutcome

    data object NotEditable :
        UpdateScheduleOutcome

    data class Invalid(
        val errors: List<CarePlanValidationError>,
    ) : UpdateScheduleOutcome
}

sealed interface StopMedicationOutcome {

    data object Stopped :
        StopMedicationOutcome

    data object AlreadyStopped :
        StopMedicationOutcome

    data object NotFound :
        StopMedicationOutcome
}

sealed interface ArchiveMedicationOutcome {

    data object Archived :
        ArchiveMedicationOutcome

    data object AlreadyArchived :
        ArchiveMedicationOutcome

    data object MustStopFirst :
        ArchiveMedicationOutcome

    data object NotFound :
        ArchiveMedicationOutcome
}

sealed interface SetupProgress {

    data object Empty :
        SetupProgress

    data class RecipientOnly(
        val recipientId: String,
    ) : SetupProgress

    data object Complete :
        SetupProgress
}

data class SchedulePlan(
    val scheduleSeriesId: String,
    val scheduleVersionId: String,
    val versionNumber: Int,
    val weekdays: Set<DayOfWeek>,
    val times: List<LocalTime>,
    val schedulePattern: SchedulePattern =
        FixedTimeSchedule(
            minutesOfDay =
                times.map(
                    SchedulePatternRules::minuteOfDayFrom,
                ),
        ),
    val zoneId: String,
    val effectiveFrom: Instant,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
)

data class MedicationPlanItem(
    val medicationId: String,
    val name: String,
    val instruction: String,
    val status: MedicationStatus,
    val createdAt: Instant,
    val stoppedAt: Instant?,
    val schedule: SchedulePlan?,
)

data class CarePlanOverview(
    val recipientId: String,
    val recipientDisplayName: String,
    val medications: List<MedicationPlanItem>,
)

data class MedicationEditorSnapshot(
    val medicationId: String,
    val name: String,
    val instruction: String,
    val status: MedicationStatus,
    val schedule: SchedulePlan?,
)

interface CarePlanService {

    suspend fun createRecipient(
        command: CreateRecipientCommand,
    ): CreateRecipientOutcome

    suspend fun updateRecipientName(
        command: UpdateRecipientNameCommand,
    ): UpdateRecipientNameOutcome

    suspend fun createMedicationAndSchedule(
        command: CreateMedicationScheduleCommand,
    ): CreateMedicationScheduleOutcome

    suspend fun updateMedicationText(
        command: UpdateMedicationTextCommand,
    ): UpdateMedicationTextOutcome

    suspend fun updateSchedule(
        command: UpdateScheduleCommand,
    ): UpdateScheduleOutcome

    suspend fun stopMedication(
        medicationId: String,
    ): StopMedicationOutcome

    suspend fun archiveMedication(
        medicationId: String,
    ): ArchiveMedicationOutcome

    suspend fun getSetupProgress():
            SetupProgress

    fun observeCarePlan():
            Flow<CarePlanOverview?>

    suspend fun getMedicationEditor(
        medicationId: String,
    ): MedicationEditorSnapshot?
}
