package ir.carepack.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.SetupProgress
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.TodayReportFormatter
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.feature.careplan.CarePlanRoute
import ir.carepack.feature.careplan.CarePlanViewModel
import ir.carepack.feature.careplan.MedicationTextEditRoute
import ir.carepack.feature.careplan.MedicationTextEditViewModel
import ir.carepack.feature.careplan.ScheduleEditRoute
import ir.carepack.feature.careplan.ScheduleEditViewModel
import ir.carepack.feature.deletion.DeleteAllDataRoute
import ir.carepack.feature.detail.OccurrenceDetailRoute
import ir.carepack.feature.detail.OccurrenceDetailViewModel
import ir.carepack.feature.onboarding.OnboardingScreen
import ir.carepack.feature.privacy.PrivacyRoute
import ir.carepack.feature.reminder.ReminderSettingsRoute
import ir.carepack.feature.reminder.ReminderSettingsViewModel
import ir.carepack.feature.reporting.TodayReportRoute
import ir.carepack.feature.settings.SettingsScreen
import ir.carepack.feature.setup.MedicationScheduleRoute
import ir.carepack.feature.setup.MedicationScheduleViewModel
import ir.carepack.feature.setup.RecipientSetupRoute
import ir.carepack.feature.setup.RecipientSetupViewModel
import ir.carepack.feature.today.TodayRoute
import ir.carepack.feature.today.TodayViewModel
import ir.carepack.reminder.permission.NotificationPermissionGateway
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.settings.deletion.DataDeletionCoordinator
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object Routes {
    const val Onboarding = "onboarding"
    const val Recipient = "recipient"
    const val Today = "today"
    const val CarePlan = "care-plan"
    const val Settings = "settings"
    const val ReminderSettings = "reminder-settings"
    const val TodayReport = "today-report"
    const val Privacy = "privacy"
    const val DeleteAllData = "delete-all-data"

    const val RecipientIdArgument = "recipientId"
    const val MedicationIdArgument = "medicationId"
    const val OccurrenceIdArgument = "occurrenceId"

    const val MedicationSchedulePattern =
        "medication-schedule/{$RecipientIdArgument}"

    const val AddMedicationPattern =
        "add-medication/{$RecipientIdArgument}"

    const val EditMedicationTextPattern =
        "edit-medication/{$MedicationIdArgument}"

    const val EditSchedulePattern =
        "edit-schedule/{$MedicationIdArgument}"

    const val OccurrenceDetailPattern =
        "occurrence/{$OccurrenceIdArgument}"

    fun medicationSchedule(
        recipientId: String,
    ): String =
        "medication-schedule/$recipientId"

    fun addMedication(
        recipientId: String,
    ): String =
        "add-medication/$recipientId"

    fun editMedicationText(
        medicationId: String,
    ): String =
        "edit-medication/$medicationId"

    fun editSchedule(
        medicationId: String,
    ): String =
        "edit-schedule/$medicationId"

    fun occurrenceDetail(
        occurrenceId: String,
    ): String =
        "occurrence/$occurrenceId"
}

sealed interface AppLaunchState {

    data object Loading :
        AppLaunchState

    data class Ready(
        val startRoute: String,
    ) : AppLaunchState

    data class Error(
        val message: String,
    ) : AppLaunchState
}

