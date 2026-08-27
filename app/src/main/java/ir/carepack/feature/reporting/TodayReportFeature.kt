package ir.carepack.feature.reporting

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.carepack.R
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.calendar.JalaliPresentationDate
import ir.carepack.domain.report.TodayReportFormatter
import ir.carepack.reporting.share.ShareDescriptor
import ir.carepack.reporting.share.ShareReportKind
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.carePackExperience
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
    val actionMessage: TodayReportActionMessage? = null,
    val errorMessage: String? = null,
)

class TodayReportViewModel(
    private val date: LocalDate,
    private val formatter: TodayReportFormatter,
    private val privacyPreferenceStore: PrivacyPreferenceStore,
    private val textShareGateway: TextShareGateway,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
            TodayReportUiState(
                date = date,
            ),
        )

    val state = mutableState.asStateFlow()

    private val reportActions = ReportActionController(
            textShareGateway = textShareGateway,
            scope = viewModelScope,
            onTransition = ::applyReportActionTransition,
        )

    init {
        observeIncludeRecipientName()
    }

    fun setIncludeRecipientName(
        includeRecipientName: Boolean,
    ) {
        viewModelScope.launch {
            try {
                privacyPreferenceStore.setIncludeRecipientName(
                        includeRecipientName,
                    )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableState.update { current ->
                    current.copy(
                        errorMessage = "ذخیره تنظیم حریم خصوصی انجام نشد. مقدار قبلی همچنان فعال است.",
                    )
                }
            }
        }
    }

    fun copyReport() {
        reportActions.copy(
            reportText = mutableState.value.reportText,
            descriptor = ShareDescriptor(ShareReportKind.TODAY),
        )
    }

    fun consumeActionMessage() {
        reportActions.consumeMessage()
    }

    fun shareReport() {
        val current = mutableState.value
        reportActions.share(
            reportText = current.reportText,
            descriptor = ShareDescriptor(ShareReportKind.TODAY),
            isSharing = current.isSharing,
        )
    }

    fun refresh() {
        loadReport(
            includeRecipientName = mutableState
                    .value.includeRecipientName,
        )
    }

    private fun applyReportActionTransition(
        transition: ReportActionTransition,
    ) {
        mutableState.update { current ->
            when (transition) {
                ReportActionTransition.SharingStarted -> current.copy(
                    isSharing = true,
                    errorMessage = null,
                    actionMessage = null,
                )
                ReportActionTransition.SharingFinished ->
                    current.copy(isSharing = false)
                is ReportActionTransition.Succeeded -> current.copy(
                    actionMessage = when (transition.message) {
                            ReportActionMessage.COPIED -> TodayReportActionMessage.COPIED
                            ReportActionMessage.SHARE_CHOOSER_OPENED ->
                                TodayReportActionMessage.SHARE_CHOOSER_OPENED
                        },
                    errorMessage = null,
                )
                is ReportActionTransition.Failed -> current.copy(
                    actionMessage = null,
                    errorMessage = when (transition.failure) {
                            ReportActionFailure.COPY_FAILED ->
                                "کپی متن گزارش انجام نشد."
                            ReportActionFailure.NO_SHARE_TARGET ->
                                "برنامه‌ای برای اشتراک‌گذاری پیدا نشد."
                            ReportActionFailure.SHARE_FAILED ->
                                "اشتراک‌گذاری انجام نشد."
                        },
                )
                ReportActionTransition.MessageConsumed ->
                    current.copy(actionMessage = null)
            }
        }
    }

    private fun observeIncludeRecipientName() {
        viewModelScope.launch {
            privacyPreferenceStore.state
                .map {
                    it.includeRecipientName
                }.distinctUntilChanged()
                .collectLatest {
                        includeRecipientName ->
                    mutableState.update { current ->
                        current.copy(
                            includeRecipientName = includeRecipientName,
                        )
                    }

                    loadReport(
                        includeRecipientName = includeRecipientName,
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
                val report = formatter.createTodayReport(
                        date = date,
                        includeRecipientName = includeRecipientName,
                    )

                mutableState.update {
                        current ->
                    current.copy(
                        isLoading = false,
                        reportText = report.value,
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
                        errorMessage = "گزارش آماده نشد. دوباره تلاش کنید.",
                    )
                }
            }
        }
    }

    companion object {

        fun factory(
            date: LocalDate,
            formatter: TodayReportFormatter,
            privacyPreferenceStore: PrivacyPreferenceStore,
            textShareGateway: TextShareGateway,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    TodayReportViewModel(
                        date = date,
                        formatter = formatter,
                        privacyPreferenceStore = privacyPreferenceStore,
                        textShareGateway = textShareGateway,
                    )
            }
    }
}

