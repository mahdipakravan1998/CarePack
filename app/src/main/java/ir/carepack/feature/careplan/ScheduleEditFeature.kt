package ir.carepack.feature.careplan

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.UpdateScheduleCommand
import ir.carepack.domain.careplan.UpdateScheduleOutcome
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.model.MedicationStatus
import ir.carepack.domain.schedule.FixedTimeSchedule
import ir.carepack.domain.schedule.IntervalSchedule
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleEditUiState(
    val isLoading: Boolean = true,
    val originalZoneId: String? = null,
    val medicationName: String? = null,
    val schedule: ScheduleFormUiState? = null,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val previewAnchorDate: LocalDate = LocalDate.now(),
    val isSaving: Boolean = false,
    val generalError: String? = null,
)

sealed interface ScheduleEditEvent {

    data object Completed :
        ScheduleEditEvent
}

class ScheduleEditViewModel(
    private val scheduleSeriesId: String,
    private val carePlanService: CarePlanService,
    private val zoneProvider: ZoneProvider,
    private val userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val currentZone =
        zoneProvider.currentZone()

    private val mutableState =
        MutableStateFlow(
            ScheduleEditUiState(),
        )

    val state =
        mutableState.asStateFlow()

    private val eventChannel =
        Channel<ScheduleEditEvent>(
            capacity = Channel.BUFFERED,
        )

    val events =
        eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            userExperiencePreferenceStore
                .state
                .collect { preferenceState ->
                    mutableState.update { current ->
                        current.copy(
                            firstDayOfWeek =
                                FirstDayOfWeekPolicy
                                    .resolve(
                                        preference =
                                            preferenceState
                                                .firstDayOfWeekPreference,
                                        zoneId =
                                            currentZone,
                                        locale =
                                            Locale.getDefault(),
                                    ),
                        )
                    }
                }
        }

        load()
    }

    fun onWeekdayToggled(
        day: DayOfWeek,
    ) {
        updateSchedule {
            it.toggleWeekday(day)
        }
    }

    fun onInputModeSelected(
        mode: ScheduleInputMode,
    ) {
        updateSchedule {
            it.withInputMode(mode)
        }
    }

    fun onTimeDraftChanged(
        value: String,
    ) {
        updateSchedule {
            it.withTimeDraft(value)
        }
    }

    fun addTime() {
        updateSchedule(
            clearGeneralError = false,
        ) {
            it.addDraftTime()
        }
    }

    fun removeTime(
        minuteOfDay: Int,
    ) {
        updateSchedule {
            it.removeTime(minuteOfDay)
        }
    }

    fun onIntervalHoursSelected(
        hours: Int,
    ) {
        updateSchedule {
            it.withIntervalHours(hours)
        }
    }

    fun onIntervalAnchorChanged(
        value: String,
    ) {
        updateSchedule {
            it.withIntervalAnchorDraft(value)
        }
    }

    fun onStartDateChanged(
        value: String,
    ) {
        updateSchedule {
            it.withStartDate(value)
        }
    }

    fun onEndDateChanged(
        value: String,
    ) {
        updateSchedule {
            it.withEndDate(value)
        }
    }

    fun save() {
        val effectiveFrom =
            currentEffectiveFrom()

        val schedule =
            mutableState
                .value
                .schedule
                ?.withPreviewEffectiveFrom(
                    effectiveFrom,
                )
                ?: return

        mutableState.update { current ->
            current.copy(
                schedule =
                    schedule,
                previewAnchorDate =
                    effectiveFrom
                        .atZone(
                            currentZone,
                        )
                        .toLocalDate(),
            )
        }

        if (
            mutableState
                .value
                .isSaving
        ) {
            return
        }

        val parsedDates =
            schedule.parseDates()

        if (parsedDates.errors.isNotEmpty()) {
            mutableState.update { current ->
                current.copy(
                    schedule =
                        current
                            .schedule
                            ?.withDateErrors(
                                parsedDates.errors,
                            ),
                )
            }

            return
        }

        viewModelScope.launch {
            mutableState.update { current ->
                current.copy(
                    isSaving = true,
                    schedule =
                        current
                            .schedule
                            ?.clearErrors()
                            ?.withPreviewEffectiveFrom(
                                currentEffectiveFrom(),
                            ),
                    previewAnchorDate =
                        currentPreviewDate(),
                    generalError = null,
                )
            }

            try {
                val latestSchedule =
                    mutableState
                        .value
                        .schedule
                        ?: return@launch

                when (
                    val outcome =
                        carePlanService.updateSchedule(
                            UpdateScheduleCommand(
                                scheduleSeriesId =
                                    scheduleSeriesId,
                                weekdays =
                                    latestSchedule.weekdays,
                                minutesOfDay =
                                    latestSchedule
                                        .effectiveMinutesOfDay(),
                                schedulePattern =
                                    latestSchedule
                                        .toSchedulePattern(),
                                startDate =
                                    parsedDates.startDate,
                                endDate =
                                    parsedDates.endDate,
                                zoneId =
                                    latestSchedule.zoneId,
                            ),
                        )
                ) {
                    UpdateScheduleOutcome.Updated,
                    UpdateScheduleOutcome.Unchanged,
                        -> {
                        eventChannel.send(
                            ScheduleEditEvent.Completed,
                        )
                    }

                    UpdateScheduleOutcome.NotFound -> {
                        showGeneralError(
                            "برنامه پیدا نشد.",
                        )
                    }

                    UpdateScheduleOutcome.NotEditable -> {
                        showGeneralError(
                            "این برنامه قابل ویرایش نیست.",
                        )
                    }

                    is UpdateScheduleOutcome.Invalid -> {
                        val fieldErrors =
                            outcome
                                .errors
                                .toFieldErrors()

                        mutableState.update { current ->
                            current.copy(
                                schedule =
                                    current
                                        .schedule
                                        ?.withValidationErrors(
                                            fieldErrors,
                                        )
                                        ?.withPreviewEffectiveFrom(
                                            currentEffectiveFrom(),
                                        ),
                                previewAnchorDate =
                                    currentPreviewDate(),
                            )
                        }
                    }
                }
            } catch (
                cancellationException: CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                showGeneralError(
                    "ذخیره‌سازی انجام نشد. دوباره تلاش کنید.",
                )
            } finally {
                mutableState.update { current ->
                    current.copy(
                        isSaving = false,
                    )
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val snapshot =
                    carePlanService
                        .getScheduleEditor(
                            scheduleSeriesId,
                        )

                if (
                    snapshot == null ||
                    snapshot.status !=
                    MedicationStatus.ACTIVE
                ) {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            generalError =
                                "برنامه قابل ویرایش پیدا نشد.",
                        )
                    }

                    return@launch
                }

                val existingSchedule =
                    snapshot.schedule

                val inputMode =
                    when (
                        existingSchedule
                            .schedulePattern
                    ) {
                        is FixedTimeSchedule -> {
                            ScheduleInputMode.FIXED_TIMES
                        }

                        is IntervalSchedule -> {
                            ScheduleInputMode.EVERY_X_HOURS
                        }
                    }

                val intervalSchedule =
                    existingSchedule
                        .schedulePattern as?
                            IntervalSchedule

                val fixedMinutes =
                    existingSchedule
                        .times
                        .map { time ->
                            time.toMinuteOfDay()
                        }

                val effectiveFrom =
                    currentEffectiveFrom()

                mutableState.update { current ->
                    current.copy(
                        isLoading = false,
                        originalZoneId =
                            existingSchedule.zoneId,
                        medicationName =
                            snapshot.medicationName,
                        schedule =
                            ScheduleFormUiState(
                                weekdays =
                                    existingSchedule.weekdays,
                                minutesOfDay =
                                    fixedMinutes,
                                timeDraft = "",
                                startDateText =
                                    existingSchedule
                                        .startDate
                                        ?.toJalaliDateText()
                                        .orEmpty(),
                                endDateText =
                                    existingSchedule
                                        .endDate
                                        ?.toJalaliDateText()
                                        .orEmpty(),
                                zoneId =
                                    currentZone.id,
                                previewEffectiveFrom =
                                    effectiveFrom,
                                inputMode =
                                    inputMode,
                                intervalHours =
                                    intervalSchedule
                                        ?.intervalHours
                                        ?: DEFAULT_INTERVAL_HOURS,
                                intervalAnchorDraft =
                                    (
                                            intervalSchedule
                                                ?.anchorMinuteOfDay
                                                ?: fixedMinutes
                                                    .firstOrNull()
                                                ?: DEFAULT_ANCHOR_MINUTE
                                            ).toHourMinuteText(),
                            ),
                        previewAnchorDate =
                            effectiveFrom
                                .atZone(
                                    currentZone,
                                )
                                .toLocalDate(),
                        generalError = null,
                    )
                }
            } catch (
                cancellationException: CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.update { current ->
                    current.copy(
                        isLoading = false,
                        generalError =
                            "خواندن برنامه انجام نشد.",
                    )
                }
            }
        }
    }

    private fun updateSchedule(
        clearGeneralError: Boolean = true,
        transform:
            (
            ScheduleFormUiState,
        ) -> ScheduleFormUiState,
    ) {
        mutableState.update { current ->
            val schedule =
                current.schedule
                    ?: return@update current

            val effectiveFrom =
                currentEffectiveFrom()

            current.copy(
                schedule =
                    transform(schedule)
                        .withPreviewEffectiveFrom(
                            effectiveFrom,
                        ),
                previewAnchorDate =
                    effectiveFrom
                        .atZone(
                            currentZone,
                        )
                        .toLocalDate(),
                generalError =
                    if (clearGeneralError) {
                        null
                    } else {
                        current.generalError
                    },
            )
        }
    }

    private fun showGeneralError(
        message: String,
    ) {
        mutableState.update { current ->
            current.copy(
                generalError = message,
            )
        }
    }

    private fun currentEffectiveFrom(): Instant =
        clock.instant()

    private fun currentPreviewDate(): LocalDate =
        currentEffectiveFrom()
            .atZone(
                currentZone,
            )
            .toLocalDate()

    companion object {

        fun factory(
            scheduleSeriesId: String,
            carePlanService: CarePlanService,
            zoneProvider: ZoneProvider,
            userExperiencePreferenceStore:
            UserExperiencePreferenceStore,
            clock: Clock = Clock.systemUTC(),
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ScheduleEditViewModel(
                        scheduleSeriesId =
                            scheduleSeriesId,
                        carePlanService =
                            carePlanService,
                        zoneProvider = zoneProvider,
                        userExperiencePreferenceStore =
                            userExperiencePreferenceStore,
                        clock = clock,
                    )
                }
            }

        private const val DEFAULT_INTERVAL_HOURS = 8
        private const val DEFAULT_ANCHOR_MINUTE = 8 * 60
    }
}

