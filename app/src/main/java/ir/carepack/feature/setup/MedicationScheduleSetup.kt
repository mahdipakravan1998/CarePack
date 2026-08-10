package ir.carepack.feature.setup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.feature.careplan.removeTime
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import kotlinx.coroutines.launch



@Composable
fun MedicationScheduleRoute(
    viewModel: MedicationScheduleViewModel,
    onCompleted: () -> Unit,
    onCompletionModeSelected: (SeniorMode?) -> Unit = {},
    onOpenReminderSettings: () -> Unit = {},
    firstSetupReminderReadiness: FirstSetupReminderReadinessUiState? = null,
    onFirstSetupRequestNotificationPermission: (() -> Unit)? = null,
    onFirstSetupOpenNotificationSettings: (() -> Unit)? = null,
    onFirstSetupRequestExactAlarmAccess: (() -> Unit)? = null,
    onFirstSetupOpenBatterySettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts
                    .RequestPermission(),
        ) {
            Unit
        }

    val readiness = firstSetupReminderReadiness
            ?: platformFirstSetupReminderReadiness(
                context = context,
            )

    val requestNotificationPermission = onFirstSetupRequestNotificationPermission
            ?: {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                }
            }

    val openNotificationSettings = onFirstSetupOpenNotificationSettings
            ?: {
                context.startFirstSetupSettingsActivity(
                    Intent(
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    ).putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        context.packageName,
                    ),
                )
            }

    val requestExactAlarmAccess = onFirstSetupRequestExactAlarmAccess
            ?: {
                context.startFirstSetupSettingsActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse(
                            "package:${context.packageName}",
                        ),
                    ),
                    fallback = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse(
                                "package:${context.packageName}",
                            ),
                        ),
                )
            }

    val openBatterySettings = onFirstSetupOpenBatterySettings
            ?: {
                context.startFirstSetupSettingsActivity(
                    Intent(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                    ),
                    fallback = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse(
                                "package:${context.packageName}",
                            ),
                        ),
                )
            }

    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        viewModel,
        state.completionRequested,
        state.completionSeniorMode,
    ) {
        if (state.completionRequested) {
            onCompletionModeSelected(
                state.completionSeniorMode,
            )
            onCompleted()
            viewModel.onCompletionHandled()
        }
    }
    CompositionLocalProvider(
        LocalCarePackExperience provides
                CarePackExperience.forMode(
                    state.seniorMode,
                ),
    ) {
        MedicationScheduleScreen(
            state = state,
            firstSetupReminderReadiness = readiness,
            onMedicationNameChanged = viewModel::onMedicationNameChanged,
            onInstructionChanged = viewModel::onInstructionChanged,
            onMedicationTypeChanged = viewModel::onMedicationTypeChanged,
            onDosageTextChanged = viewModel::onDosageTextChanged,
            onDoseUnitChanged = viewModel::onDoseUnitChanged,
            onWeekdayToggled = viewModel::onWeekdayToggled,
            onInputModeSelected = viewModel::onInputModeSelected,
            onTimeDraftChanged = viewModel::onTimeDraftChanged,
            onAddTime = viewModel::addTime,
            onRemoveTime = viewModel::removeTime,
            onIntervalHoursSelected = viewModel::onIntervalHoursSelected,
            onIntervalAnchorChanged = viewModel::onIntervalAnchorChanged,
            onStartDateChanged = viewModel::onStartDateChanged,
            onEndDateChanged = viewModel::onEndDateChanged,
            onInitialReminderGuidanceContinue = viewModel::dismissInitialReminderGuidance,
            onEnableSimpleModeAfterFirstSetup = viewModel::enableSimpleModeAfterFirstSetup,
            onDeferSimpleModeAfterFirstSetup = viewModel::deferSimpleModeAfterFirstSetup,
            onOpenReminderSettings = onOpenReminderSettings,
            onRequestNotificationPermission = requestNotificationPermission,
            onOpenNotificationSettings = openNotificationSettings,
            onRequestExactAlarmAccess = requestExactAlarmAccess,
            onOpenBatterySettings = openBatterySettings,
            onSave = viewModel::save,
        )
    }
}
