package ir.carepack.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CarePackRoutesTest {

    @Test
    fun builders_preserveCanonicalRouteStrings() {
        assertEquals("medication-schedule/recipient-1", CarePackRoutes.medicationSchedule("recipient-1"))
        assertEquals("add-medication/recipient-1", CarePackRoutes.addMedication("recipient-1"))
        assertEquals("add-schedule/medication-1", CarePackRoutes.addSchedule("medication-1"))
        assertEquals("edit-medication/medication-1", CarePackRoutes.editMedicationText("medication-1"))
        assertEquals("edit-schedule/schedule-1", CarePackRoutes.editSchedule("schedule-1"))
        assertEquals("delete-medication/medication-1", CarePackRoutes.deleteMedication("medication-1"))
        assertEquals("occurrence/occurrence-1", CarePackRoutes.occurrenceDetail("occurrence-1"))
        assertEquals(
            "reminder/occurrence/occurrence-1",
            CarePackRoutes.reminderOccurrenceDetail("occurrence-1"),
        )
    }

    @Test
    fun patterns_preserveArgumentNames() {
        assertEquals("medication-schedule/{recipientId}", CarePackRoutes.MedicationSchedulePattern)
        assertEquals("occurrence/{occurrenceId}", CarePackRoutes.OccurrenceDetailPattern)
        assertEquals(
            "reminder/occurrence/{occurrenceId}",
            CarePackRoutes.ReminderOccurrenceDetailPattern,
        )
    }
}