@Composable
fun ScheduleEditRoute(
    viewModel: ScheduleEditViewModel,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        viewModel,
    ) {
        viewModel.events.collect { event ->
            when (event) {
                ScheduleEditEvent.Completed -> {
                    onCompleted()
                }
            }
        }
    }

    ScheduleEditScreen(
        state = state,
        onBack = onBack,
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
        onSave =
            viewModel::save,
    )
}

@Composable
private fun ScheduleEditScreen(
    state: ScheduleEditUiState,
    onBack: () -> Unit,
    onWeekdayToggled:
        (DayOfWeek) -> Unit,
    onInputModeSelected:
        (ScheduleInputMode) -> Unit,
    onTimeDraftChanged:
        (String) -> Unit,
    onAddTime: () -> Unit,
    onRemoveTime:
        (Int) -> Unit,
    onIntervalHoursSelected:
        (Int) -> Unit,
    onIntervalAnchorChanged:
        (String) -> Unit,
    onStartDateChanged:
        (String) -> Unit,
    onEndDateChanged:
        (String) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    "schedule_edit_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        contentPadding,
                    )
                    .imePadding()
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
                    16.dp,
                ),
        ) {
            TextButton(
                onClick = onBack,
                enabled =
                    !state.isSaving,
                modifier =
                    Modifier.testTag(
                        "schedule_edit_back",
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.back,
                        ),
                )
            }

            Text(
                text =
                    stringResource(
                        R.string.schedule_edit_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "schedule_edit_title",
                        ),
            )

            state.medicationName
                ?.let { medicationName ->
                    Text(
                        text =
                            medicationName,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        modifier =
                            Modifier.testTag(
                                "schedule_edit_medication_name",
                            ),
                    )
                }

            when {
                state.isLoading -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .carePackPoliteLiveRegion()
                                .testTag(
                                    "schedule_edit_loading",
                                ),
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.spacedBy(
                                12.dp,
                            ),
                    ) {
                        CircularProgressIndicator()

                        Text(
                            text =
                                "در حال خواندن برنامه…",
                        )
                    }
                }

                else -> {
                    state.originalZoneId
                        ?.let { oldZone ->
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag(
                                            "schedule_original_zone",
                                        ),
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.original_zone_value,
                                            oldZone,
                                        ),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium
                                            .copy(
                                                textDirection =
                                                    TextDirection.Ltr,
                                            ),
                                    modifier =
                                        Modifier.padding(
                                            16.dp,
                                        ),
                                )
                            }
                        }

                    state.schedule
                        ?.let { schedule ->
                            ScheduleFormFields(
                                state = schedule,
                                callbacks =
                                    ScheduleFormCallbacks(
                                        onWeekdayToggled =
                                            onWeekdayToggled,
                                        onInputModeSelected =
                                            onInputModeSelected,
                                        onTimeDraftChanged =
                                            onTimeDraftChanged,
                                        onAddTime =
                                            onAddTime,
                                        onRemoveTime =
                                            onRemoveTime,
                                        onIntervalHoursSelected =
                                            onIntervalHoursSelected,
                                        onIntervalAnchorChanged =
                                            onIntervalAnchorChanged,
                                        onStartDateChanged =
                                            onStartDateChanged,
                                        onEndDateChanged =
                                            onEndDateChanged,
                                    ),
                                enabled =
                                    !state.isSaving,
                                firstDayOfWeek =
                                    state.firstDayOfWeek,
                                previewAnchorDate =
                                    state.previewAnchorDate,
                                modifier =
                                    Modifier.testTag(
                                        "schedule_edit_form",
                                    ),
                            )
                        }

                    state.generalError
                        ?.let { error ->
                            Text(
                                text = error,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .carePackPoliteLiveRegion()
                                        .testTag(
                                            "schedule_edit_error",
                                        ),
                            )
                        }

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp,
                            ),
                    )

                    Button(
                        onClick = onSave,
                        enabled =
                            !state.isSaving &&
                                    state.schedule != null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "schedule_edit_save",
                                ),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(
                                        24.dp,
                                    ),
                            )
                        } else {
                            Text(
                                text =
                                    stringResource(
                                        R.string.save_changes,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
