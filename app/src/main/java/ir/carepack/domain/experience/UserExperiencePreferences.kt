package ir.carepack.domain.experience

import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import kotlinx.coroutines.flow.Flow

enum class SeniorMode {
    STANDARD,
    SIMPLE,
}

data class UserExperiencePreferenceState(
    val firstDayOfWeekPreference:
    FirstDayOfWeekPreference =
        FirstDayOfWeekPreference.SYSTEM_DEFAULT,
    val seniorMode: SeniorMode =
        SeniorMode.STANDARD,
)

interface UserExperiencePreferenceStore {
    val state: Flow<UserExperiencePreferenceState>

    suspend fun setFirstDayOfWeekPreference(
        preference: FirstDayOfWeekPreference,
    )

    suspend fun setSeniorMode(
        seniorMode: SeniorMode,
    )
}
