package ir.carepack.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import ir.carepack.settings.deletion.DataDeletionMarkerReadResult
import ir.carepack.settings.deletion.DeletionMarkerCorruptionReason
import ir.carepack.settings.deletion.MedicationDeletionMarkerReadResult
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeletionMarkerStorageFailureTest {

    @Test
    fun medicationMarkerIOExceptionIsCorruptionNotAbsence() =
        runTest {
            val store =
                DataStoreMedicationDeletionMarkerStore(
                    dataStore = FailingReadPreferencesDataStore(),
                )

            assertEquals(
                MedicationDeletionMarkerReadResult.Corrupted(
                    DeletionMarkerCorruptionReason.STORAGE_READ_FAILURE,
                ),
                store.state.first(),
            )
        }

    @Test
    fun deleteAllMarkerIOExceptionIsCorruptionNotAbsence() =
        runTest {
            val store =
                DataStoreDataDeletionMarkerStore(
                    dataStore = FailingReadPreferencesDataStore(),
                )

            assertEquals(
                DataDeletionMarkerReadResult.Corrupted(
                    DeletionMarkerCorruptionReason.STORAGE_READ_FAILURE,
                ),
                store.state.first(),
            )
        }

    private class FailingReadPreferencesDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> =
            flow {
                throw IOException("synthetic storage read failure")
            }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(emptyPreferences())
    }
}
