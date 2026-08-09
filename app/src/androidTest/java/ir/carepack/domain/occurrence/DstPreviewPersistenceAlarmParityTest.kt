package ir.carepack.domain.occurrence

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.domain.schedule.SchedulePattern
import ir.carepack.testing.CarePlanRoomTestFixture
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DstPreviewPersistenceAlarmParityTest {

    @Test
    fun berlinGapAndOverlap_previewPersistenceAndAlarmUseSameInstant() =
        runBlocking {
            assertParity(
                zoneId = "Europe/Berlin",
                date = LocalDate.parse("2026-03-29"),
                minuteOfDay = 2 * 60 + 30,
            )
            assertParity(
                zoneId = "Europe/Berlin",
                date = LocalDate.parse("2026-10-25"),
                minuteOfDay = 2 * 60 + 30,
            )
        }

    @Test
    fun newYorkGapAndOverlap_previewPersistenceAndAlarmUseSameInstant() =
        runBlocking {
            assertParity(
                zoneId = "America/New_York",
                date = LocalDate.parse("2026-03-08"),
                minuteOfDay = 2 * 60 + 30,
            )
            assertParity(
                zoneId = "America/New_York",
                date = LocalDate.parse("2026-11-01"),
                minuteOfDay = 1 * 60 + 30,
            )
        }

    private suspend fun assertParity(
        zoneId: String,
        date: LocalDate,
        minuteOfDay: Int,
    ) {
        assertPatternParity(
            zoneId = zoneId,
            date = date,
            pattern = FixedTimeSchedule(listOf(minuteOfDay)),
        )
        assertPatternParity(
            zoneId = zoneId,
            date = date,
            pattern =
                IntervalSchedule(
                    intervalHours = 8,
                    anchorMinuteOfDay = minuteOfDay,
                ),
        )
    }

    private suspend fun assertPatternParity(
        zoneId: String,
        date: LocalDate,
        pattern: SchedulePattern,
    ) {
        val initialInstant =
            date.atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .minusSeconds(2L * 24L * 60L * 60L)

        CarePlanRoomTestFixture.create(
            initialInstant = initialInstant,
            idPrefix =
                "dst-${zoneId.replace('/', '-')}-${date}-${pattern::class.simpleName}",
        ).use { fixture ->
            val plan =
                fixture.createPlan(
                    weekdays = setOf(date.dayOfWeek),
                    minutesOfDay = pattern.representativeMinutesOfDay,
                    schedulePattern = pattern,
                    startDate = date,
                    endDate = date,
                    zoneId = zoneId,
                )

            val preview =
                SchedulePreviewResolver().resolve(
                    SchedulePreviewRequest(
                        weekdays = setOf(date.dayOfWeek),
                        schedulePattern = pattern,
                        zoneId = zoneId,
                        effectiveFrom = initialInstant,
                        startDate = date,
                        endDate = date,
                        anchorDate = date,
                        dayCount = 1,
                    ),
                )

            val persisted =
                fixture.occurrencesForMedication(plan.medicationId)
                    .sortedBy { it.scheduledAtEpochMillis }

            assertEquals(
                preview.map { it.scheduledAt },
                persisted.map {
                    Instant.ofEpochMilli(it.scheduledAtEpochMillis)
                },
            )

            val alarmTarget =
                fixture.reminderScheduleSource
                    .getNextEligibleTargets(initialInstant)
                    .single()

            assertEquals(preview.first().scheduledAt, alarmTarget.scheduledAt)
            assertEquals(
                Instant.ofEpochMilli(persisted.first().scheduledAtEpochMillis),
                alarmTarget.scheduledAt,
            )
        }
    }
}
