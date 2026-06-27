package ir.carepack.domain.careplan

import androidx.room.withTransaction
import ir.carepack.core.id.IdSource
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.CareRecipientEntity
import ir.carepack.data.local.MedicationDao
import ir.carepack.data.local.MedicationEntity
import ir.carepack.data.local.MedicationScheduleOverviewRow
import ir.carepack.data.local.OccurrenceDao
import ir.carepack.data.local.OpenScheduleVersionRow
import ir.carepack.data.local.ScheduleDao
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
    private val recipientDao = database.careRecipientDao()
    private val medicationDao: MedicationDao = database.medicationDao()
    private val scheduleDao: ScheduleDao = database.scheduleDao()
    private val occurrenceDao: OccurrenceDao = database.occurrenceDao()

    override suspend fun createRecipient(
        command: CreateRecipientCommand,
    ): CreateRecipientOutcome {
        val validation = CarePlanValidation.validateRecipientName(command.displayName)
        if (validation is ValidationResult.Invalid) {
            return CreateRecipientOutcome.Invalid(validation.errors)
        }
        val displayName = checkNotNull(validation.valueOrNull())

        return database.withTransaction {
            val existing = recipientDao.getSingleton()
            if (existing != null) {
                CreateRecipientOutcome.AlreadyExists(existing.id)
            } else {
                val recipientId = idSource.nextId()
                recipientDao.insert(
                    CareRecipientEntity(
                        id = recipientId,
                        singletonSlot = SINGLETON_SLOT,
                        displayName = displayName,
                        createdAtEpochMillis = clock.instant().toEpochMilli(),
                    ),
                )
                CreateRecipientOutcome.Created(recipientId)
            }
        }
    }

    override suspend fun updateRecipientName(
        command: UpdateRecipientNameCommand,
    ): UpdateRecipientNameOutcome {
        val validation = CarePlanValidation.validateRecipientName(command.displayName)
        if (validation is ValidationResult.Invalid) {
            return UpdateRecipientNameOutcome.Invalid(validation.errors)
        }
        val normalizedName = checkNotNull(validation.valueOrNull())

        return database.withTransaction {
            val recipient = recipientDao.getSingleton()
            when {
                recipient == null || recipient.id != command.recipientId -> {
                    UpdateRecipientNameOutcome.NotFound
                }
                recipient.displayName == normalizedName -> UpdateRecipientNameOutcome.Unchanged
                else -> {
                    check(
                        recipientDao.updateDisplayName(
                            recipientId = recipient.id,
                            displayName = normalizedName,
                        ) == 1,
                    )
                    UpdateRecipientNameOutcome.Updated
                }
            }
        }
    }

    override suspend fun createMedicationAndSchedule(
        command: CreateMedicationScheduleCommand,
    ): CreateMedicationScheduleOutcome {
        val medicationValidation = CarePlanValidation.validateMedicationText(
            rawName = command.medicationName,
            rawInstruction = command.instruction,
        )
        val scheduleValidation = CarePlanValidation.validateSchedule(
            weekdays = command.weekdays,
            minutesOfDay = command.minutesOfDay,
            startDate = command.startDate,
            endDate = command.endDate,
            rawZoneId = command.zoneId,
        )
        val errors = medicationValidation.errorsOrEmpty() + scheduleValidation.errorsOrEmpty()
        if (errors.isNotEmpty()) {
            return CreateMedicationScheduleOutcome.Invalid(errors)
        }

        val medication = checkNotNull(medicationValidation.valueOrNull())
        val schedule = checkNotNull(scheduleValidation.valueOrNull())

        return database.withTransaction {
            val recipient = recipientDao.getSingleton()
            if (recipient == null || recipient.id != command.recipientId) {
                return@withTransaction CreateMedicationScheduleOutcome.RecipientNotFound
            }

            val now = clock.instant()
            val nowEpochMillis = now.toEpochMilli()
            val medicationId = idSource.nextId()
            val seriesId = idSource.nextId()
            val versionId = idSource.nextId()

            medicationDao.insert(
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
            scheduleDao.insertSeries(
                ScheduleSeriesEntity(
                    id = seriesId,
                    medicationId = medicationId,
                    createdAtEpochMillis = nowEpochMillis,
                    stoppedAtEpochMillis = null,
                ),
            )
            scheduleDao.insertVersion(
                ScheduleVersionEntity(
                    id = versionId,
                    seriesId = seriesId,
                    medicationId = medicationId,
                    versionNumber = FIRST_VERSION,
                    weekdayMask = schedule.weekdayMask,
                    zoneId = schedule.zoneId.id,
                    effectiveFromEpochMillis = nowEpochMillis,
                    effectiveUntilEpochMillis = null,
                    startDateEpochDay = schedule.startDate?.toEpochDay(),
                    endDateEpochDay = schedule.endDate?.toEpochDay(),
                    medicationNameSnapshot = medication.name,
                    medicationInstructionSnapshot = medication.instruction,
                    createdAtEpochMillis = nowEpochMillis,
                ),
            )
            insertScheduleTimes(versionId, schedule.minutesOfDay)

            val generation = occurrenceGenerator.guaranteeWindowForSchedule(
                scheduleVersionId = versionId,
                anchorDate = now.atZone(schedule.zoneId).toLocalDate(),
                now = now,
            )

            CreateMedicationScheduleOutcome.Created(
                medicationId = medicationId,
                scheduleSeriesId = seriesId,
                scheduleVersionId = versionId,
                occurrenceIds = generation.occurrences.map { it.occurrenceId },
            )
        }
    }

    override suspend fun updateMedicationText(
        command: UpdateMedicationTextCommand,
    ): UpdateMedicationTextOutcome {
        val validation = CarePlanValidation.validateMedicationText(
            rawName = command.medicationName,
            rawInstruction = command.instruction,
        )
        if (validation is ValidationResult.Invalid) {
            return UpdateMedicationTextOutcome.Invalid(validation.errors)
        }
        val validated = checkNotNull(validation.valueOrNull())

        return database.withTransaction {
            val medication = medicationDao.getById(command.medicationId)
                ?: return@withTransaction UpdateMedicationTextOutcome.NotFound

            if (!medication.isEditable) {
                return@withTransaction UpdateMedicationTextOutcome.NotEditable
            }
            if (medication.name == validated.name && medication.instruction == validated.instruction) {
                return@withTransaction UpdateMedicationTextOutcome.Unchanged
            }

            val openVersions = scheduleDao.getOpenVersionsForMedication(medication.id)
            if (openVersions.isEmpty()) {
                return@withTransaction UpdateMedicationTextOutcome.NotEditable
            }

            val now = clock.instant()
            check(
                medicationDao.updateText(
                    medicationId = medication.id,
                    name = validated.name,
                    instruction = validated.instruction,
                ) == 1,
            )

            openVersions.forEach { oldVersion ->
                replaceVersion(
                    oldVersion = oldVersion,
                    definition = oldVersion.toDefinition(
                        scheduleDao.getTimesForVersion(oldVersion.scheduleVersionId),
                    ),
                    medicationNameSnapshot = validated.name,
                    medicationInstructionSnapshot = validated.instruction,
                    now = now,
                    cancellationReason = OccurrenceCancellationReason.MEDICATION_UPDATED,
                )
            }

            UpdateMedicationTextOutcome.Updated
        }
    }

    override suspend fun updateSchedule(command: UpdateScheduleCommand): UpdateScheduleOutcome {
        val validation = CarePlanValidation.validateSchedule(
            weekdays = command.weekdays,
            minutesOfDay = command.minutesOfDay,
            startDate = command.startDate,
            endDate = command.endDate,
            rawZoneId = command.zoneId,
        )
        if (validation is ValidationResult.Invalid) {
            return UpdateScheduleOutcome.Invalid(validation.errors)
        }
        val validated = checkNotNull(validation.valueOrNull())

        return database.withTransaction {
            val medication = medicationDao.getById(command.medicationId)
                ?: return@withTransaction UpdateScheduleOutcome.NotFound

            if (!medication.isEditable) {
                return@withTransaction UpdateScheduleOutcome.NotEditable
            }

            val oldVersion = scheduleDao
                .getOpenVersionsForMedication(medication.id)
                .singleOrNull()
                ?: return@withTransaction UpdateScheduleOutcome.NotEditable
            val oldDefinition = oldVersion.toDefinition(
                scheduleDao.getTimesForVersion(oldVersion.scheduleVersionId),
            )
            val newDefinition = validated.toDefinition()

            if (oldDefinition == newDefinition) {
                return@withTransaction UpdateScheduleOutcome.Unchanged
            }

            replaceVersion(
                oldVersion = oldVersion,
                definition = newDefinition,
                medicationNameSnapshot = medication.name,
                medicationInstructionSnapshot = medication.instruction,
                now = clock.instant(),
                cancellationReason = OccurrenceCancellationReason.SCHEDULE_REPLACED,
            )
            UpdateScheduleOutcome.Updated
        }
    }

    override suspend fun stopMedication(medicationId: String): StopMedicationOutcome =
        database.withTransaction {
            val medication = medicationDao.getById(medicationId)
                ?: return@withTransaction StopMedicationOutcome.NotFound
            if (medication.stoppedAtEpochMillis != null) {
                return@withTransaction StopMedicationOutcome.AlreadyStopped
            }

            val nowEpochMillis = clock.instant().toEpochMilli()
            scheduleDao.getOpenVersionsForMedication(medicationId).forEach { version ->
                check(
                    scheduleDao.closeVersion(
                        scheduleVersionId = version.scheduleVersionId,
                        effectiveUntilEpochMillis = nowEpochMillis,
                    ) == 1,
                )
            }
            occurrenceDao.cancelFutureUnreportedForMedication(
                medicationId = medicationId,
                nowEpochMillis = nowEpochMillis,
                cancelledAtEpochMillis = nowEpochMillis,
                cancellationReason = OccurrenceCancellationReason.MEDICATION_STOPPED.name,
            )
            scheduleDao.stopOpenSeriesForMedication(
                medicationId = medicationId,
                stoppedAtEpochMillis = nowEpochMillis,
            )
            check(
                medicationDao.markStopped(
                    medicationId = medicationId,
                    stoppedAtEpochMillis = nowEpochMillis,
                ) == 1,
            )
            StopMedicationOutcome.Stopped
        }

    override suspend fun archiveMedication(medicationId: String): ArchiveMedicationOutcome =
        database.withTransaction {
            val medication = medicationDao.getById(medicationId)
                ?: return@withTransaction ArchiveMedicationOutcome.NotFound

            when {
                medication.archivedAtEpochMillis != null -> {
                    ArchiveMedicationOutcome.AlreadyArchived
                }
                medication.stoppedAtEpochMillis == null -> ArchiveMedicationOutcome.MustStopFirst
                else -> {
                    check(
                        medicationDao.markArchived(
                            medicationId = medicationId,
                            archivedAtEpochMillis = clock.instant().toEpochMilli(),
                        ) == 1,
                    )
                    ArchiveMedicationOutcome.Archived
                }
            }
        }

    override suspend fun getSetupProgress(): SetupProgress = database.withTransaction {
        val recipient = recipientDao.getSingleton() ?: return@withTransaction SetupProgress.Empty
        val complete = medicationDao.count() > 0 && scheduleDao.countVersions() > 0
        if (complete) SetupProgress.Complete else SetupProgress.RecipientOnly(recipient.id)
    }

    override fun observeCarePlan(): Flow<CarePlanOverview?> = combine(
        recipientDao.observeSingleton(),
        medicationDao.observeNonArchivedScheduleRows(),
    ) { recipient, rows ->
        recipient?.let {
            CarePlanOverview(
                recipientId = it.id,
                recipientDisplayName = it.displayName,
                medications = rows.toMedicationPlans(),
            )
        }
    }

    override suspend fun getMedicationEditor(medicationId: String): MedicationEditorSnapshot? {
        val medication = medicationDao.getById(medicationId) ?: return null
        val rows = medicationDao.getScheduleRowsForMedication(medicationId)

        return MedicationEditorSnapshot(
            medicationId = medication.id,
            name = medication.name,
            instruction = medication.instruction,
            status = if (medication.stoppedAtEpochMillis == null) {
                MedicationStatus.ACTIVE
            } else {
                MedicationStatus.STOPPED
            },
            schedule = rows.toSchedulePlanOrNull(),
        )
    }

    private suspend fun replaceVersion(
        oldVersion: OpenScheduleVersionRow,
        definition: VersionDefinition,
        medicationNameSnapshot: String,
        medicationInstructionSnapshot: String,
        now: Instant,
        cancellationReason: OccurrenceCancellationReason,
    ) {
        val nowEpochMillis = now.toEpochMilli()
        check(
            scheduleDao.closeVersion(
                scheduleVersionId = oldVersion.scheduleVersionId,
                effectiveUntilEpochMillis = nowEpochMillis,
            ) == 1,
        )
        occurrenceDao.cancelFutureUnreportedForVersion(
            scheduleVersionId = oldVersion.scheduleVersionId,
            nowEpochMillis = nowEpochMillis,
            cancelledAtEpochMillis = nowEpochMillis,
            cancellationReason = cancellationReason.name,
        )

        val newVersionId = idSource.nextId()
        scheduleDao.insertVersion(
            ScheduleVersionEntity(
                id = newVersionId,
                seriesId = oldVersion.scheduleSeriesId,
                medicationId = oldVersion.medicationId,
                versionNumber = oldVersion.versionNumber + 1,
                weekdayMask = definition.weekdayMask,
                zoneId = definition.zoneId,
                effectiveFromEpochMillis = nowEpochMillis,
                effectiveUntilEpochMillis = null,
                startDateEpochDay = definition.startDateEpochDay,
                endDateEpochDay = definition.endDateEpochDay,
                medicationNameSnapshot = medicationNameSnapshot,
                medicationInstructionSnapshot = medicationInstructionSnapshot,
                createdAtEpochMillis = nowEpochMillis,
            ),
        )
        insertScheduleTimes(newVersionId, definition.minutesOfDay)

        occurrenceGenerator.guaranteeWindowForSchedule(
            scheduleVersionId = newVersionId,
            anchorDate = now.atZone(ZoneId.of(definition.zoneId)).toLocalDate(),
            now = now,
        )
    }

    private suspend fun insertScheduleTimes(versionId: String, minutesOfDay: List<Int>) {
        scheduleDao.insertTimes(
            minutesOfDay.distinct().sorted().map { minute ->
                ScheduleTimeEntity(
                    scheduleVersionId = versionId,
                    minuteOfDay = minute,
                )
            },
        )
    }

    private companion object {
        const val SINGLETON_SLOT = 1
        const val FIRST_VERSION = 1
    }
}

