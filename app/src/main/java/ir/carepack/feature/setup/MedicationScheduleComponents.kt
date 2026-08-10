package ir.carepack.feature.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.carepack.R
import ir.carepack.domain.reminder.ManufacturerGuidance
import ir.carepack.domain.reminder.ManufacturerGuidanceClassifier
import ir.carepack.reminder.permission.AndroidBatteryOptimizationGateway
import ir.carepack.reminder.permission.AndroidExactAlarmCapabilityGateway
import ir.carepack.reminder.permission.AndroidNotificationPermissionGateway
import ir.carepack.reminder.permission.BatteryOptimizationState
import ir.carepack.feature.careplan.MedicationTextFields
import ir.carepack.feature.careplan.ScheduleFormCallbacks
import ir.carepack.feature.careplan.ScheduleFormFields
import ir.carepack.feature.careplan.ScheduleInputMode
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience
import java.time.DayOfWeek
import java.util.Locale



internal fun platformFirstSetupReminderReadiness(
    context: Context,
): FirstSetupReminderReadinessUiState {
    val notificationGateway = AndroidNotificationPermissionGateway(
            context = context,
        )

    val exactAlarmGateway = AndroidExactAlarmCapabilityGateway(
            context = context,
        )

    val batteryGateway = AndroidBatteryOptimizationGateway(
            context = context,
        )

    return FirstSetupReminderReadinessUiState(
        notificationRuntimePermissionRequired = notificationGateway
                .requiresRuntimePermission(),
        notificationPermissionGranted = notificationGateway
                .isPermissionGranted(),
        notificationPermissionCanBeRequested = notificationGateway
                .requiresRuntimePermission() && !notificationGateway
                        .isPermissionGranted(),
        exactAlarmRelevant = true,
        exactAlarmAvailable = exactAlarmGateway
                .canScheduleExactAlarms(),
        batteryOptimizationState = batteryGateway
                .currentState(),
        manufacturer = Build.MANUFACTURER,
    )
}

internal fun Context.startFirstSetupSettingsActivity(
    intent: Intent,
    fallback: Intent? = null,
) {
    val launchIntent = Intent(
            intent,
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK,
        )

    try {
        startActivity(
            launchIntent,
        )
    } catch (_: ActivityNotFoundException) {
        fallback?.let { fallbackIntent ->
                startActivity(
                    Intent(
                        fallbackIntent,
                    ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                    ),
                )
            }
    }
}

@Composable
internal fun InitialReminderGuidanceCard(
    readiness: FirstSetupReminderReadinessUiState,
    onContinue: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val manufacturerGuidance = firstSetupManufacturerGuidance(
            manufacturer = readiness.manufacturer,
        )

    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "first_setup_reminder_guidance",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.first_setup_reminder_guidance_title,
                    ),
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "first_setup_reminder_guidance_title",
                        ),
            )

            Text(
                text = stringResource(
                        R.string.first_setup_reminder_guidance_body,
                    ),
                style = MaterialTheme
                        .typography.bodyMedium,
                modifier = Modifier.testTag(
                        "first_setup_reminder_guidance_body",
                    ),
            )

            Text(
                text = stringResource(
                        R.string.first_setup_reminder_guidance_settings_path,
                    ),
                style = MaterialTheme
                        .typography.bodyMedium,
                modifier = Modifier.testTag(
                        "first_setup_reminder_guidance_settings_path",
                    ),
            )

            FirstSetupReadinessActions(
                readiness = readiness,
                manufacturerGuidance = manufacturerGuidance,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onRequestExactAlarmAccess = onRequestExactAlarmAccess,
                onOpenBatterySettings = onOpenBatterySettings,
            )

            Button(
                onClick = onOpenReminderSettings,
                modifier = Modifier
                        .fillMaxWidth().testTag(
                            "first_setup_reminder_guidance_open_reminder_settings",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.open_reminder_settings,
                        ),
                )
            }

            TextButton(
                onClick = onContinue,
                modifier = Modifier
                        .fillMaxWidth().testTag(
                            "first_setup_reminder_guidance_continue",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.first_setup_reminder_guidance_continue,
                        ),
                )
            }
        }
    }
}

