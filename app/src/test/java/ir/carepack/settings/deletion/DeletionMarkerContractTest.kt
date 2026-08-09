package ir.carepack.settings.deletion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionMarkerContractTest {

    @Test
    fun medicationMarker_checksumCoversTargetScopeAndStage() {
        val marker =
            MedicationDeletionMarker.create(
                expectedPreview = preview(),
                scheduleSeriesIds = setOf("series-1", "series-2"),
                occurrenceIds = setOf("occurrence-1", "occurrence-2"),
                stage =
                    MedicationDeletionMarkerStage
                        .PLATFORM_CLEANUP_PENDING,
                startedAtEpochMillis = 1_750_752_030_000L,
            )

        assertTrue(marker.hasValidChecksum())

        val next =
            marker.withStage(
                MedicationDeletionMarkerStage.DATABASE_DELETE_PENDING,
            )

        assertTrue(next.hasValidChecksum())
        assertNotEquals(marker.checksum, next.checksum)

        val tampered =
            marker.copy(
                occurrenceIds = marker.occurrenceIds + "other-occurrence",
            )

        assertFalse(tampered.hasValidChecksum())
    }

    @Test
    fun deleteAllMarker_checksumCoversOperationAndStage() {
        val marker =
            DataDeletionMarker.create(
                operationId = "delete-all-operation",
                stage =
                    DataDeletionMarkerStage
                        .PLATFORM_CLEANUP_PENDING,
                startedAtEpochMillis = 1_750_752_030_000L,
            )

        assertTrue(marker.hasValidChecksum())

        val next =
            marker.withStage(
                DataDeletionMarkerStage.DOMAIN_DATA_PENDING,
            )

        assertTrue(next.hasValidChecksum())
        assertNotEquals(marker.checksum, next.checksum)
        assertFalse(
            marker.copy(operationId = "other-operation")
                .hasValidChecksum(),
        )
    }

    private fun preview(): MedicationDeletionPreview =
        MedicationDeletionPreview(
            medicationId = "medication-1",
            medicationName = "داروی آزمایشی",
            medicationUpdatedAtEpochMillis = 1_750_752_000_000L,
            scheduleSeriesCount = 2,
            scheduleVersionCount = 3,
            scheduleTimeCount = 4,
            occurrenceCount = 5,
            caregiverReportCount = 3,
        )
}
