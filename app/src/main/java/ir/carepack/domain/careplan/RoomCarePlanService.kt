package ir.carepack.domain.careplan

import androidx.room.withTransaction
import ir.carepack.core.id.IdSource
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.CareRecipientEntity
import ir.carepack.data.local.MedicationEntity
import ir.carepack.data.local.MedicationScheduleOverviewRow
import ir.carepack.data.local.OpenScheduleVersionRow
import ir.carepack.data.local.ScheduleSeriesEntity
import ir.carepack.data.local.ScheduleTimeEntity
import ir.carepack.data.local.ScheduleVersionEntity
import ir.carepack.domain.model.MedicationStatus
import ir.carepack.domain.model.OccurrenceCancellationReason
import ir.carepack.domain.occurrence.OccurrenceGenerator
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomCarePlanService(
    private val database: CarePackDatabase,
    private val occurrenceGenerator: OccurrenceGenerator,
    private val clock: Clock,
    private val idSource: IdSource,
) : CarePlanService {

    override suspend fun createRecipient(
        command: CreateRecipientCommand,
    ): CreateRecipientOutcome {
        val validation =
            CarePlanValidation.validateRecipientName(
                command.displayName,
            )

        if (validation is ValidationResult.Invalid) {
            return CreateRecipientOutcome.Invalid(
                errors = validation.errors,
            )
        }

        val displayName =
            checkNotNull(validation.valueOrNull())

        return database.withTransaction<CreateRecipientOutcome> {
            val existing =
                database
                    .careRecipientDao()
                    .getSingleton()

            if (existing != null) {
                CreateRecipientOutcome.AlreadyExists(
                    recipientId = existing.id,
                )
            } else {
                val now = clock.instant()
                val recipientId = idSource.nextId()

                database
                    .careRecipientDao()
                    .insert(
                        CareRecipientEntity(
                            id = recipientId,
                            singletonSlot = SINGLETON_SLOT,
                            displayName = displayName,
                            createdAtEpochMillis =
                                now.toEpochMilli(),
                        ),
                    )

                CreateRecipientOutcome.Created(
                    recipientId = recipientId,
                )
            }
        }
    }

    override suspend fun updateRecipientName(
        command: UpdateRecipientNameCommand,
    ): UpdateRecipientNameOutcome {
        val validation =
            CarePlanValidation.validateRecipientName(
                command.displayName,
            )

        if (validation is ValidationResult.Invalid) {
            return UpdateRecipientNameOutcome.Invalid(
                errors = validation.errors,
            )
        }

        val normalizedName =
            checkNotNull(validation.valueOrNull())

        return database.withTransaction<UpdateRecipientNameOutcome> {
            val recipient =
                database
                    .careRecipientDao()
                    .getSingleton()

            when {
                recipient == null ||
                        recipient.id != command.recipientId -> {
                    UpdateRecipientNameOutcome.NotFound
                }

                recipient.displayName == normalizedName -> {
                    UpdateRecipientNameOutcome.Unchanged
                }

                else -> {
                    val updatedRows =
                        database
                            .careRecipientDao()
                            .updateDisplayName(
                                recipientId = recipient.id,
                                displayName = normalizedName,
                            )

                    check(updatedRows == 1)

                    UpdateRecipientNameOutcome.Updated
                }
            }
        }
    }

    override suspend fun createMedicationAndSchedule(
        command: CreateMedicationScheduleCommand,
    ): CreateMedicationScheduleOutcome {
        val medicationValidation =
            CarePlanValidation.validateMedicationText(
                rawName = command.medicationName,
                rawInstruction = command.instruction,
            )

        val scheduleValidation =
            CarePlanValidation.validateSchedule(
                weekdays = command.weekdays,
                minutesOfDay = command.minutesOfDay,
                startDate = command.startDate,
                endDate = command.endDate,
                rawZoneId = command.zoneId,
            )

        val errors =
            medicationValidation.errorsOrEmpty() +
                    scheduleValidation.errorsOrEmpty()

        if (errors.isNotEmpty()) {
            return CreateMedicationScheduleOutcome.Invalid(
                errors = errors,
            )
        }

        val medication =
            checkNotNull(
                medicationValidation.valueOrNull(),
            )

        val schedule =
            checkNotNull(
                scheduleValidation.valueOrNull(),
            )

        return database.withTransaction<CreateMedicationScheduleOutcome> {
            val recipient =
                database
                    .careRecipientDao()
                    .getSingleton()

            if (
                recipient == null ||
                recipient.id != command.recipientId
            ) {
                return@withTransaction CreateMedicationScheduleOutcome
                    .RecipientNotFound
            }

            val now = clock.instant()
            val nowEpochMillis = now.toEpochMilli()

            val medicationId = idSource.nextId()
            val seriesId = idSource.nextId()
            val versionId = idSource.nextId()

            database
                .medicationDao()
                .insert(
                    MedicationEntity(
                        id = medicationId,
                        careRecipientId = recipient.id,
                        name = medication.name,
                        instruction = medication.instruction,
                        createdAtEpochMillis = nowEpochMillis,
                        stoppedAtEpochMillis = null,
                        archivedAtEpochMillis = null,
                    ),
                )

            database
                .scheduleDao()
                .insertSeries(
                    ScheduleSeriesEntity(
                        id = seriesId,
                        medicationId = medicationId,
                        createdAtEpochMillis = nowEpochMillis,
                        stoppedAtEpochMillis = null,
                    ),
                )

            database
                .scheduleDao()
                .insertVersion(
                    ScheduleVersionEntity(
                        id = versionId,
                        seriesId = seriesId,
                        medicationId = medicationId,
                        versionNumber = FIRST_VERSION,
                        weekdayMask = schedule.weekdayMask,
                        zoneId = schedule.zoneId.id,
                        effectiveFromEpochMillis =
                            nowEpochMillis,
                        effectiveUntilEpochMillis = null,
                        startDateEpochDay =
                            schedule.startDate?.toEpochDay(),
                        endDateEpochDay =
                            schedule.endDate?.toEpochDay(),
                        medicationNameSnapshot =
                            medication.name,
                        medicationInstructionSnapshot =
                            medication.instruction,
                        createdAtEpochMillis =
                            nowEpochMillis,
                    ),
                )

            insertScheduleTimes(
                versionId = versionId,
                minutesOfDay = schedule.minutesOfDay,
            )

            val anchorDate =
                now
                    .atZone(schedule.zoneId)
                    .toLocalDate()

            val generation =
                occurrenceGenerator
                    .guaranteeWindowForSchedule(
                        scheduleVersionId = versionId,
                        anchorDate = anchorDate,
                        now = now,
                    )

            CreateMedicationScheduleOutcome.Created(
                medicationId = medicationId,
                scheduleSeriesId = seriesId,
                scheduleVersionId = versionId,
                occurrenceIds =
                    generation.occurrences.map {
                        it.occurrenceId
                    },
            )
        }
    }

    override suspend fun updateMedicationText(
        command: UpdateMedicationTextCommand,
    ): UpdateMedicationTextOutcome {
        val validation =
            CarePlanValidation.validateMedicationText(
                rawName = command.medicationName,
                rawInstruction = command.instruction,
            )

        if (validation is ValidationResult.Invalid) {
            return UpdateMedicationTextOutcome.Invalid(
                errors = validation.errors,
            )
        }

        val validated =
            checkNotNull(validation.valueOrNull())

        return database.withTransaction<UpdateMedicationTextOutcome> {
            val medication =
                database
                    .medicationDao()
                    .getById(command.medicationId)

            if (medication == null) {
                return@withTransaction UpdateMedicationTextOutcome.NotFound
            }

            if (
                medication.stoppedAtEpochMillis != null ||
                medication.archivedAtEpochMillis != null
            ) {
                return@withTransaction UpdateMedicationTextOutcome.NotEditable
            }

            if (
                medication.name == validated.name &&
                medication.instruction == validated.instruction
            ) {
                return@withTransaction UpdateMedicationTextOutcome.Unchanged
            }

            val openVersions =
                database
                    .scheduleDao()
                    .getOpenVersionsForMedication(
                        medication.id,
                    )

            if (openVersions.isEmpty()) {
                return@withTransaction UpdateMedicationTextOutcome.NotEditable
            }

            val now = clock.instant()

            val updatedRows =
                database
                    .medicationDao()
                    .updateText(
                        medicationId = medication.id,
                        name = validated.name,
                        instruction = validated.instruction,
                    )

            check(updatedRows == 1)

            openVersions.forEach { oldVersion ->
                val oldTimes =
                    database
                        .scheduleDao()
                        .getTimesForVersion(
                            oldVersion.scheduleVersionId,
                        )

                replaceVersion(
                    oldVersion = oldVersion,
                    weekdayMask =
                        oldVersion.weekdayMask,
                    minutesOfDay = oldTimes,
                    zoneId = oldVersion.zoneId,
                    startDateEpochDay =
                        oldVersion.startDateEpochDay,
                    endDateEpochDay =
                        oldVersion.endDateEpochDay,
                    medicationNameSnapshot =
                        validated.name,
                    medicationInstructionSnapshot =
                        validated.instruction,
                    now = now,
                    cancellationReason =
                        OccurrenceCancellationReason
                            .MEDICATION_UPDATED,
                )
            }

            UpdateMedicationTextOutcome.Updated
        }
    }

    override suspend fun updateSchedule(
        command: UpdateScheduleCommand,
    ): UpdateScheduleOutcome {
        val validation =
            CarePlanValidation.validateSchedule(
                weekdays = command.weekdays,
                minutesOfDay = command.minutesOfDay,
                startDate = command.startDate,
                endDate = command.endDate,
                rawZoneId = command.zoneId,
            )

        if (validation is ValidationResult.Invalid) {
            return UpdateScheduleOutcome.Invalid(
                errors = validation.errors,
            )
        }

        val validated =
            checkNotNull(validation.valueOrNull())

        return database.withTransaction<UpdateScheduleOutcome> {
            val medication =
                database
                    .medicationDao()
                    .getById(command.medicationId)

            if (medication == null) {
                return@withTransaction UpdateScheduleOutcome.NotFound
            }

            if (
                medication.stoppedAtEpochMillis != null ||
                medication.archivedAtEpochMillis != null
            ) {
                return@withTransaction UpdateScheduleOutcome.NotEditable
            }

            val openVersions =
                database
                    .scheduleDao()
                    .getOpenVersionsForMedication(
                        medication.id,
                    )

            val oldVersion =
                openVersions.singleOrNull()

            if (oldVersion == null) {
                return@withTransaction UpdateScheduleOutcome.NotEditable
            }

            val oldTimes =
                database
                    .scheduleDao()
                    .getTimesForVersion(
                        oldVersion.scheduleVersionId,
                    )

            if (
                sameSchedule(
                    oldVersion = oldVersion,
                    oldTimes = oldTimes,
                    newSchedule = validated,
                )
            ) {
                return@withTransaction UpdateScheduleOutcome.Unchanged
            }

            replaceVersion(
                oldVersion = oldVersion,
                weekdayMask =
                    validated.weekdayMask,
                minutesOfDay =
                    validated.minutesOfDay,
                zoneId =
                    validated.zoneId.id,
                startDateEpochDay =
                    validated.startDate?.toEpochDay(),
                endDateEpochDay =
                    validated.endDate?.toEpochDay(),
                medicationNameSnapshot =
                    medication.name,
                medicationInstructionSnapshot =
                    medication.instruction,
                now = clock.instant(),
                cancellationReason =
                    OccurrenceCancellationReason
                        .SCHEDULE_REPLACED,
            )

            UpdateScheduleOutcome.Updated
        }
    }

    override suspend fun stopMedication(
        medicationId: String,
    ): StopMedicationOutcome {
        return database.withTransaction<StopMedicationOutcome> {
            val medication =
                database
                    .medicationDao()
                    .getById(medicationId)

            if (medication == null) {
                return@withTransaction StopMedicationOutcome.NotFound
            }

            if (medication.stoppedAtEpochMillis != null) {
                return@withTransaction StopMedicationOutcome.AlreadyStopped
            }

            val now = clock.instant()
            val nowEpochMillis = now.toEpochMilli()

            val openVersions =
                database
                    .scheduleDao()
                    .getOpenVersionsForMedication(
                        medicationId,
                    )

            openVersions.forEach { version ->
                val closedRows =
                    database
                        .scheduleDao()
                        .closeVersion(
                            scheduleVersionId =
                                version.scheduleVersionId,
                            effectiveUntilEpochMillis =
                                nowEpochMillis,
                        )

                check(closedRows == 1)
            }

            database
                .occurrenceDao()
                .cancelFutureUnreportedForMedication(
                    medicationId = medicationId,
                    nowEpochMillis = nowEpochMillis,
                    cancelledAtEpochMillis =
                        nowEpochMillis,
                    cancellationReason =
                        OccurrenceCancellationReason
                            .MEDICATION_STOPPED
                            .name,
                )

            database
                .scheduleDao()
                .stopOpenSeriesForMedication(
                    medicationId = medicationId,
                    stoppedAtEpochMillis =
                        nowEpochMillis,
                )

            val stoppedRows =
                database
                    .medicationDao()
                    .markStopped(
                        medicationId = medicationId,
                        stoppedAtEpochMillis =
                            nowEpochMillis,
                    )

            check(stoppedRows == 1)

            StopMedicationOutcome.Stopped
        }
    }

    override suspend fun archiveMedication(
        medicationId: String,
    ): ArchiveMedicationOutcome {
        return database.withTransaction<ArchiveMedicationOutcome> {
            val medication =
                database
                    .medicationDao()
                    .getById(medicationId)

            if (medication == null) {
                return@withTransaction ArchiveMedicationOutcome.NotFound
            }

            if (medication.archivedAtEpochMillis != null) {
                return@withTransaction ArchiveMedicationOutcome.AlreadyArchived
            }

            if (medication.stoppedAtEpochMillis == null) {
                return@withTransaction ArchiveMedicationOutcome.MustStopFirst
            }

            val archivedRows =
                database
                    .medicationDao()
                    .markArchived(
                        medicationId = medicationId,
                        archivedAtEpochMillis =
                            clock
                                .instant()
                                .toEpochMilli(),
                    )

            check(archivedRows == 1)

            ArchiveMedicationOutcome.Archived
        }
    }

    override suspend fun getSetupProgress(): SetupProgress {
        return database.withTransaction<SetupProgress> {
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

                if (hasMedication && hasSchedule) {
                    SetupProgress.Complete
                } else {
                    SetupProgress.RecipientOnly(
                        recipientId = recipient.id,
                    )
                }
            }
        }
    }

    override fun observeCarePlan(): Flow<CarePlanOverview?> {
        return combine(
            database
                .careRecipientDao()
                .observeSingleton(),
            database
                .medicationDao()
                .observeNonArchivedScheduleRows(),
        ) { recipient, rows ->
            if (recipient == null) {
                null
            } else {
                CarePlanOverview(
                    recipientId = recipient.id,
                    recipientDisplayName =
                        recipient.displayName,
                    medications =
                        rows.toMedicationPlans(),
                )
            }
        }
    }

    override suspend fun getMedicationEditor(
        medicationId: String,
    ): MedicationEditorSnapshot? {
        val medication =
            database
                .medicationDao()
                .getById(medicationId)
                ?: return null

        val rows =
            database
                .medicationDao()
                .getScheduleRowsForMedication(
                    medicationId,
                )

        return MedicationEditorSnapshot(
            medicationId = medication.id,
            name = medication.name,
            instruction = medication.instruction,
            status =
                if (
                    medication.stoppedAtEpochMillis == null
                ) {
                    MedicationStatus.ACTIVE
                } else {
                    MedicationStatus.STOPPED
                },
            schedule =
                rows.toSchedulePlanOrNull(),
        )
    }

    private suspend fun replaceVersion(
        oldVersion: OpenScheduleVersionRow,
        weekdayMask: Int,
        minutesOfDay: List<Int>,
        zoneId: String,
        startDateEpochDay: Long?,
        endDateEpochDay: Long?,
        medicationNameSnapshot: String,
        medicationInstructionSnapshot: String,
        now: Instant,
        cancellationReason: OccurrenceCancellationReason,
    ) {
        val nowEpochMillis = now.toEpochMilli()

        val closedRows =
            database
                .scheduleDao()
                .closeVersion(
                    scheduleVersionId =
                        oldVersion.scheduleVersionId,
                    effectiveUntilEpochMillis =
                        nowEpochMillis,
                )

        check(closedRows == 1)

        database
            .occurrenceDao()
            .cancelFutureUnreportedForVersion(
                scheduleVersionId =
                    oldVersion.scheduleVersionId,
                nowEpochMillis = nowEpochMillis,
                cancelledAtEpochMillis =
                    nowEpochMillis,
                cancellationReason =
                    cancellationReason.name,
            )

        val newVersionId = idSource.nextId()

        database
            .scheduleDao()
            .insertVersion(
                ScheduleVersionEntity(
                    id = newVersionId,
                    seriesId =
                        oldVersion.scheduleSeriesId,
                    medicationId =
                        oldVersion.medicationId,
                    versionNumber =
                        oldVersion.versionNumber + 1,
                    weekdayMask = weekdayMask,
                    zoneId = zoneId,
                    effectiveFromEpochMillis =
                        nowEpochMillis,
                    effectiveUntilEpochMillis = null,
                    startDateEpochDay =
                        startDateEpochDay,
                    endDateEpochDay =
                        endDateEpochDay,
                    medicationNameSnapshot =
                        medicationNameSnapshot,
                    medicationInstructionSnapshot =
                        medicationInstructionSnapshot,
                    createdAtEpochMillis =
                        nowEpochMillis,
                ),
            )

        insertScheduleTimes(
            versionId = newVersionId,
            minutesOfDay = minutesOfDay,
        )

        val anchorDate =
            now
                .atZone(ZoneId.of(zoneId))
                .toLocalDate()

        occurrenceGenerator
            .guaranteeWindowForSchedule(
                scheduleVersionId =
                    newVersionId,
                anchorDate = anchorDate,
                now = now,
            )
    }

    private suspend fun insertScheduleTimes(
        versionId: String,
        minutesOfDay: List<Int>,
    ) {
        database
            .scheduleDao()
            .insertTimes(
                minutesOfDay
                    .distinct()
                    .sorted()
                    .map { minute ->
                        ScheduleTimeEntity(
                            scheduleVersionId =
                                versionId,
                            minuteOfDay = minute,
                        )
                    },
            )
    }

    private fun sameSchedule(
        oldVersion: OpenScheduleVersionRow,
        oldTimes: List<Int>,
        newSchedule: ValidatedScheduleDefinition,
    ): Boolean {
        return oldVersion.weekdayMask ==
                newSchedule.weekdayMask &&
                oldVersion.zoneId ==
                newSchedule.zoneId.id &&
                oldVersion.startDateEpochDay ==
                newSchedule.startDate?.toEpochDay() &&
                oldVersion.endDateEpochDay ==
                newSchedule.endDate?.toEpochDay() &&
                oldTimes.sorted() ==
                newSchedule.minutesOfDay.sorted()
    }

    private companion object {
        const val SINGLETON_SLOT = 1
        const val FIRST_VERSION = 1
    }
}

