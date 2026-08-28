package ir.carepack.feature.settings

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.calendar.FirstDayOfWeekPolicy
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience
import java.time.DayOfWeek
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferenceState: UserExperiencePreferenceState =
        UserExperiencePreferenceState(),
    val resolvedFirstDayOfWeek: DayOfWeek =
        DayOfWeek.MONDAY,
    val recipientDisplayName: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val appVersion: String = "",
)

private data class SettingsTransientState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val userExperiencePreferenceStore: UserExperiencePreferenceStore,
    carePlanService: CarePlanService? = null,
    zoneProvider: ZoneProvider,
    private val appVersion: String,
) : ViewModel() {

    private val zoneId = zoneProvider.currentZone()
    private val transientState = MutableStateFlow(SettingsTransientState())

    val state: StateFlow<SettingsUiState> = combine(
            userExperiencePreferenceStore.state,
            carePlanService?.observeCarePlan() ?: flowOf(null),
            transientState,
        ) { preferences, carePlan, transient ->
            SettingsUiState(
                preferenceState = preferences,
                resolvedFirstDayOfWeek = FirstDayOfWeekPolicy.resolve(
                    preference = preferences.firstDayOfWeekPreference,
                    zoneId = zoneId,
                    locale = Locale.getDefault(),
                ),
                recipientDisplayName = carePlan?.recipientDisplayName.orEmpty(),
                isSaving = transient.isSaving,
                errorMessage = transient.errorMessage,
                appVersion = appVersion,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(appVersion = appVersion),
        )

    fun setFirstDayOfWeekPreference(preference: FirstDayOfWeekPreference) {
        savePreference {
            userExperiencePreferenceStore.setFirstDayOfWeekPreference(preference)
        }
    }

    fun setSeniorMode(seniorMode: SeniorMode) {
        savePreference {
            userExperiencePreferenceStore.setSeniorMode(seniorMode)
        }
    }

    private fun savePreference(operation: suspend () -> Unit) {
        viewModelScope.launch {
            transientState.value = SettingsTransientState(isSaving = true)
            try {
                operation()
                transientState.value = SettingsTransientState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                transientState.value = SettingsTransientState(
                    errorMessage = "ذخیره تنظیمات انجام نشد. مقدار قبلی همچنان فعال است.",
                )
            }
        }
    }

    companion object {
        fun factory(
            userExperiencePreferenceStore: UserExperiencePreferenceStore,
            carePlanService: CarePlanService,
            zoneProvider: ZoneProvider,
            appVersion: String,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
            SettingsViewModel(
                userExperiencePreferenceStore = userExperiencePreferenceStore,
                carePlanService = carePlanService,
                zoneProvider = zoneProvider,
                appVersion = appVersion,
            )
        }
    }
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onEditRecipient: () -> Unit = {},
    onOpenReminderSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDeleteAllData: () -> Unit,
) {
    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onEditRecipient = onEditRecipient,
        onOpenReminderSettings = onOpenReminderSettings,
        onOpenPrivacy = onOpenPrivacy,
        onDeleteAllData = onDeleteAllData,
        onFirstDayOfWeekPreferenceChanged = viewModel::setFirstDayOfWeekPreference,
        onSeniorModeChanged = viewModel::setSeniorMode,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEditRecipient: () -> Unit = {},
    onOpenReminderSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDeleteAllData: () -> Unit,
    onFirstDayOfWeekPreferenceChanged: (FirstDayOfWeekPreference) -> Unit,
    onSeniorModeChanged: (SeniorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val experience = carePackExperience()

    Scaffold(
        modifier = modifier
                .fillMaxSize().testTag(
                    "settings_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                    .fillMaxSize().padding(
                        contentPadding,
                    ).navigationBarsPadding()
                    .verticalScroll(
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
                text = stringResource(
                        R.string.carepack_settings_title,
                    ),
                style = MaterialTheme
                        .typography.headlineMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "settings_title",
                        ),
            )

            if (!experience.isSimple) {
                Text(
                    text = stringResource(
                            R.string.carepack_settings_description,
                        ),
                    style = MaterialTheme
                            .typography.bodyLarge,
                )
            }

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("settings_save_error"),
                )
            } else if (state.isSaving) {
                Text(
                    text = "در حال ذخیره تنظیمات…",
                    modifier = Modifier.testTag("settings_saving"),
                )
            }

            SettingsActionButton(
                title = stringResource(R.string.edit_recipient),
                description = if (state.recipientDisplayName.isBlank()) {
                        "نام فرد تحت مراقبت را ویرایش کنید."
                    } else {
                        "فرد تحت مراقبت: ${state.recipientDisplayName}"
                    },
                testTag = "settings_edit_recipient",
                onClick = onEditRecipient,
            )

            SettingsActionButton(
                title = stringResource(
                        R.string.carepack_settings_reminders,
                    ),
                description = stringResource(
                        R.string.carepack_settings_reminders_description,
                    ),
                testTag = "settings_reminders",
                onClick = onOpenReminderSettings,
            )

            SettingsActionButton(
                title = stringResource(
                        R.string.carepack_settings_privacy,
                    ),
                description = stringResource(
                        R.string.carepack_settings_privacy_description,
                    ),
                testTag = "settings_privacy",
                onClick = onOpenPrivacy,
            )

            WeekStartSection(
                state = state,
                onFirstDayOfWeekPreferenceChanged = onFirstDayOfWeekPreferenceChanged,
            )

            DisplaySection(
                state = state,
                onSeniorModeChanged = onSeniorModeChanged,
            )

            AppVersionSection(
                appVersion = state.appVersion,
            )

            SettingsActionButton(
                title = stringResource(
                        R.string.carepack_settings_delete_all,
                    ),
                description = stringResource(
                        R.string.carepack_settings_delete_all_description,
                    ),
                testTag = "settings_delete_all",
                onClick = onDeleteAllData,
                destructive = true,
            )
        }
    }
}