@Composable
fun TodayReportRoute(
    date: LocalDate,
    formatter: TodayReportFormatter,
    privacyPreferenceStore: PrivacyPreferenceStore,
    textShareGateway: TextShareGateway,
    onBack: () -> Unit,
) {
    val viewModel: TodayReportViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = TodayReportViewModel.factory(
                    date = date,
                    formatter = formatter,
                    privacyPreferenceStore = privacyPreferenceStore,
                    textShareGateway = textShareGateway,
                ),
        )

    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    val snackbarHostState = remember {
            SnackbarHostState()
        }

    TodayReportScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onIncludeRecipientNameChanged = viewModel::setIncludeRecipientName,
        onCopyReport = viewModel::copyReport,
        onShareReport = viewModel::shareReport,
        onRetry = viewModel::refresh,
    )

    ReportActionMessages(
        actionMessage = state.actionMessage,
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::consumeActionMessage,
    )
}

@Composable
private fun TodayReportScreen(
    state: TodayReportUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onIncludeRecipientNameChanged: (Boolean) -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
    onRetry: () -> Unit,
) {
    val experience = carePackExperience()

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
            )
        },
        modifier = Modifier
                .fillMaxSize().testTag(
                    "today_report_screen",
                ),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                    .fillMaxSize().padding(
                        paddingValues,
                    ).imePadding()
                    .navigationBarsPadding().verticalScroll(
                        rememberScrollState(),
                    ).padding(
                        horizontal = experience
                                .screenHorizontalPadding,
                        vertical = experience
                                .screenVerticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.sectionSpacing,
                ),
        ) {
            TextButton(
                onClick = onBack,
                enabled = !state.isSharing,
                modifier = Modifier
                        .carePackInteractiveControl().testTag(
                            "today_report_back",
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
                        R.string.carepack_today_report_title,
                    ),
                style = MaterialTheme
                        .typography.headlineMedium,
                modifier = Modifier
                        .carePackHeading().testTag(
                            "today_report_title",
                        ),
            )

            Text(
                text = stringResource(
                        R.string.carepack_today_report_date,
                        JalaliPresentationDate.from(state.date)
                            .formatNumeric(),
                    ),
                style = MaterialTheme
                        .typography.bodyLarge,
                modifier = Modifier.testTag(
                        "today_report_date",
                    ),
            )

            TodayIncludeRecipientNameToggle(
                checked = state.includeRecipientName,
                enabled = !state.isLoading &&
                            !state.isSharing,
                onCheckedChange = onIncludeRecipientNameChanged,
            )

            if (!experience.isSimple) {
                Text(
                    text = stringResource(
                            R.string.carepack_share_destination_notice,
                        ),
                    style = MaterialTheme
                            .typography.bodyMedium,
                    modifier = Modifier.testTag(
                            "share_notice",
                        ),
                )
            }

            when {
                state.isLoading -> {
                    LoadingReport()
                }

                state.errorMessage != null -> {
                    ErrorReport(
                        message = state.errorMessage,
                        onRetry = onRetry,
                    )
                }

                else -> {
                    ReportPreview(
                        reportText = state.reportText,
                    )

                    ReportActions(
                        reportText = state.reportText,
                        isSharing = state.isSharing,
                        onCopyReport = onCopyReport,
                        onShareReport = onShareReport,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayIncludeRecipientNameToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val experience = carePackExperience()

    Row(
        modifier = Modifier
                .fillMaxWidth().carePackInteractiveControl()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).testTag(
                    "include_recipient_name_row",
                ),
        horizontalArrangement = Arrangement.spacedBy(
                experience.itemSpacing,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(
                    1f,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.compactSpacing,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.carepack_include_recipient_name,
                    ),
                style = MaterialTheme
                        .typography.titleMedium,
            )

            if (!experience.isSimple) {
                Text(
                    text = stringResource(
                            R.string.carepack_include_recipient_name_description,
                        ),
                    style = MaterialTheme
                            .typography.bodySmall,
                )
            }
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            modifier = Modifier.testTag(
                    "include_recipient_name_switch",
                ),
        )
    }
}

@Composable
private fun LoadingReport() {
    val experience = carePackExperience()

    Column(
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(
                    "today_report_loading",
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
                experience.itemSpacing,
            ),
    ) {
        CircularProgressIndicator()

        Text(
            text = stringResource(
                    R.string.carepack_report_loading,
                ),
            style = MaterialTheme
                    .typography.bodyLarge,
        )
    }
}

@Composable
private fun ErrorReport(
    message: String,
    onRetry: () -> Unit,
) {
    val experience = carePackExperience()

    Column(
        modifier = Modifier
                .fillMaxWidth().carePackPoliteLiveRegion()
                .testTag(
                    "today_report_error",
                ),
        verticalArrangement = Arrangement.spacedBy(
                experience.itemSpacing,
            ),
    ) {
        Text(
            text = message,
            style = MaterialTheme
                    .typography.bodyLarge,
            color = MaterialTheme
                    .colorScheme.error,
        )

        Button(
            onClick = onRetry,
            modifier = Modifier
                    .fillMaxWidth().carePackPrimaryAction()
                    .testTag(
                        "today_report_retry",
                    ),
        ) {
            Text(
                text = stringResource(
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
    val experience = carePackExperience()

    Card(
        modifier = Modifier
                .fillMaxWidth().testTag(
                    "today_report_preview_card",
                ),
    ) {
        Column(
            modifier = Modifier.padding(
                    experience.itemSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(
                    experience.itemSpacing,
                ),
        ) {
            Text(
                text = stringResource(
                        R.string.carepack_report_preview_heading,
                    ),
                style = MaterialTheme
                        .typography.titleMedium,
                modifier = Modifier.carePackHeading(),
            )

            SelectionContainer {
                Text(
                    text = reportText,
                    style = if (experience.isSimple) {
                            MaterialTheme.typography
                                .bodyLarge.copy(
                                    textDirection = TextDirection.ContentOrRtl,
                                )
                        } else {
                            MaterialTheme.typography
                                .bodyMedium.copy(
                                    textDirection = TextDirection.ContentOrRtl,
                                )
                        },
                    modifier = Modifier.testTag(
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
    val experience = carePackExperience()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
                experience.itemSpacing,
            ),
    ) {
        OutlinedButton(
            onClick = onCopyReport,
            enabled = reportText.isNotBlank() &&
                        !isSharing,
            modifier = Modifier
                    .fillMaxWidth().carePackPrimaryAction()
                    .testTag(
                        "today_report_copy",
                    ),
        ) {
            Text(
                text = stringResource(
                        R.string.carepack_copy_report,
                    ),
            )
        }

        Button(
            onClick = onShareReport,
            enabled = reportText.isNotBlank() &&
                        !isSharing,
            modifier = Modifier
                    .fillMaxWidth().carePackPrimaryAction()
                    .testTag(
                        "today_report_share",
                    ),
        ) {
            if (isSharing) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = stringResource(
                            R.string.carepack_share_report,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ReportActionMessages(
    actionMessage: TodayReportActionMessage?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    val copiedMessage = stringResource(
            R.string.carepack_report_copied,
        )

    val shareOpenedMessage = stringResource(
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

                TodayReportActionMessage.SHARE_CHOOSER_OPENED ->
                    shareOpenedMessage
            },
        )

        onConsumed()
    }
}
