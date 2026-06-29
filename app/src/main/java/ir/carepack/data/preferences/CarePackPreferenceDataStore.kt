package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.carePackDataStore:
        DataStore<Preferences> by preferencesDataStore(
    name = "carepack_preferences",
)
