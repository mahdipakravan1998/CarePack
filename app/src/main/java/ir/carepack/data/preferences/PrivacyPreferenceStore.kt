package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class PrivacyPreferenceState(
    val includeRecipientName: Boolean = false,
)

interface PrivacyPreferenceStore {

    val state: Flow<PrivacyPreferenceState>

    suspend fun setIncludeRecipientName(
        includeRecipientName: Boolean,
    )
}

class DataStorePrivacyPreferenceStore(
    context: Context,
) : PrivacyPreferenceStore {

    private val applicationContext = context.applicationContext

    override val state: Flow<PrivacyPreferenceState> = applicationContext
            .carePackDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }.map { preferences ->
                PrivacyPreferenceState(
                    includeRecipientName = preferences[INCLUDE_RECIPIENT_NAME]
                            ?: false,
                )
            }

    override suspend fun setIncludeRecipientName(
        includeRecipientName: Boolean,
    ) {
        applicationContext.carePackDataStore
            .edit { preferences ->
                preferences[INCLUDE_RECIPIENT_NAME] = includeRecipientName
            }
    }

    private companion object {
        val INCLUDE_RECIPIENT_NAME = booleanPreferencesKey(
                "report_include_recipient_name",
            )
    }
}
