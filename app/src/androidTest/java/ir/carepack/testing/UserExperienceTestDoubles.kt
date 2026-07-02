package ir.carepack.testing

import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class InstrumentedUserExperiencePreferenceStore(
    initialState: UserExperiencePreferenceState =
        UserExperiencePreferenceState(),
) : UserExperiencePreferenceStore {

    private val mutableState =
        MutableStateFlow(
            initialState,
        )

    override val state:
            Flow<UserExperiencePreferenceState> =
        mutableState

    override suspend fun setFirstDayOfWeekPreference(
        preference: FirstDayOfWeekPreference,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    firstDayOfWeekPreference =
                        preference,
                )
    }

    override suspend fun setSeniorMode(
        seniorMode: SeniorMode,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    seniorMode =
                        seniorMode,
                )
    }
}
