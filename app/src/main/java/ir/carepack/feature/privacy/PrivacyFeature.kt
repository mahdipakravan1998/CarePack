package ir.carepack.feature.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.settings.privacy.OpenPrivacyPolicyResult
import ir.carepack.settings.privacy.PrivacyPolicyGateway
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PrivacyExternalActionError {
    POLICY_ADDRESS_UNAVAILABLE,
    NO_BROWSER,
    BLOCKED,
}

data class PrivacyUiState(
    val externalActionError:
    PrivacyExternalActionError? = null,
)

class PrivacyViewModel(
    private val privacyPolicyGateway:
    PrivacyPolicyGateway,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            PrivacyUiState(),
        )

    val state =
        mutableState.asStateFlow()

    fun openPublicPolicy() {
        val error =
            when (
                privacyPolicyGateway
                    .openPublicPolicy()
            ) {
                OpenPrivacyPolicyResult.Opened -> {
                    null
                }

                OpenPrivacyPolicyResult
                    .InvalidAddress -> {
                    PrivacyExternalActionError
                        .POLICY_ADDRESS_UNAVAILABLE
                }

                OpenPrivacyPolicyResult
                    .NoBrowser -> {
                    PrivacyExternalActionError
                        .NO_BROWSER
                }

                OpenPrivacyPolicyResult.Blocked -> {
                    PrivacyExternalActionError
                        .BLOCKED
                }
            }

        mutableState.update {
                current ->
            current.copy(
                externalActionError =
                    error,
            )
        }
    }

    fun clearExternalActionError() {
        mutableState.update {
                current ->
            current.copy(
                externalActionError = null,
            )
        }
    }

    companion object {

        fun factory(
            privacyPolicyGateway:
            PrivacyPolicyGateway,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PrivacyViewModel(
                        privacyPolicyGateway =
                            privacyPolicyGateway,
                    )
                }
            }
    }
}

@Composable
fun PrivacyRoute(
    privacyPolicyGateway:
    PrivacyPolicyGateway,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel:
            PrivacyViewModel =
        viewModel(
            factory =
                PrivacyViewModel.factory(
                    privacyPolicyGateway =
                        privacyPolicyGateway,
                ),
        )

    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    PrivacyScreen(
        state = state,
        onOpenPublicPolicy =
            viewModel::openPublicPolicy,
        onDismissExternalError =
            viewModel::clearExternalActionError,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun PrivacyScreen(
    state: PrivacyUiState,
    onOpenPublicPolicy: () -> Unit,
    onDismissExternalError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "privacy_screen",
                ),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        contentPadding,
                    )
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal = 20.dp,
                        vertical = 16.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp,
                ),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier =
                    Modifier.testTag(
                        "privacy_back",
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
                            .carepack_privacy_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "privacy_title",
                        ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_privacy_summary,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                modifier =
                    Modifier.testTag(
                        "privacy_summary",
                    ),
            )

            PrivacySection(
                title =
                    stringResource(
                        R.string
                            .carepack_privacy_local_storage_title,
                    ),
                body =
                    stringResource(
                        R.string
                            .carepack_privacy_local_storage_body,
                    ),
                testTag =
                    "privacy_local_storage",
            )

            PrivacySection(
                title =
                    stringResource(
                        R.string
                            .carepack_privacy_permissions_title,
                    ),
                body =
                    stringResource(
                        R.string
                            .carepack_privacy_permissions_body,
                    ),
                testTag =
                    "privacy_permissions",
            )

            PrivacySection(
                title =
                    stringResource(
                        R.string
                            .carepack_privacy_sharing_title,
                    ),
                body =
                    stringResource(
                        R.string
                            .carepack_privacy_sharing_body,
                    ),
                testTag =
                    "privacy_sharing",
            )

            PrivacySection(
                title =
                    stringResource(
                        R.string
                            .carepack_privacy_backup_title,
                    ),
                body =
                    stringResource(
                        R.string
                            .carepack_privacy_backup_body,
                    ),
                testTag =
                    "privacy_backup",
            )

            PrivacySection(
                title =
                    stringResource(
                        R.string
                            .carepack_privacy_deletion_title,
                    ),
                body =
                    stringResource(
                        R.string
                            .carepack_privacy_deletion_body,
                    ),
                testTag =
                    "privacy_deletion",
            )

            PrivacySection(
                title =
                    stringResource(
                        R.string
                            .carepack_privacy_medical_title,
                    ),
                body =
                    stringResource(
                        R.string
                            .carepack_privacy_medical_body,
                    ),
                testTag =
                    "privacy_medical",
            )

            state.externalActionError
                ?.let { error ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .carePackPoliteLiveRegion()
                                .testTag(
                                    "privacy_external_error",
                                ),
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(
                                    16.dp,
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    12.dp,
                                ),
                        ) {
                            Text(
                                text =
                                    error.toDisplayText(),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,
                            )

                            OutlinedButton(
                                onClick =
                                    onDismissExternalError,
                                modifier =
                                    Modifier.testTag(
                                        "privacy_external_error_dismiss",
                                    ),
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string
                                                .carepack_dismiss_message,
                                        ),
                                )
                            }
                        }
                    }
                }

            Button(
                onClick =
                    onOpenPublicPolicy,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            "privacy_open_public_policy",
                        ),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .carepack_open_public_policy,
                        ),
                )
            }
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    body: String,
    testTag: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    testTag,
                ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    16.dp,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.carePackHeading(),
            )

            Text(
                text = body,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
            )
        }
    }
}

@Composable
private fun PrivacyExternalActionError.toDisplayText():
        String =
    when (this) {
        PrivacyExternalActionError
            .POLICY_ADDRESS_UNAVAILABLE -> {
            stringResource(
                R.string
                    .carepack_policy_address_unavailable,
            )
        }

        PrivacyExternalActionError
            .NO_BROWSER -> {
            stringResource(
                R.string
                    .carepack_policy_no_browser,
            )
        }

        PrivacyExternalActionError
            .BLOCKED -> {
            stringResource(
                R.string
                    .carepack_policy_open_blocked,
            )
        }
    }
