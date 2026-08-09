package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
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

    private lateinit var context: Context
    private lateinit var store: PrivacyPreferenceStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.carePackDataStore.edit { it.clear() }
        store = DataStorePrivacyPreferenceStore(context)
    }

    @After
    fun tearDown() {
        runBlocking {
            context.carePackDataStore.edit { it.clear() }
        }
    }

    @Test
    fun defaultState_isPrivacyConservative() = runBlocking {
        assertEquals(
            PrivacyPreferenceState(),
            store.state.first(),
        )
        assertFalse(store.state.first().includeRecipientName)
    }

    @Test
    fun includeRecipientName_isPersisted() = runBlocking {
        store.setIncludeRecipientName(true)
        assertTrue(store.state.first().includeRecipientName)

        val reopened = DataStorePrivacyPreferenceStore(context)
        assertTrue(reopened.state.first().includeRecipientName)
    }
}
