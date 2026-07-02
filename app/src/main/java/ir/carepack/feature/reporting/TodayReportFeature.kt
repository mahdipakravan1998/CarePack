package ir.carepack.feature.reporting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.calendar.JalaliPresentationDate
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
}

data class TodayReportUiState(
    val date: LocalDate,
    val includeRecipientName: Boolean = false,
    val isLoading: Boolean = true,
    val isSharing: Boolean = false,
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
            privacyPreferenceStore
                .setIncludeRecipientName(
                    includeRecipientName,
                )
        }
    }

    fun copyReport() {
        val reportText =
            mutableState
                .value
                .reportText

        if (reportText.isBlank()) {
            return
        }

        val result =
            textShareGateway.copy(
                reportText,
            )

        mutableState.update { current ->
            when (result) {
                CopyTextResult.Copied -> {
                    current.copy(
                        actionMessage =
                            TodayReportActionMessage.COPIED,
                        errorMessage = null,
                    )
                }

                CopyTextResult.Blocked,
                CopyTextResult.InvalidText,
                    -> {
                    current.copy(
                        actionMessage = null,
                        errorMessage =
                            "کپی متن گزارش انجام نشد.",
                    )
                }
            }
        }
    }

    fun consumeActionMessage() {
        mutableState.update { current ->
            current.copy(
                actionMessage = null,
            )
        }
    }

    fun shareReport() {
        val reportText =
            mutableState
                .value
                .reportText

        if (
            reportText.isBlank() ||
            mutableState
                .value
                .isSharing
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update { current ->
                current.copy(
                    isSharing = true,
                    errorMessage = null,
                    actionMessage = null,
                )
            }

            try {
                val result =
                    textShareGateway.share(
                        reportText,
                    )

                mutableState.update { current ->
                    when (result) {
                        ShareTextResult.ChooserOpened -> {
                            current.copy(
                                actionMessage =
                                    TodayReportActionMessage
                                        .SHARE_CHOOSER_OPENED,
                                errorMessage = null,
                            )
                        }

                        ShareTextResult.NoShareTarget -> {
                            current.copy(
                                actionMessage = null,
                                errorMessage =
                                    "برنامه‌ای برای اشتراک‌گذاری پیدا نشد.",
                            )
                        }

                        ShareTextResult.Blocked,
                        ShareTextResult.InvalidText,
                            -> {
                            current.copy(
                                actionMessage = null,
                                errorMessage =
                                    "اشتراک‌گذاری انجام نشد.",
                            )
                        }
                    }
                }
            } catch (
                cancellationException: CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.update { current ->
                    current.copy(
                        errorMessage =
                            "اشتراک‌گذاری انجام نشد.",
                    )
                }
            } finally {
                mutableState.update { current ->
                    current.copy(
                        isSharing = false,
                    )
                }
            }
        }
    }

    fun refresh() {
        loadReport(
            includeRecipientName =
                mutableState
                    .value
                    .includeRecipientName,
        )
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
                    mutableState.update { current ->
                        current.copy(
                            includeRecipientName =
                                includeRecipientName,
                        )
                    }

                    loadReport(
                        includeRecipientName =
                            includeRecipientName,
                    )
                }
        }
    }

    private fun loadReport(
        includeRecipientName: Boolean,
    ) {
        viewModelScope.launch {
            mutableState.update { current ->
                current.copy(
                    isLoading = true,
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
                        reportText =
                            report.value,
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
                        errorMessage =
                            "گزارش آماده نشد. دوباره تلاش کنید.",
                    )
                }
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
) {
    val viewModel:
            TodayReportViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
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

    TodayReportScreen(
        state = state,
        snackbarHostState =
            snackbarHostState,
        onBack = onBack,
        onIncludeRecipientNameChanged =
            viewModel::setIncludeRecipientName,
        onCopyReport =
            viewModel::copyReport,
        onShareReport =
            viewModel::shareReport,
        onRetry =
            viewModel::refresh,
    )

    ReportActionMessages(
        actionMessage =
            state.actionMessage,
        snackbarHostState =
            snackbarHostState,
        onConsumed =
            viewModel::consumeActionMessage,
    )
}

@Composable
private fun TodayReportScreen(
    state: TodayReportUiState,
    snackbarHostState:
    SnackbarHostState,
    onBack: () -> Unit,
    onIncludeRecipientNameChanged:
        (Boolean) -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState =
                    snackbarHostState,
            )
        },
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(
                    "today_report_screen",
                ),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues,
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
            TextButton(
                onClick = onBack,
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
                        JalaliPresentationDate
                            .from(state.date)
                            .formatNumeric(),
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

            IncludeRecipientNameToggle(
                checked =
                    state.includeRecipientName,
                onCheckedChange =
                    onIncludeRecipientNameChanged,
            )

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_share_destination_notice,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                modifier =
                    Modifier.testTag(
                        "share_notice",
                    ),
            )

            when {
                state.isLoading -> {
                    LoadingReport()
                }

                state.errorMessage != null -> {
                    ErrorReport(
                        message =
                            state.errorMessage,
                        onRetry = onRetry,
                    )
                }

                else -> {
                    ReportPreview(
                        reportText =
                            state.reportText,
                    )

                    ReportActions(
                        reportText =
                            state.reportText,
                        isSharing =
                            state.isSharing,
                        onCopyReport =
                            onCopyReport,
                        onShareReport =
                            onShareReport,
                    )
                }
            }
        }
    }
}

