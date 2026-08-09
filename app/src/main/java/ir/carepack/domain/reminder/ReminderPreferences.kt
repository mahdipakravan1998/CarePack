package ir.carepack.domain.reminder

import ir.carepack.core.error.SafeAppFailure
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
    data object Initialized : TimezoneObservation
    data object Unchanged : TimezoneObservation

    data class Changed(
        val warning: TimezoneWarning,
    ) : TimezoneObservation
}

sealed interface ReminderHealth {
    data object Healthy : ReminderHealth

    data class PendingRetry(
        val failure: SafeAppFailure,
        val failedAtEpochMillis: Long,
    ) : ReminderHealth {
        init {
            require(failedAtEpochMillis >= 0L)
        }
    }

    data class Unavailable(
        val failure: SafeAppFailure,
        val failedAtEpochMillis: Long,
    ) : ReminderHealth {
        init {
            require(failedAtEpochMillis >= 0L)
        }
    }
}

data class ReminderPreferenceState(
    val remindersEnabled: Boolean = false,
    val lastObservedZoneId: String? = null,
    val timezoneWarning: TimezoneWarning? = null,
    val health: ReminderHealth = ReminderHealth.Healthy,
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

    suspend fun markHealthy()

    suspend fun markFailure(
        failure: SafeAppFailure,
        failedAtEpochMillis: Long,
    )
}
