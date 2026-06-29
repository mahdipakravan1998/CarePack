package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.domain.reminder.TimezoneWarning
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreReminderPreferenceStore(
    context: Context,
) : ReminderPreferenceStore {
    private val applicationContext =
        context.applicationContext

    override val state:
            Flow<ReminderPreferenceState> =
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
                val previousZoneId =
                    preferences[
                        TIMEZONE_WARNING_PREVIOUS_ZONE
                    ]

                val currentZoneId =
                    preferences[
                        TIMEZONE_WARNING_CURRENT_ZONE
                    ]

                ReminderPreferenceState(
                    remindersEnabled =
                        preferences[
                            REMINDERS_ENABLED
                        ] ?: false,
                    lastObservedZoneId =
                        preferences[
                            LAST_OBSERVED_ZONE_ID
                        ],
                    timezoneWarning =
                        if (
                            previousZoneId != null &&
                            currentZoneId != null
                        ) {
                            TimezoneWarning(
                                previousZoneId =
                                    previousZoneId,
                                currentZoneId =
                                    currentZoneId,
                            )
                        } else {
                            null
                        },
                )
            }

    override suspend fun setRemindersEnabled(
        enabled: Boolean,
    ) {
        applicationContext
            .carePackDataStore
            .edit { preferences ->
                preferences[REMINDERS_ENABLED] =
                    enabled
            }
    }

    override suspend fun observeDeviceZone(
        zoneId: String,
    ): TimezoneObservation {
        require(zoneId.isNotBlank())

        var observation:
                TimezoneObservation =
            TimezoneObservation.Unchanged

        applicationContext
            .carePackDataStore
            .edit { preferences ->
                val previous =
                    preferences[
                        LAST_OBSERVED_ZONE_ID
                    ]

                when {
                    previous == null -> {
                        preferences[
                            LAST_OBSERVED_ZONE_ID
                        ] = zoneId

                        observation =
                            TimezoneObservation
                                .Initialized
                    }

                    previous == zoneId -> {
                        observation =
                            TimezoneObservation
                                .Unchanged
                    }

                    else -> {
                        val warning =
                            TimezoneWarning(
                                previousZoneId =
                                    previous,
                                currentZoneId =
                                    zoneId,
                            )

                        preferences[
                            LAST_OBSERVED_ZONE_ID
                        ] = zoneId

                        preferences[
                            TIMEZONE_WARNING_PREVIOUS_ZONE
                        ] = previous

                        preferences[
                            TIMEZONE_WARNING_CURRENT_ZONE
                        ] = zoneId

                        observation =
                            TimezoneObservation
                                .Changed(warning)
                    }
                }
            }

        return observation
    }

    override suspend fun dismissTimezoneWarning() {
        applicationContext
            .carePackDataStore
            .edit { preferences ->
                preferences.remove(
                    TIMEZONE_WARNING_PREVIOUS_ZONE,
                )

                preferences.remove(
                    TIMEZONE_WARNING_CURRENT_ZONE,
                )
            }
    }

    private companion object {
        val REMINDERS_ENABLED =
            booleanPreferencesKey(
                "reminders_enabled",
            )

        val LAST_OBSERVED_ZONE_ID =
            stringPreferencesKey(
                "last_observed_zone_id",
            )

        val TIMEZONE_WARNING_PREVIOUS_ZONE =
            stringPreferencesKey(
                "timezone_warning_previous_zone",
            )

        val TIMEZONE_WARNING_CURRENT_ZONE =
            stringPreferencesKey(
                "timezone_warning_current_zone",
            )
    }
}
