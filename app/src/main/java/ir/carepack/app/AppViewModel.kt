package ir.carepack.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.carepack.BuildConfig
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.SetupProgress
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderTestCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.DateRangeSummaryService
import ir.carepack.domain.report.RangeReportFormatter
import ir.carepack.domain.report.TodayReportFormatter
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.feature.careplan.CarePlanRoute
import ir.carepack.feature.careplan.CarePlanViewModel
import ir.carepack.feature.careplan.MedicationTextEditRoute
import ir.carepack.feature.careplan.MedicationTextEditViewModel
import ir.carepack.feature.careplan.RecipientNameEditRoute
import ir.carepack.feature.careplan.RecipientNameEditViewModel
import ir.carepack.feature.careplan.ScheduleEditRoute
import ir.carepack.feature.careplan.ScheduleEditViewModel
import ir.carepack.feature.calendar.CalendarRoute
import ir.carepack.feature.deletion.DeleteAllDataRoute
import ir.carepack.feature.deletion.MedicationDeletionRoute
import ir.carepack.feature.detail.OccurrenceDetailEntryMode
import ir.carepack.feature.detail.OccurrenceDetailRoute
import ir.carepack.feature.detail.OccurrenceDetailViewModel
import ir.carepack.feature.onboarding.OnboardingScreen
import ir.carepack.feature.privacy.PrivacyRoute
import ir.carepack.feature.reminder.ReminderSettingsRoute
import ir.carepack.feature.reminder.ReminderSettingsViewModel
import ir.carepack.feature.reporting.RangeReportRoute
import ir.carepack.feature.reporting.TodayReportRoute
import ir.carepack.feature.settings.SettingsRoute
import ir.carepack.feature.settings.SettingsViewModel
import ir.carepack.feature.setup.MedicationScheduleRoute
import ir.carepack.feature.setup.MedicationScheduleViewModel
import ir.carepack.feature.setup.RecipientSetupRoute
import ir.carepack.feature.setup.RecipientSetupViewModel
import ir.carepack.feature.today.TodayRoute
import ir.carepack.feature.today.TodayViewModel
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.MedicationDeletionCoordinator
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object AppRoutes {
    const val Onboarding = "onboarding"
    const val Recipient = "recipient"
    const val Today = "today"
    const val CarePlan = "care-plan"
    const val Calendar = "calendar"
    const val Settings = "settings"
    const val ReminderSettings = "reminder-settings"
    const val TodayReport = "today-report"
    const val RangeReport = "range-report"
    const val Privacy = "privacy"
    const val DeleteAllData = "delete-all-data"
    const val EditRecipientName = "edit-recipient-name"

    const val RecipientIdArgument = "recipientId"
    const val MedicationIdArgument = "medicationId"
    const val ScheduleSeriesIdArgument = "scheduleSeriesId"
    const val OccurrenceIdArgument = "occurrenceId"

    const val MedicationSchedulePattern =
        "medication-schedule/{$RecipientIdArgument}"

    const val AddMedicationPattern =
        "add-medication/{$RecipientIdArgument}"

    const val AddSchedulePattern =
        "add-schedule/{$MedicationIdArgument}"

    const val EditMedicationTextPattern =
        "edit-medication/{$MedicationIdArgument}"

    const val EditSchedulePattern =
        "edit-schedule/{$ScheduleSeriesIdArgument}"

    const val DeleteMedicationPattern =
        "delete-medication/{$MedicationIdArgument}"

    const val OccurrenceDetailPattern =
        "occurrence/{$OccurrenceIdArgument}"

    const val ReminderOccurrenceDetailPattern =
        "reminder/occurrence/{$OccurrenceIdArgument}"

    fun medicationSchedule(
        recipientId: String,
    ): String =
        "medication-schedule/$recipientId"

    fun addMedication(
        recipientId: String,
    ): String =
        "add-medication/$recipientId"

    fun addSchedule(
        medicationId: String,
    ): String =
        "add-schedule/$medicationId"

    fun editMedicationText(
        medicationId: String,
    ): String =
        "edit-medication/$medicationId"

    fun editSchedule(
        scheduleSeriesId: String,
    ): String =
        "edit-schedule/$scheduleSeriesId"

    fun deleteMedication(
        medicationId: String,
    ): String =
        "delete-medication/$medicationId"

    fun occurrenceDetail(
        occurrenceId: String,
    ): String =
        "occurrence/$occurrenceId"

    fun reminderOccurrenceDetail(
        occurrenceId: String,
    ): String =
        "reminder/occurrence/$occurrenceId"
}

private data class AppPrimaryDestination(
    val route: String,
    val labelResId: Int,
    val testTag: String,
)

private val appPrimaryDestinations =
    listOf(
        AppPrimaryDestination(
            route = AppRoutes.Today,
            labelResId = R.string.primary_nav_today,
            testTag = "primary_nav_today",
        ),
        AppPrimaryDestination(
            route = AppRoutes.CarePlan,
            labelResId = R.string.primary_nav_medications,
            testTag = "primary_nav_medications",
        ),
        AppPrimaryDestination(
            route = AppRoutes.Calendar,
            labelResId = R.string.primary_nav_calendar,
            testTag = "primary_nav_calendar",
        ),
        AppPrimaryDestination(
            route = AppRoutes.Settings,
            labelResId = R.string.primary_nav_settings,
            testTag = "primary_nav_settings",
        ),
    )


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

    private val mutableState =
        MutableStateFlow<AppLaunchState>(
            AppLaunchState.Loading,
        )

    val state =
        mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value =
                AppLaunchState.Loading

            mutableState.value =
                try {
                    val setupCompleted =
                        setupPreferenceStore
                            .isInitialSetupComplete()
                            .first()

                    val progress =
                        carePlanService
                            .getSetupProgress()

                    AppLaunchState.Ready(
                        startRoute =
                            routeFor(
                                setupCompleted =
                                    setupCompleted,
                                progress =
                                    progress,
                            ),
                    )
                } catch (
                    cancellationException: CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    AppLaunchState.Error(
                        message =
                            "راه‌اندازی برنامه انجام نشد.",
                    )
                }
        }
    }

    fun completeInitialSetup() {
        mutableState.value =
            AppLaunchState.Ready(
                startRoute =
                    AppRoutes.Today,
            )
    }

    private fun routeFor(
        setupCompleted: Boolean,
        progress: SetupProgress,
    ): String {
        return when {
            setupCompleted &&
                    progress == SetupProgress.Complete -> {
                AppRoutes.Today
            }

            progress is SetupProgress.RecipientOnly -> {
                AppRoutes.medicationSchedule(
                    recipientId =
                        progress.recipientId,
                )
            }

            progress == SetupProgress.Complete -> {
                AppRoutes.Today
            }

            else -> {
                AppRoutes.Onboarding
            }
        }
    }

    companion object {
        fun factory(
            carePlanService: CarePlanService,
            setupPreferenceStore: SetupPreferenceStore,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AppViewModel(
                        carePlanService =
                            carePlanService,
                        setupPreferenceStore =
                            setupPreferenceStore,
                    )
                }
            }
    }
}
