package ir.carepack.app

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.SetupProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AppLaunchState {
    data object Loading : AppLaunchState

    data class Ready(
        val startRoute: String,
    ) : AppLaunchState

    data class Error(
        val message: String,
    ) : AppLaunchState
}

class AppViewModel(
    private val carePlanService: CarePlanService,
    private val setupPreferenceStore: SetupPreferenceStore,
) : ViewModel() {

    private val mutableState = MutableStateFlow<AppLaunchState>(
            AppLaunchState.Loading,
        )

    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = AppLaunchState.Loading

            mutableState.value = try {
                    val setupCompleted = setupPreferenceStore
                            .setupComplete.first()

                    val progress = carePlanService
                            .getSetupProgress()

                    AppLaunchState.Ready(
                        startRoute = routeFor(
                                setupCompleted = setupCompleted,
                                progress = progress,
                            ),
                    )
                } catch (
                    cancellationException: CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    AppLaunchState.Error(
                        message = "راه‌اندازی برنامه انجام نشد.",
                    )
                }
        }
    }

    fun completeInitialSetup() {
        mutableState.value = AppLaunchState.Ready(
                startRoute = CarePackRoutes.Today,
            )
    }

    private fun routeFor(
        setupCompleted: Boolean,
        progress: SetupProgress,
    ): String {
        return when {
            setupCompleted && progress == SetupProgress.Complete -> {
                CarePackRoutes.Today
            }

            progress is SetupProgress.RecipientOnly -> {
                CarePackRoutes.medicationSchedule(
                    recipientId = progress.recipientId,
                )
            }

            progress == SetupProgress.Complete -> {
                CarePackRoutes.Today
            }

            else -> {
                CarePackRoutes.Onboarding
            }
        }
    }

    companion object {
        fun factory(
            carePlanService: CarePlanService,
            setupPreferenceStore: SetupPreferenceStore,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    AppViewModel(
                        carePlanService = carePlanService,
                        setupPreferenceStore = setupPreferenceStore,
                    )
            }
    }
}
