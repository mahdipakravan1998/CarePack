package ir.carepack.feature.careplan

import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleFormStateTest {

    @Test
    fun pickerSelection_updatesJalaliTextAndLocalDateSelection() {
        val selectedDate =
            LocalDate.parse(
                "2025-03-21",
            )

        val updated =
            state()
                .withStartDate(selectedDate)
                .withEndDate(
                    selectedDate.plusDays(9),
                )

        assertEquals(
            "1404/01/01",
            updated.startDateText,
        )
        assertEquals(
            "1404/01/10",
            updated.endDateText,
        )
        assertEquals(
            selectedDate,
            updated.startDateSelection(),
        )
        assertEquals(
            selectedDate.plusDays(9),
            updated.endDateSelection(),
        )
    }

    @Test
    fun optionalEndDate_canBeClearedForNoEndDateBehavior() {
        val updated =
            state()
                .withEndDate(
                    LocalDate.parse(
                        "2025-03-30",
                    ),
                )
                .withEndDate(null)

        assertEquals("", updated.endDateText)
        assertNull(updated.endDateSelection())
        assertNull(updated.parseDates().endDate)
    }

    @Test
    fun manualPersianDigits_updatePickerSelection() {
        val updated =
            state().withStartDate(
                "۱۴۰۴/۰۱/۰۱",
            )

        assertEquals(
            LocalDate.parse(
                "2025-03-21",
            ),
            updated.startDateSelection(),
        )
    }

    @Test
    fun manualArabicDigits_updatePickerSelection() {
        val updated =
            state().withStartDate(
                "١٤٠٤/٠١/٠١",
            )

        assertEquals(
            LocalDate.parse(
                "2025-03-21",
            ),
            updated.startDateSelection(),
        )
    }

    @Test
    fun invalidManualDate_remainsInvalidAndDoesNotSelectDate() {
        val updated =
            state().withStartDate(
                "1405/12/30",
            )

        assertNull(updated.startDateSelection())
        assertTrue(
            updated.parseDates().errors.containsKey(
                CarePlanField.START_DATE,
            ),
        )
    }

    @Test
    fun endBeforeStart_remainsRejected() {
        val updated =
            state()
                .withStartDate(
                    "1404/01/10",
                )
                .withEndDate(
                    "1404/01/09",
                )

        val parsed =
            updated.parseDates()

        assertTrue(
            parsed.errors.containsKey(
                CarePlanField.END_DATE,
            ),
        )
    }

    @Test
    fun fixedSchedulePattern_preservesSortedDistinctTimes() {
        val updated =
            state().copy(
                inputMode =
                    ScheduleInputMode.FIXED_TIMES,
                minutesOfDay =
                    listOf(
                        20 * 60,
                        8 * 60,
                        8 * 60,
                    ),
            )

        assertEquals(
            listOf(
                8 * 60,
                20 * 60,
            ),
            updated.effectiveMinutesOfDay(),
        )
        assertEquals(
            FixedTimeSchedule(
                minutesOfDay =
                    listOf(
                        20 * 60,
                        8 * 60,
                        8 * 60,
                    ),
            ),
            updated.toSchedulePattern(),
        )
    }

    @Test
    fun intervalSchedule_preservesHoursAndAnchor() {
        val updated =
            state()
                .withInputMode(
                    ScheduleInputMode.EVERY_X_HOURS,
                )
                .withIntervalHours(6)
                .withIntervalAnchorDraft(
                    "۲۳:۰۰",
                )

        assertEquals(
            IntervalSchedule(
                intervalHours = 6,
                anchorMinuteOfDay =
                    23 * 60,
            ),
            updated.toSchedulePattern(),
        )
        assertEquals(
            listOf(
                5 * 60,
                11 * 60,
                17 * 60,
                23 * 60,
            ),
            updated.effectiveMinutesOfDay(),
        )
    }

    @Test
    fun validDraftTime_isAddedAndDuplicateIsRejected() {
        val first =
            state()
                .withTimeDraft("08:30")
                .addDraftTime()

        assertEquals(
            listOf(8 * 60 + 30),
            first.minutesOfDay,
        )
        assertEquals("", first.timeDraft)

        val duplicate =
            first
                .withTimeDraft("08:30")
                .addDraftTime()

        assertTrue(
            duplicate.errors.containsKey(
                CarePlanField.TIMES,
            ),
        )
    }

    @Test
    fun previewUsesSelectedDatesAndKeepsIntervalAndFixedSemantics() {
        val anchorDate =
            LocalDate.parse(
                "2025-03-21",
            )

        val fixed =
            state().copy(
                weekdays =
                    setOf(
                        DayOfWeek.FRIDAY,
                    ),
                minutesOfDay =
                    listOf(8 * 60),
                startDateText =
                    "1404/01/01",
                endDateText =
                    "1404/01/01",
            )

        val fixedPreview =
            fixed.previewItems(
                anchorDate = anchorDate,
                dayCount = 1,
            )

        assertEquals(1, fixedPreview.size)
        assertEquals(
            8 * 60,
            fixedPreview.single().minuteOfDay,
        )

        val interval =
            fixed.copy(
                inputMode =
                    ScheduleInputMode.EVERY_X_HOURS,
                intervalHours = 8,
                intervalAnchorDraft = "07:00",
            )

        val intervalPreview =
            interval.previewItems(
                anchorDate = anchorDate,
                dayCount = 1,
            )

        assertEquals(
            listOf(
                7 * 60,
                15 * 60,
                23 * 60,
            ),
            intervalPreview.map {
                it.minuteOfDay
            },
        )
    }

    @Test
    fun dateAndTimeDraftFilters_keepOnlySupportedCharacters() {
        val updated =
            state()
                .withStartDate(
                    "abc۱۴۰۴/۰۱/۰۱xyz",
                )
                .withIntervalAnchorDraft(
                    "ساعت ۰۸：۳۰",
                )

        assertEquals(
            "۱۴۰۴/۰۱/۰۱",
            updated.startDateText,
        )
        assertEquals(
            "۰۸:۳۰",
            updated.intervalAnchorDraft,
        )
        assertFalse(
            updated.startDateText.any {
                it.isLetter()
            },
        )
    }

    private fun state(): ScheduleFormUiState =
        ScheduleFormUiState(
            weekdays = emptySet(),
            minutesOfDay = emptyList(),
            timeDraft = "",
            startDateText = "",
            endDateText = "",
            zoneId = "Asia/Tehran",
            previewEffectiveFrom =
                Instant.parse(
                    "2025-03-20T20:30:00Z",
                ),
        )
}
