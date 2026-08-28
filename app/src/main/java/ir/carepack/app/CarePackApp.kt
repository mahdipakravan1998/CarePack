package ir.carepack.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.reminder.ReminderCoordinator
import ir.carepack.domain.reminder.ReminderPreferenceState
import ir.carepack.domain.reminder.ReminderPreferenceStore
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.today.TodayQueryService
import ir.carepack.feature.careplan.ArchivedMedicationDetailRoute
import ir.carepack.feature.careplan.ArchivedMedicationDetailViewModel
import ir.carepack.feature.careplan.ArchivedMedicationListRoute
import ir.carepack.feature.careplan.ArchivedMedicationListViewModel
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
import ir.carepack.feature.onboarding.OnboardingSimpleModeViewModel
import ir.carepack.feature.privacy.PrivacyRoute
import ir.carepack.feature.reminder.ReminderSettingsRoute
import ir.carepack.feature.reminder.ReminderSettingsViewModel
import ir.carepack.feature.reminder.TimezoneWarningBanner
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
import java.time.Clock

import kotlinx.coroutines.launch

private data class PrimaryDestination(
    val route: String,
    val labelResId: Int,
    val testTag: String,
)

private val primaryDestinations = listOf(
        PrimaryDestination(
            route = CarePackRoutes.Today,
            labelResId = R.string.primary_nav_today,
            testTag = "primary_nav_today",
        ),
        PrimaryDestination(
            route = CarePackRoutes.CarePlan,
            labelResId = R.string.primary_nav_medications,
            testTag = "primary_nav_medications",
        ),
        PrimaryDestination(
            route = CarePackRoutes.Calendar,
            labelResId = R.string.primary_nav_calendar,
            testTag = "primary_nav_calendar",
        ),
        PrimaryDestination(
            route = CarePackRoutes.Settings,
            labelResId = R.string.primary_nav_settings,
            testTag = "primary_nav_settings",
        ),
    )


