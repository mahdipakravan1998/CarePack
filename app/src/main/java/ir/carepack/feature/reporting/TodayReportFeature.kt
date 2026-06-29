package ir.carepack.feature.reporting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.report.TodayReportFormatter
import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TodayReportActionMessage {
    COPIED,
    SHARE_CHOOSER_OPENED,
    NO_SHARE_TARGET,
    ACTION_BLOCKED,
}

data class TodayReportUiState(
    val date: LocalDate,
    val isLoading: Boolean = true,
    val includeRecipientName:
    Boolean = false,
    val reportText: String = "",
    val actionMessage:
    TodayReportActionMessage? = null,
    val errorMessage: String? = null,
)

class TodayReportViewModel(
    private val date: LocalDate,
    private val formatter:
    TodayReportFormatter,
    private val privacyPreferenceStore:
    PrivacyPreferenceStore,
    private val textShareGateway:
    TextShareGateway,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            TodayReportUiState(
                date = date,
            ),
        )

    val state =
        mutableState.asStateFlow()

    init {
        observeIncludeRecipientName()
    }

    fun setIncludeRecipientName(
        includeRecipientName: Boolean,
    ) {
        viewModelScope.launch {
            try {
                privacyPreferenceStore
                    .setIncludeRecipientName(
                        includeRecipientName =
                            includeRecipientName,
                    )
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.update {
                        current ->
                    current.copy(
                        errorMessage =
                            STORAGE_ERROR_MESSAGE,
                    )
                }
            }
        }
    }

    fun retry() {
        generateReport(
            includeRecipientName =
                mutableState
                    .value
                    .includeRecipientName,
        )
    }

    fun copyReport() {
        val reportText =
            mutableState
                .value
                .reportText

        val actionMessage =
            when (
                textShareGateway.copy(
                    text = reportText,
                )
            ) {
                CopyTextResult.Copied -> {
                    TodayReportActionMessage
                        .COPIED
                }

                CopyTextResult.Blocked,
                CopyTextResult.InvalidText -> {
                    TodayReportActionMessage
                        .ACTION_BLOCKED
                }
            }

        mutableState.update {
                current ->
            current.copy(
                actionMessage =
                    actionMessage,
            )
        }
    }

    fun shareReport() {
        val reportText =
            mutableState
                .value
                .reportText

        val actionMessage =
            when (
                textShareGateway.share(
                    text = reportText,
                )
            ) {
                ShareTextResult
                    .ChooserOpened -> {
                    TodayReportActionMessage
                        .SHARE_CHOOSER_OPENED
                }

                ShareTextResult
                    .NoShareTarget -> {
                    TodayReportActionMessage
                        .NO_SHARE_TARGET
                }

                ShareTextResult.Blocked,
                ShareTextResult.InvalidText -> {
                    TodayReportActionMessage
                        .ACTION_BLOCKED
                }
            }

        mutableState.update {
                current ->
            current.copy(
                actionMessage =
                    actionMessage,
            )
        }
    }

    fun consumeActionMessage() {
        mutableState.update {
                current ->
            current.copy(
                actionMessage = null,
            )
        }
    }

    private fun observeIncludeRecipientName() {
        viewModelScope.launch {
            privacyPreferenceStore
                .state
                .map {
                    it.includeRecipientName
                }
                .distinctUntilChanged()
                .collectLatest {
                        includeRecipientName ->
                    loadReport(
                        includeRecipientName =
                            includeRecipientName,
                    )
                }
        }
    }

    private fun generateReport(
        includeRecipientName: Boolean,
    ) {
        viewModelScope.launch {
            loadReport(
                includeRecipientName =
                    includeRecipientName,
            )
        }
    }

    private suspend fun loadReport(
        includeRecipientName: Boolean,
    ) {
        mutableState.update {
                current ->
            current.copy(
                isLoading = true,
                includeRecipientName =
                    includeRecipientName,
                errorMessage = null,
            )
        }

        try {
            val report =
                formatter.createTodayReport(
                    date = date,
                    includeRecipientName =
                        includeRecipientName,
                )

            mutableState.update {
                    current ->
                current.copy(
                    isLoading = false,
                    includeRecipientName =
                        includeRecipientName,
                    reportText =
                        report.value,
                    errorMessage = null,
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
                    includeRecipientName =
                        includeRecipientName,
                    reportText = "",
                    errorMessage =
                        REPORT_LOAD_ERROR_MESSAGE,
                )
            }
        }
    }

    companion object {

        fun factory(
            date: LocalDate,
            formatter:
            TodayReportFormatter,
            privacyPreferenceStore:
            PrivacyPreferenceStore,
            textShareGateway:
            TextShareGateway,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TodayReportViewModel(
                        date = date,
                        formatter = formatter,
                        privacyPreferenceStore =
                            privacyPreferenceStore,
                        textShareGateway =
                            textShareGateway,
                    )
                }
            }

        private const val STORAGE_ERROR_MESSAGE =
            "ذخیره انتخاب نمایش نام انجام نشد."

        private const val REPORT_LOAD_ERROR_MESSAGE =
            "ساخت گزارش امروز انجام نشد."
    }
}

