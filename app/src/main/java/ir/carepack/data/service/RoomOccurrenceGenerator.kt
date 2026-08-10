package ir.carepack.data.service

import androidx.room.withTransaction
import ir.carepack.core.id.IdSource
import ir.carepack.data.local.CarePackDatabase
import ir.carepack.data.local.OccurrenceEntity
import ir.carepack.data.local.ScheduleDefinitionRow
import ir.carepack.domain.model.OccurrenceLifecycle
import ir.carepack.domain.model.ScheduleDefinition
import ir.carepack.domain.occurrence.GenerationSummary
import ir.carepack.domain.occurrence.GuaranteedOccurrence
import ir.carepack.domain.occurrence.OccurrenceCandidate
import ir.carepack.domain.occurrence.OccurrenceCandidateResolver
import ir.carepack.domain.occurrence.OccurrenceGenerationDateWindow
import ir.carepack.domain.occurrence.OccurrenceGenerationWindow
import ir.carepack.domain.occurrence.OccurrenceGenerator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RoomOccurrenceGenerator(
    private val database: CarePackDatabase,
    private val idSource: IdSource,
    private val candidateResolver: OccurrenceCandidateResolver,
) : OccurrenceGenerator {

    override suspend fun guaranteeWindowForSchedule(
        scheduleVersionId: String,
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary = database.withTransaction {
            generateVersionInCurrentTransaction(
                scheduleVersionId = scheduleVersionId,
                window = OccurrenceGenerationWindow.around(
                        anchorDate,
                    ),
                now = now,
            )
        }

    override suspend fun guaranteeWindowForAll(
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary = guaranteeWindowForAll(
            window = OccurrenceGenerationWindow.around(
                    anchorDate,
                ),
            now = now,
        )

    override suspend fun guaranteeMaintenanceWindowForAll(
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary = guaranteeWindowForAll(
            window = OccurrenceGenerationWindow.maintenance(
                    anchorDate,
                ),
            now = now,
        )

    private suspend fun guaranteeWindowForAll(
        window: OccurrenceGenerationDateWindow,
        now: Instant,
    ): GenerationSummary = database.withTransaction {
            val broadWindowStart = window.firstDate
                    .minusDays(2L).atStartOfDay(ZoneOffset.UTC)
                    .toInstant()

            val broadWindowEndExclusive = window.lastDate
                    .plusDays(3L).atStartOfDay(ZoneOffset.UTC)
                    .toInstant()

            val versionIds = database
                    .scheduleDao().getGenerationVersionIds(
                        windowStartEpochMillis = broadWindowStart.toEpochMilli(),
                        windowEndExclusiveEpochMillis = broadWindowEndExclusive
                                .toEpochMilli(),
                    )

            val guaranteed = mutableListOf<GuaranteedOccurrence>()
            var skipped = 0

            versionIds.forEach { versionId ->
                val summary = generateVersionInCurrentTransaction(
                        scheduleVersionId = versionId,
                        window = window,
                        now = now,
                    )

                guaranteed += summary.occurrences
                skipped += summary.skippedCandidateCount
            }

            GenerationSummary(
                occurrences = guaranteed,
                skippedCandidateCount = skipped,
            )
        }

    private suspend fun generateVersionInCurrentTransaction(
        scheduleVersionId: String,
        window: OccurrenceGenerationDateWindow,
        now: Instant,
    ): GenerationSummary {
        val definitions = database
                .scheduleDao().getDefinitionsForVersion(
                    scheduleVersionId,
                ).map(ScheduleDefinitionRow::toDomain)

        val guaranteed = mutableListOf<GuaranteedOccurrence>()
        var skipped = 0

        window.dates().forEach { date ->
            definitions.forEach { definition ->
                val candidates = candidateResolver.resolveAll(
                        definition = definition,
                        anchorDate = date,
                    )

                if (candidates.isEmpty()) {
                    skipped += 1
                } else {
                    candidates.forEach { candidate ->
                        guaranteed += guaranteeCandidate(
                                definition = definition,
                                candidate = candidate,
                                now = now,
                            )
                    }
                }
            }
        }

        return GenerationSummary(
            occurrences = guaranteed,
            skippedCandidateCount = skipped,
        )
    }

    private suspend fun guaranteeCandidate(
        definition: ScheduleDefinition,
        candidate: OccurrenceCandidate,
        now: Instant,
    ): GuaranteedOccurrence {
        val proposedId = idSource.nextId()

        val proposed = OccurrenceEntity(
                id = proposedId,
                scheduleVersionId = definition.scheduleVersionId,
                medicationId = definition.medicationId,
                localEpochDay = candidate.localDate.toEpochDay(),
                minuteOfDay = candidate.minuteOfDay,
                zoneIdSnapshot = candidate.zoneId,
                scheduledAtEpochMillis = candidate.scheduledAt.toEpochMilli(),
                medicationNameSnapshot = definition.medicationNameSnapshot,
                instructionSnapshot = definition.medicationInstructionSnapshot,
                medicationTypeSnapshot = definition.medicationTypeSnapshot,
                dosageTextSnapshot = definition.dosageTextSnapshot,
                doseUnitSnapshot = definition.doseUnitSnapshot,
                lifecycle = OccurrenceLifecycle.ACTIVE.name,
                cancelledAtEpochMillis = null,
                cancellationReason = null,
                createdAtEpochMillis = now.toEpochMilli(),
            )

        val insertResult = database
                .occurrenceDao().insertIgnoringLogicalConflict(proposed)

        val persisted = if (insertResult == -1L) {
                checkNotNull(
                    database.occurrenceDao()
                        .getByLogicalKey(
                            scheduleVersionId = definition.scheduleVersionId,
                            localEpochDay = candidate.localDate.toEpochDay(),
                            minuteOfDay = candidate.minuteOfDay,
                        ),
                )
            } else {
                checkNotNull(
                    database.occurrenceDao()
                        .getById(proposedId),
                )
            }

        verifyLogicalIdentity(
            expected = proposed,
            actual = persisted,
        )

        return GuaranteedOccurrence(
            occurrenceId = persisted.id,
            wasCreated = insertResult != -1L,
        )
    }

    private fun verifyLogicalIdentity(
        expected: OccurrenceEntity,
        actual: OccurrenceEntity,
    ) {
        check(actual.scheduleVersionId == expected.scheduleVersionId)
        check(actual.medicationId == expected.medicationId)
        check(actual.localEpochDay == expected.localEpochDay)
        check(actual.minuteOfDay == expected.minuteOfDay)
        check(actual.zoneIdSnapshot == expected.zoneIdSnapshot)
        check(
            actual.scheduledAtEpochMillis == expected.scheduledAtEpochMillis,
        )
        check(
            actual.medicationNameSnapshot == expected.medicationNameSnapshot,
        )
        check(
            actual.instructionSnapshot == expected.instructionSnapshot,
        )
        check(
            actual.medicationTypeSnapshot == expected.medicationTypeSnapshot,
        )
        check(
            actual.dosageTextSnapshot == expected.dosageTextSnapshot,
        )
        check(
            actual.doseUnitSnapshot == expected.doseUnitSnapshot,
        )
    }
}

private fun ScheduleDefinitionRow.toDomain(): ScheduleDefinition = ScheduleDefinition(
        scheduleVersionId = scheduleVersionId,
        scheduleSeriesId = scheduleSeriesId,
        medicationId = medicationId,
        weekdayMask = weekdayMask,
        minuteOfDay = minuteOfDay,
        schedulePattern = SchedulePatternPersistenceCodec.decode(
                patternType = patternType,
                intervalHours = intervalHours,
                anchorMinuteOfDay = anchorMinuteOfDay,
                fixedMinutesOfDay = listOf(minuteOfDay),
            ),
        zoneId = zoneId,
        effectiveFrom = Instant.ofEpochMilli(effectiveFromEpochMillis),
        effectiveUntil = effectiveUntilEpochMillis?.let(
                Instant::ofEpochMilli,
            ),
        startDate = startEpochDay?.let(LocalDate::ofEpochDay),
        endDate = endEpochDay?.let(LocalDate::ofEpochDay),
        medicationNameSnapshot = medicationNameSnapshot,
        medicationInstructionSnapshot = instructionSnapshot,
        medicationTypeSnapshot = medicationTypeSnapshot,
        dosageTextSnapshot = dosageTextSnapshot,
        doseUnitSnapshot = doseUnitSnapshot,
    )