@Composable
fun CarePackApp(
    dependencies: CarePackUiDependencies,
    notificationOccurrenceId: String? = null,
    onNotificationOccurrenceHandled: () -> Unit = {},
    openReminderSettingsRequested: Boolean = false,
    onReminderSettingsRequestHandled: () -> Unit = {},
) {
    val appViewModel: AppViewModel =
        viewModel(
            factory = AppViewModel.factory(
                    carePlanService = dependencies.carePlanService,
                    setupPreferenceStore = dependencies.setupPreferenceStore,
                ),
        )

    val launchState by
    appViewModel.state
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
                onInitialSetupCompleted = appViewModel::completeInitialSetup,
                dependencies = dependencies,
                notificationOccurrenceId = notificationOccurrenceId,
                onNotificationOccurrenceHandled = onNotificationOccurrenceHandled,
                openReminderSettingsRequested = openReminderSettingsRequested,
                onReminderSettingsRequestHandled = onReminderSettingsRequestHandled,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
        modifier = Modifier
                .fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
        )

        Button(
            onClick = onRetry,
            modifier = Modifier.padding(
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
    onInitialSetupCompleted: () -> Unit,
    dependencies: CarePackUiDependencies,
    notificationOccurrenceId: String?,
    onNotificationOccurrenceHandled: () -> Unit,
    openReminderSettingsRequested: Boolean,
    onReminderSettingsRequestHandled: () -> Unit,
) {
    val navController = rememberNavController()

    val carePlanService = dependencies.carePlanService
    val todayQueryService = dependencies.todayQueryService
    val caregiverReportService = dependencies.caregiverReportService
    val setupPreferenceStore = dependencies.setupPreferenceStore
    val reminderPreferenceStore = dependencies.reminderPreferenceStore
    val reminderCoordinator = dependencies.reminderCoordinator
    val reminderTestCoordinator = dependencies.reminderTestCoordinator
    val notificationPermissionGateway = dependencies.notificationPermissionGateway
    val todayReportFormatter = dependencies.todayReportFormatter
    val dateRangeSummaryService = dependencies.dateRangeSummaryService
    val rangeReportFormatter = dependencies.rangeReportFormatter
    val privacyPreferenceStore = dependencies.privacyPreferenceStore
    val userExperiencePreferenceStore = dependencies.userExperiencePreferenceStore
    val textShareGateway = dependencies.textShareGateway
    val dataDeletionCoordinator = dependencies.dataDeletionCoordinator
    val medicationDeletionCoordinator = dependencies.medicationDeletionCoordinator
    val clock = dependencies.clock
    val zoneProvider = dependencies.zoneProvider


    val backStackEntry by
    navController.currentBackStackEntryAsState()

    val currentRoute = backStackEntry
            ?.destination?.route

    val reminderPreferenceState by reminderPreferenceStore.state
        .collectAsStateWithLifecycle(initialValue = ReminderPreferenceState())
    val navigationScope = rememberCoroutineScope()
    var timezoneDismissError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(
        notificationOccurrenceId,
    ) {
        val occurrenceId = notificationOccurrenceId

        if (!occurrenceId.isNullOrBlank()) {
            navController.navigate(
                CarePackRoutes.reminderOccurrenceDetail(
                    occurrenceId = occurrenceId,
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
                CarePackRoutes.ReminderSettings,
            ) {
                launchSingleTop = true
            }

            onReminderSettingsRequestHandled()
        }
    }

    Scaffold(
        topBar = {
            reminderPreferenceState.timezoneWarning?.let { warning ->
                TimezoneWarningBanner(
                    warning = warning,
                    errorMessage = timezoneDismissError,
                    onReviewSchedules = {
                        navController.navigatePrimary(CarePackRoutes.CarePlan)
                    },
                    onDismiss = {
                        navigationScope.launch {
                            try {
                                reminderPreferenceStore.dismissTimezoneWarning()
                                timezoneDismissError = null
                            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                                throw cancellation
                            } catch (_: Exception) {
                                timezoneDismissError =
                                    "ذخیره وضعیت هشدار انجام نشد؛ هشدار همچنان نمایش داده می‌شود."
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (
                currentRoute in primaryDestinations.map {
                    it.route
                }) {
                CarePackPrimaryNavigationBar(
                    currentRoute = currentRoute,
                    navController = navController,
                )
            }
        },

    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(
                    contentPadding,
                ),
        ) {
            composable(
                CarePackRoutes.Onboarding,
            ) {
                val onboardingSimpleModeViewModel: OnboardingSimpleModeViewModel =
                    viewModel(
                        factory = OnboardingSimpleModeViewModel.factory(
                            userExperiencePreferenceStore,
                        ),
                    )
                val onboardingSimpleModeState by onboardingSimpleModeViewModel.state
                    .collectAsStateWithLifecycle()

                OnboardingScreen(
                    onContinue = {
                        navController.navigate(
                            CarePackRoutes.Recipient,
                        ) {
                            popUpTo(
                                CarePackRoutes.Onboarding,
                            ) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    },
                    onOpenPrivacy = {
                        navController.navigate(
                            CarePackRoutes.Privacy,
                        ) {
                            launchSingleTop = true
                        }
                    },
                    simpleModeEnabled = onboardingSimpleModeState.preferenceState
                            .seniorMode == SeniorMode.SIMPLE,
                    isSavingSimpleMode = onboardingSimpleModeState.isSaving,
                    simpleModeErrorMessage = onboardingSimpleModeState.errorMessage,
                    onEnableSimpleMode = onboardingSimpleModeViewModel::selectSimpleMode,
                    onKeepStandardMode = onboardingSimpleModeViewModel::keepStandardMode,
                    onRetrySimpleModeSelection =
                        onboardingSimpleModeViewModel::retryLastSelection,
                )
            }

            composable(
                CarePackRoutes.Recipient,
            ) {
                val viewModel: RecipientSetupViewModel =
                    viewModel(
                        factory = RecipientSetupViewModel
                                .factory(
                                    carePlanService = carePlanService,
                                ),
                    )

                RecipientSetupRoute(
                    viewModel = viewModel,
                    onContinue = {
                            recipientId ->
                        navController.navigate(
                            CarePackRoutes.medicationSchedule(
                                recipientId = recipientId,
                            ),
                        ) {
                            popUpTo(
                                CarePackRoutes.Recipient,
                            ) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = CarePackRoutes.MedicationSchedulePattern,
                arguments = listOf(
                        navArgument(
                            CarePackRoutes.RecipientIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val recipientId = entry.requireStringArgument(
                        CarePackRoutes.RecipientIdArgument,
                    )

                val viewModel: MedicationScheduleViewModel =
                    viewModel(
                        factory = MedicationScheduleViewModel
                                .factory(
                                    recipientId = recipientId,
                                    carePlanService = carePlanService,
                                    setupPreferenceStore = setupPreferenceStore,
                                    userExperiencePreferenceStore = userExperiencePreferenceStore,
                                    completeInitialSetup = true,
                                    clock = clock,
                                    zoneProvider = zoneProvider,
                                ),
                    )

                MedicationScheduleRoute(
                    viewModel = viewModel,
                    onCompleted = {
                        navController.navigate(
                            CarePackRoutes.Today,
                        ) {
                            popUpTo(
                                CarePackRoutes.Onboarding,
                            ) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    },
                    onCompletionModeSelected = { _ ->
                        onInitialSetupCompleted()
                    },
                    onOpenReminderSettings = {
                        navController.navigate(
                            CarePackRoutes.ReminderSettings,
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                CarePackRoutes.Today,
            ) {
                val viewModel: TodayViewModel =
                    viewModel(
                        factory = todayViewModelFactory(
                                todayQueryService = todayQueryService,
                                caregiverReportService = caregiverReportService,
                                carePlanService = carePlanService,
                                reminderPreferenceStore = reminderPreferenceStore,
                                reminderCoordinator = reminderCoordinator,
                                userExperiencePreferenceStore = userExperiencePreferenceStore,
                                clock = clock,
                                zoneProvider = zoneProvider,
                            ),
                    )

                TodayRoute(
                    viewModel = viewModel,
                    onOpenCarePlan = {
                        navController.navigatePrimary(
                            CarePackRoutes.CarePlan,
                        )
                    },

                    onOpenTodayReport = {
                        navController.navigate(
                            CarePackRoutes.TodayReport,
                        )
                    },
                    onOpenOccurrence = {
                            occurrenceId ->
                        navController.navigate(
                            CarePackRoutes.occurrenceDetail(
                                occurrenceId = occurrenceId,
                            ),
                        )
                    },
                )
            }

            composable(
                CarePackRoutes.CarePlan,
            ) {
                val viewModel: CarePlanViewModel =
                    viewModel(
                        factory = CarePlanViewModel.factory(
                                carePlanService = carePlanService,
                            ),
                    )

                CarePlanRoute(
                    viewModel = viewModel,
                    onAddMedication = {
                            recipientId ->
                        navController.navigate(
                            CarePackRoutes.addMedication(
                                recipientId = recipientId,
                            ),
                        )
                    },
                    onAddSchedule = {
                            medicationId ->
                        navController.navigate(
                            CarePackRoutes.addSchedule(
                                medicationId = medicationId,
                            ),
                        )
                    },
                    onEditMedicationText = {
                            medicationId ->
                        navController.navigate(
                            CarePackRoutes.editMedicationText(
                                medicationId = medicationId,
                            ),
                        )
                    },
                    onEditSchedule = {
                            scheduleSeriesId ->
                        navController.navigate(
                            CarePackRoutes.editSchedule(
                                scheduleSeriesId = scheduleSeriesId,
                            ),
                        )
                    },
                    onDeleteMedication = {
                            medicationId ->
                        navController.navigate(
                            CarePackRoutes.deleteMedication(
                                medicationId = medicationId,
                            ),
                        )
                    },
                    onOpenArchivedMedications = {
                        navController.navigate(CarePackRoutes.ArchivedMedications)
                    },
                )
            }

            composable(CarePackRoutes.ArchivedMedications) {
                val viewModel: ArchivedMedicationListViewModel = viewModel(
                    factory = ArchivedMedicationListViewModel.factory(carePlanService),
                )
                ArchivedMedicationListRoute(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenMedication = { medicationId ->
                        navController.navigate(
                            CarePackRoutes.archivedMedicationDetail(medicationId),
                        )
                    },
                )
            }

            composable(
                route = CarePackRoutes.ArchivedMedicationDetailPattern,
                arguments = listOf(
                    navArgument(CarePackRoutes.MedicationIdArgument) {
                        type = NavType.StringType
                    },
                ),
            ) { entry ->
                val medicationId = entry.requireStringArgument(
                    CarePackRoutes.MedicationIdArgument,
                )
                val viewModel: ArchivedMedicationDetailViewModel = viewModel(
                    factory = ArchivedMedicationDetailViewModel.factory(
                        medicationId = medicationId,
                        carePlanService = carePlanService,
                    ),
                )
                ArchivedMedicationDetailRoute(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onDeleteMedication = { id ->
                        navController.navigate(CarePackRoutes.deleteMedication(id))
                    },
                )
            }

            composable(
                CarePackRoutes.Calendar,
            ) {
                CalendarRoute(
                    summaryService = dateRangeSummaryService,
                    userExperiencePreferenceStore = userExperiencePreferenceStore,
                    clock = clock,
                    zoneProvider = zoneProvider,
                    onOpenOccurrence = {
                            occurrenceId ->
                        navController.navigate(
                            CarePackRoutes.occurrenceDetail(
                                occurrenceId = occurrenceId,
                            ),
                        )
                    },
                    onOpenRangeReport = {
                        navController.navigate(
                            CarePackRoutes.RangeReport,
                        )
                    },
                )
            }

            composable(
                CarePackRoutes.Settings,
            ) {
                val viewModel: SettingsViewModel =
                    viewModel(
                        factory = SettingsViewModel.factory(
                                userExperiencePreferenceStore = userExperiencePreferenceStore,
                                carePlanService = carePlanService,
                                zoneProvider = zoneProvider,
                                appVersion = BuildConfig.VERSION_NAME,
                            ),
                    )

                SettingsRoute(
                    viewModel = viewModel,
                    onEditRecipient = {
                        navController.navigate(CarePackRoutes.EditRecipientName)
                    },
                    onOpenReminderSettings = {
                        navController.navigate(
                            CarePackRoutes.ReminderSettings,
                        )
                    },
                    onOpenPrivacy = {
                        navController.navigate(
                            CarePackRoutes.Privacy,
                        )
                    },
                    onDeleteAllData = {
                        navController.navigate(
                            CarePackRoutes.DeleteAllData,
                        )
                    },
                )
            }

            composable(
                CarePackRoutes.ReminderSettings,
            ) {
                val viewModel: ReminderSettingsViewModel =
                    viewModel(
                        factory = ReminderSettingsViewModel.factory(
                                preferenceStore = reminderPreferenceStore,
                                reminderCoordinator = reminderCoordinator,
                                reminderTestCoordinator = reminderTestCoordinator,
                                notificationPermissionGateway = notificationPermissionGateway,
                                userExperiencePreferenceStore = userExperiencePreferenceStore,
                                clock = clock,
                            ),
                    )

                ReminderSettingsRoute(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    },
                    onReviewSchedules = {
                        navController.navigatePrimary(
                            CarePackRoutes.CarePlan,
                        )
                    },
                )
            }

            composable(
                CarePackRoutes.TodayReport,
            ) {
                val reportDate = clock
                        .instant().atZone(
                            zoneProvider.currentZone(),
                        ).toLocalDate()

                TodayReportRoute(
                    date = reportDate,
                    formatter = todayReportFormatter,
                    privacyPreferenceStore = privacyPreferenceStore,
                    textShareGateway = textShareGateway,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                CarePackRoutes.RangeReport,
            ) {
                RangeReportRoute(
                    formatter = rangeReportFormatter,
                    privacyPreferenceStore = privacyPreferenceStore,
                    userExperiencePreferenceStore = userExperiencePreferenceStore,
                    textShareGateway = textShareGateway,
                    clock = clock,
                    zoneProvider = zoneProvider,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                CarePackRoutes.Privacy,
            ) {
                PrivacyRoute(
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                CarePackRoutes.EditRecipientName,
            ) {
                val viewModel: RecipientNameEditViewModel =
                    viewModel(
                        factory = RecipientNameEditViewModel
                                .factory(
                                    carePlanService = carePlanService,
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
                CarePackRoutes.DeleteAllData,
            ) {
                DeleteAllDataRoute(
                    dataDeletionCoordinator = dataDeletionCoordinator,
                    onBack = {
                        navController.popBackStack()
                    },
                    onDeletionCompleted = {
                        navController.navigate(
                            CarePackRoutes.Onboarding,
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
                route = CarePackRoutes.DeleteMedicationPattern,
                arguments = listOf(
                        navArgument(
                            CarePackRoutes.MedicationIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val medicationId = entry.requireStringArgument(
                        CarePackRoutes.MedicationIdArgument,
                    )

                MedicationDeletionRoute(
                    medicationId = medicationId,
                    coordinator = medicationDeletionCoordinator,
                    onDeletionCompleted = {
                        navController.navigatePrimary(
                            CarePackRoutes.CarePlan,
                        )
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = CarePackRoutes.AddMedicationPattern,
                arguments = listOf(
                        navArgument(
                            CarePackRoutes.RecipientIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val recipientId = entry.requireStringArgument(
                        CarePackRoutes.RecipientIdArgument,
                    )

                val viewModel: MedicationScheduleViewModel =
                    viewModel(
                        factory = MedicationScheduleViewModel
                                .factory(
                                    recipientId = recipientId,
                                    carePlanService = carePlanService,
                                    setupPreferenceStore = setupPreferenceStore,
                                    userExperiencePreferenceStore = userExperiencePreferenceStore,
                                    completeInitialSetup = false,
                                    clock = clock,
                                    zoneProvider = zoneProvider,
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
                route = CarePackRoutes.AddSchedulePattern,
                arguments = listOf(
                        navArgument(
                            CarePackRoutes.MedicationIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val medicationId = entry.requireStringArgument(
                        CarePackRoutes.MedicationIdArgument,
                    )

                val viewModel: MedicationScheduleViewModel =
                    viewModel(
                        factory = MedicationScheduleViewModel
                                .addScheduleFactory(
                                    medicationId = medicationId,
                                    carePlanService = carePlanService,
                                    setupPreferenceStore = setupPreferenceStore,
                                    userExperiencePreferenceStore = userExperiencePreferenceStore,
                                    clock = clock,
                                    zoneProvider = zoneProvider,
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
                route = CarePackRoutes.EditMedicationTextPattern,
                arguments = listOf(
                        navArgument(
                            CarePackRoutes.MedicationIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val medicationId = entry.requireStringArgument(
                        CarePackRoutes.MedicationIdArgument,
                    )

                val viewModel: MedicationTextEditViewModel =
                    viewModel(
                        factory = MedicationTextEditViewModel
                                .factory(
                                    medicationId = medicationId,
                                    carePlanService = carePlanService,
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
                route = CarePackRoutes.EditSchedulePattern,
                arguments = listOf(
                        navArgument(
                            CarePackRoutes.ScheduleSeriesIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val scheduleSeriesId = entry.requireStringArgument(
                        CarePackRoutes.ScheduleSeriesIdArgument,
                    )

                val viewModel: ScheduleEditViewModel =
                    viewModel(
                        factory = ScheduleEditViewModel
                                .factory(
                                    scheduleSeriesId = scheduleSeriesId,
                                    carePlanService = carePlanService,
                                    zoneProvider = zoneProvider,
                                    userExperiencePreferenceStore = userExperiencePreferenceStore,
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
                route = CarePackRoutes.ReminderOccurrenceDetailPattern,
                arguments = listOf(
                        navArgument(
                            CarePackRoutes.OccurrenceIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val occurrenceId = entry.requireStringArgument(
                        CarePackRoutes.OccurrenceIdArgument,
                    )

                val viewModel: OccurrenceDetailViewModel =
                    viewModel(
                        factory = OccurrenceDetailViewModel
                                .factory(
                                    occurrenceId = occurrenceId,
                                    todayQueryService = todayQueryService,
                                    caregiverReportService = caregiverReportService,
                                    reminderCoordinator = reminderCoordinator,
                                    clock = clock,
                                ),
                    )

                OccurrenceDetailRoute(
                    viewModel = viewModel,
                    entryMode = OccurrenceDetailEntryMode.REMINDER,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = CarePackRoutes.OccurrenceDetailPattern,
                arguments = listOf(
                        navArgument(
                            CarePackRoutes.OccurrenceIdArgument,
                        ) {
                            type = NavType.StringType
                        },
                    ),
            ) { entry ->
                val occurrenceId = entry.requireStringArgument(
                        CarePackRoutes.OccurrenceIdArgument,
                    )

                val viewModel: OccurrenceDetailViewModel =
                    viewModel(
                        factory = OccurrenceDetailViewModel
                                .factory(
                                    occurrenceId = occurrenceId,
                                    todayQueryService = todayQueryService,
                                    caregiverReportService = caregiverReportService,
                                    reminderCoordinator = reminderCoordinator,
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
        modifier = Modifier.testTag(
                "primary_navigation",
            ),
    ) {
        primaryDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute ==
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
                        text = stringResource(
                                destination.labelResId,
                            ),
                    )
                },
                modifier = Modifier.testTag(
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
    userExperiencePreferenceStore: UserExperiencePreferenceStore,
    clock: Clock,
    zoneProvider: ZoneProvider,
): ViewModelProvider.Factory = TodayViewModel.factory(
        todayQueryService = todayQueryService,
        caregiverReportService = caregiverReportService,
        carePlanService = carePlanService,
        reminderPreferenceStore = reminderPreferenceStore,
        reminderCoordinator = reminderCoordinator,
        userExperiencePreferenceStore = userExperiencePreferenceStore,
        clock = clock,
        zoneProvider = zoneProvider,
    )
