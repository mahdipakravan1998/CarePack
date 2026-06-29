package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface SetupPreferenceStore {
    val setupComplete: Flow<Boolean>

    suspend fun markSetupComplete()
}

class DataStoreSetupPreferenceStore(
    context: Context,
) : SetupPreferenceStore {

    private val applicationContext =
        context.applicationContext

    override val setupComplete: Flow<Boolean> =
        applicationContext
            .carePackDataStore
            .data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { preferences ->
                preferences[SETUP_COMPLETE] ?: false
            }

    override suspend fun markSetupComplete() {
        applicationContext.carePackDataStore.edit {
                preferences ->
            preferences[SETUP_COMPLETE] = true
        }
    }

    private companion object {
        val SETUP_COMPLETE =
            booleanPreferencesKey("setup_complete")
    }
}
