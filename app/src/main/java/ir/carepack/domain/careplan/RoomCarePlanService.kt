package ir.carepack.domain.careplan

import androidx.room.withTransaction
import ir.carepack.core.id.IdSource
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.CareRecipientEntity
import ir.carepack.data.local.MedicationEntity
import ir.carepack.data.local.ScheduleSeriesEntity
import ir.carepack.data.local.ScheduleTimeEntity
import ir.carepack.data.local.ScheduleVersionEntity
import ir.carepack.domain.occurrence.OccurrenceGenerator
import java.time.Clock

class RoomCarePlanService(
    private val database: CarePackDatabase,
    private val occurrenceGenerator: OccurrenceGenerator,
    private val clock: Clock,
    private val idSource: IdSource,
) : CarePlanService {

    override suspend fun createRecipient(
        command: CreateRecipientCommand,
    ): CreateRecipientOutcome {
        val normalizedName =
            CarePlanValidation.normalizeRequiredText(
                command.displayName,
            ) ?: return CreateRecipientOutcome.Invalid(
                reason = "نام فرد تحت مراقبت نمی‌تواند خالی باشد.",
            )

        return database.withTransaction {
            val existingRecipient =
                database
                    .careRecipientDao()
                    .getSingleton()

            if (existingRecipient != null) {
                CreateRecipientOutcome.AlreadyExists(
                    recipientId = existingRecipient.id,
                )
            } else {
                val recipientId = idSource.nextId()

                val nowEpochMillis =
                    clock
                        .instant()
                        .toEpochMilli()

                database
                    .careRecipientDao()
                    .insert(
                        CareRecipientEntity(
                            id = recipientId,
                            singletonSlot = SINGLETON_SLOT,
                            displayName = normalizedName,
                            createdAtEpochMillis =
                                nowEpochMillis,
                        ),
                    )

                CreateRecipientOutcome.Created(
                    recipientId = recipientId,
                )
            }
        }
    }

    override suspend fun createMedicationAndSchedule(
        command: CreateMedicationScheduleCommand,
    ): CreateMedicationScheduleOutcome {
        val medicationName =
            CarePlanValidation.normalizeRequiredText(
                command.medicationName,
            ) ?: return CreateMedicationScheduleOutcome.Invalid(
                reason = "نام دارو نمی‌تواند خالی باشد.",
            )

        val instruction =
            CarePlanValidation.normalizeRequiredText(
                command.instruction,
            ) ?: return CreateMedicationScheduleOutcome.Invalid(
                reason =
                    "دستور مصرف یا توضیح مراقبت نمی‌تواند خالی باشد.",
            )

        val zoneId =
            CarePlanValidation.parseZoneId(
                command.zoneId,
            ) ?: return CreateMedicationScheduleOutcome.Invalid(
                reason = "منطقه زمانی معتبر نیست.",
            )

        val minuteOfDay =
            command.localTime.hour * MINUTES_PER_HOUR +
                    command.localTime.minute

        val weekdayMask =
            CarePlanValidation.weekdayMask(
                command.weekday,
            )

        val now = clock.instant()

        val nowEpochMillis =
            now.toEpochMilli()

        return database.withTransaction {
            val recipient =
                database
                    .careRecipientDao()
                    .getSingleton()

            if (
                recipient == null ||
                recipient.id != command.recipientId
            ) {
                CreateMedicationScheduleOutcome.Invalid(
                    reason = "فرد تحت مراقبت پیدا نشد.",
                )
            } else {
                val medicationId =
                    idSource.nextId()

                val seriesId =
                    idSource.nextId()

                val versionId =
                    idSource.nextId()

                database
                    .medicationDao()
                    .insert(
                        MedicationEntity(
                            id = medicationId,
                            careRecipientId =
                                recipient.id,
                            name = medicationName,
                            instruction = instruction,
                            createdAtEpochMillis =
                                nowEpochMillis,
                        ),
                    )

                database
                    .scheduleDao()
                    .insertSeries(
                        ScheduleSeriesEntity(
                            id = seriesId,
                            medicationId =
                                medicationId,
                            createdAtEpochMillis =
                                nowEpochMillis,
                            stoppedAtEpochMillis =
                                null,
                        ),
                    )

                database
                    .scheduleDao()
                    .insertVersion(
                        ScheduleVersionEntity(
                            id = versionId,
                            seriesId = seriesId,
                            medicationId =
                                medicationId,
                            versionNumber =
                                FIRST_VERSION,
                            weekdayMask =
                                weekdayMask,
                            zoneId = zoneId.id,
                            effectiveFromEpochMillis =
                                nowEpochMillis,
                            effectiveUntilEpochMillis =
                                null,
                            startDateEpochDay =
                                null,
                            endDateEpochDay =
                                null,
                            medicationNameSnapshot =
                                medicationName,
                            medicationInstructionSnapshot =
                                instruction,
                            createdAtEpochMillis =
                                nowEpochMillis,
                        ),
                    )

                database
                    .scheduleDao()
                    .insertTime(
                        ScheduleTimeEntity(
                            scheduleVersionId =
                                versionId,
                            minuteOfDay =
                                minuteOfDay,
                        ),
                    )

                val anchorDate =
                    now
                        .atZone(zoneId)
                        .toLocalDate()

                val generationSummary =
                    occurrenceGenerator
                        .guaranteeForSchedule(
                            scheduleVersionId =
                                versionId,
                            anchorDate =
                                anchorDate,
                            now = now,
                        )

                CreateMedicationScheduleOutcome.Created(
                    medicationId =
                        medicationId,
                    scheduleSeriesId =
                        seriesId,
                    scheduleVersionId =
                        versionId,
                    occurrenceIds =
                        generationSummary
                            .occurrences
                            .map { occurrence ->
                                occurrence.occurrenceId
                            },
                )
            }
        }
    }

    override suspend fun getSetupProgress():
            SetupProgress {
        return database.withTransaction {
            val recipient =
                database
                    .careRecipientDao()
                    .getSingleton()

            if (recipient == null) {
                SetupProgress.Empty
            } else {
                val hasMedication =
                    database
                        .medicationDao()
                        .count() > 0

                val hasSchedule =
                    database
                        .scheduleDao()
                        .countVersions() > 0

                if (
                    hasMedication &&
                    hasSchedule
                ) {
                    SetupProgress.Complete
                } else {
                    SetupProgress.RecipientOnly(
                        recipientId =
                            recipient.id,
                    )
                }
            }
        }
    }

    private companion object {
        const val SINGLETON_SLOT = 1
        const val FIRST_VERSION = 1
        const val MINUTES_PER_HOUR = 60
    }
}