internal fun firstSetupManufacturerGuidance(
    manufacturer: String?,
): ManufacturerGuidance {
    val normalized = manufacturer
            ?.trim()?.lowercase(
                Locale.ROOT,
            ).orEmpty()

    val classifierInput = if (
            normalized.contains(
                other = "miui",
            ) || normalized.contains(
                other = "hyperos",
            )) {
            "Xiaomi"
        } else {
            manufacturer
        }

    return ManufacturerGuidanceClassifier.classify(
            manufacturer = classifierInput,
        )
}

@Composable
internal fun FirstSetupReadinessActions(
    readiness: FirstSetupReminderReadinessUiState,
    manufacturerGuidance: ManufacturerGuidance,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Column(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "first_setup_readiness_actions",
                ),
        verticalArrangement = Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text = stringResource(
                    R.string.first_setup_readiness_title,
                ),
            style = MaterialTheme
                    .typography.titleSmall,
            modifier = Modifier
                    .carePackHeading().testTag(
                        "first_setup_readiness_title",
                    ),
        )

        FirstSetupNotificationPermissionSection(
            readiness = readiness,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationSettings = onOpenNotificationSettings,
        )

        FirstSetupExactAlarmSection(
            readiness = readiness,
            onRequestExactAlarmAccess = onRequestExactAlarmAccess,
        )

        FirstSetupBatterySection(
            readiness = readiness,
            onOpenBatterySettings = onOpenBatterySettings,
        )

        FirstSetupOemGuidanceSection(
            guidance = manufacturerGuidance,
        )
    }
}

