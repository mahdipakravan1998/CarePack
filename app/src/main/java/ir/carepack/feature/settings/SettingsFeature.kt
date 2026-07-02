package ir.carepack.feature.settings

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.calendar.FirstDayOfWeekPolicy
import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.ui.accessibility.carePackHeading
import java.time.DayOfWeek
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferenceState:
    UserExperiencePreferenceState =
        UserExperiencePreferenceState(),
    val resolvedFirstDayOfWeek:
    DayOfWeek =
        DayOfWeek.MONDAY,
    val appVersion: String = "",
)

class SettingsViewModel(
    private val userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    zoneProvider: ZoneProvider,
    private val appVersion: String,
) : ViewModel() {

    private val zoneId =
        zoneProvider.currentZone()

    val state:
            StateFlow<SettingsUiState> =
        userExperiencePreferenceStore
            .state
            .map { preferences ->
                SettingsUiState(
                    preferenceState =
                        preferences,
                    resolvedFirstDayOfWeek =
                        FirstDayOfWeekPolicy
                            .resolve(
                                preference =
                                    preferences
                                        .firstDayOfWeekPreference,
                                zoneId =
                                    zoneId,
                                locale =
                                    Locale.getDefault(),
                            ),
                    appVersion =
                        appVersion,
                )
            }
            .stateIn(
                scope =
                    viewModelScope,
                started =
                    SharingStarted
                        .WhileSubscribed(
                            5_000,
                        ),
                initialValue =
                    SettingsUiState(
                        appVersion =
                            appVersion,
                    ),
            )

    fun setFirstDayOfWeekPreference(
        preference:
        FirstDayOfWeekPreference,
    ) {
        viewModelScope.launch {
            userExperiencePreferenceStore
                .setFirstDayOfWeekPreference(
                    preference,
                )
        }
    }

    fun setSeniorMode(
        seniorMode: SeniorMode,
    ) {
        viewModelScope.launch {
            userExperiencePreferenceStore
                .setSeniorMode(
                    seniorMode,
                )
        }
    }

    companion object {

        fun factory(
            userExperiencePreferenceStore:
            UserExperiencePreferenceStore,
            zoneProvider: ZoneProvider,
            appVersion: String,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SettingsViewModel(
                        userExperiencePreferenceStore =
                            userExperiencePreferenceStore,
                        zoneProvider =
                            zoneProvider,
                        appVersion =
                            appVersion,
                    )
                }
            }
    }
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onOpenTodayReport: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDeleteAllData: () -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onBack = onBack,
        onOpenReminderSettings =
            onOpenReminderSettings,
        onOpenTodayReport =
            onOpenTodayReport,
        onOpenPrivacy =
            onOpenPrivacy,
        onDeleteAllData =
            onDeleteAllData,
        onFirstDayOfWeekPreferenceChanged =
            viewModel::setFirstDayOfWeekPreference,
        onSeniorModeChanged =
            viewModel::setSeniorMode,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onOpenTodayReport: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDeleteAllData: () -> Unit,
    onFirstDayOfWeekPreferenceChanged:
        (FirstDayOfWeekPreference) -> Unit,
    onSeniorModeChanged:
        (SeniorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "settings_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        contentPadding,
                    )
                    .navigationBarsPadding()
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal = 24.dp,
                        vertical = 16.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    18.dp,
                ),
        ) {
            TextButtonBack(
                onBack = onBack,
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_settings_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "settings_title",
                        ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_settings_description,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            SettingsActionButton(
                title =
                    stringResource(
                        R.string
                            .carepack_settings_reminders,
                    ),
                description =
                    stringResource(
                        R.string
                            .carepack_settings_reminders_description,
                    ),
                testTag =
                    "settings_reminders",
                onClick =
                    onOpenReminderSettings,
            )

            SettingsActionButton(
                title =
                    stringResource(
                        R.string
                            .carepack_settings_today_report,
                    ),
                description =
                    stringResource(
                        R.string
                            .carepack_settings_today_report_description,
                    ),
                testTag =
                    "settings_today_report",
                onClick =
                    onOpenTodayReport,
            )

            SettingsActionButton(
                title =
                    stringResource(
                        R.string
                            .carepack_settings_privacy,
                    ),
                description =
                    stringResource(
                        R.string
                            .carepack_settings_privacy_description,
                    ),
                testTag =
                    "settings_privacy",
                onClick =
                    onOpenPrivacy,
            )

            WeekStartSection(
                state = state,
                onFirstDayOfWeekPreferenceChanged =
                    onFirstDayOfWeekPreferenceChanged,
            )

            DisplaySection(
                state = state,
                onSeniorModeChanged =
                    onSeniorModeChanged,
            )

            AppVersionSection(
                appVersion =
                    state.appVersion,
            )

            SettingsActionButton(
                title =
                    stringResource(
                        R.string
                            .carepack_settings_delete_all,
                    ),
                description =
                    stringResource(
                        R.string
                            .carepack_settings_delete_all_description,
                    ),
                testTag =
                    "settings_delete_all",
                onClick =
                    onDeleteAllData,
                destructive = true,
            )
        }
    }
}

