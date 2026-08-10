package ir.carepack.feature.careplan

import ir.carepack.ui.viewmodel.carePackViewModelFactory

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.carepack.R
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.UpdateMedicationTextCommand
import ir.carepack.domain.careplan.UpdateMedicationTextOutcome
import ir.carepack.domain.model.MedicationStatus
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience
import java.util.concurrent.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationTextEditUiState(
    val isLoading: Boolean = true,
    val medication: MedicationTextDraft = MedicationTextDraft(),
    val errors: Map<CarePlanField, String> =
        emptyMap(),
    val isSaving: Boolean = false,
    val generalError: String? = null,
)

sealed interface MedicationTextEditEvent {

    data object Completed : MedicationTextEditEvent
}

class MedicationTextEditViewModel(
    private val medicationId: String,
    private val carePlanService: CarePlanService,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
            MedicationTextEditUiState(),
        )

    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<MedicationTextEditEvent>(
            capacity = Channel.BUFFERED,
        )

    val events = eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun onMedicationNameChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.MEDICATION_NAME, value)
    }

    fun onInstructionChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.INSTRUCTION, value)
    }

    fun onMedicationTypeChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.MEDICATION_TYPE, value)
    }

    fun onDosageTextChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.DOSAGE_TEXT, value)
    }

    fun onDoseUnitChanged(
        value: String,
    ) {
        updateMedicationField(CarePlanField.DOSE_UNIT, value)
    }

    fun save() {
        val current = mutableState.value

        if (
            current.isSaving || current.isLoading
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                    state ->
                state.copy(
                    isSaving = true,
                    errors = emptyMap(),
                    generalError = null,
                )
            }

            try {
                val state = mutableState.value

                when (
                    val outcome = carePlanService
                            .updateMedicationText(
                                UpdateMedicationTextCommand(
                                    medicationId = medicationId,
                                    medicationName = state.medication.medicationName,
                                    instruction = state.medication.instruction,
                                    medicationType = state.medication.medicationType,
                                    dosageText = state.medication.dosageText,
                                    doseUnit = state.medication.doseUnit,
                                ),
                            )) {
                    UpdateMedicationTextOutcome.Updated,
                    UpdateMedicationTextOutcome.Unchanged,
                        -> {
                        eventChannel.send(
                            MedicationTextEditEvent.Completed,
                        )
                    }

                    UpdateMedicationTextOutcome.NotFound -> {
                        showGeneralError(
                            "دارو پیدا نشد.",
                        )
                    }

                    UpdateMedicationTextOutcome.NotEditable -> {
                        showGeneralError(
                            "این دارو قابل ویرایش نیست.",
                        )
                    }

                    is UpdateMedicationTextOutcome.Invalid -> {
                        mutableState.update {
                                value ->
                            value.copy(
                                errors = outcome
                                        .errors.toFieldErrors(),
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
                mutableState.update {
                        state ->
                    state.copy(
                        isSaving = false,
                    )
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val snapshot = carePlanService
                        .getMedicationEditor(
                            medicationId,
                        )

                if (
                    snapshot == null || snapshot.status !=
                    MedicationStatus.ACTIVE) {
                    mutableState.update {
                            current ->
                        current.copy(
                            isLoading = false,
                            generalError = "داروی قابل ویرایش پیدا نشد.",
                        )
                    }

                    return@launch
                }

                mutableState.update {
                        current ->
                    current.copy(
                        isLoading = false,
                        medication = MedicationTextDraft(
                            medicationName = snapshot.name,
                            instruction = snapshot.instruction,
                            medicationType = snapshot.medicationType,
                            dosageText = snapshot.dosageText,
                            doseUnit = snapshot.doseUnit,
                        ),
                        errors = emptyMap(),
                        generalError = null,
                    )
                }
            } catch (
                cancellationException: CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.update {
                        current ->
                    current.copy(
                        isLoading = false,
                        generalError = "خواندن اطلاعات دارو انجام نشد.",
                    )
                }
            }
        }
    }

    private fun showGeneralError(
        message: String,
    ) {
        mutableState.update {
                current ->
            current.copy(
                generalError = message,
            )
        }
    }

    private fun updateMedicationField(
        field: CarePlanField,
        value: String,
    ) {
        mutableState.update { current ->
            current.copy(
                medication = current.medication.withField(field, value),
                errors = current.errors - field,
                generalError = null,
            )
        }
    }

    companion object {

        fun factory(
            medicationId: String,
            carePlanService: CarePlanService,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    MedicationTextEditViewModel(
                        medicationId = medicationId,
                        carePlanService = carePlanService,
                    )
            }
    }
}

@Composable
fun MedicationTextEditRoute(
    viewModel: MedicationTextEditViewModel,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        viewModel,
    ) {
        viewModel.events.collect {
                event ->
            when (event) {
                MedicationTextEditEvent.Completed -> {
                    onCompleted()
                }
            }
        }
    }

    MedicationTextEditScreen(
        state = state,
        onBack = onBack,
        onMedicationNameChanged = viewModel::
            onMedicationNameChanged,
        onInstructionChanged = viewModel::
            onInstructionChanged,
        onMedicationTypeChanged = viewModel::
            onMedicationTypeChanged,
        onDosageTextChanged = viewModel::
            onDosageTextChanged,
        onDoseUnitChanged = viewModel::
            onDoseUnitChanged,
        onSave = viewModel::save,
    )
}

@Composable
private fun MedicationTextEditScreen(
    state: MedicationTextEditUiState,
    onBack: () -> Unit,
    onMedicationNameChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    onMedicationTypeChanged: (String) -> Unit,
    onDosageTextChanged: (String) -> Unit,
    onDoseUnitChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val experience = carePackExperience()

    Scaffold(
        modifier = Modifier
                .fillMaxSize().testTag(
                    "medication_text_edit_screen",
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
                        horizontal = experience.screenHorizontalPadding,
                        vertical = experience.screenVerticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            TextButton(
                onClick = onBack,
                enabled = !state.isSaving,
                modifier = Modifier
                        .carePackInteractiveControl().testTag(
                            "medication_text_edit_back",
                        ),
            ) {
                Text(
                    text = stringResource(
                            R.string.back,
                        ),
                )
            }

            Text(
                text = stringResource(
                        R.string.medication_text_edit_title,
                    ),
                style = MaterialTheme
                        .typography.headlineMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "medication_text_edit_title",
                        ),
            )

            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier
                                .fillMaxWidth().carePackPoliteLiveRegion()
                                .testTag(
                                    "medication_text_edit_loading",
                                ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                                experience.itemSpacing,
                            ),
                    ) {
                        CircularProgressIndicator()

                        Text(
                            text = "در حال خواندن اطلاعات دارو…",
                        )
                    }
                }

                else -> {
                    MedicationTextFields(
                        medicationName = state.medication.medicationName,
                        instruction = state.medication.instruction,
                        medicationType = state.medication.medicationType,
                        dosageText = state.medication.dosageText,
                        doseUnit = state.medication.doseUnit,
                        errors = state.errors,
                        enabled = !state.isSaving,
                        onMedicationNameChanged = onMedicationNameChanged,
                        onInstructionChanged = onInstructionChanged,
                        onMedicationTypeChanged = onMedicationTypeChanged,
                        onDosageTextChanged = onDosageTextChanged,
                        onDoseUnitChanged = onDoseUnitChanged,
                        instructionMinLines = 4,
                        medicationNameTestTag = "medication_text_edit_name",
                        instructionTestTag = "medication_text_edit_instruction",
                        medicationTypeTestTag = "medication_text_edit_type",
                        dosageTextTestTag = "medication_text_edit_dosage",
                        doseUnitTestTag = "medication_text_edit_unit",
                    )

                    state.generalError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme
                                        .colorScheme.error,
                                modifier = Modifier
                                        .fillMaxWidth().carePackPoliteLiveRegion()
                                        .testTag(
                                            "medication_text_edit_error",
                                        ),
                            )
                        }

                    Spacer(
                        modifier = Modifier.height(
                                experience.compactSpacing,
                            ),
                    )

                    Button(
                        onClick = onSave,
                        enabled = !state.isSaving,
                        modifier = Modifier
                                .fillMaxWidth().carePackPrimaryAction()
                                .testTag(
                                    "medication_text_edit_save",
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
                                text = stringResource(
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