@Composable
internal fun FirstSetupNotificationPermissionSection(
    readiness: FirstSetupReminderReadinessUiState,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "first_setup_notification_permission_card",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.notification_permission_rationale_title,
                    ),
                style = MaterialTheme
                        .typography.titleSmall,
            )

            Text(
                text = stringResource(
                        when {
                            !readiness.notificationRuntimePermissionRequired -> {
                                R.string.notification_permission_not_required
                            }

                            readiness.notificationPermissionGranted -> {
                                R.string.notification_permission_granted
                            }

                            else -> {
                                R.string.notification_permission_denied
                            }
                        },
                    ),
                modifier = Modifier.testTag(
                        "first_setup_notification_permission_status",
                    ),
            )

            if (
                readiness.notificationRuntimePermissionRequired &&
                !readiness.notificationPermissionGranted
            ) {
                Text(
                    text = stringResource(
                            R.string.notification_permission_rationale_body,
                        ),
                    modifier = Modifier.testTag(
                            "first_setup_notification_permission_rationale",
                        ),
                )

                if (
                    readiness.notificationPermissionCanBeRequested
                ) {
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier
                                .fillMaxWidth().testTag(
                                    "first_setup_request_notification_permission",
                                ),
                    ) {
                        Text(
                            text = stringResource(
                                    R.string.request_notification_permission,
                                ),
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier
                            .fillMaxWidth().testTag(
                                "first_setup_open_notification_settings",
                            ),
                ) {
                    Text(
                        text = stringResource(
                                R.string.open_notification_settings,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FirstSetupExactAlarmSection(
    readiness: FirstSetupReminderReadinessUiState,
    onRequestExactAlarmAccess: () -> Unit,
) {
    if (
        !readiness.exactAlarmRelevant || readiness.exactAlarmAvailable
    ) {
        return
    }

    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "first_setup_exact_alarm_card",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.exact_alarm_rationale_title,
                    ),
                style = MaterialTheme
                        .typography.titleSmall,
            )

            Text(
                text = stringResource(
                        R.string.exact_alarm_rationale_body,
                    ),
                modifier = Modifier.testTag(
                        "first_setup_approximate_fallback",
                    ),
            )

            OutlinedButton(
                onClick = onRequestExactAlarmAccess,
                modifier = Modifier
                        .fillMaxWidth().testTag(
                            "first_setup_request_exact_alarm_access",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.request_exact_alarm_access,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun FirstSetupBatterySection(
    readiness: FirstSetupReminderReadinessUiState,
    onOpenBatterySettings: () -> Unit,
) {
    if (
        readiness.batteryOptimizationState != BatteryOptimizationState.NOT_IGNORED
    ) {
        return
    }

    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "first_setup_battery_guidance_card",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.battery_guidance_title,
                    ),
                style = MaterialTheme
                        .typography.titleSmall,
            )

            Text(
                text = stringResource(
                        R.string.battery_guidance_body,
                    ),
                modifier = Modifier.testTag(
                        "first_setup_battery_guidance_body",
                    ),
            )

            Text(
                text = stringResource(
                        R.string.battery_optimization_not_ignored,
                    ),
                modifier = Modifier.testTag(
                        "first_setup_battery_optimization_status",
                    ),
            )

            OutlinedButton(
                onClick = onOpenBatterySettings,
                modifier = Modifier
                        .fillMaxWidth().testTag(
                            "first_setup_open_battery_settings",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.open_battery_settings,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun FirstSetupOemGuidanceSection(
    guidance: ManufacturerGuidance,
) {
    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "first_setup_oem_guidance_card",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text = guidance.title,
                style = MaterialTheme
                        .typography.titleSmall,
                modifier = Modifier.testTag(
                        "first_setup_oem_guidance_title",
                    ),
            )

            Text(
                text = guidance.body,
                modifier = Modifier.testTag(
                        "first_setup_oem_guidance_body",
                    ),
            )

            guidance.actionItems
                .forEachIndexed {
                        index,
                        actionItem ->
                    Text(
                        text = "• $actionItem",
                        modifier = Modifier.testTag(
                                "first_setup_oem_guidance_action_$index",
                            ),
                    )
                }
        }
    }
}

@Composable
internal fun PostSetupSimpleModeSuggestionCard(
    onEnableSimpleMode: () -> Unit,
    onDeferSimpleMode: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeferSimpleMode,
        modifier = Modifier.testTag(
                "post_setup_simple_mode_suggestion",
            ),
        title = {
            Text(
                text = stringResource(
                        R.string.post_setup_simple_mode_title,
                    ),
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "post_setup_simple_mode_title",
                        ),
            )
        },
        text = {
            Text(
                text = stringResource(
                        R.string.post_setup_simple_mode_summary,
                    ),
                style = MaterialTheme
                        .typography.bodyMedium,
                modifier = Modifier.testTag(
                        "post_setup_simple_mode_summary",
                    ),
            )
        },
        confirmButton = {
            Button(
                onClick = onEnableSimpleMode,
                modifier = Modifier.testTag(
                        "post_setup_enable_simple_mode",
                    ),
            ) {
                Text(
                    text = stringResource(
                            R.string.post_setup_simple_mode_enable,
                        ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDeferSimpleMode,
                modifier = Modifier.testTag(
                        "post_setup_defer_simple_mode",
                    ),
            ) {
                Text(
                    text = stringResource(
                            R.string.post_setup_simple_mode_defer,
                        ),
                )
            }
        },
    )
}

@Composable
internal fun MedicationScheduleScreen(
    state: MedicationScheduleUiState,
    firstSetupReminderReadiness: FirstSetupReminderReadinessUiState,
    onMedicationNameChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    onMedicationTypeChanged: (String) -> Unit,
    onDosageTextChanged: (String) -> Unit,
    onDoseUnitChanged: (String) -> Unit,
    onWeekdayToggled: (DayOfWeek) -> Unit,
    onInputModeSelected: (ScheduleInputMode) -> Unit,
    onTimeDraftChanged: (String) -> Unit,
    onAddTime: () -> Unit,
    onRemoveTime: (Int) -> Unit,
    onIntervalHoursSelected: (Int) -> Unit,
    onIntervalAnchorChanged: (String) -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
    onInitialReminderGuidanceContinue: () -> Unit,
    onEnableSimpleModeAfterFirstSetup: () -> Unit,
    onDeferSimpleModeAfterFirstSetup: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSave: () -> Unit,
) {
    val experience = carePackExperience()

    Scaffold(
        modifier = Modifier
                .fillMaxSize().testTag(
                    if (state.isAddScheduleOnly) {
                        "add_schedule_screen"
                    } else {
                        "medication_schedule_screen"
                    },
                ),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                    .fillMaxSize().padding(
                        contentPadding,
                    ).imePadding()
                    .navigationBarsPadding().verticalScroll(
                        rememberScrollState(),
                    ).padding(
                        horizontal = experience
                                .screenHorizontalPadding,
                        vertical = experience
                                .screenVerticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.sectionSpacing,
                ),
        ) {
            Text(
                text = if (state.isAddScheduleOnly) {
                        stringResource(
                            R.string.add_schedule_title,
                        )
                    } else {
                        stringResource(
                            R.string.medication_schedule_title,
                        )
                    },
                style = MaterialTheme
                        .typography.headlineMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "medication_schedule_title",
                        ),
            )

            if (
                !state.isAddScheduleOnly && state.showInitialReminderGuidance
            ) {
                InitialReminderGuidanceCard(
                    readiness = firstSetupReminderReadiness,
                    onContinue = onInitialReminderGuidanceContinue,
                    onOpenReminderSettings = onOpenReminderSettings,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onRequestExactAlarmAccess = onRequestExactAlarmAccess,
                    onOpenBatterySettings = onOpenBatterySettings,
                )
            }

            if (
                state.showPostSetupSimpleModeSuggestion) {
                PostSetupSimpleModeSuggestionCard(
                    onEnableSimpleMode = onEnableSimpleModeAfterFirstSetup,
                    onDeferSimpleMode = onDeferSimpleModeAfterFirstSetup,
                )
            }

            if (!state.isAddScheduleOnly) {
                MedicationTextFields(
                    medicationName = state.medication.medicationName,
                    instruction = state.medication.instruction,
                    medicationType = state.medication.medicationType,
                    dosageText = state.medication.dosageText,
                    doseUnit = state.medication.doseUnit,
                    errors = state.medicationErrors,
                    enabled = !state.isSaving,
                    onMedicationNameChanged = onMedicationNameChanged,
                    onInstructionChanged = onInstructionChanged,
                    onMedicationTypeChanged = onMedicationTypeChanged,
                    onDosageTextChanged = onDosageTextChanged,
                    onDoseUnitChanged = onDoseUnitChanged,
                    instructionMinLines = 3,
                    medicationNameTestTag = "medication_name",
                    instructionTestTag = "medication_instruction",
                    medicationTypeTestTag = "medication_type",
                    dosageTextTestTag = "dosage_text",
                    doseUnitTestTag = "dose_unit",
                )
            }

            ScheduleFormFields(
                state = state.schedule,
                callbacks = ScheduleFormCallbacks(
                        onWeekdayToggled = onWeekdayToggled,
                        onInputModeSelected = onInputModeSelected,
                        onTimeDraftChanged = onTimeDraftChanged,
                        onAddTime = onAddTime,
                        onRemoveTime = onRemoveTime,
                        onIntervalHoursSelected = onIntervalHoursSelected,
                        onIntervalAnchorChanged = onIntervalAnchorChanged,
                        onStartDateChanged = onStartDateChanged,
                        onEndDateChanged = onEndDateChanged,
                    ),
                enabled = !state.isSaving,
                firstDayOfWeek = state.firstDayOfWeek,
                previewAnchorDate = state.previewAnchorDate,
                modifier = Modifier.testTag(
                        "medication_schedule_form",
                    ),
            )

            state.generalError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme
                                .colorScheme.error,
                        modifier = Modifier
                                .fillMaxWidth().carePackPoliteLiveRegion()
                                .testTag(
                                    "medication_schedule_error",
                                ),
                    )
                }

            Spacer(
                modifier = Modifier.height(
                        8.dp,
                    ),
            )

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                        .fillMaxWidth().carePackPrimaryAction()
                        .testTag(
                            "save_medication_schedule",
                        ),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(
                                24.dp,
                            ),
                    )
                } else {
                    Text(
                        text = if (state.isAddScheduleOnly) {
                                stringResource(
                                    R.string.add_schedule,
                                )
                            } else {
                                stringResource(
                                    R.string.create_care_plan,
                                )
                            },
                    )
                }
            }
        }
    }
}
