package ir.carepack.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import ir.carepack.BuildConfig
import ir.carepack.R
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.OccurrenceDetail
import ir.carepack.domain.report.CaregiverReportService
import ir.carepack.domain.report.RecordGivenOutcome
import ir.carepack.domain.today.TodayQueryService
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OccurrenceDetailUiState(
    val isLoading: Boolean = true,
    val occurrence: OccurrenceDetail? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

class OccurrenceDetailViewModel(
    private val occurrenceId: String,
    todayQueryService: TodayQueryService,
    private val caregiverReportService:
    CaregiverReportService,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(
            OccurrenceDetailUiState(),
        )

    val state = mutableState

    init {
        todayQueryService
            .observeOccurrence(occurrenceId)
            .onEach { occurrence ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        occurrence = occurrence,
                        errorMessage = null,
                    )
                }
            }
            .catch {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage =
                            "خواندن جزئیات انجام نشد.",
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun recordGiven() {
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
                when (
                    caregiverReportService.recordGiven(
                        occurrenceId,
                    )
                ) {
                    is RecordGivenOutcome.Recorded,
                    is RecordGivenOutcome.Unchanged,
                        -> Unit

                    is RecordGivenOutcome.ExistingDifferentReport -> {
                        mutableState.update {
                            it.copy(
                                errorMessage =
                                    "برای این نوبت گزارش دیگری ثبت شده است.",
                            )
                        }
                    }

                    RecordGivenOutcome.OccurrenceNotFound -> {
                        mutableState.update {
                            it.copy(
                                errorMessage =
                                    "نوبت پیدا نشد.",
                            )
                        }
                    }

                    RecordGivenOutcome.CancelledOccurrenceRejected -> {
                        mutableState.update {
                            it.copy(
                                errorMessage =
                                    "برای نوبت لغوشده نمی‌توان گزارش ثبت کرد.",
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        errorMessage =
                            "ثبت گزارش انجام نشد.",
                    )
                }
            } finally {
                mutableState.update {
                    it.copy(isSaving = false)
                }
            }
        }
    }

    companion object {
        fun factory(
            occurrenceId: String,
            todayQueryService:
            TodayQueryService,
            caregiverReportService:
            CaregiverReportService,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    OccurrenceDetailViewModel(
                        occurrenceId =
                            occurrenceId,
                        todayQueryService =
                            todayQueryService,
                        caregiverReportService =
                            caregiverReportService,
                    )
                }
            }
        }
    }
}

@Composable
fun OccurrenceDetailRoute(
    viewModel: OccurrenceDetailViewModel,
    onBack: () -> Unit,
) {
    val state by
    viewModel.state.collectAsStateWithLifecycle()

    OccurrenceDetailScreen(
        state = state,
        onRecordGiven =
            viewModel::recordGiven,
        onBack = onBack,
    )
}

@Composable
fun OccurrenceDetailScreen(
    state: OccurrenceDetailUiState,
    onRecordGiven: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("detail_back"),
            ) {
                Text(
                    text = stringResource(R.string.back),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.detail_title,
                ),
                style =
                    MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                }

                state.errorMessage != null &&
                        state.occurrence == null -> {
                    Text(
                        text = state.errorMessage,
                        color =
                            MaterialTheme.colorScheme.error,
                    )
                }

                state.occurrence == null -> {
                    Text(text = "نوبت پیدا نشد.")
                }

                else -> {
                    val occurrence =
                        state.occurrence

                    Text(
                        text = occurrence.medicationName,
                        style =
                            MaterialTheme.typography.titleLarge,
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp),
                    )

                    Text(
                        text = stringResource(
                            R.string.scheduled_time,
                        ),
                        style =
                            MaterialTheme.typography.labelLarge,
                    )

                    Text(
                        text = occurrence.localTime.format(
                            HOUR_MINUTE_FORMATTER,
                        ),
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp),
                    )

                    Text(
                        text = stringResource(
                            R.string.instruction,
                        ),
                        style =
                            MaterialTheme.typography.labelLarge,
                    )

                    Text(
                        text =
                            occurrence
                                .medicationInstruction,
                    )

                    if (BuildConfig.DEBUG) {
                        Spacer(
                            modifier =
                                Modifier.height(16.dp),
                        )

                        Text(
                            text = stringResource(
                                R.string.debug_occurrence_id,
                                occurrence.occurrenceId,
                            ),
                            style =
                                MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier.testTag(
                                    "debug_occurrence_id",
                                ),
                        )
                    }

                    state.errorMessage?.let {
                            errorMessage ->
                        Spacer(
                            modifier =
                                Modifier.height(16.dp),
                        )

                        Text(
                            text = errorMessage,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(24.dp),
                    )

                    if (
                        occurrence.reportState ==
                        CaregiverReportState.GIVEN
                    ) {
                        Text(
                            text = stringResource(
                                R.string.already_given,
                            ),
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            modifier =
                                Modifier.testTag(
                                    "given_status",
                                ),
                        )
                    } else {
                        Button(
                            onClick = onRecordGiven,
                            enabled = !state.isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(
                                    "record_given",
                                ),
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator()
                            } else {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.record_given,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val HOUR_MINUTE_FORMATTER =
    DateTimeFormatter.ofPattern("HH:mm")