@Composable
private fun WeekStartSection(
    state: SettingsUiState,
    onFirstDayOfWeekPreferenceChanged: (FirstDayOfWeekPreference) -> Unit,
) {
    val experience = carePackExperience()

    Column(
        verticalArrangement = Arrangement.spacedBy(
                experience.compactSpacing,
            ),
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "settings_week_start",
                ),
    ) {
        Text(
            text = stringResource(
                    R.string.carepack_settings_week_start,
                ),
            style = MaterialTheme
                    .typography.titleMedium,
            modifier = Modifier.carePackHeading(),
        )

        if (!experience.isSimple) {
            Text(
                text = stringResource(
                        R.string.carepack_settings_week_start_description,
                    ),
                style = MaterialTheme
                        .typography.bodyMedium,
            )
        }

        Text(
            text = stringResource(
                    R.string.carepack_week_start_current,
                    stringResource(
                        weekdayPersianNameResource(
                            state.resolvedFirstDayOfWeek,
                        ),
                    ),
                ),
            style = MaterialTheme
                    .typography.bodyLarge,
        )

        if (experience.isSimple) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                        experience.itemSpacing,
                    ),
            ) {
                WeekStartOptions(
                    state = state,
                    onFirstDayOfWeekPreferenceChanged = onFirstDayOfWeekPreferenceChanged,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                        experience.compactSpacing,
                    ),
            ) {
                WeekStartOptions(
                    state = state,
                    onFirstDayOfWeekPreferenceChanged = onFirstDayOfWeekPreferenceChanged,
                )
            }
        }
    }
}

@Composable
private fun WeekStartOptions(
    state: SettingsUiState,
    onFirstDayOfWeekPreferenceChanged: (FirstDayOfWeekPreference) -> Unit,
) {
    WeekStartChip(
        label = stringResource(
                R.string.carepack_week_start_system,
            ),
        selected = state
                .preferenceState.firstDayOfWeekPreference ==
                    FirstDayOfWeekPreference.SYSTEM_DEFAULT,
        testTag = "week_start_system",
        onClick = {
            onFirstDayOfWeekPreferenceChanged(
                FirstDayOfWeekPreference.SYSTEM_DEFAULT,
            )
        },
    )

    WeekStartChip(
        label = stringResource(
                R.string.carepack_week_start_saturday,
            ),
        selected = state
                .preferenceState.firstDayOfWeekPreference ==
                    FirstDayOfWeekPreference.SATURDAY,
        testTag = "week_start_saturday",
        onClick = {
            onFirstDayOfWeekPreferenceChanged(
                FirstDayOfWeekPreference.SATURDAY,
            )
        },
    )

    WeekStartChip(
        label = stringResource(
                R.string.carepack_week_start_monday,
            ),
        selected = state
                .preferenceState.firstDayOfWeekPreference ==
                    FirstDayOfWeekPreference.MONDAY,
        testTag = "week_start_monday",
        onClick = {
            onFirstDayOfWeekPreferenceChanged(
                FirstDayOfWeekPreference.MONDAY,
            )
        },
    )
}

