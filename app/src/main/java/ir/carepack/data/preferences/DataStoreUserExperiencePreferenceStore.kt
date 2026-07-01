package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreUserExperiencePreferenceStore(
    context: Context,
) : UserExperiencePreferenceStore {
    private val applicationContext =
        context.applicationContext

    override val state:
            Flow<UserExperiencePreferenceState> =
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
                UserExperiencePreferenceState(
                    firstDayOfWeekPreference =
                        preferences[FIRST_DAY_OF_WEEK]
                            ?.toFirstDayOfWeekPreference()
                            ?: FirstDayOfWeekPreference
                                .SYSTEM_DEFAULT,
                    seniorMode =
                        preferences[SENIOR_MODE]
                            ?.toSeniorMode()
                            ?: SeniorMode.STANDARD,
                )
            }

    override suspend fun setFirstDayOfWeekPreference(
        preference: FirstDayOfWeekPreference,
    ) {
        applicationContext
            .carePackDataStore
            .edit { preferences ->
                preferences[FIRST_DAY_OF_WEEK] =
                    preference.name
            }
    }

    override suspend fun setSeniorMode(
        seniorMode: SeniorMode,
    ) {
        applicationContext
            .carePackDataStore
            .edit { preferences ->
                preferences[SENIOR_MODE] =
                    seniorMode.name
            }
    }

    private fun String.toFirstDayOfWeekPreference():
            FirstDayOfWeekPreference? =
        runCatching {
            FirstDayOfWeekPreference.valueOf(this)
        }.getOrNull()

    private fun String.toSeniorMode(): SeniorMode? =
        runCatching {
            SeniorMode.valueOf(this)
        }.getOrNull()

    private companion object {
        val FIRST_DAY_OF_WEEK =
            stringPreferencesKey(
                "first_day_of_week_preference",
            )

        val SENIOR_MODE =
            stringPreferencesKey(
                "senior_mode",
            )
    }
}
