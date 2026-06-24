package ir.carepack.domain.careplan

import java.time.DayOfWeek
import java.time.LocalTime

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
        val reason: String,
    ) : CreateRecipientOutcome
}

data class CreateMedicationScheduleCommand(
    val recipientId: String,
    val medicationName: String,
    val instruction: String,
    val weekday: DayOfWeek,
    val localTime: LocalTime,
    val zoneId: String,
)

sealed interface CreateMedicationScheduleOutcome {
    data class Created(
        val medicationId: String,
        val scheduleSeriesId: String,
        val scheduleVersionId: String,
        val occurrenceIds: List<String>,
    ) : CreateMedicationScheduleOutcome

    data class Invalid(
        val reason: String,
    ) : CreateMedicationScheduleOutcome
}

sealed interface SetupProgress {
    data object Empty : SetupProgress

    data class RecipientOnly(
        val recipientId: String,
    ) : SetupProgress

    data object Complete : SetupProgress
}

interface CarePlanService {
    suspend fun createRecipient(
        command: CreateRecipientCommand,
    ): CreateRecipientOutcome

    suspend fun createMedicationAndSchedule(
        command: CreateMedicationScheduleCommand,
    ): CreateMedicationScheduleOutcome

    suspend fun getSetupProgress(): SetupProgress
}