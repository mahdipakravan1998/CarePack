package ir.carepack.feature.reporting

import ir.carepack.ui.viewmodel.carePackViewModelFactory

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import ir.carepack.domain.report.DateRangeSummary
import ir.carepack.domain.report.RangeReportFormatter
import ir.carepack.domain.report.RangeReportPeriod
import ir.carepack.reporting.share.ShareDescriptor
import ir.carepack.reporting.share.ShareReportKind
import ir.carepack.reporting.share.TextShareGateway
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
    val period: RangeReportPeriod = RangeReportPeriod.SEVEN_DAYS,
    val includeRecipientName: Boolean = false,
    val seniorMode: SeniorMode = SeniorMode.STANDARD,
    val summary: DateRangeSummary? = null,
    val reportText: String = "",
    val isLoading: Boolean = true,
    val isSharing: Boolean = false,
    val actionMessage: RangeReportActionMessage? = null,
    val failure: RangeReportFailure? = null,
)

class RangeReportViewModel(
    private val formatter: RangeReportFormatter,
    private val privacyPreferenceStore: PrivacyPreferenceStore,
    private val userExperiencePreferenceStore: UserExperiencePreferenceStore,
    private val textShareGateway: TextShareGateway,
    clock: Clock,
    zoneProvider: ZoneProvider,
) : ViewModel() {

    private val reportDate = LocalDate.now(
            clock.withZone(
                zoneProvider.currentZone(),
            ),
        )

    private val mutableState = MutableStateFlow(
            RangeReportUiState(
                today = reportDate,
            ),
        )

    val state = mutableState.asStateFlow()

    private val reportActions = ReportActionController(
            textShareGateway = textShareGateway,
            scope = viewModelScope,
            onTransition = ::applyReportActionTransition,
        )

    private var reportLoadJob: Job? = null

    init {
        observeIncludeRecipientName()
        observeSeniorMode()
    }

    fun selectPeriod(
        period: RangeReportPeriod,
    ) {
        if (
            period == mutableState
                .value.period
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
            privacyPreferenceStore.setIncludeRecipientName(
                    includeRecipientName,
                )
        }
    }

    fun copyReport() {
        val current = mutableState.value
        reportActions.copy(
            reportText = current.reportText,
            descriptor = shareDescriptor(current.period),
        )
    }

    fun shareReport() {
        val current = mutableState.value
        reportActions.share(
            reportText = current.reportText,
            descriptor = shareDescriptor(current.period),
            isSharing = current.isSharing,
        )
    }

    fun consumeActionMessage() {
        reportActions.consumeMessage()
    }

    fun refresh() {
        loadReport()
    }

    private fun applyReportActionTransition(
        transition: ReportActionTransition,
    ) {
        mutableState.update { current ->
            when (transition) {
                ReportActionTransition.SharingStarted -> current.copy(
                    isSharing = true,
                    actionMessage = null,
                    failure = null,
                )
                ReportActionTransition.SharingFinished ->
                    current.copy(isSharing = false)
                is ReportActionTransition.Succeeded -> current.copy(
                    actionMessage = when (transition.message) {
                            ReportActionMessage.COPIED -> RangeReportActionMessage.COPIED
                            ReportActionMessage.SHARE_CHOOSER_OPENED ->
                                RangeReportActionMessage.SHARE_CHOOSER_OPENED
                        },
                    failure = null,
                )
                is ReportActionTransition.Failed -> current.copy(
                    actionMessage = null,
                    failure = when (transition.failure) {
                            ReportActionFailure.COPY_FAILED ->
                                RangeReportFailure.COPY_FAILED
                            ReportActionFailure.NO_SHARE_TARGET ->
                                RangeReportFailure.NO_SHARE_TARGET
                            ReportActionFailure.SHARE_FAILED ->
                                RangeReportFailure.SHARE_FAILED
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

                    loadReport()
                }
        }
    }

    private fun observeSeniorMode() {
        viewModelScope.launch {
            userExperiencePreferenceStore.state
                .map {
                    it.seniorMode
                }.distinctUntilChanged()
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

        val request = mutableState.value

        reportLoadJob = viewModelScope.launch {
                mutableState.update { current ->
                    current.copy(
                        isLoading = true,
                        failure = null,
                    )
                }

                try {
                    val content = formatter.createRangeReport(
                            period = request.period,
                            today = request.today,
                            includeRecipientName = request
                                    .includeRecipientName,
                        )

                    mutableState.update { current ->
                        if (
                            current.period != request.period ||
                            current.includeRecipientName != request.includeRecipientName
                        ) {
                            current
                        } else {
                            current.copy(
                                summary = content.summary,
                                reportText = content
                                        .text.value,
                                isLoading = false,
                                failure = null,
                            )
                        }
                    }
                } catch (
                    cancellationException: CancellationException,
                ) {
                    throw cancellationException
                } catch (_: Exception) {
                    mutableState.update { current ->
                        current.copy(
                            isLoading = false,
                            summary = null,
                            reportText = "",
                            failure = RangeReportFailure
                                    .LOAD_FAILED,
                        )
                    }
                }
            }
    }

    companion object {
        fun factory(
            formatter: RangeReportFormatter,
            privacyPreferenceStore: PrivacyPreferenceStore,
            userExperiencePreferenceStore: UserExperiencePreferenceStore,
            textShareGateway: TextShareGateway,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory = carePackViewModelFactory {
                    RangeReportViewModel(
                        formatter = formatter,
                        privacyPreferenceStore = privacyPreferenceStore,
                        userExperiencePreferenceStore = userExperiencePreferenceStore,
                        textShareGateway = textShareGateway,
                        clock = clock,
                        zoneProvider = zoneProvider,
                    )
            }
    }
}

private fun shareDescriptor(
    period: RangeReportPeriod,
): ShareDescriptor = ShareDescriptor(
        kind = when (period) {
                RangeReportPeriod.SEVEN_DAYS ->
                    ShareReportKind.SEVEN_DAY
                RangeReportPeriod.THIRTY_DAYS ->
                    ShareReportKind.THIRTY_DAY
            },
    )
