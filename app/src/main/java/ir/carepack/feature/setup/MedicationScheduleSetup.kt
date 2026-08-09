package ir.carepack.feature.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.SetupPreferenceStore
import ir.carepack.domain.calendar.FirstDayOfWeekPolicy
import ir.carepack.domain.careplan.AddScheduleCommand
import ir.carepack.domain.careplan.AddScheduleOutcome
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.CreateMedicationScheduleCommand
import ir.carepack.domain.careplan.CreateMedicationScheduleOutcome
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.reminder.ManufacturerGuidance
import ir.carepack.domain.reminder.ManufacturerGuidanceClassifier
import ir.carepack.reminder.permission.AndroidBatteryOptimizationGateway
import ir.carepack.reminder.permission.AndroidExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.AndroidNotificationPermissionGateway
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.feature.careplan.MedicationTextFields
import ir.carepack.feature.careplan.ScheduleFormCallbacks
import ir.carepack.feature.careplan.ScheduleFormFields
import ir.carepack.feature.careplan.ScheduleFormUiState
import ir.carepack.feature.careplan.ScheduleInputMode
import ir.carepack.feature.careplan.addDraftTime
import ir.carepack.feature.careplan.clearErrors
import ir.carepack.feature.careplan.effectiveMinutesOfDay
import ir.carepack.feature.careplan.parseDates
import ir.carepack.feature.careplan.removeTime
import ir.carepack.feature.careplan.toFieldErrors
import ir.carepack.feature.careplan.toHourMinuteText
import ir.carepack.feature.careplan.toMinuteOfDay
import ir.carepack.feature.careplan.toSchedulePattern
import ir.carepack.feature.careplan.toggleWeekday
import ir.carepack.feature.careplan.withDateErrors
import ir.carepack.feature.careplan.withEndDate
import ir.carepack.feature.careplan.withInputMode
import ir.carepack.feature.careplan.withIntervalAnchorDraft
import ir.carepack.feature.careplan.withIntervalHours
import ir.carepack.feature.careplan.withIntervalHoursDefault
import ir.carepack.feature.careplan.withPreviewEffectiveFrom
import ir.carepack.feature.careplan.withStartDate
import ir.carepack.feature.careplan.withTimeDraft
import ir.carepack.feature.careplan.withValidationErrors
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import ir.carepack.ui.experience.carePackExperience
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch



@Composable
fun MedicationScheduleRoute(
    viewModel:
    MedicationScheduleViewModel,
    onCompleted: () -> Unit,
    onCompletionModeSelected:
    (SeniorMode?) -> Unit = {},
    onOpenReminderSettings: () -> Unit = {},
    firstSetupReminderReadiness:
    FirstSetupReminderReadinessUiState? = null,
    onFirstSetupRequestNotificationPermission:
    (() -> Unit)? = null,
    onFirstSetupOpenNotificationSettings:
    (() -> Unit)? = null,
    onFirstSetupRequestExactAlarmAccess:
    (() -> Unit)? = null,
    onFirstSetupOpenBatterySettings:
    (() -> Unit)? = null,
) {
    val context =
        LocalContext.current

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission(),
        ) {
            Unit
        }

    val readiness =
        firstSetupReminderReadiness
            ?: platformFirstSetupReminderReadiness(
                context = context,
            )

    val requestNotificationPermission =
        onFirstSetupRequestNotificationPermission
            ?: {
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                }
            }

    val openNotificationSettings =
        onFirstSetupOpenNotificationSettings
            ?: {
                context.startFirstSetupSettingsActivity(
                    Intent(
                        Settings
                            .ACTION_APP_NOTIFICATION_SETTINGS,
                    ).putExtra(
                        Settings
                            .EXTRA_APP_PACKAGE,
                        context.packageName,
                    ),
                )
            }

    val requestExactAlarmAccess =
        onFirstSetupRequestExactAlarmAccess
            ?: {
                context.startFirstSetupSettingsActivity(
                    Intent(
                        Settings
                            .ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse(
                            "package:${context.packageName}",
                        ),
                    ),
                    fallback =
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse(
                                "package:${context.packageName}",
                            ),
                        ),
                )
            }

    val openBatterySettings =
        onFirstSetupOpenBatterySettings
            ?: {
                context.startFirstSetupSettingsActivity(
                    Intent(
                        Settings
                            .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                    ),
                    fallback =
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse(
                                "package:${context.packageName}",
                            ),
                        ),
                )
            }

    val state by
    viewModel
        .state
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
            firstSetupReminderReadiness =
                readiness,
            onMedicationNameChanged =
                viewModel::onMedicationNameChanged,
            onInstructionChanged =
                viewModel::onInstructionChanged,
            onMedicationTypeChanged =
                viewModel::onMedicationTypeChanged,
            onDosageTextChanged =
                viewModel::onDosageTextChanged,
            onDoseUnitChanged =
                viewModel::onDoseUnitChanged,
            onWeekdayToggled =
                viewModel::onWeekdayToggled,
            onInputModeSelected =
                viewModel::onInputModeSelected,
            onTimeDraftChanged =
                viewModel::onTimeDraftChanged,
            onAddTime =
                viewModel::addTime,
            onRemoveTime =
                viewModel::removeTime,
            onIntervalHoursSelected =
                viewModel::onIntervalHoursSelected,
            onIntervalAnchorChanged =
                viewModel::onIntervalAnchorChanged,
            onStartDateChanged =
                viewModel::onStartDateChanged,
            onEndDateChanged =
                viewModel::onEndDateChanged,
            onInitialReminderGuidanceContinue =
                viewModel::dismissInitialReminderGuidance,
            onEnableSimpleModeAfterFirstSetup =
                viewModel::enableSimpleModeAfterFirstSetup,
            onDeferSimpleModeAfterFirstSetup =
                viewModel::deferSimpleModeAfterFirstSetup,
            onOpenReminderSettings =
                onOpenReminderSettings,
            onRequestNotificationPermission =
                requestNotificationPermission,
            onOpenNotificationSettings =
                openNotificationSettings,
            onRequestExactAlarmAccess =
                requestExactAlarmAccess,
            onOpenBatterySettings =
                openBatterySettings,
            onSave =
                viewModel::save,
        )
    }
}
