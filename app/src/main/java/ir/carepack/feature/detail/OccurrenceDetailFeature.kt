package ir.carepack.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.carepack.R
import ir.carepack.domain.model.CaregiverReportState
import ir.carepack.ui.accessibility.carePackHeading
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion
import ir.carepack.ui.accessibility.carePackInteractiveControl
import ir.carepack.ui.experience.carePackExperience



@Composable
fun OccurrenceDetailRoute(
    viewModel: OccurrenceDetailViewModel,
    onBack: () -> Unit,
    entryMode: OccurrenceDetailEntryMode = OccurrenceDetailEntryMode.NORMAL,
) {
    val state by
    viewModel.state
        .collectAsStateWithLifecycle()

    OccurrenceDetailScreen(
        state = state,
        entryMode = entryMode,
        onBack = onBack,
        onGiven = {
            viewModel.setReport(
                CaregiverReportState.GIVEN,
            )
        },
        onNotGiven = {
            viewModel.setReport(
                CaregiverReportState.NOT_GIVEN,
            )
        },
        onUnknown = {
            viewModel.setReport(
                CaregiverReportState.UNKNOWN,
            )
        },
        onRemindLater = viewModel::remindLater,
        onUndo = viewModel::undoReportChange,
        onSnackbarConsumed = viewModel::consumeSnackbar,
    )
}

@Composable
fun OccurrenceDetailScreen(
    state: OccurrenceDetailUiState,
    entryMode: OccurrenceDetailEntryMode = OccurrenceDetailEntryMode.NORMAL,
    onBack: () -> Unit,
    onGiven: () -> Unit,
    onNotGiven: () -> Unit,
    onUnknown: () -> Unit,
    onRemindLater: () -> Unit = {},
    onUndo: () -> Unit,
    onSnackbarConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val experience = carePackExperience()

    Scaffold(
        modifier = modifier
                .fillMaxSize().testTag(
                    "occurrence_detail_screen",
                ),
    ) { contentPadding ->
        Box(
            modifier = Modifier
                    .fillMaxSize().padding(contentPadding),
        ) {
            Column(
                modifier = Modifier
                        .fillMaxSize().imePadding()
                        .navigationBarsPadding().verticalScroll(
                            rememberScrollState(),
                        ).padding(
                            horizontal = experience.screenHorizontalPadding,
                            vertical = experience.screenVerticalPadding,
                        ),
                verticalArrangement = Arrangement.spacedBy(
                        experience.itemSpacing,
                    ),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier
                            .carePackInteractiveControl().testTag(
                                "occurrence_detail_back",
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
                            if (
                                entryMode == OccurrenceDetailEntryMode.REMINDER
                            ) {
                                R.string.reminder_action_title
                            } else {
                                R.string.detail_title
                            },
                        ),
                    style = MaterialTheme
                            .typography.headlineMedium,
                    modifier = Modifier
                            .carePackHeading().testTag(
                                "occurrence_detail_title",
                            ),
                )

                when {
                    state.isLoading -> {
                        LoadingContent()
                    }

                    state.errorMessage != null -> {
                        Text(
                            text = state.errorMessage,
                            color = MaterialTheme
                                    .colorScheme.error,
                            modifier = Modifier
                                    .carePackPoliteLiveRegion().testTag(
                                        "occurrence_detail_error",
                                    ),
                        )
                    }

                    state.detail != null -> {
                        OccurrenceDetailContent(
                            detail = state.detail,
                            entryMode = entryMode,
                            onGiven = onGiven,
                            onNotGiven = onNotGiven,
                            onUnknown = onUnknown,
                            onRemindLater = onRemindLater,
                        )
                    }
                }
            }

            if (state.snackbarMessage != null) {
                Snackbar(
                    modifier = Modifier
                            .align(
                                Alignment.BottomCenter,
                            ).navigationBarsPadding()
                            .padding(
                                experience.compactSpacing,
                            ).carePackPoliteLiveRegion()
                            .testTag(
                                "occurrence_detail_snackbar",
                            ),
                    action = {
                        if (state.undoChange != null) {
                            TextButton(
                                onClick = onUndo,
                                modifier = Modifier
                                        .carePackInteractiveControl().testTag(
                                            "occurrence_detail_undo",
                                        ),
                            ) {
                                Text(
                                    text = stringResource(
                                            R.string.undo,
                                        ),
                                )
                            }
                        } else {
                            TextButton(
                                onClick = onSnackbarConsumed,
                                modifier = Modifier
                                        .carePackInteractiveControl().testTag(
                                            "occurrence_detail_snackbar_dismiss",
                                        ),
                            ) {
                                Text(
                                    text = stringResource(
                                            R.string.dismiss_for_later,
                                        ),
                                )
                            }
                        }
                    },
                ) {
                    Text(
                        text = state.snackbarMessage,
                    )
                }
            }
        }
    }
}
