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
import java.time.ZoneOffset

class RoomOccurrenceGenerator(
    private val database: CarePackDatabase,
    private val idSource: IdSource,
    private val candidateResolver:
    OccurrenceCandidateResolver,
) : OccurrenceGenerator {

    override suspend fun guaranteeWindowForSchedule(
        scheduleVersionId: String,
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary {
        return database.withTransaction {
            generateVersionInCurrentTransaction(
                scheduleVersionId =
                    scheduleVersionId,
                anchorDate = anchorDate,
                now = now,
            )
        }
    }

    override suspend fun guaranteeWindowForAll(
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary {
        return database.withTransaction {
            val broadWindowStart =
                anchorDate
                    .minusDays(
                        WINDOW_RADIUS_DAYS + 1,
                    )
                    .atStartOfDay(
                        ZoneOffset.UTC,
                    )
                    .toInstant()

            val broadWindowEndExclusive =
                anchorDate
                    .plusDays(
                        WINDOW_RADIUS_DAYS + 2,
                    )
                    .atStartOfDay(
                        ZoneOffset.UTC,
                    )
                    .toInstant()

            val versionIds =
                database
                    .scheduleDao()
                    .getGenerationVersionIds(
                        windowStartEpochMillis =
                            broadWindowStart
                                .toEpochMilli(),
                        windowEndExclusiveEpochMillis =
                            broadWindowEndExclusive
                                .toEpochMilli(),
                    )

            val guaranteed =
                mutableListOf<GuaranteedOccurrence>()

            var skipped = 0

            versionIds.forEach { versionId ->
                val summary =
                    generateVersionInCurrentTransaction(
                        scheduleVersionId =
                            versionId,
                        anchorDate = anchorDate,
                        now = now,
                    )

                guaranteed +=
                    summary.occurrences

                skipped +=
                    summary.skippedCandidateCount
            }

            GenerationSummary(
                occurrences = guaranteed,
                skippedCandidateCount = skipped,
            )
        }
    }

    private suspend fun generateVersionInCurrentTransaction(
        scheduleVersionId: String,
        anchorDate: LocalDate,
        now: Instant,
    ): GenerationSummary {
        val definitions =
            database
                .scheduleDao()
                .getDefinitionsForVersion(
                    scheduleVersionId,
                )
                .map(
                    ScheduleDefinitionRow::toDomain,
                )

        val guaranteed =
            mutableListOf<GuaranteedOccurrence>()

        var skipped = 0

        val firstDate =
            anchorDate.minusDays(
                WINDOW_RADIUS_DAYS,
            )

        val lastDate =
            anchorDate.plusDays(
                WINDOW_RADIUS_DAYS,
            )

        var date =
            firstDate

        while (!date.isAfter(lastDate)) {
            definitions.forEach { definition ->
                val candidate =
                    candidateResolver.resolve(
                        definition = definition,
                        anchorDate = date,
                    )

                if (candidate == null) {
                    skipped += 1
                } else {
                    guaranteed +=
                        guaranteeCandidate(
                            definition =
                                definition,
                            candidate =
                                candidate,
                            now = now,
                        )
                }
            }

            date =
                date.plusDays(1)
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
        val proposedId =
            idSource.nextId()

        val proposed =
            OccurrenceEntity(
                id = proposedId,
                scheduleVersionId =
                    definition
                        .scheduleVersionId,
                medicationId =
                    definition.medicationId,
                localEpochDay =
                    candidate
                        .localDate
                        .toEpochDay(),
                minuteOfDay =
                    candidate.minuteOfDay,
                zoneIdSnapshot =
                    candidate.zoneId,
                scheduledAtEpochMillis =
                    candidate
                        .scheduledAt
                        .toEpochMilli(),
                medicationNameSnapshot =
                    definition
                        .medicationNameSnapshot,
                instructionSnapshot =
                    definition
                        .medicationInstructionSnapshot,
                lifecycle =
                    OccurrenceLifecycle
                        .ACTIVE
                        .name,
                cancelledAtEpochMillis = null,
                cancellationReason = null,
                createdAtEpochMillis =
                    now.toEpochMilli(),
            )

        val insertResult =
            database
                .occurrenceDao()
                .insertIgnoringLogicalConflict(
                    proposed,
                )

        val persisted =
            if (insertResult == -1L) {
                checkNotNull(
                    database
                        .occurrenceDao()
                        .getByLogicalKey(
                            scheduleVersionId =
                                definition
                                    .scheduleVersionId,
                            localEpochDay =
                                candidate
                                    .localDate
                                    .toEpochDay(),
                            minuteOfDay =
                                candidate.minuteOfDay,
                        ),
                )
            } else {
                checkNotNull(
                    database
                        .occurrenceDao()
                        .getById(
                            proposedId,
                        ),
                )
            }

        verifyLogicalIdentity(
            expected = proposed,
            actual = persisted,
        )

        return GuaranteedOccurrence(
            occurrenceId = persisted.id,
            wasCreated =
                insertResult != -1L,
        )
    }

    private fun verifyLogicalIdentity(
        expected: OccurrenceEntity,
        actual: OccurrenceEntity,
    ) {
        check(
            actual.scheduleVersionId ==
                    expected.scheduleVersionId,
        )

        check(
            actual.medicationId ==
                    expected.medicationId,
        )

        check(
            actual.localEpochDay ==
                    expected.localEpochDay,
        )

        check(
            actual.minuteOfDay ==
                    expected.minuteOfDay,
        )

        check(
            actual.zoneIdSnapshot ==
                    expected.zoneIdSnapshot,
        )

        check(
            actual.scheduledAtEpochMillis ==
                    expected.scheduledAtEpochMillis,
        )

        check(
            actual.medicationNameSnapshot ==
                    expected.medicationNameSnapshot,
        )

        check(
            actual.instructionSnapshot ==
                    expected.instructionSnapshot,
        )
    }

    private companion object {
        const val WINDOW_RADIUS_DAYS =
            7L
    }
}

private fun ScheduleDefinitionRow.toDomain():
        ScheduleDefinition {
    return ScheduleDefinition(
        scheduleVersionId =
            scheduleVersionId,
        scheduleSeriesId =
            scheduleSeriesId,
        medicationId =
            medicationId,
        weekdayMask =
            weekdayMask,
        minuteOfDay =
            minuteOfDay,
        zoneId =
            zoneId,
        effectiveFrom =
            Instant.ofEpochMilli(
                effectiveFromEpochMillis,
            ),
        effectiveUntil =
            effectiveUntilEpochMillis?.let(
                Instant::ofEpochMilli,
            ),
        startDate =
            startEpochDay?.let(
                LocalDate::ofEpochDay,
            ),
        endDate =
            endEpochDay?.let(
                LocalDate::ofEpochDay,
            ),
        medicationNameSnapshot =
            medicationNameSnapshot,
        medicationInstructionSnapshot =
            instructionSnapshot,
    )
}
