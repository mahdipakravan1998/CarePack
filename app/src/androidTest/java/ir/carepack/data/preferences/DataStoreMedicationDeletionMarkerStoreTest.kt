package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.settings.deletion.DeletionMarkerCorruptionReason
import ir.carepack.settings.deletion.MedicationDeletionMarker
import ir.carepack.settings.deletion.MedicationDeletionMarkerReadResult
import ir.carepack.settings.deletion.MedicationDeletionMarkerStage
import ir.carepack.settings.deletion.MedicationDeletionPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreMedicationDeletionMarkerStoreTest {

    private lateinit var context: Context
    private lateinit var store:
        DataStoreMedicationDeletionMarkerStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.carePackDataStore.edit { it.clear() }
        store = DataStoreMedicationDeletionMarkerStore(context)
    }

    @After
    fun tearDown() {
        runBlocking {
            context.carePackDataStore.edit { it.clear() }
        }
    }

    @Test
    fun markerRoundTripsAllTargetScopedRecoveryFields() =
        runBlocking {
            val marker = marker()
            store.save(marker)

            assertEquals(
                MedicationDeletionMarkerReadResult.Valid(marker),
                store.state.first(),
            )
        }

    @Test
    fun updateStageRecomputesChecksum() = runBlocking {
        val marker = marker()
        store.save(marker)
        store.updateStage(
            medicationId = marker.expectedPreview.medicationId,
            stage =
                MedicationDeletionMarkerStage.DATABASE_DELETED,
        )

        val read = store.state.first()
        assertTrue(read is MedicationDeletionMarkerReadResult.Valid)
        val updated =
            (read as MedicationDeletionMarkerReadResult.Valid).marker
        assertEquals(
            MedicationDeletionMarkerStage.DATABASE_DELETED,
            updated.stage,
        )
        assertTrue(updated.hasValidChecksum())
    }

    @Test
    fun partialMarkerIsCorruptedNotAbsent() = runBlocking {
        context.carePackDataStore.edit { preferences ->
            preferences[MedicationDeletionPreferenceKeys.version] = 1
            preferences[MedicationDeletionPreferenceKeys.medicationId] =
                "medication-1"
        }

        assertEquals(
            MedicationDeletionMarkerReadResult.Corrupted(
                DeletionMarkerCorruptionReason.PARTIAL_MARKER,
            ),
            store.state.first(),
        )
    }

    @Test
    fun unknownVersionIsCorrupted() = runBlocking {
        val marker = marker()
        store.save(marker)
        context.carePackDataStore.edit { preferences ->
            preferences[MedicationDeletionPreferenceKeys.version] =
                99
        }

        assertEquals(
            MedicationDeletionMarkerReadResult.Corrupted(
                DeletionMarkerCorruptionReason.UNKNOWN_VERSION,
            ),
            store.state.first(),
        )
    }

    @Test
    fun invalidStageIsCorrupted() = runBlocking {
        val marker = marker()
        store.save(marker)
        context.carePackDataStore.edit { preferences ->
            preferences[MedicationDeletionPreferenceKeys.stage] =
                "NOT_A_STAGE"
        }

        assertEquals(
            MedicationDeletionMarkerReadResult.Corrupted(
                DeletionMarkerCorruptionReason.INVALID_STAGE,
            ),
            store.state.first(),
        )
    }

    @Test
    fun checksumMismatchIsCorrupted() = runBlocking {
        val marker = marker()
        store.save(marker)
        context.carePackDataStore.edit { preferences ->
            preferences[MedicationDeletionPreferenceKeys.checksum] =
                "tampered-checksum"
        }

        assertEquals(
            MedicationDeletionMarkerReadResult.Corrupted(
                DeletionMarkerCorruptionReason.CHECKSUM_MISMATCH,
            ),
            store.state.first(),
        )
    }

    private fun marker(): MedicationDeletionMarker =
        MedicationDeletionMarker.create(
            expectedPreview =
                MedicationDeletionPreview(
                    medicationId = "medication-1",
                    medicationName = "داروی آزمایشی",
                    medicationUpdatedAtEpochMillis =
                        1_750_752_000_000L,
                    scheduleSeriesCount = 2,
                    scheduleVersionCount = 3,
                    scheduleTimeCount = 4,
                    occurrenceCount = 5,
                    caregiverReportCount = 3,
                ),
            scheduleSeriesIds =
                setOf("series-1", "series-2"),
            occurrenceIds =
                setOf("occurrence-1", "occurrence-2"),
            stage =
                MedicationDeletionMarkerStage
                    .PLATFORM_CLEANUP_PENDING,
            startedAtEpochMillis =
                1_750_752_030_000L,
        )
}
