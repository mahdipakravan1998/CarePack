package ir.carepack.feature.reporting

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.calendar.PersianDateText
import ir.carepack.domain.calendar.toPersianDigits
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.report.DateRangeSummary
import ir.carepack.domain.report.RangeReportFormatter
import ir.carepack.domain.report.RangeReportPeriod
import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareDescriptor
import ir.carepack.reporting.share.ShareReportKind
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.reporting.share.TextShareGateway
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackPrimaryAction
import ir.carepack.ui.experience.CarePackExperience
import ir.carepack.ui.experience.LocalCarePackExperience
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch



enum class RangeReportActionMessage {
    COPIED,
    SHARE_CHOOSER_OPENED,
}


enum class RangeReportFailure {
    LOAD_FAILED,
    COPY_FAILED,
    NO_SHARE_TARGET,
    SHARE_FAILED,
}


data class RangeReportUiState(
    val today: LocalDate,
    val period: RangeReportPeriod =
        RangeReportPeriod.SEVEN_DAYS,
    val includeRecipientName: Boolean = false,
    val seniorMode: SeniorMode = SeniorMode.STANDARD,
    val summary: DateRangeSummary? = null,
    val reportText: String = "",
    val isLoading: Boolean = true,
    val isSharing: Boolean = false,
    val actionMessage:
    RangeReportActionMessage? = null,
    val failure: RangeReportFailure? = null,
)

class RangeReportViewModel(
    private val formatter: RangeReportFormatter,
    private val privacyPreferenceStore:
    PrivacyPreferenceStore,
    private val userExperiencePreferenceStore:
    UserExperiencePreferenceStore,
    private val textShareGateway:
    TextShareGateway,
    clock: Clock,
    zoneProvider: ZoneProvider,
) : ViewModel() {

    private val reportDate =
        LocalDate.now(
            clock.withZone(
                zoneProvider.currentZone(),
            ),
        )

    private val mutableState =
        MutableStateFlow(
            RangeReportUiState(
                today = reportDate,
            ),
        )

    val state =
        mutableState.asStateFlow()

    private var reportLoadJob:
            Job? = null

    init {
        observeIncludeRecipientName()
        observeSeniorMode()
    }

    fun selectPeriod(
        period: RangeReportPeriod,
    ) {
        if (
            period ==
            mutableState
                .value
                .period
        ) {
            return
        }

        mutableState.update { current ->
            current.copy(
                period = period,
                actionMessage = null,
                failure = null,
            )
        }

        loadReport()
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
                text = reportText,
                descriptor =
                    shareDescriptor(
                        mutableState.value.period,
                    ),
            )

        mutableState.update { current ->
            when (result) {
                CopyTextResult.Copied ->
                    current.copy(
                        actionMessage =
                            RangeReportActionMessage
                                .COPIED,
                        failure = null,
                    )

                CopyTextResult.Blocked,
                CopyTextResult.InvalidText,
                    ->
                    current.copy(
                        actionMessage = null,
                        failure =
                            RangeReportFailure
                                .COPY_FAILED,
                    )
            }
        }
    }

    fun shareReport() {
        val currentState =
            mutableState.value

        if (
            currentState.reportText.isBlank() ||
            currentState.isSharing
        ) {
            return
        }

        viewModelScope.launch {
            mutableState.update { current ->
                current.copy(
                    isSharing = true,
                    actionMessage = null,
                    failure = null,
                )
            }

            try {
                val result =
                    textShareGateway.share(
                        text = currentState.reportText,
                        descriptor =
                            shareDescriptor(
                                currentState.period,
                            ),
                    )

                mutableState.update { current ->
                    when (result) {
                        ShareTextResult.ChooserOpened ->
                            current.copy(
                                actionMessage =
                                    RangeReportActionMessage
                                        .SHARE_CHOOSER_OPENED,
                                failure = null,
                            )

                        ShareTextResult.NoShareTarget ->
                            current.copy(
                                actionMessage = null,
                                failure =
                                    RangeReportFailure
                                        .NO_SHARE_TARGET,
                            )

                        ShareTextResult.Blocked,
                        ShareTextResult.InvalidText,
                            ->
                            current.copy(
                                actionMessage = null,
                                failure =
                                    RangeReportFailure
                                        .SHARE_FAILED,
                            )
                    }
                }
            } catch (
                cancellationException:
                CancellationException,
            ) {
                throw cancellationException
            } catch (_: Exception) {
                mutableState.update { current ->
                    current.copy(
                        failure =
                            RangeReportFailure
                                .SHARE_FAILED,
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

    fun consumeActionMessage() {
        mutableState.update { current ->
            current.copy(
                actionMessage = null,
            )
        }
    }

    fun refresh() {
        loadReport()
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

                    loadReport()
                }
        }
    }

    private fun observeSeniorMode() {
        viewModelScope.launch {
            userExperiencePreferenceStore
                .state
                .map {
                    it.seniorMode
                }
                .distinctUntilChanged()
                .collectLatest { seniorMode ->
                    mutableState.update { current ->
                        current.copy(
                            seniorMode = seniorMode,
                        )
                    }
                }
        }
    }

    private fun loadReport() {
        reportLoadJob?.cancel()

        val request =
            mutableState.value

        reportLoadJob =
            viewModelScope.launch {
                mutableState.update { current ->
                    current.copy(
                        isLoading = true,
                        failure = null,
                    )
                }

                try {
                    val content =
                        formatter.createRangeReport(
                            period = request.period,
                            today = request.today,
                            includeRecipientName =
                                request
                                    .includeRecipientName,
                        )

                    mutableState.update { current ->
                        if (
                            current.period !=
                            request.period ||
                            current.includeRecipientName !=
                            request.includeRecipientName
                        ) {
                            current
                        } else {
                            current.copy(
                                summary =
                                    content.summary,
                                reportText =
                                    content
                                        .text
                                        .value,
                                isLoading = false,
                                failure = null,
                            )
                        }
                    }
                } catch (
                    cancellationException:
                    CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            summary = null,
                            reportText = "",
                            failure =
                                RangeReportFailure
                                    .LOAD_FAILED,
                        )
                    }
                }
            }
    }

    companion object {
        fun factory(
            formatter: RangeReportFormatter,
            privacyPreferenceStore:
            PrivacyPreferenceStore,
            userExperiencePreferenceStore:
            UserExperiencePreferenceStore,
            textShareGateway:
            TextShareGateway,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RangeReportViewModel(
                        formatter = formatter,
                        privacyPreferenceStore =
                            privacyPreferenceStore,
                        userExperiencePreferenceStore =
                            userExperiencePreferenceStore,
                        textShareGateway =
                            textShareGateway,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }
    }
}

private fun shareDescriptor(
    period: RangeReportPeriod,
): ShareDescriptor =
    ShareDescriptor(
        kind =
            when (period) {
                RangeReportPeriod.SEVEN_DAYS ->
                    ShareReportKind.SEVEN_DAY
                RangeReportPeriod.THIRTY_DAYS ->
                    ShareReportKind.THIRTY_DAY
            },
    )
