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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.UpdateMedicationTextCommand
import ir.carepack.domain.careplan.UpdateMedicationTextOutcome
import ir.carepack.domain.model.MedicationStatus
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.util.concurrent.CancellationException
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
            capacity =
                Channel.BUFFERED,
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
                current ->
            current.copy(
                medicationName =
                    value,
                errors =
                    current.errors -
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
                current ->
            current.copy(
                instruction =
                    value,
                errors =
                    current.errors -
                            CarePlanField
                                .INSTRUCTION,
                generalError = null,
            )
        }
    }

    fun save() {
        val current =
            mutableState.value

        if (
            current.isSaving ||
            current.isLoading
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
                val state =
                    mutableState.value

                when (
                    val outcome =
                        carePlanService
                            .updateMedicationText(
                                UpdateMedicationTextCommand(
                                    medicationId =
                                        medicationId,
                                    medicationName =
                                        state
                                            .medicationName,
                                    instruction =
                                        state
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
                                value ->
                            value.copy(
                                errors =
                                    outcome
                                        .errors
                                        .toFieldErrors(),
                            )
                        }
                    }
                }
            } catch (
                cancellationException:
                CancellationException,
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
                            current ->
                        current.copy(
                            isLoading = false,
                            generalError =
                                "داروی قابل ویرایش پیدا نشد.",
                        )
                    }

                    return@launch
                }

                mutableState.update {
                        current ->
                    current.copy(
                        isLoading = false,
                        medicationName =
                            snapshot.name,
                        instruction =
                            snapshot.instruction,
                        errors = emptyMap(),
                        generalError = null,
                    )
                }
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.update {
                        current ->
                    current.copy(
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
                current ->
            current.copy(
                generalError =
                    message,
            )
        }
    }

    companion object {

        fun factory(
            medicationId: String,
            carePlanService:
            CarePlanService,
        ): ViewModelProvider.Factory =
            viewModelFactory {
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

    LaunchedEffect(
        viewModel,
    ) {
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
            Modifier
                .fillMaxSize()
                .testTag(
                    "medication_text_edit_screen",
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
                        "medication_text_edit_back",
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
                        R.string
                            .medication_text_edit_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "medication_text_edit_title",
                        ),
            )

            when {
                state.isLoading -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .carePackPoliteLiveRegion()
                                .testTag(
                                    "medication_text_edit_loading",
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
                                "در حال خواندن اطلاعات دارو…",
                        )
                    }
                }

                else -> {
                    MedicationTextFields(
                        medicationName =
                            state.medicationName,
                        instruction =
                            state.instruction,
                        errors =
                            state.errors,
                        enabled =
                            !state.isSaving,
                        onMedicationNameChanged =
                            onMedicationNameChanged,
                        onInstructionChanged =
                            onInstructionChanged,
                        instructionMinLines = 4,
                        medicationNameTestTag =
                            "medication_text_edit_name",
                        instructionTestTag =
                            "medication_text_edit_instruction",
                    )

                    state.generalError
                        ?.let { error ->
                            Text(
                                text =
                                    error,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .carePackPoliteLiveRegion()
                                        .testTag(
                                            "medication_text_edit_error",
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
                            !state.isSaving,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "medication_text_edit_save",
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
                                        R.string
                                            .save_changes,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