@Composable
fun TodayReportRoute(
    date: LocalDate,
    formatter: TodayReportFormatter,
    privacyPreferenceStore:
    PrivacyPreferenceStore,
    textShareGateway:
    TextShareGateway,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel:
            TodayReportViewModel =
        viewModel(
            factory =
                TodayReportViewModel.factory(
                    date = date,
                    formatter = formatter,
                    privacyPreferenceStore =
                        privacyPreferenceStore,
                    textShareGateway =
                        textShareGateway,
                ),
        )

    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    val snackbarHostState =
        remember {
            SnackbarHostState()
        }

    val actionMessage =
        state.actionMessage
            ?.toDisplayText()

    LaunchedEffect(
        actionMessage,
    ) {
        if (actionMessage != null) {
            snackbarHostState.showSnackbar(
                message = actionMessage,
            )

            viewModel.consumeActionMessage()
        }
    }

    TodayReportScreen(
        state = state,
        snackbarHostState =
            snackbarHostState,
        onIncludeRecipientNameChanged =
            viewModel::setIncludeRecipientName,
        onCopy =
            viewModel::copyReport,
        onShare =
            viewModel::shareReport,
        onRetry =
            viewModel::retry,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun TodayReportScreen(
    state: TodayReportUiState,
    snackbarHostState:
    SnackbarHostState,
    onIncludeRecipientNameChanged:
        (Boolean) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(
                    "today_report_screen",
                ),
        snackbarHost = {
            SnackbarHost(
                hostState =
                    snackbarHostState,
            )
        },
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
                enabled =
                    !state.isLoading,
                modifier =
                    Modifier.testTag(
                        "today_report_back",
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
                            .carepack_today_report_title,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                modifier =
                    Modifier
                        .carePackHeading()
                        .testTag(
                            "today_report_title",
                        ),
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_today_report_date,
                        state.date.toString(),
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                modifier =
                    Modifier.testTag(
                        "today_report_date",
                    ),
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value =
                                    state
                                        .includeRecipientName,
                                enabled =
                                    !state.isLoading,
                                role = Role.Switch,
                                onValueChange =
                                    onIncludeRecipientNameChanged,
                            )
                            .padding(
                                16.dp,
                            )
                            .testTag(
                                "today_report_include_name",
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            16.dp,
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(
                        modifier =
                            Modifier.weight(
                                1f,
                            ),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .carepack_include_recipient_name,
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    4.dp,
                                ),
                        )

                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .carepack_include_recipient_name_description,
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                        )
                    }

                    Switch(
                        checked =
                            state
                                .includeRecipientName,
                        enabled =
                            !state.isLoading,
                        onCheckedChange = null,
                    )
                }
            }

            when {
                state.isLoading -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .carePackPoliteLiveRegion()
                                .testTag(
                                    "today_report_loading",
                                ),
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()

                        Spacer(
                            modifier =
                                Modifier.height(
                                    12.dp,
                                ),
                        )

                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .carepack_report_loading,
                                ),
                        )
                    }
                }

                state.errorMessage != null -> {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .carePackPoliteLiveRegion()
                                .testTag(
                                    "today_report_error",
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
                                    state
                                        .errorMessage,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,
                            )

                            Button(
                                onClick = onRetry,
                                modifier =
                                    Modifier.testTag(
                                        "today_report_retry",
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
                    }
                }

                else -> {
                    Text(
                        text =
                            stringResource(
                                R.string
                                    .carepack_report_preview_heading,
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        modifier =
                            Modifier
                                .carePackHeading()
                                .testTag(
                                    "today_report_preview_heading",
                                ),
                    )

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        SelectionContainer {
                            Text(
                                text =
                                    state.reportText,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            16.dp,
                                        )
                                        .semantics {
                                            contentDescription =
                                                state
                                                    .reportText
                                        }
                                        .testTag(
                                            "today_report_preview",
                                        ),
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyLarge,
                            )
                        }
                    }

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string
                                        .carepack_share_destination_notice,
                                ),
                            modifier =
                                Modifier
                                    .padding(
                                        16.dp,
                                    )
                                    .testTag(
                                        "today_report_destination_notice",
                                    ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                        )
                    }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                12.dp,
                            ),
                    ) {
                        OutlinedButton(
                            onClick = onCopy,
                            enabled =
                                state
                                    .reportText
                                    .isNotBlank(),
                            modifier =
                                Modifier
                                    .weight(
                                        1f,
                                    )
                                    .testTag(
                                        "today_report_copy",
                                    ),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string
                                            .carepack_copy_report,
                                    ),
                            )
                        }

                        Button(
                            onClick = onShare,
                            enabled =
                                state
                                    .reportText
                                    .isNotBlank(),
                            modifier =
                                Modifier
                                    .weight(
                                        1f,
                                    )
                                    .testTag(
                                        "today_report_share",
                                    ),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string
                                            .carepack_share_report,
                                    ),
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(
                        16.dp,
                    ),
            )
        }
    }
}

@Composable
private fun TodayReportActionMessage.toDisplayText():
        String =
    when (this) {
        TodayReportActionMessage.COPIED -> {
            stringResource(
                R.string
                    .carepack_report_copied,
            )
        }

        TodayReportActionMessage
            .SHARE_CHOOSER_OPENED -> {
            stringResource(
                R.string
                    .carepack_share_chooser_opened,
            )
        }

        TodayReportActionMessage
            .NO_SHARE_TARGET -> {
            stringResource(
                R.string
                    .carepack_no_share_target,
            )
        }

        TodayReportActionMessage
            .ACTION_BLOCKED -> {
            stringResource(
                R.string
                    .carepack_share_action_failed,
            )
        }
    }
