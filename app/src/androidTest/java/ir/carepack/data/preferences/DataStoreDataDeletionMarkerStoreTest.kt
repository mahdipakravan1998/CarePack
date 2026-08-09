package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.settings.deletion.DataDeletionMarker
import ir.carepack.settings.deletion.DataDeletionMarkerReadResult
import ir.carepack.settings.deletion.DataDeletionMarkerStage
import ir.carepack.settings.deletion.DeletionMarkerCorruptionReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreDataDeletionMarkerStoreTest {
    private lateinit var context: Context
    private lateinit var store: DataStoreDataDeletionMarkerStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.carePackDataStore.edit { it.clear() }
        store = DataStoreDataDeletionMarkerStore(context)
    }

    @After
    fun tearDown() {
        runBlocking {
            context.carePackDataStore.edit { it.clear() }
        }
    }

    @Test
    fun validMarkerRoundTrips() = runBlocking {
        val marker =
            DataDeletionMarker.create(
                operationId = "delete-all-1",
                stage =
                    DataDeletionMarkerStage
                        .DOMAIN_DATA_PENDING,
                startedAtEpochMillis = 100L,
            )
        store.save(marker)
        assertEquals(
            DataDeletionMarkerReadResult.Valid(marker),
            store.state.first(),
        )
    }

    @Test
    fun invalidStageIsCorruptedNotAbsent() = runBlocking {
        val marker =
            DataDeletionMarker.create(
                operationId = "delete-all-1",
                stage =
                    DataDeletionMarkerStage
                        .DOMAIN_DATA_PENDING,
                startedAtEpochMillis = 100L,
            )
        store.save(marker)
        context.carePackDataStore.edit { preferences ->
            preferences[DataDeletionPreferenceKeys.stage] =
                "NOT_A_STAGE"
        }

        assertEquals(
            DataDeletionMarkerReadResult.Corrupted(
                DeletionMarkerCorruptionReason.INVALID_STAGE,
            ),
            store.state.first(),
        )
    }

    @Test
    fun partialMarkerIsCorruptedNotAbsent() = runBlocking {
        context.carePackDataStore.edit { preferences ->
            preferences[DataDeletionPreferenceKeys.version] = 1
        }

        assertEquals(
            DataDeletionMarkerReadResult.Corrupted(
                DeletionMarkerCorruptionReason.PARTIAL_MARKER,
            ),
            store.state.first(),
        )
    }

    @Test
    fun unknownVersionIsCorrupted() = runBlocking {
        val marker =
            DataDeletionMarker.create(
                operationId = "delete-all-1",
                stage = DataDeletionMarkerStage.DOMAIN_DATA_PENDING,
                startedAtEpochMillis = 100L,
            )
        store.save(marker)
        context.carePackDataStore.edit { preferences ->
            preferences[DataDeletionPreferenceKeys.version] = 99
        }

        assertEquals(
            DataDeletionMarkerReadResult.Corrupted(
                DeletionMarkerCorruptionReason.UNKNOWN_VERSION,
            ),
            store.state.first(),
        )
    }

    @Test
    fun checksumMismatchIsCorrupted() = runBlocking {
        val marker =
            DataDeletionMarker.create(
                operationId = "delete-all-1",
                stage = DataDeletionMarkerStage.DOMAIN_DATA_PENDING,
                startedAtEpochMillis = 100L,
            )
        store.save(marker)
        context.carePackDataStore.edit { preferences ->
            preferences[DataDeletionPreferenceKeys.checksum] =
                "tampered-checksum"
        }

        assertEquals(
            DataDeletionMarkerReadResult.Corrupted(
                DeletionMarkerCorruptionReason.CHECKSUM_MISMATCH,
            ),
            store.state.first(),
        )
    }
}