@Composable
private fun TextButtonBack(
    onBack: () -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onBack,
        modifier =
            Modifier.testTag(
                "settings_back",
            ),
    ) {
        Text(
            text =
                stringResource(
                    R.string.back,
                ),
        )
    }
}

@Composable
private fun WeekStartSection(
    state: SettingsUiState,
    onFirstDayOfWeekPreferenceChanged:
        (FirstDayOfWeekPreference) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "settings_week_start",
                ),
    ) {
        Text(
            text =
                stringResource(
                    R.string
                        .carepack_settings_week_start,
                ),
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            modifier =
                Modifier.carePackHeading(),
        )

        Text(
            text =
                stringResource(
                    R.string
                        .carepack_settings_week_start_description,
                ),
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
        )

        Text(
            text =
                stringResource(
                    R.string
                        .carepack_week_start_current,
                    stringResource(
                        weekdayPersianNameResource(
                            state
                                .resolvedFirstDayOfWeek,
                        ),
                    ),
                ),
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            WeekStartChip(
                label =
                    stringResource(
                        R.string
                            .carepack_week_start_system,
                    ),
                selected =
                    state
                        .preferenceState
                        .firstDayOfWeekPreference ==
                            FirstDayOfWeekPreference
                                .SYSTEM_DEFAULT,
                testTag =
                    "week_start_system",
                onClick = {
                    onFirstDayOfWeekPreferenceChanged(
                        FirstDayOfWeekPreference
                            .SYSTEM_DEFAULT,
                    )
                },
            )

            WeekStartChip(
                label =
                    stringResource(
                        R.string
                            .carepack_week_start_saturday,
                    ),
                selected =
                    state
                        .preferenceState
                        .firstDayOfWeekPreference ==
                            FirstDayOfWeekPreference
                                .SATURDAY,
                testTag =
                    "week_start_saturday",
                onClick = {
                    onFirstDayOfWeekPreferenceChanged(
                        FirstDayOfWeekPreference
                            .SATURDAY,
                    )
                },
            )

            WeekStartChip(
                label =
                    stringResource(
                        R.string
                            .carepack_week_start_monday,
                    ),
                selected =
                    state
                        .preferenceState
                        .firstDayOfWeekPreference ==
                            FirstDayOfWeekPreference
                                .MONDAY,
                testTag =
                    "week_start_monday",
                onClick = {
                    onFirstDayOfWeekPreferenceChanged(
                        FirstDayOfWeekPreference
                            .MONDAY,
                    )
                },
            )
        }
    }
}

@Composable
private fun DisplaySection(
    state: SettingsUiState,
    onSeniorModeChanged:
        (SeniorMode) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "settings_display",
                ),
    ) {
        Text(
            text =
                stringResource(
                    R.string
                        .carepack_settings_display,
                ),
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            modifier =
                Modifier.carePackHeading(),
        )

        Text(
            text =
                stringResource(
                    R.string
                        .carepack_settings_display_description,
                ),
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            FilterChip(
                selected =
                    state
                        .preferenceState
                        .seniorMode ==
                            SeniorMode.STANDARD,
                onClick = {
                    onSeniorModeChanged(
                        SeniorMode.STANDARD,
                    )
                },
                label = {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .carepack_display_standard,
                            ),
                    )
                },
                modifier =
                    Modifier.testTag(
                        "display_standard",
                    ),
            )

            FilterChip(
                selected =
                    state
                        .preferenceState
                        .seniorMode ==
                            SeniorMode.SIMPLE,
                onClick = {
                    onSeniorModeChanged(
                        SeniorMode.SIMPLE,
                    )
                },
                label = {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .carepack_display_simple,
                            ),
                    )
                },
                modifier =
                    Modifier.testTag(
                        "display_simple",
                    ),
            )
        }
    }
}

@Composable
private fun AppVersionSection(
    appVersion: String,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                4.dp,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "settings_app_version",
                ),
    ) {
        Text(
            text =
                stringResource(
                    R.string
                        .carepack_settings_app_version,
                ),
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            modifier =
                Modifier.carePackHeading(),
        )

        Text(
            text =
                appVersion,
            style =
                MaterialTheme
                    .typography
                    .bodyMedium
                    .copy(
                        textDirection =
                            TextDirection.Ltr,
                    ),
            modifier =
                Modifier.testTag(
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
            )
        },
        modifier =
            Modifier.testTag(
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
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                6.dp,
            ),
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Text(
            text =
                description,
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
        )

        if (destructive) {
            OutlinedButton(
                onClick =
                    onClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            testTag,
                        ),
            ) {
                Text(
                    text =
                        title,
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                )
            }
        } else {
            Button(
                onClick =
                    onClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            testTag,
                        ),
            ) {
                Text(
                    text =
                        title,
                )
            }
        }
    }
}

@Composable
private fun weekdayPersianNameResource(
    dayOfWeek: DayOfWeek,
): Int =
    when (dayOfWeek) {
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