class AppViewModel(
    private val carePlanService:
    CarePlanService,
    private val setupPreferenceStore:
    SetupPreferenceStore,
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

            try {
                val preferenceWasComplete =
                    setupPreferenceStore
                        .setupComplete
                        .first()

                val progress =
                    carePlanService
                        .getSetupProgress()

                val startRoute =
                    when (progress) {
                        SetupProgress.Empty -> {
                            Routes.Onboarding
                        }

                        is SetupProgress.RecipientOnly -> {
                            Routes.medicationSchedule(
                                progress.recipientId,
                            )
                        }

                        SetupProgress.Complete -> {
                            if (!preferenceWasComplete) {
                                runCatching {
                                    setupPreferenceStore
                                        .markSetupComplete()
                                }
                            }

                            Routes.Today
                        }
                    }

                mutableState.value =
                    AppLaunchState.Ready(
                        startRoute = startRoute,
                    )
            } catch (_: Exception) {
                mutableState.value =
                    AppLaunchState.Error(
                        message =
                            "راه‌اندازی برنامه انجام نشد.",
                    )
            }
        }
    }

    companion object {

        fun factory(
            carePlanService:
            CarePlanService,
            setupPreferenceStore:
            SetupPreferenceStore,
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

@Composable
fun CarePackApp(
    carePlanService: CarePlanService,
    todayQueryService:
    TodayQueryService,
    caregiverReportService:
    CaregiverReportService,
    setupPreferenceStore:
    SetupPreferenceStore,
    reminderPreferenceStore:
    ReminderPreferenceStore,
    reminderCoordinator:
    ReminderCoordinator,
    notificationPermissionGateway:
    NotificationPermissionGateway,
    todayReportFormatter:
    TodayReportFormatter,
    privacyPreferenceStore:
    PrivacyPreferenceStore,
    textShareGateway:
    TextShareGateway,
    dataDeletionCoordinator:
    DataDeletionCoordinator,
    clock: Clock,
    zoneProvider: ZoneProvider,
    notificationOccurrenceId:
    String? = null,
    onNotificationOccurrenceHandled:
        () -> Unit = {},
) {
    val appViewModel:
            AppViewModel =
        viewModel(
            factory =
                AppViewModel.factory(
                    carePlanService =
                        carePlanService,
                    setupPreferenceStore =
                        setupPreferenceStore,
                ),
        )

    val launchState by
    appViewModel
        .state
        .collectAsStateWithLifecycle()

    when (
        val currentState =
            launchState
    ) {
        AppLaunchState.Loading -> {
            LoadingScreen()
        }

        is AppLaunchState.Error -> {
            LaunchErrorScreen(
                message =
                    currentState.message,
                onRetry =
                    appViewModel::refresh,
            )
        }

        is AppLaunchState.Ready -> {
            CarePackNavigation(
                startRoute =
                    currentState.startRoute,
                carePlanService =
                    carePlanService,
                todayQueryService =
                    todayQueryService,
                caregiverReportService =
                    caregiverReportService,
                setupPreferenceStore =
                    setupPreferenceStore,
                reminderPreferenceStore =
                    reminderPreferenceStore,
                reminderCoordinator =
                    reminderCoordinator,
                notificationPermissionGateway =
                    notificationPermissionGateway,
                todayReportFormatter =
                    todayReportFormatter,
                privacyPreferenceStore =
                    privacyPreferenceStore,
                textShareGateway =
                    textShareGateway,
                dataDeletionCoordinator =
                    dataDeletionCoordinator,
                clock = clock,
                zoneProvider =
                    zoneProvider,
                notificationOccurrenceId =
                    notificationOccurrenceId,
                onNotificationOccurrenceHandled =
                    onNotificationOccurrenceHandled,
            )
        }
    }
}

@Composable
private fun CarePackNavigation(
    startRoute: String,
    carePlanService:
    CarePlanService,
    todayQueryService:
    TodayQueryService,
    caregiverReportService:
    CaregiverReportService,
    setupPreferenceStore:
    SetupPreferenceStore,
    reminderPreferenceStore:
    ReminderPreferenceStore,
    reminderCoordinator:
    ReminderCoordinator,
    notificationPermissionGateway:
    NotificationPermissionGateway,
    todayReportFormatter:
    TodayReportFormatter,
    privacyPreferenceStore:
    PrivacyPreferenceStore,
    textShareGateway:
    TextShareGateway,
    dataDeletionCoordinator:
    DataDeletionCoordinator,
    clock: Clock,
    zoneProvider: ZoneProvider,
    notificationOccurrenceId:
    String?,
    onNotificationOccurrenceHandled:
        () -> Unit,
) {
    val navController =
        rememberNavController()

    LaunchedEffect(
        notificationOccurrenceId,
    ) {
        val occurrenceId =
            notificationOccurrenceId

        if (!occurrenceId.isNullOrBlank()) {
            navController.navigate(
                Routes.occurrenceDetail(
                    occurrenceId,
                ),
            ) {
                launchSingleTop = true
            }

            onNotificationOccurrenceHandled()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
    ) {
        composable(
            Routes.Onboarding,
        ) {
            OnboardingScreen(
                onContinue = {
                    navController.navigate(
                        Routes.Recipient,
                    ) {
                        popUpTo(
                            Routes.Onboarding,
                        ) {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Routes.Recipient,
        ) {
            val recipientViewModel:
                    RecipientSetupViewModel =
                viewModel(
                    factory =
                        RecipientSetupViewModel
                            .factory(
                                carePlanService =
                                    carePlanService,
                            ),
                )

            RecipientSetupRoute(
                viewModel =
                    recipientViewModel,
                onContinue = {
                        recipientId ->
                    navController.navigate(
                        Routes.medicationSchedule(
                            recipientId,
                        ),
                    ) {
                        popUpTo(
                            Routes.Recipient,
                        ) {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route =
                Routes.MedicationSchedulePattern,
            arguments =
                listOf(
                    navArgument(
                        Routes.RecipientIdArgument,
                    ) {
                        type =
                            NavType.StringType
                    },
                ),
        ) { backStackEntry ->
            val recipientId =
                checkNotNull(
                    backStackEntry
                        .arguments
                        ?.getString(
                            Routes.RecipientIdArgument,
                        ),
                )

            val medicationViewModel:
                    MedicationScheduleViewModel =
                viewModel(
                    factory =
                        MedicationScheduleViewModel
                            .factory(
                                recipientId =
                                    recipientId,
                                carePlanService =
                                    carePlanService,
                                setupPreferenceStore =
                                    setupPreferenceStore,
                                completeInitialSetup =
                                    true,
                                clock = clock,
                                zoneProvider =
                                    zoneProvider,
                            ),
                )

            MedicationScheduleRoute(
                viewModel =
                    medicationViewModel,
                onCompleted = {
                    navController.navigate(
                        Routes.Today,
                    ) {
                        popUpTo(
                            Routes.Onboarding,
                        ) {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Routes.Today,
        ) {
            val todayViewModel:
                    TodayViewModel =
                viewModel(
                    factory =
                        TodayViewModel.factory(
                            todayQueryService =
                                todayQueryService,
                            reminderCoordinator =
                                reminderCoordinator,
                            reminderPreferenceStore =
                                reminderPreferenceStore,
                            clock = clock,
                            zoneProvider =
                                zoneProvider,
                        ),
                )

            TodayRoute(
                viewModel =
                    todayViewModel,
                onOccurrenceSelected = {
                        occurrenceId ->
                    navController.navigate(
                        Routes.occurrenceDetail(
                            occurrenceId,
                        ),
                    )
                },
                onManageCarePlan = {
                    navController.navigate(
                        Routes.CarePlan,
                    )
                },
                onOpenTodayReport = {
                    navController.navigate(
                        Routes.TodayReport,
                    )
                },
                onOpenSettings = {
                    navController.navigate(
                        Routes.Settings,
                    )
                },
                onReminderSettings = {
                    navController.navigate(
                        Routes.ReminderSettings,
                    )
                },
                onReviewSchedules = {
                    navController.navigate(
                        Routes.CarePlan,
                    )
                },
            )
        }

        composable(
            Routes.CarePlan,
        ) {
            val carePlanViewModel:
                    CarePlanViewModel =
                viewModel(
                    factory =
                        CarePlanViewModel.factory(
                            carePlanService =
                                carePlanService,
                        ),
                )

            CarePlanRoute(
                viewModel =
                    carePlanViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onAddMedication = {
                        recipientId ->
                    navController.navigate(
                        Routes.addMedication(
                            recipientId,
                        ),
                    )
                },
                onEditMedicationText = {
                        medicationId ->
                    navController.navigate(
                        Routes.editMedicationText(
                            medicationId,
                        ),
                    )
                },
                onEditSchedule = {
                        medicationId ->
                    navController.navigate(
                        Routes.editSchedule(
                            medicationId,
                        ),
                    )
                },
            )
        }

        composable(
            Routes.Settings,
        ) {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenReminderSettings = {
                    navController.navigate(
                        Routes.ReminderSettings,
                    )
                },
                onOpenTodayReport = {
                    navController.navigate(
                        Routes.TodayReport,
                    )
                },
                onOpenPrivacy = {
                    navController.navigate(
                        Routes.Privacy,
                    )
                },
                onDeleteAllData = {
                    navController.navigate(
                        Routes.DeleteAllData,
                    )
                },
            )
        }

        composable(
            Routes.ReminderSettings,
        ) {
            val reminderViewModel:
                    ReminderSettingsViewModel =
                viewModel(
                    factory =
                        ReminderSettingsViewModel.factory(
                            preferenceStore =
                                reminderPreferenceStore,
                            reminderCoordinator =
                                reminderCoordinator,
                            notificationPermissionGateway =
                                notificationPermissionGateway,
                        ),
                )

            ReminderSettingsRoute(
                viewModel =
                    reminderViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onReviewSchedules = {
                    navController.navigate(
                        Routes.CarePlan,
                    )
                },
            )
        }

        composable(
            Routes.TodayReport,
        ) {
            val reportDate =
                clock
                    .instant()
                    .atZone(
                        zoneProvider.currentZone(),
                    )
                    .toLocalDate()

            TodayReportRoute(
                date =
                    reportDate,
                formatter =
                    todayReportFormatter,
                privacyPreferenceStore =
                    privacyPreferenceStore,
                textShareGateway =
                    textShareGateway,
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            Routes.Privacy,
        ) {
            PrivacyRoute(
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            Routes.DeleteAllData,
        ) {
            DeleteAllDataRoute(
                dataDeletionCoordinator =
                    dataDeletionCoordinator,
                onDeletionCompleted = {
                    navController.navigate(
                        Routes.Onboarding,
                    ) {
                        popUpTo(
                            navController.graph.startDestinationId,
                        ) {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route =
                Routes.AddMedicationPattern,
            arguments =
                listOf(
                    navArgument(
                        Routes.RecipientIdArgument,
                    ) {
                        type =
                            NavType.StringType
                    },
                ),
        ) { backStackEntry ->
            val recipientId =
                checkNotNull(
                    backStackEntry
                        .arguments
                        ?.getString(
                            Routes.RecipientIdArgument,
                        ),
                )

            val medicationViewModel:
                    MedicationScheduleViewModel =
                viewModel(
                    factory =
                        MedicationScheduleViewModel
                            .factory(
                                recipientId =
                                    recipientId,
                                carePlanService =
                                    carePlanService,
                                setupPreferenceStore =
                                    setupPreferenceStore,
                                completeInitialSetup =
                                    false,
                                clock = clock,
                                zoneProvider =
                                    zoneProvider,
                            ),
                )

            MedicationScheduleRoute(
                viewModel =
                    medicationViewModel,
                onCompleted = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route =
                Routes.EditMedicationTextPattern,
            arguments =
                listOf(
                    navArgument(
                        Routes.MedicationIdArgument,
                    ) {
                        type =
                            NavType.StringType
                    },
                ),
        ) { backStackEntry ->
            val medicationId =
                checkNotNull(
                    backStackEntry
                        .arguments
                        ?.getString(
                            Routes.MedicationIdArgument,
                        ),
                )

            val medicationTextViewModel:
                    MedicationTextEditViewModel =
                viewModel(
                    factory =
                        MedicationTextEditViewModel
                            .factory(
                                medicationId =
                                    medicationId,
                                carePlanService =
                                    carePlanService,
                            ),
                )

            MedicationTextEditRoute(
                viewModel =
                    medicationTextViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onCompleted = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route =
                Routes.EditSchedulePattern,
            arguments =
                listOf(
                    navArgument(
                        Routes.MedicationIdArgument,
                    ) {
                        type =
                            NavType.StringType
                    },
                ),
        ) { backStackEntry ->
            val medicationId =
                checkNotNull(
                    backStackEntry
                        .arguments
                        ?.getString(
                            Routes.MedicationIdArgument,
                        ),
                )

            val scheduleEditViewModel:
                    ScheduleEditViewModel =
                viewModel(
                    factory =
                        ScheduleEditViewModel
                            .factory(
                                medicationId =
                                    medicationId,
                                carePlanService =
                                    carePlanService,
                                zoneProvider =
                                    zoneProvider,
                            ),
                )

            ScheduleEditRoute(
                viewModel =
                    scheduleEditViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onCompleted = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route =
                Routes.OccurrenceDetailPattern,
            arguments =
                listOf(
                    navArgument(
                        Routes.OccurrenceIdArgument,
                    ) {
                        type =
                            NavType.StringType
                    },
                ),
        ) { backStackEntry ->
            val occurrenceId =
                checkNotNull(
                    backStackEntry
                        .arguments
                        ?.getString(
                            Routes.OccurrenceIdArgument,
                        ),
                )

            val occurrenceViewModel:
                    OccurrenceDetailViewModel =
                viewModel(
                    factory =
                        OccurrenceDetailViewModel
                            .factory(
                                occurrenceId =
                                    occurrenceId,
                                todayQueryService =
                                    todayQueryService,
                                caregiverReportService =
                                    caregiverReportService,
                                clock = clock,
                            ),
                )

            OccurrenceDetailRoute(
                viewModel =
                    occurrenceViewModel,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    24.dp,
                ),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LaunchErrorScreen(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    24.dp,
                ),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
        )

        Button(
            onClick = onRetry,
            modifier =
                Modifier.padding(
                    top = 16.dp,
                ),
        ) {
            Text(
                text = "تلاش دوباره",
            )
        }
    }
}
