package ir.carepack.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.ui.viewmodel.carePackViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingSimpleModeUiState(
    val preferenceState: UserExperiencePreferenceState =
        UserExperiencePreferenceState(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val retrySelection: SeniorMode? = null,
)

private data class OnboardingSimpleModeTransientState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val retrySelection: SeniorMode? = null,
)

class OnboardingSimpleModeViewModel(
    private val userExperiencePreferenceStore: UserExperiencePreferenceStore,
) : ViewModel() {

    private val transientState =
        MutableStateFlow(OnboardingSimpleModeTransientState())

    val state: StateFlow<OnboardingSimpleModeUiState> = combine(
            userExperiencePreferenceStore.state,
            transientState,
        ) { preferenceState, transient ->
            OnboardingSimpleModeUiState(
                preferenceState = preferenceState,
                isSaving = transient.isSaving,
                errorMessage = transient.errorMessage,
                retrySelection = transient.retrySelection,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = OnboardingSimpleModeUiState(),
        )

    fun selectSimpleMode() {
        saveSelection(SeniorMode.SIMPLE)
    }

    fun keepStandardMode() {
        saveSelection(SeniorMode.STANDARD)
    }

    fun retryLastSelection() {
        transientState.value.retrySelection?.let(::saveSelection)
    }

    private fun saveSelection(selection: SeniorMode) {
        if (transientState.value.isSaving) {
            return
        }

        transientState.value = OnboardingSimpleModeTransientState(isSaving = true)
        viewModelScope.launch {
            try {
                userExperiencePreferenceStore.setSeniorMode(selection)
                transientState.value = OnboardingSimpleModeTransientState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                transientState.value = OnboardingSimpleModeTransientState(
                    errorMessage = "تغییر ذخیره نشد. دوباره تلاش کنید.",
                    retrySelection = selection,
                )
            }
        }
    }

    companion object {
        fun factory(
            userExperiencePreferenceStore: UserExperiencePreferenceStore,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
            OnboardingSimpleModeViewModel(userExperiencePreferenceStore)
        }
    }
}
