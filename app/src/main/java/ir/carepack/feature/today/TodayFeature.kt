package ir.carepack.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.carepack.R
import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.domain.model.TodayItem
import ir.carepack.domain.today.TodayQueryService
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class TodayUiState(
    val localDate: LocalDate,
    val isLoading: Boolean = true,
    val items: List<TodayItem> = emptyList(),
    val errorMessage: String? = null,
)

class TodayViewModel(
    todayQueryService: TodayQueryService,
    clock: Clock,
    zoneProvider: ZoneProvider,
) : ViewModel() {

    private val localDate =
        clock
            .instant()
            .atZone(
                zoneProvider.currentZone(),
            )
            .toLocalDate()

    private val mutableState =
        MutableStateFlow(
            TodayUiState(
                localDate = localDate,
            ),
        )

    val state = mutableState

    init {
        todayQueryService
            .observeToday(localDate)
            .onEach { items ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        items = items,
                        errorMessage = null,
                    )
                }
            }
            .catch {
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage =
                            "خواندن اطلاعات امروز انجام نشد.",
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    companion object {
        fun factory(
            todayQueryService: TodayQueryService,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    TodayViewModel(
                        todayQueryService =
                            todayQueryService,
                        clock = clock,
                        zoneProvider =
                            zoneProvider,
                    )
                }
            }
        }
    }
}

@Composable
fun TodayRoute(
    viewModel: TodayViewModel,
    onOccurrenceSelected: (String) -> Unit,
) {
    val state by
    viewModel
        .state
        .collectAsStateWithLifecycle()

    TodayScreen(
        state = state,
        onOccurrenceSelected =
            onOccurrenceSelected,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onOccurrenceSelected: (String) -> Unit,
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
        ) {
            Text(
                text = stringResource(
                    R.string.today_title,
                ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp),
            )

            Text(
                text = state.localDate.toString(),
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp),
            )

            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.testTag(
                                "today_loading",
                            ),
                    )
                }

                state.errorMessage != null -> {
                    Text(
                        text =
                            state.errorMessage,
                        color =
                            MaterialTheme
                                .colorScheme
                                .error,
                        modifier =
                            Modifier.testTag(
                                "today_error",
                            ),
                    )
                }

                state.items.isEmpty() -> {
                    Text(
                        text = stringResource(
                            R.string.today_empty_title,
                        ),
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp),
                    )

                    Text(
                        text = stringResource(
                            R.string.today_empty_body,
                        ),
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                12.dp,
                            ),
                        modifier =
                            Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.items,
                            key = { item ->
                                item.occurrenceId
                            },
                        ) { item ->
                            TodayOccurrenceCard(
                                item = item,
                                onClick = {
                                    onOccurrenceSelected(
                                        item.occurrenceId,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayOccurrenceCard(
    item: TodayItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
            )
            .testTag(
                "today_item_${item.occurrenceId}",
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(20.dp),
        ) {
            Text(
                text =
                    item.medicationName,
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    item.localTime.format(
                        HOUR_MINUTE_FORMATTER,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp),
            )

            Text(
                text =
                    when (item.reportState) {
                        CaregiverReportState.GIVEN -> {
                            stringResource(
                                R.string.report_given,
                            )
                        }

                        else -> {
                            stringResource(
                                R.string.report_not_recorded,
                            )
                        }
                    },
                color =
                    when (item.reportState) {
                        CaregiverReportState.GIVEN -> {
                            MaterialTheme
                                .colorScheme
                                .primary
                        }

                        else -> {
                            MaterialTheme
                                .colorScheme
                                .onSurface
                        }
                    },
            )
        }
    }
}

private val HOUR_MINUTE_FORMATTER =
    DateTimeFormatter.ofPattern("HH:mm")