@Composable
private fun DisplaySection(
    state: SettingsUiState,
    onSeniorModeChanged: (SeniorMode) -> Unit,
) {
    val experience = carePackExperience()

    Column(
        verticalArrangement = Arrangement.spacedBy(
                experience.compactSpacing,
            ),
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "settings_display",
                ),
    ) {
        Text(
            text = stringResource(
                    R.string.carepack_settings_display,
                ),
            style = MaterialTheme
                    .typography.titleMedium,
            modifier = Modifier.carePackHeading(),
        )

        if (!experience.isSimple) {
            Text(
                text = stringResource(
                        R.string.carepack_settings_display_description,
                    ),
                style = MaterialTheme
                        .typography.bodyMedium,
            )
        }

        if (experience.isSimple) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                        experience.itemSpacing,
                    ),
            ) {
                DisplayOptions(
                    state = state,
                    onSeniorModeChanged = onSeniorModeChanged,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                        experience.compactSpacing,
                    ),
            ) {
                DisplayOptions(
                    state = state,
                    onSeniorModeChanged = onSeniorModeChanged,
                )
            }
        }
    }
}

@Composable
private fun DisplayOptions(
    state: SettingsUiState,
    onSeniorModeChanged: (SeniorMode) -> Unit,
) {
    FilterChip(
        selected = state
                .preferenceState.seniorMode ==
                    SeniorMode.STANDARD,
        onClick = {
            onSeniorModeChanged(
                SeniorMode.STANDARD,
            )
        },
        label = {
            Text(
                text = stringResource(
                        R.string.carepack_display_standard,
                    ),
            )
        },
        modifier = Modifier
                .carePackInteractiveControl().testTag(
                    "display_standard",
                ),
    )

    FilterChip(
        selected = state
                .preferenceState.seniorMode ==
                    SeniorMode.SIMPLE,
        onClick = {
            onSeniorModeChanged(
                SeniorMode.SIMPLE,
            )
        },
        label = {
            Text(
                text = stringResource(
                        R.string.carepack_display_simple,
                    ),
            )
        },
        modifier = Modifier
                .carePackInteractiveControl().testTag(
                    "display_simple",
                ),
    )
}

@Composable
private fun AppVersionSection(
    appVersion: String,
) {
    val experience = carePackExperience()

    Column(
        verticalArrangement = Arrangement.spacedBy(
                experience.compactSpacing,
            ),
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "settings_app_version",
                ),
    ) {
        Text(
            text = stringResource(
                    R.string.carepack_settings_app_version,
                ),
            style = MaterialTheme
                    .typography.titleMedium,
            modifier = Modifier.carePackHeading(),
        )

        Text(
            text = appVersion,
            style = MaterialTheme
                    .typography.bodyMedium
                    .copy(
                        textDirection = TextDirection.Ltr,
                    ),
            modifier = Modifier.testTag(
                    "settings_app_version_value",
                ),
        )
    }
}

@Composable
private fun WeekStartChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val experience = carePackExperience()

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
            )
        },
        modifier = Modifier
                .then(
                    if (experience.isSimple) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                    },
                ).carePackInteractiveControl()
                .testTag(
                    testTag,
                ),
    )
}

@Composable
private fun SettingsActionButton(
    title: String,
    description: String,
    testTag: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val experience = carePackExperience()

    Column(
        verticalArrangement = Arrangement.spacedBy(
                experience.compactSpacing,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!experience.isSimple) {
            Text(
                text = description,
                style = MaterialTheme
                        .typography.bodyMedium,
            )
        }

        if (destructive) {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                        .fillMaxWidth().carePackPrimaryAction()
                        .testTag(
                            testTag,
                        ),
            ) {
                Text(
                    text = title,
                    color = MaterialTheme
                            .colorScheme.error,
                )
            }
        } else {
            Button(
                onClick = onClick,
                modifier = Modifier
                        .fillMaxWidth().carePackPrimaryAction()
                        .testTag(
                            testTag,
                        ),
            ) {
                Text(
                    text = title,
                )
            }
        }
    }
}

@Composable
private fun weekdayPersianNameResource(
    dayOfWeek: DayOfWeek,
): Int = when (dayOfWeek) {
        DayOfWeek.SATURDAY ->
            R.string.saturday

        DayOfWeek.SUNDAY ->
            R.string.sunday

        DayOfWeek.MONDAY ->
            R.string.monday

        DayOfWeek.TUESDAY ->
            R.string.tuesday

        DayOfWeek.WEDNESDAY ->
            R.string.wednesday

        DayOfWeek.THURSDAY ->
            R.string.thursday

        DayOfWeek.FRIDAY ->
            R.string.friday
    }
