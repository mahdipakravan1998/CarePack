package ir.carepack.domain.reminder

import kotlinx.coroutines.flow.Flow

data class TimezoneWarning(
    val previousZoneId: String,
    val currentZoneId: String,
) {
    init {
        require(previousZoneId.isNotBlank())
        require(currentZoneId.isNotBlank())
        require(previousZoneId != currentZoneId)
    }
}

sealed interface TimezoneObservation {

    data object Initialized :
        TimezoneObservation

    data object Unchanged :
        TimezoneObservation

    data class Changed(
        val warning: TimezoneWarning,
    ) : TimezoneObservation
}

data class ReminderPreferenceState(
    val remindersEnabled: Boolean = false,
    val lastObservedZoneId: String? = null,
    val timezoneWarning: TimezoneWarning? = null,
)

interface ReminderPreferenceStore {

    val state: Flow<ReminderPreferenceState>

    suspend fun setRemindersEnabled(
        enabled: Boolean,
    )

    suspend fun observeDeviceZone(
        zoneId: String,
    ): TimezoneObservation

    suspend fun dismissTimezoneWarning()
}