private data class VersionDefinition(
    val weekdayMask: Int,
    val minutesOfDay: List<Int>,
    val zoneId: String,
    val startDateEpochDay: Long?,
    val endDateEpochDay: Long?,
)

private val MedicationEntity.isEditable: Boolean
    get() = stoppedAtEpochMillis == null && archivedAtEpochMillis == null

private fun OpenScheduleVersionRow.toDefinition(minutesOfDay: List<Int>) = VersionDefinition(
    weekdayMask = weekdayMask,
    minutesOfDay = minutesOfDay.sorted(),
    zoneId = zoneId,
    startDateEpochDay = startDateEpochDay,
    endDateEpochDay = endDateEpochDay,
)

private fun ValidatedScheduleDefinition.toDefinition() = VersionDefinition(
    weekdayMask = weekdayMask,
    minutesOfDay = minutesOfDay.sorted(),
    zoneId = zoneId.id,
    startDateEpochDay = startDate?.toEpochDay(),
    endDateEpochDay = endDate?.toEpochDay(),
)

private fun List<MedicationScheduleOverviewRow>.toMedicationPlans(): List<MedicationPlanItem> =
    groupBy(MedicationScheduleOverviewRow::medicationId)
        .values
        .map { rows ->
            val first = rows.first()
            MedicationPlanItem(
                medicationId = first.medicationId,
                name = first.medicationName,
                instruction = first.medicationInstruction,
                status = if (first.medicationStoppedAtEpochMillis == null) {
                    MedicationStatus.ACTIVE
                } else {
                    MedicationStatus.STOPPED
                },
                createdAt = Instant.ofEpochMilli(first.medicationCreatedAtEpochMillis),
                stoppedAt = first.medicationStoppedAtEpochMillis?.let(Instant::ofEpochMilli),
                schedule = rows.toSchedulePlanOrNull(),
            )
        }
        .sortedBy(MedicationPlanItem::createdAt)