@Composable
private fun IncludeRecipientNameToggle(
    checked: Boolean,
    onCheckedChange:
        (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange =
                        onCheckedChange,
                )
                .testTag(
                    "include_recipient_name_row",
                ),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier.weight(
                    1f,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    4.dp,
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

            Text(
                text =
                    stringResource(
                        R.string
                            .carepack_include_recipient_name_description,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier =
                Modifier.testTag(
                    "include_recipient_name_switch",
                ),
        )
    }
}

@Composable
private fun LoadingReport() {
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
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        CircularProgressIndicator()

        Text(
            text =
                stringResource(
                    R.string.carepack_report_loading,
                ),
        )
    }
}

@Composable
private fun ErrorReport(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .carePackPoliteLiveRegion()
                .testTag(
                    "today_report_error",
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        Text(
            text = message,
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
                        R.string.retry_action,
                    ),
            )
        }
    }
}

@Composable
private fun ReportPreview(
    reportText: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "today_report_preview_card",
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
                    stringResource(
                        R.string
                            .carepack_report_preview_heading,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                modifier =
                    Modifier.carePackHeading(),
            )

            SelectionContainer {
                Text(
                    text =
                        reportText,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                            .copy(
                                textDirection =
                                    TextDirection.ContentOrRtl,
                            ),
                    modifier =
                        Modifier.testTag(
                            "today_report_preview_text",
                        ),
                )
            }
        }
    }
}

@Composable
private fun ReportActions(
    reportText: String,
    isSharing: Boolean,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        OutlinedButton(
            onClick =
                onCopyReport,
            enabled =
                reportText.isNotBlank() &&
                        !isSharing,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "today_report_copy",
                    ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.carepack_copy_report,
                    ),
            )
        }

        Button(
            onClick =
                onShareReport,
            enabled =
                reportText.isNotBlank() &&
                        !isSharing,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(
                        "today_report_share",
                    ),
        ) {
            if (isSharing) {
                CircularProgressIndicator()
            } else {
                Text(
                    text =
                        stringResource(
                            R.string.carepack_share_report,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ReportActionMessages(
    actionMessage:
    TodayReportActionMessage?,
    snackbarHostState:
    SnackbarHostState,
    onConsumed: () -> Unit,
) {
    val copiedMessage =
        stringResource(
            R.string.carepack_report_copied,
        )

    val shareOpenedMessage =
        stringResource(
            R.string.carepack_share_chooser_opened,
        )

    LaunchedEffect(
        actionMessage,
    ) {
        if (actionMessage == null) {
            return@LaunchedEffect
        }

        snackbarHostState.showSnackbar(
            when (actionMessage) {
                TodayReportActionMessage.COPIED ->
                    copiedMessage

                TodayReportActionMessage
                    .SHARE_CHOOSER_OPENED ->
                    shareOpenedMessage
            },
        )

        onConsumed()
    }
}
