package ir.carepack.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStorePrivacyPreferenceStoreTest {

    private val context:
            Context =
        ApplicationProvider
            .getApplicationContext()

    private lateinit var store:
            DataStorePrivacyPreferenceStore

    @Before
    fun setUp() =
        runBlocking {
            store =
                DataStorePrivacyPreferenceStore(
                    context = context,
                )

            clearAllPreferences()
        }

    @After
    fun tearDown() =
        runBlocking {
            clearAllPreferences()
        }

    @Test
    fun defaultState_isPrivacyConservative() =
        runBlocking {
            assertEquals(
                PrivacyPreferenceState(
                    includeRecipientName =
                        false,
                    deletionInProgress =
                        false,
                ),
                store.state.first(),
            )
        }

    @Test
    fun includeRecipientName_isPersisted() =
        runBlocking {
            store.setIncludeRecipientName(
                includeRecipientName = true,
            )

            assertTrue(
                store
                    .state
                    .first()
                    .includeRecipientName,
            )

            val recreatedStore =
                DataStorePrivacyPreferenceStore(
                    context = context,
                )

            assertTrue(
                recreatedStore
                    .state
                    .first()
                    .includeRecipientName,
            )
        }

    @Test
    fun clearingPreferences_preservesOnlyDeletionMarker() =
        runBlocking {
            store.setIncludeRecipientName(
                includeRecipientName = true,
            )

            store.markDeletionInProgress()

            store.clearAllPreservingDeletionMarker()

            val state =
                store.state.first()

            assertFalse(
                state.includeRecipientName,
            )

            assertTrue(
                state.deletionInProgress,
            )
        }

    @Test
    fun completingDeletion_removesRecoveryMarker() =
        runBlocking {
            store.markDeletionInProgress()

            store.clearAllPreservingDeletionMarker()

            store.completeDeletion()

            assertEquals(
                PrivacyPreferenceState(),
                store.state.first(),
            )
        }

    private suspend fun clearAllPreferences() {
        store.markDeletionInProgress()
        store.clearAllPreservingDeletionMarker()
        store.completeDeletion()
    }
}
