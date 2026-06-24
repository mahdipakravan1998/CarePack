package ir.carepack.domain.occurrence

import androidx.room.withTransaction
import ir.carepack.core.id.IdSource
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.OccurrenceEntity
import ir.carepack.data.local.ScheduleDefinitionRow
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.ScheduleDefinition
import java.time.Instant
import java.time.LocalDate

class RoomOccurrenceGenerator(
    private val database: CarePackDatabase,
    private val idSource: IdSource,
    private val candidateResolver: OccurrenceCandidateResolver,
) : OccurrenceGenerator {

    override suspend fun guaranteeForSchedule(
        scheduleVersionId: String,
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary {
        return database.withTransaction {
            val definitions = database
                .scheduleDao()
                .getDefinitionsForVersion(scheduleVersionId)
                .map(ScheduleDefinitionRow::toDomain)

            val guaranteedOccurrences =
                mutableListOf<GuaranteedOccurrence>()

            var skippedCount = 0

            definitions.forEach { definition ->
                val candidate = candidateResolver.resolve(
                    definition = definition,
                    anchorDate = anchorDate,
                )

                if (candidate == null) {
                    skippedCount += 1
                    return@forEach
                }

                val proposedOccurrenceId = idSource.nextId()

                val proposedEntity = OccurrenceEntity(
                    id = proposedOccurrenceId,
                    scheduleSeriesId = definition.scheduleSeriesId,
                    scheduleVersionId = definition.scheduleVersionId,
                    medicationId = definition.medicationId,
                    localDateEpochDay = candidate.localDate.toEpochDay(),
                    minuteOfDay = candidate.minuteOfDay,
                    zoneId = candidate.zoneId,
                    scheduledAtEpochMillis = candidate.scheduledAt.toEpochMilli(),
                    medicationNameSnapshot =
                        definition.medicationNameSnapshot,
                    medicationInstructionSnapshot =
                        definition.medicationInstructionSnapshot,
                    lifecycle = OccurrenceLifecycle.ACTIVE.name,
                    createdAtEpochMillis = now.toEpochMilli(),
                )

                val insertResult = database
                    .occurrenceDao()
                    .insertIgnoringLogicalConflict(proposedEntity)

                val persistedOccurrence =
                    if (insertResult == -1L) {
                        checkNotNull(
                            database.occurrenceDao().getByLogicalKey(
                                scheduleVersionId =
                                    definition.scheduleVersionId,
                                localDateEpochDay =
                                    candidate.localDate.toEpochDay(),
                                minuteOfDay = candidate.minuteOfDay,
                            ),
                        )
                    } else {
                        checkNotNull(
                            database
                                .occurrenceDao()
                                .getById(proposedOccurrenceId),
                        )
                    }

                guaranteedOccurrences += GuaranteedOccurrence(
                    occurrenceId = persistedOccurrence.id,
                    wasCreated = insertResult != -1L,
                )
            }

            GenerationSummary(
                occurrences = guaranteedOccurrences,
                skippedCandidateCount = skippedCount,
            )
        }
    }

    override suspend fun guaranteeForEffectiveSchedules(
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary {
        val scheduleVersionIds = database
            .scheduleDao()
            .getEffectiveVersionIds(now.toEpochMilli())

        val allOccurrences =
            mutableListOf<GuaranteedOccurrence>()

        var skippedCount = 0

        scheduleVersionIds.forEach { scheduleVersionId ->
            val summary = guaranteeForSchedule(
                scheduleVersionId = scheduleVersionId,
                anchorDate = anchorDate,
                now = now,
            )

            allOccurrences += summary.occurrences
            skippedCount += summary.skippedCandidateCount
        }

        return GenerationSummary(
            occurrences = allOccurrences,
            skippedCandidateCount = skippedCount,
        )
    }
}

private fun ScheduleDefinitionRow.toDomain(): ScheduleDefinition {
    return ScheduleDefinition(
        scheduleVersionId = scheduleVersionId,
        scheduleSeriesId = scheduleSeriesId,
        medicationId = medicationId,
        weekdayMask = weekdayMask,
        minuteOfDay = minuteOfDay,
        zoneId = zoneId,
        effectiveFrom = Instant.ofEpochMilli(
            effectiveFromEpochMillis,
        ),
        effectiveUntil = effectiveUntilEpochMillis?.let(
            Instant::ofEpochMilli,
        ),
        startDate = startDateEpochDay?.let(LocalDate::ofEpochDay),
        endDate = endDateEpochDay?.let(LocalDate::ofEpochDay),
        medicationNameSnapshot = medicationNameSnapshot,
        medicationInstructionSnapshot =
            medicationInstructionSnapshot,
    )
}