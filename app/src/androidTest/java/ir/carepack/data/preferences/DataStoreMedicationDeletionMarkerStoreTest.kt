package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.settings.deletion.MedicationDeletionMarker
import ir.carepack.settings.deletion.MedicationDeletionMarkerStage
import ir.carepack.settings.deletion.MedicationDeletionPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreMedicationDeletionMarkerStoreTest {

    private lateinit var context: Context

    private lateinit var store:
            DataStoreMedicationDeletionMarkerStore

    @Before
    fun setUp() =
        runBlocking {
            context =
                ApplicationProvider
                    .getApplicationContext()

            clearPreferences()

            store =
                DataStoreMedicationDeletionMarkerStore(
                    context = context,
                )
        }

    @After
    fun tearDown() =
        runBlocking {
            clearPreferences()
        }

    @Test
    fun markerRoundTripsAllRecoveryFields() =
        runBlocking {
            val marker = marker()

            store.save(marker)

            assertEquals(
                marker,
                store.marker.first(),
            )

            val reopened =
                DataStoreMedicationDeletionMarkerStore(
                    context = context,
                )

            assertEquals(
                marker,
                reopened.marker.first(),
            )
        }

    @Test
    fun updateStageChangesOnlyMatchingMarkerStage() =
        runBlocking {
            val marker = marker()

            store.save(marker)

            store.updateStage(
                medicationId =
                    marker
                        .expectedPreview
                        .medicationId,
                stage =
                    MedicationDeletionMarkerStage
                        .DATABASE_DELETED,
            )

            assertEquals(
                marker.copy(
                    stage =
                        MedicationDeletionMarkerStage
                            .DATABASE_DELETED,
                ),
                store.marker.first(),
            )
        }

    @Test
    fun clearRemovesOnlyMatchingTargetMarker() =
        runBlocking {
            val marker = marker()

            store.save(marker)

            store.clear(
                medicationId =
                    "another-medication",
            )

            assertEquals(
                marker,
                store.marker.first(),
            )

            store.clear(
                medicationId =
                    marker
                        .expectedPreview
                        .medicationId,
            )

            assertNull(
                store.marker.first(),
            )
        }

    private suspend fun clearPreferences() {
        context
            .carePackDataStore
            .edit { preferences ->
                preferences.clear()
            }
    }

    private fun marker():
            MedicationDeletionMarker =
        MedicationDeletionMarker(
            expectedPreview =
                MedicationDeletionPreview(
                    medicationId =
                        "medication-1",
                    medicationName =
                        "داروی آزمایشی",
                    medicationUpdatedAtEpochMillis =
                        1_750_752_000_000L,
                    scheduleSeriesCount = 2,
                    scheduleVersionCount = 3,
                    scheduleTimeCount = 4,
                    occurrenceCount = 5,
                    caregiverReportCount = 3,
                ),
            scheduleSeriesIds =
                setOf(
                    "series-1",
                    "series-2",
                ),
            stage =
                MedicationDeletionMarkerStage
                    .PLATFORM_CLEANUP_PENDING,
            startedAtEpochMillis =
                1_750_752_030_000L,
        )
}
