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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object Routes {
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

private data class PrimaryDestination(
    val route: String,
    val labelResId: Int,
    val testTag: String,
)

private val primaryDestinations =
    listOf(
        PrimaryDestination(
            route = Routes.Today,
            labelResId = R.string.primary_nav_today,
            testTag = "primary_nav_today",
        ),
        PrimaryDestination(
            route = Routes.CarePlan,
            labelResId = R.string.primary_nav_medications,
            testTag = "primary_nav_medications",
        ),
        PrimaryDestination(
            route = Routes.Calendar,
            labelResId = R.string.primary_nav_calendar,
            testTag = "primary_nav_calendar",
        ),
        PrimaryDestination(
            route = Routes.Settings,
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
                } catch (_: Exception) {
                    AppLaunchState.Error(
                        message =
                            "راه‌اندازی برنامه انجام نشد.",
                    )
                }
        }
    }

    private fun routeFor(
        setupCompleted: Boolean,
        progress: SetupProgress,
    ): String {
        return when {
            setupCompleted &&
                    progress == SetupProgress.Complete -> {
                Routes.Today
            }

            progress is SetupProgress.RecipientOnly -> {
                Routes.medicationSchedule(
                    recipientId =
                        progress.recipientId,
                )
            }

            progress == SetupProgress.Complete -> {
                Routes.Today
            }

            else -> {
                Routes.Onboarding
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

@Composable
fun CarePackApp(
    carePlanService: CarePlanService,
    todayQueryService: TodayQueryService,
    caregiverReportService: CaregiverReportService,
    setupPreferenceStore: SetupPreferenceStore,
    reminderPreferenceStore: ReminderPreferenceStore,
    reminderCoordinator: ReminderCoordinator,
    reminderTestCoordinator: ReminderTestCoordinator,
    notificationPermissionGateway: NotificationPermissionGateway,
    todayReportFormatter: TodayReportFormatter,
    dateRangeSummaryService: DateRangeSummaryService,
    rangeReportFormatter: RangeReportFormatter,
    privacyPreferenceStore: PrivacyPreferenceStore,
    userExperiencePreferenceStore: UserExperiencePreferenceStore,
    textShareGateway: TextShareGateway,
    dataDeletionCoordinator: DataDeletionCoordinator,
    medicationDeletionCoordinator: MedicationDeletionCoordinator,
    clock: Clock,
    zoneProvider: ZoneProvider,
    notificationOccurrenceId: String? = null,
    onNotificationOccurrenceHandled: () -> Unit = {},
    openReminderSettingsRequested: Boolean = false,
    onReminderSettingsRequestHandled: () -> Unit = {},
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

    when (val state = launchState) {
        AppLaunchState.Loading -> {
            LoadingScreen()
        }

        is AppLaunchState.Error -> {
            LaunchErrorScreen(
                message = state.message,
                onRetry = appViewModel::refresh,
            )
        }

        is AppLaunchState.Ready -> {
            CarePackNavigation(
                startRoute = state.startRoute,
                carePlanService = carePlanService,
                todayQueryService = todayQueryService,
                caregiverReportService = caregiverReportService,
                setupPreferenceStore = setupPreferenceStore,
                reminderPreferenceStore = reminderPreferenceStore,
                reminderCoordinator = reminderCoordinator,
                reminderTestCoordinator =
                    reminderTestCoordinator,
                notificationPermissionGateway =
                    notificationPermissionGateway,
                todayReportFormatter = todayReportFormatter,
                dateRangeSummaryService =
                    dateRangeSummaryService,
                rangeReportFormatter =
                    rangeReportFormatter,
                privacyPreferenceStore = privacyPreferenceStore,
                userExperiencePreferenceStore =
                    userExperiencePreferenceStore,
                textShareGateway = textShareGateway,
                dataDeletionCoordinator = dataDeletionCoordinator,
                medicationDeletionCoordinator =
                    medicationDeletionCoordinator,
                clock = clock,
                zoneProvider = zoneProvider,
                notificationOccurrenceId =
                    notificationOccurrenceId,
                onNotificationOccurrenceHandled =
                    onNotificationOccurrenceHandled,
                openReminderSettingsRequested =
                    openReminderSettingsRequested,
                onReminderSettingsRequestHandled =
                    onReminderSettingsRequestHandled,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier =
            Modifier.fillMaxSize(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center,
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
                .padding(24.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center,
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
                text = stringResource(
                    R.string.retry,
                ),
            )
        }
    }
}

@Composable
private fun CarePackNavigation(
    startRoute: String,
    carePlanService: CarePlanService,
    todayQueryService: TodayQueryService,
    caregiverReportService: CaregiverReportService,
    setupPreferenceStore: SetupPreferenceStore,
    reminderPreferenceStore: ReminderPreferenceStore,
    reminderCoordinator: ReminderCoordinator,
    reminderTestCoordinator: ReminderTestCoordinator,
    notificationPermissionGateway: NotificationPermissionGateway,
    todayReportFormatter: TodayReportFormatter,
    dateRangeSummaryService: DateRangeSummaryService,
    rangeReportFormatter: RangeReportFormatter,
    privacyPreferenceStore: PrivacyPreferenceStore,
    userExperiencePreferenceStore: UserExperiencePreferenceStore,
    textShareGateway: TextShareGateway,
    dataDeletionCoordinator: DataDeletionCoordinator,
    medicationDeletionCoordinator: MedicationDeletionCoordinator,
    clock: Clock,
    zoneProvider: ZoneProvider,
    notificationOccurrenceId: String?,
    onNotificationOccurrenceHandled: () -> Unit,
    openReminderSettingsRequested: Boolean,
    onReminderSettingsRequestHandled: () -> Unit,
) {
    val navController =
        rememberNavController()

    val backStackEntry by
    navController
        .currentBackStackEntryAsState()

    val currentRoute =
        backStackEntry
            ?.destination
            ?.route

    LaunchedEffect(
        notificationOccurrenceId,
    ) {
        val occurrenceId =
            notificationOccurrenceId

        if (!occurrenceId.isNullOrBlank()) {
            navController.navigate(
                Routes.reminderOccurrenceDetail(
                    occurrenceId =
                        occurrenceId,
                ),
            ) {
                launchSingleTop = true
            }

            onNotificationOccurrenceHandled()
        }
    }

    LaunchedEffect(
        openReminderSettingsRequested,
    ) {
        if (openReminderSettingsRequested) {
            navController.navigate(
                Routes.ReminderSettings,
            ) {
                launchSingleTop = true
            }

            onReminderSettingsRequestHandled()
        }
    }

    Scaffold(
        bottomBar = {
            if (
                currentRoute in
                primaryDestinations.map {
                    it.route
                }
            ) {
                CarePackPrimaryNavigationBar(
                    currentRoute = currentRoute,
                    navController = navController,
                )
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.Settings) {
                ExtendedFloatingActionButton(
                    onClick = {
                        navController.navigate(
                            Routes.EditRecipientName,
                        ) {
                            launchSingleTop = true
                        }
                    },
                    modifier =
                        Modifier.testTag(
                            "settings_edit_recipient_name",
                        ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .edit_recipient,
                            ),
                    )
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier =
                Modifier.padding(
                    contentPadding,
                ),
        ) {
            composable(
                Routes.Onboarding,
            ) {
                val onboardingScope =
                    rememberCoroutineScope()

                val userExperienceState by
                userExperiencePreferenceStore
                    .state
                    .collectAsStateWithLifecycle(
                        initialValue =
                            UserExperiencePreferenceState(),
                    )

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
                    onOpenPrivacy = {
                        navController.navigate(
                            Routes.Privacy,
                        ) {
                            launchSingleTop = true
                        }
                    },
                    simpleModeEnabled =
                        userExperienceState
                            .seniorMode ==
                                SeniorMode.SIMPLE,
                    onEnableSimpleMode = {
                        onboardingScope.launch {
                            userExperiencePreferenceStore
                                .setSeniorMode(
                                    SeniorMode.SIMPLE,
                                )
                        }
                    },
                    onKeepStandardMode = {
                        onboardingScope.launch {
                            userExperiencePreferenceStore
                                .setSeniorMode(
                                    SeniorMode.STANDARD,
                                )
                        }
                    },
                )
            }

            composable(
                Routes.Recipient,
            ) {
                val viewModel:
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
                    viewModel = viewModel,
                    onContinue = {
                            recipientId ->
                        navController.navigate(
                            Routes.medicationSchedule(
                                recipientId =
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
                route = Routes.MedicationSchedulePattern,
                arguments =
                    listOf(
                        navArgument(
                            Routes.RecipientIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val recipientId =
                    checkNotNull(
                        entry.arguments?.getString(
                            Routes.RecipientIdArgument,
                        ),
                    )

                val viewModel:
                        MedicationScheduleViewModel =
                    viewModel(
                        factory =
                            MedicationScheduleViewModel
                                .factory(
                                    recipientId = recipientId,
                                    carePlanService =
                                        carePlanService,
                                    setupPreferenceStore =
                                        setupPreferenceStore,
                                    userExperiencePreferenceStore =
                                        userExperiencePreferenceStore,
                                    completeInitialSetup = true,
                                    clock = clock,
                                    zoneProvider =
                                        zoneProvider,
                                ),
                    )

                MedicationScheduleRoute(
                    viewModel = viewModel,
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
                    onOpenReminderSettings = {
                        navController.navigate(
                            Routes.ReminderSettings,
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                Routes.Today,
            ) {
                val viewModel:
                        TodayViewModel =
                    viewModel(
                        factory =
                            todayViewModelFactory(
                                todayQueryService =
                                    todayQueryService,
                                caregiverReportService =
                                    caregiverReportService,
                                carePlanService =
                                    carePlanService,
                                reminderPreferenceStore =
                                    reminderPreferenceStore,
                                reminderCoordinator =
                                    reminderCoordinator,
                                userExperiencePreferenceStore =
                                    userExperiencePreferenceStore,
                                clock = clock,
                                zoneProvider =
                                    zoneProvider,
                            ),
                    )

                TodayRoute(
                    viewModel = viewModel,
                    onOpenCarePlan = {
                        navController.navigatePrimary(
                            Routes.CarePlan,
                        )
                    },
                    onOpenSettings = {
                        navController.navigatePrimary(
                            Routes.Settings,
                        )
                    },
                    onOpenTodayReport = {
                        navController.navigate(
                            Routes.TodayReport,
                        )
                    },
                    onOpenOccurrence = {
                            occurrenceId ->
                        navController.navigate(
                            Routes.occurrenceDetail(
                                occurrenceId =
                                    occurrenceId,
                            ),
                        )
                    },
                )
            }

            composable(
                Routes.CarePlan,
            ) {
                val viewModel:
                        CarePlanViewModel =
                    viewModel(
                        factory =
                            CarePlanViewModel.factory(
                                carePlanService =
                                    carePlanService,
                            ),
                    )

                CarePlanRoute(
                    viewModel = viewModel,
                    onAddMedication = {
                            recipientId ->
                        navController.navigate(
                            Routes.addMedication(
                                recipientId =
                                    recipientId,
                            ),
                        )
                    },
                    onAddSchedule = {
                            medicationId ->
                        navController.navigate(
                            Routes.addSchedule(
                                medicationId =
                                    medicationId,
                            ),
                        )
                    },
                    onEditMedicationText = {
                            medicationId ->
                        navController.navigate(
                            Routes.editMedicationText(
                                medicationId =
                                    medicationId,
                            ),
                        )
                    },
                    onEditSchedule = {
                            scheduleSeriesId ->
                        navController.navigate(
                            Routes.editSchedule(
                                scheduleSeriesId =
                                    scheduleSeriesId,
                            ),
                        )
                    },
                    onDeleteMedication = {
                            medicationId ->
                        navController.navigate(
                            Routes.deleteMedication(
                                medicationId =
                                    medicationId,
                            ),
                        )
                    },
                )
            }

            composable(
                Routes.Calendar,
            ) {
                CalendarRoute(
                    summaryService =
                        dateRangeSummaryService,
                    userExperiencePreferenceStore =
                        userExperiencePreferenceStore,
                    clock = clock,
                    zoneProvider =
                        zoneProvider,
                    onOpenOccurrence = {
                            occurrenceId ->
                        navController.navigate(
                            Routes.occurrenceDetail(
                                occurrenceId =
                                    occurrenceId,
                            ),
                        )
                    },
                    onOpenRangeReport = {
                        navController.navigate(
                            Routes.RangeReport,
                        )
                    },
                )
            }

            composable(
                Routes.Settings,
            ) {
                val viewModel:
                        SettingsViewModel =
                    viewModel(
                        factory =
                            SettingsViewModel.factory(
                                userExperiencePreferenceStore =
                                    userExperiencePreferenceStore,
                                zoneProvider =
                                    zoneProvider,
                                appVersion =
                                    BuildConfig.VERSION_NAME,
                            ),
                    )

                SettingsRoute(
                    viewModel = viewModel,
                    onBack = {
                        navController.navigatePrimary(
                            Routes.Today,
                        )
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
                val viewModel:
                        ReminderSettingsViewModel =
                    viewModel(
                        factory =
                            ReminderSettingsViewModel.factory(
                                preferenceStore =
                                    reminderPreferenceStore,
                                reminderCoordinator =
                                    reminderCoordinator,
                                reminderTestCoordinator =
                                    reminderTestCoordinator,
                                notificationPermissionGateway =
                                    notificationPermissionGateway,
                                userExperiencePreferenceStore =
                                    userExperiencePreferenceStore,
                            ),
                    )

                ReminderSettingsRoute(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    },
                    onReviewSchedules = {
                        navController.navigatePrimary(
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
                    date = reportDate,
                    formatter = todayReportFormatter,
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
                Routes.RangeReport,
            ) {
                RangeReportRoute(
                    formatter =
                        rangeReportFormatter,
                    privacyPreferenceStore =
                        privacyPreferenceStore,
                    userExperiencePreferenceStore =
                        userExperiencePreferenceStore,
                    textShareGateway =
                        textShareGateway,
                    clock = clock,
                    zoneProvider =
                        zoneProvider,
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
                Routes.EditRecipientName,
            ) {
                val viewModel:
                        RecipientNameEditViewModel =
                    viewModel(
                        factory =
                            RecipientNameEditViewModel
                                .factory(
                                    carePlanService =
                                        carePlanService,
                                ),
                    )

                RecipientNameEditRoute(
                    viewModel = viewModel,
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
                    onBack = {
                        navController.popBackStack()
                    },
                    onDeletionCompleted = {
                        navController.navigate(
                            Routes.Onboarding,
                        ) {
                            popUpTo(0) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Routes.DeleteMedicationPattern,
                arguments =
                    listOf(
                        navArgument(
                            Routes.MedicationIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val medicationId =
                    checkNotNull(
                        entry.arguments?.getString(
                            Routes.MedicationIdArgument,
                        ),
                    )

                MedicationDeletionRoute(
                    medicationId =
                        medicationId,
                    coordinator =
                        medicationDeletionCoordinator,
                    onDeletionCompleted = {
                        navController.navigatePrimary(
                            Routes.CarePlan,
                        )
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Routes.AddMedicationPattern,
                arguments =
                    listOf(
                        navArgument(
                            Routes.RecipientIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val recipientId =
                    checkNotNull(
                        entry.arguments?.getString(
                            Routes.RecipientIdArgument,
                        ),
                    )

                val viewModel:
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
                                    userExperiencePreferenceStore =
                                        userExperiencePreferenceStore,
                                    completeInitialSetup =
                                        false,
                                    clock = clock,
                                    zoneProvider =
                                        zoneProvider,
                                ),
                    )

                MedicationScheduleRoute(
                    viewModel = viewModel,
                    onCompleted = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Routes.AddSchedulePattern,
                arguments =
                    listOf(
                        navArgument(
                            Routes.MedicationIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val medicationId =
                    checkNotNull(
                        entry.arguments?.getString(
                            Routes.MedicationIdArgument,
                        ),
                    )

                val viewModel:
                        MedicationScheduleViewModel =
                    viewModel(
                        factory =
                            MedicationScheduleViewModel
                                .addScheduleFactory(
                                    medicationId =
                                        medicationId,
                                    carePlanService =
                                        carePlanService,
                                    setupPreferenceStore =
                                        setupPreferenceStore,
                                    userExperiencePreferenceStore =
                                        userExperiencePreferenceStore,
                                    clock = clock,
                                    zoneProvider =
                                        zoneProvider,
                                ),
                    )

                MedicationScheduleRoute(
                    viewModel = viewModel,
                    onCompleted = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Routes.EditMedicationTextPattern,
                arguments =
                    listOf(
                        navArgument(
                            Routes.MedicationIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val medicationId =
                    checkNotNull(
                        entry.arguments?.getString(
                            Routes.MedicationIdArgument,
                        ),
                    )

                val viewModel:
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
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    },
                    onCompleted = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Routes.EditSchedulePattern,
                arguments =
                    listOf(
                        navArgument(
                            Routes.ScheduleSeriesIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val scheduleSeriesId =
                    checkNotNull(
                        entry.arguments?.getString(
                            Routes.ScheduleSeriesIdArgument,
                        ),
                    )

                val viewModel:
                        ScheduleEditViewModel =
                    viewModel(
                        factory =
                            ScheduleEditViewModel
                                .factory(
                                    scheduleSeriesId =
                                        scheduleSeriesId,
                                    carePlanService =
                                        carePlanService,
                                    zoneProvider =
                                        zoneProvider,
                                    userExperiencePreferenceStore =
                                        userExperiencePreferenceStore,
                                ),
                    )

                ScheduleEditRoute(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    },
                    onCompleted = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Routes.ReminderOccurrenceDetailPattern,
                arguments =
                    listOf(
                        navArgument(
                            Routes.OccurrenceIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val occurrenceId =
                    checkNotNull(
                        entry.arguments?.getString(
                            Routes.OccurrenceIdArgument,
                        ),
                    )

                val viewModel:
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
                                    reminderCoordinator =
                                        reminderCoordinator,
                                    clock = clock,
                                ),
                    )

                OccurrenceDetailRoute(
                    viewModel = viewModel,
                    entryMode =
                        OccurrenceDetailEntryMode.REMINDER,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Routes.OccurrenceDetailPattern,
                arguments =
                    listOf(
                        navArgument(
                            Routes.OccurrenceIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val occurrenceId =
                    checkNotNull(
                        entry.arguments?.getString(
                            Routes.OccurrenceIdArgument,
                        ),
                    )

                val viewModel:
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
                                    reminderCoordinator =
                                        reminderCoordinator,
                                    clock = clock,
                                ),
                    )

                OccurrenceDetailRoute(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

@Composable
private fun CarePackPrimaryNavigationBar(
    currentRoute: String?,
    navController: NavHostController,
) {
    NavigationBar(
        modifier =
            Modifier.testTag(
                "primary_navigation",
            ),
    ) {
        primaryDestinations.forEach { destination ->
            NavigationBarItem(
                selected =
                    currentRoute ==
                            destination.route,
                onClick = {
                    navController.navigatePrimary(
                        destination.route,
                    )
                },
                icon = {
                    Text(
                        text = "•",
                    )
                },
                label = {
                    Text(
                        text =
                            stringResource(
                                destination.labelResId,
                            ),
                    )
                },
                modifier =
                    Modifier.testTag(
                        destination.testTag,
                    ),
            )
        }
    }
}

private fun NavHostController.navigatePrimary(
    route: String,
) {
    navigate(
        route,
    ) {
        popUpTo(
            graph.findStartDestination().id,
        ) {
            saveState = true
        }

        launchSingleTop = true
        restoreState = true
    }
}

private fun todayViewModelFactory(
    todayQueryService: TodayQueryService,
    caregiverReportService: CaregiverReportService,
    carePlanService: CarePlanService,
    reminderPreferenceStore: ReminderPreferenceStore,
    reminderCoordinator: ReminderCoordinator,
    userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    clock: Clock,
    zoneProvider: ZoneProvider,
): ViewModelProvider.Factory =
    TodayViewModel.factory(
        todayQueryService =
            todayQueryService,
        caregiverReportService =
            caregiverReportService,
        carePlanService =
            carePlanService,
        reminderPreferenceStore =
            reminderPreferenceStore,
        reminderCoordinator =
            reminderCoordinator,
        userExperiencePreferenceStore =
            userExperiencePreferenceStore,
        clock = clock,
        zoneProvider =
            zoneProvider,
    )