private fun List<MedicationScheduleOverviewRow>.toSchedulePlanOrNull(): SchedulePlan? {
    val first = firstOrNull { it.scheduleVersionId != null } ?: return null

    return SchedulePlan(
        scheduleSeriesId = checkNotNull(first.scheduleSeriesId),
        scheduleVersionId = checkNotNull(first.scheduleVersionId),
        versionNumber = checkNotNull(first.scheduleVersionNumber),
        weekdays = checkNotNull(first.weekdayMask).toDaysOfWeek(),
        times = mapNotNull(MedicationScheduleOverviewRow::minuteOfDay)
            .distinct()
            .sorted()
            .map { minuteOfDay -> minuteOfDay.toLocalTime() },
        zoneId = checkNotNull(first.zoneId),
        effectiveFrom = Instant.ofEpochMilli(checkNotNull(first.effectiveFromEpochMillis)),
        startDate = first.startDateEpochDay?.let(LocalDate::ofEpochDay),
        endDate = first.endDateEpochDay?.let(LocalDate::ofEpochDay),
    )
}

private fun Int.toDaysOfWeek(): Set<DayOfWeek> = DayOfWeek.entries.filterTo(linkedSetOf()) { day ->
    this and (1 shl (day.value - 1)) != 0
}

private fun Int.toLocalTime(): LocalTime = LocalTime.of(
    this / MINUTES_PER_HOUR,
    this % MINUTES_PER_HOUR,
)

private const val MINUTES_PER_HOUR = 60
