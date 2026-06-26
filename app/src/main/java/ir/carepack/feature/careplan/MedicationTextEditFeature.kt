package ir.carepack.feature.careplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.UpdateMedicationTextCommand
import ir.carepack.domain.careplan.UpdateMedicationTextOutcome
import ir.carepack.domain.model.MedicationStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationTextEditUiState(
    val isLoading: Boolean = true,
    val medicationName: String = "",
    val instruction: String = "",
    val errors:
    Map<CarePlanField, String> =
        emptyMap(),
    val isSaving: Boolean = false,
    val generalError: String? = null,
)

sealed interface MedicationTextEditEvent {

    data object Completed :
        MedicationTextEditEvent
}

class MedicationTextEditViewModel(
    private val medicationId: String,
    private val carePlanService:
    CarePlanService,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            MedicationTextEditUiState(),
        )

    val state =
        mutableState.asStateFlow()

    private val eventChannel =
        Channel<MedicationTextEditEvent>(
            capacity = Channel.BUFFERED,
        )

    val events =
        eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun onMedicationNameChanged(
        value: String,
    ) {
        mutableState.update {
            it.copy(
                medicationName = value,
                errors =
                    it.errors -
                            CarePlanField
                                .MEDICATION_NAME,
                generalError = null,
            )
        }
    }

    fun onInstructionChanged(
        value: String,
    ) {
        mutableState.update {
            it.copy(
                instruction = value,
                errors =
                    it.errors -
                            CarePlanField
                                .INSTRUCTION,
                generalError = null,
            )
        }
    }

    fun save() {
        if (
            mutableState.value.isSaving ||
            mutableState.value.isLoading
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isSaving = true,
                    errors = emptyMap(),
                    generalError = null,
                )
            }

            try {
                val current =
                    mutableState.value

                when (
                    val outcome =
                        carePlanService
                            .updateMedicationText(
                                UpdateMedicationTextCommand(
                                    medicationId =
                                        medicationId,
                                    medicationName =
                                        current
                                            .medicationName,
                                    instruction =
                                        current
                                            .instruction,
                                ),
                            )
                ) {
                    UpdateMedicationTextOutcome
                        .Updated,
                    UpdateMedicationTextOutcome
                        .Unchanged,
                        -> {
                        eventChannel.send(
                            MedicationTextEditEvent
                                .Completed,
                        )
                    }

                    UpdateMedicationTextOutcome
                        .NotFound -> {
                        showGeneralError(
                            "دارو پیدا نشد.",
                        )
                    }

                    UpdateMedicationTextOutcome
                        .NotEditable -> {
                        showGeneralError(
                            "این دارو قابل ویرایش نیست.",
                        )
                    }

                    is UpdateMedicationTextOutcome
                    .Invalid -> {
                        mutableState.update {
                            it.copy(
                                errors =
                                    outcome
                                        .errors
                                        .associate {
                                                error ->
                                            error.field to
                                                    error.message
                                        },
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                showGeneralError(
                    "ذخیره‌سازی انجام نشد. دوباره تلاش کنید.",
                )
            } finally {
                mutableState.update {
                    it.copy(
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
                        .getMedicationEditor(
                            medicationId,
                        )

                if (
                    snapshot == null ||
                    snapshot.status !=
                    MedicationStatus.ACTIVE
                ) {
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            generalError =
                                "داروی قابل ویرایش پیدا نشد.",
                        )
                    }
                    return@launch
                }

                mutableState.update {
                    it.copy(
                        isLoading = false,
                        medicationName =
                            snapshot.name,
                        instruction =
                            snapshot.instruction,
                    )
                }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        generalError =
                            "خواندن اطلاعات دارو انجام نشد.",
                    )
                }
            }
        }
    }

    private fun showGeneralError(
        message: String,
    ) {
        mutableState.update {
            it.copy(
                generalError = message,
            )
        }
    }

    companion object {
        fun factory(
            medicationId: String,
            carePlanService:
            CarePlanService,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    MedicationTextEditViewModel(
                        medicationId =
                            medicationId,
                        carePlanService =
                            carePlanService,
                    )
                }
            }
        }
    }
}

@Composable
fun MedicationTextEditRoute(
    viewModel:
    MedicationTextEditViewModel,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect {
                event ->
            when (event) {
                MedicationTextEditEvent
                    .Completed -> {
                    onCompleted()
                }
            }
        }
    }

    MedicationTextEditScreen(
        state = state,
        onBack = onBack,
        onMedicationNameChanged =
            viewModel::
            onMedicationNameChanged,
        onInstructionChanged =
            viewModel::
            onInstructionChanged,
        onSave =
            viewModel::save,
    )
}

@Composable
private fun MedicationTextEditScreen(
    state:
    MedicationTextEditUiState,
    onBack: () -> Unit,
    onMedicationNameChanged:
        (String) -> Unit,
    onInstructionChanged:
        (String) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        modifier =
            Modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(
                    rememberScrollState(),
                )
                .padding(24.dp),
            verticalArrangement =
                Arrangement.Top,
        ) {
            TextButton(
                onClick = onBack,
            ) {
                Text(
                    text = stringResource(
                        R.string.back,
                    ),
                )
            }

            Text(
                text = stringResource(
                    R.string
                        .medication_text_edit_title,
                ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp),
            )

            if (state.isLoading) {
                CircularProgressIndicator()
                return@Column
            }

            OutlinedTextField(
                value =
                    state.medicationName,
                onValueChange =
                    onMedicationNameChanged,
                enabled =
                    !state.isSaving,
                label = {
                    Text(
                        text = stringResource(
                            R.string
                                .medication_name_label,
                        ),
                    )
                },
                singleLine = true,
                isError =
                    state.errors.containsKey(
                        CarePlanField
                            .MEDICATION_NAME,
                    ),
                supportingText = {
                    state.errors[
                        CarePlanField
                            .MEDICATION_NAME
                    ]?.let {
                        Text(it)
                    }
                },
                modifier =
                    Modifier.fillMaxWidth(),
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp),
            )

            OutlinedTextField(
                value =
                    state.instruction,
                onValueChange =
                    onInstructionChanged,
                enabled =
                    !state.isSaving,
                label = {
                    Text(
                        text = stringResource(
                            R.string
                                .instruction_label,
                        ),
                    )
                },
                minLines = 4,
                isError =
                    state.errors.containsKey(
                        CarePlanField
                            .INSTRUCTION,
                    ),
                supportingText = {
                    state.errors[
                        CarePlanField
                            .INSTRUCTION
                    ]?.let {
                        Text(it)
                    }
                },
                modifier =
                    Modifier.fillMaxWidth(),
            )

            state.generalError?.let {
                    error ->
                Spacer(
                    modifier =
                        Modifier.height(12.dp),
                )

                Text(
                    text = error,
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(24.dp),
            )

            Button(
                onClick = onSave,
                enabled =
                    !state.isSaving,
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = stringResource(
                            R.string
                                .save_changes,
                        ),
                    )
                }
            }
        }
    }
}
