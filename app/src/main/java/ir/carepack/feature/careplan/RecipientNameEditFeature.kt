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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import ir.carepack.domain.careplan.UpdateRecipientNameCommand
import ir.carepack.domain.careplan.UpdateRecipientNameOutcome
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipientNameEditUiState(
    val isLoading: Boolean = true,
    val recipientId: String? = null,
    val displayName: String = "",
    val isSaving: Boolean = false,
    val fieldErrorMessage: String? = null,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
)

class RecipientNameEditViewModel(
    private val carePlanService: CarePlanService,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            RecipientNameEditUiState(),
        )

    val state =
        mutableState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeRecipient()
    }

    fun retry() {
        observeRecipient()
    }

    fun onDisplayNameChanged(
        value: String,
    ) {
        mutableState.update { currentState ->
            currentState.copy(
                displayName = value,
                fieldErrorMessage = null,
                statusMessage = null,
                statusIsError = false,
            )
        }
    }

    fun save() {
        val currentState =
            mutableState.value

        val recipientId =
            currentState.recipientId

        if (
            currentState.isSaving ||
            recipientId.isNullOrBlank()
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update { state ->
                state.copy(
                    isSaving = true,
                    fieldErrorMessage = null,
                    statusMessage = null,
                    statusIsError = false,
                )
            }

            try {
                val outcome =
                    carePlanService.updateRecipientName(
                        UpdateRecipientNameCommand(
                            recipientId = recipientId,
                            displayName =
                                mutableState
                                    .value
                                    .displayName,
                        ),
                    )

                when (outcome) {
                    UpdateRecipientNameOutcome.Updated -> {
                        mutableState.update { state ->
                            state.copy(
                                statusMessage =
                                    "نام فرد تحت مراقبت به‌روزرسانی شد.",
                                statusIsError = false,
                            )
                        }
                    }

                    UpdateRecipientNameOutcome.Unchanged -> {
                        mutableState.update { state ->
                            state.copy(
                                statusMessage =
                                    "نام تغییری نکرد.",
                                statusIsError = false,
                            )
                        }
                    }

                    UpdateRecipientNameOutcome.NotFound -> {
                        mutableState.update { state ->
                            state.copy(
                                statusMessage =
                                    "فرد تحت مراقبت پیدا نشد.",
                                statusIsError = true,
                            )
                        }
                    }

                    is UpdateRecipientNameOutcome.Invalid -> {
                        val fieldError =
                            outcome
                                .errors
                                .firstOrNull { error ->
                                    error.field ==
                                            CarePlanField
                                                .RECIPIENT_NAME
                                }
                                ?.message
                                ?: outcome
                                    .errors
                                    .firstOrNull()
                                    ?.message
                                ?: "نام واردشده معتبر نیست."

                        mutableState.update { state ->
                            state.copy(
                                fieldErrorMessage = fieldError,
                                statusIsError = true,
                            )
                        }
                    }
                }
            } catch (
                cancellationException: CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.update { state ->
                    state.copy(
                        statusMessage =
                            "ذخیره‌سازی انجام نشد. دوباره تلاش کنید.",
                        statusIsError = true,
                    )
                }
            } finally {
                mutableState.update { state ->
                    state.copy(
                        isSaving = false,
                    )
                }
            }
        }
    }

    private fun observeRecipient() {
        observeJob?.cancel()

        observeJob =
            viewModelScope.launch {
                mutableState.update { state ->
                    state.copy(
                        isLoading = true,
                        fieldErrorMessage = null,
                        statusMessage = null,
                        statusIsError = false,
                    )
                }

                try {
                    carePlanService
                        .observeCarePlan()
                        .collect { overview ->
                            mutableState.update { state ->
                                if (overview == null) {
                                    state.copy(
                                        isLoading = false,
                                        recipientId = null,
                                        statusMessage =
                                            "فرد تحت مراقبت پیدا نشد.",
                                        statusIsError = true,
                                    )
                                } else {
                                    state.copy(
                                        isLoading = false,
                                        recipientId =
                                            overview.recipientId,
                                        displayName =
                                            if (state.isSaving) {
                                                state.displayName
                                            } else {
                                                overview
                                                    .recipientDisplayName
                                            },
                                    )
                                }
                            }
                        }
                } catch (
                    cancellationException: CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            statusMessage =
                                "خواندن نام فرد تحت مراقبت انجام نشد.",
                            statusIsError = true,
                        )
                    }
                }
            }
    }

    companion object {
        fun factory(
            carePlanService: CarePlanService,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RecipientNameEditViewModel(
                        carePlanService = carePlanService,
                    )
                }
            }
    }
}

@Composable
fun RecipientNameEditRoute(
    viewModel: RecipientNameEditViewModel,
    onBack: () -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    RecipientNameEditScreen(
        state = state,
        onDisplayNameChanged =
            viewModel::onDisplayNameChanged,
        onSave =
            viewModel::save,
        onRetry =
            viewModel::retry,
        onBack = onBack,
    )
}

@Composable
fun RecipientNameEditScreen(
    state: RecipientNameEditUiState,
    onDisplayNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val experience =
        carePackExperience()

    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "recipient_name_edit_screen",
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
                    .imePadding()
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal =
                            experience.screenHorizontalPadding,
                        vertical =
                            experience.screenVerticalPadding,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            TextButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .carePackInteractiveControl()
                        .testTag(
                            "recipient_name_edit_back",
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
                text = "ویرایش نام فرد تحت مراقبت",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "recipient_name_edit_title",
                        ),
            )

            Text(
                text =
                    "این مسیر فقط نام همان فرد ثبت‌شده را تغییر می‌دهد و فرد جدیدی نمی‌سازد.",
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(
                                32.dp,
                            )
                            .testTag(
                                "recipient_name_edit_loading",
                            ),
                )
            } else {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = onDisplayNameChanged,
                    enabled =
                        !state.isSaving &&
                                state.recipientId != null,
                    label = {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .recipient_name_label,
                                ),
                        )
                    },
                    singleLine = true,
                    isError =
                        state.fieldErrorMessage != null,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction =
                                ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (!state.isSaving) {
                                    onSave()
                                }
                            },
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .carePackInteractiveControl()
                            .testTag(
                                "recipient_name_edit_field",
                            ),
                )

                state.fieldErrorMessage
                    ?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .carePackPoliteLiveRegion()
                                    .testTag(
                                        "recipient_name_edit_error",
                                    ),
                        )
                    }

                Spacer(
                    modifier =
                        Modifier.height(
                            experience.compactSpacing,
                        ),
                )

                Button(
                    onClick = onSave,
                    enabled =
                        !state.isSaving &&
                                state.recipientId != null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .carePackPrimaryAction()
                            .testTag(
                                "recipient_name_edit_save",
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

                OutlinedButton(
                    onClick = onRetry,
                    enabled = !state.isSaving,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .carePackInteractiveControl()
                            .testTag(
                                "recipient_name_edit_retry",
                            ),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.retry,
                            ),
                    )
                }
            }

            state.statusMessage
                ?.let { statusMessage ->
                    Text(
                        text = statusMessage,
                        color =
                            if (state.statusIsError) {
                                MaterialTheme
                                    .colorScheme
                                    .error
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .primary
                            },
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .carePackPoliteLiveRegion()
                                .testTag(
                                    "recipient_name_edit_status",
                                ),
                    )
                }
        }
    }
}