private fun List<MedicationScheduleOverviewRow>
        .toMedicationPlans(): List<MedicationPlanItem> {
    return groupBy {
        it.medicationId
    }.values.map { rows ->
        val first = rows.first()

        MedicationPlanItem(
            medicationId =
                first.medicationId,
            name =
                first.medicationName,
            instruction =
                first.medicationInstruction,
            status =
                if (
                    first.medicationStoppedAtEpochMillis == null
                ) {
                    MedicationStatus.ACTIVE
                } else {
                    MedicationStatus.STOPPED
                },
            createdAt =
                Instant.ofEpochMilli(
                    first.medicationCreatedAtEpochMillis,
                ),
            stoppedAt =
                first
                    .medicationStoppedAtEpochMillis
                    ?.let(Instant::ofEpochMilli),
            schedule =
                rows.toSchedulePlanOrNull(),
        )
    }.sortedBy {
        it.createdAt
    }
}

private fun List<MedicationScheduleOverviewRow>
        .toSchedulePlanOrNull(): SchedulePlan? {
    val first =
        firstOrNull {
            it.scheduleVersionId != null
        } ?: return null

    val versionId =
        checkNotNull(first.scheduleVersionId)

    val seriesId =
        checkNotNull(first.scheduleSeriesId)

    val versionNumber =
        checkNotNull(first.scheduleVersionNumber)

    val weekdayMask =
        checkNotNull(first.weekdayMask)

    val zoneId =
        checkNotNull(first.zoneId)

    val effectiveFromEpochMillis =
        checkNotNull(
            first.effectiveFromEpochMillis,
        )

    return SchedulePlan(
        scheduleSeriesId = seriesId,
        scheduleVersionId = versionId,
        versionNumber = versionNumber,
        weekdays =
            weekdayMask.toDaysOfWeek(),
        times =
            mapNotNull {
                it.minuteOfDay
            }
                .distinct()
                .sorted()
                .map { minute ->
                    LocalTime.of(
                        minute / MINUTES_PER_HOUR,
                        minute % MINUTES_PER_HOUR,
                    )
                },
        zoneId = zoneId,
        effectiveFrom =
            Instant.ofEpochMilli(
                effectiveFromEpochMillis,
            ),
        startDate =
            first.startDateEpochDay?.let(
                LocalDate::ofEpochDay,
            ),
        endDate =
            first.endDateEpochDay?.let(
                LocalDate::ofEpochDay,
            ),
    )
}

private fun Int.toDaysOfWeek(): Set<DayOfWeek> {
    return DayOfWeek.entries
        .filterTo(linkedSetOf()) { day ->
            this and
                    (1 shl (day.value - 1)) != 0
        }
}

private const val MINUTES_PER_HOUR = 60
