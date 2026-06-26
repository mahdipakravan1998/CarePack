package ir.carepack.feature.setup

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import ir.carepack.domain.careplan.CarePlanService
import ir.carepack.domain.careplan.CreateRecipientCommand
import ir.carepack.domain.careplan.CreateRecipientOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipientSetupUiState(
    val displayName: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface RecipientSetupEvent {
    data class Continue(
        val recipientId: String,
    ) : RecipientSetupEvent
}

class RecipientSetupViewModel(
    private val carePlanService: CarePlanService,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            RecipientSetupUiState(),
        )

    val state =
        mutableState.asStateFlow()

    private val eventChannel =
        Channel<RecipientSetupEvent>(
            capacity = Channel.BUFFERED,
        )

    val events =
        eventChannel.receiveAsFlow()

    fun onDisplayNameChanged(
        newValue: String,
    ) {
        mutableState.update {
            it.copy(
                displayName = newValue,
                errorMessage = null,
            )
        }
    }

    fun save() {
        if (mutableState.value.isSaving) {
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            try {
                val outcome =
                    carePlanService
                        .createRecipient(
                            CreateRecipientCommand(
                                displayName =
                                    mutableState
                                        .value
                                        .displayName,
                            ),
                        )

                when (outcome) {
                    is CreateRecipientOutcome.Created -> {
                        eventChannel.send(
                            RecipientSetupEvent.Continue(
                                recipientId =
                                    outcome.recipientId,
                            ),
                        )
                    }

                    is CreateRecipientOutcome.AlreadyExists -> {
                        eventChannel.send(
                            RecipientSetupEvent.Continue(
                                recipientId =
                                    outcome.recipientId,
                            ),
                        )
                    }

                    is CreateRecipientOutcome.Invalid -> {
                        val errorMessage =
                            outcome
                                .errors
                                .firstOrNull()
                                ?.message
                                ?: "نام واردشده معتبر نیست."

                        mutableState.update {
                            it.copy(
                                errorMessage =
                                    errorMessage,
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        errorMessage =
                            "ذخیره‌سازی انجام نشد. دوباره تلاش کنید.",
                    )
                }
            } finally {
                mutableState.update {
                    it.copy(
                        isSaving = false,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            carePlanService: CarePlanService,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    RecipientSetupViewModel(
                        carePlanService =
                            carePlanService,
                    )
                }
            }
        }
    }
}

@Composable
fun RecipientSetupRoute(
    viewModel: RecipientSetupViewModel,
    onContinue: (String) -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecipientSetupEvent.Continue -> {
                    onContinue(event.recipientId)
                }
            }
        }
    }

    RecipientSetupScreen(
        state = state,
        onDisplayNameChanged =
            viewModel::onDisplayNameChanged,
        onSave = viewModel::save,
    )
}

@Composable
fun RecipientSetupScreen(
    state: RecipientSetupUiState,
    onDisplayNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
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
            Text(
                text =
                    stringResource(
                        R.string.recipient_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .recipient_description,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp),
            )

            OutlinedTextField(
                value = state.displayName,
                onValueChange =
                    onDisplayNameChanged,
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
                    state.errorMessage != null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "recipient_name",
                        ),
            )

            state.errorMessage?.let {
                    errorMessage ->
                Spacer(
                    modifier =
                        Modifier.height(8.dp),
                )

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
                        Modifier.testTag(
                            "recipient_error",
                        ),
                )
            }

            Spacer(
                modifier =
                    Modifier.height(24.dp),
            )

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "recipient_save",
                        ),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .save_and_continue,
                            ),
                    )
                }
            }
        }
    }
}
