package ir.carepack.feature.reporting

import ir.carepack.data.preferences.PrivacyPreferenceState
import ir.carepack.data.preferences.PrivacyPreferenceStore
import ir.carepack.domain.report.TodayReportFormatter
import ir.carepack.domain.report.TodayReportText
import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.reporting.share.TextShareGateway
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayReportViewModelTest {

    private val dispatcher =
        StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(
            dispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialPreference_formatsReportWithoutRecipientName() =
        runTest(dispatcher) {
            val formatter =
                RecordingTodayReportFormatter()

            val viewModel =
                TodayReportViewModel(
                    date = REPORT_DATE,
                    formatter = formatter,
                    privacyPreferenceStore =
                        ViewModelPrivacyPreferenceStore(),
                    textShareGateway =
                        RecordingViewModelTextShareGateway(),
                )

            advanceUntilIdle()

            assertFalse(
                viewModel
                    .state
                    .value
                    .isLoading,
            )

            assertFalse(
                viewModel
                    .state
                    .value
                    .includeRecipientName,
            )

            assertEquals(
                "report-without-name",
                viewModel
                    .state
                    .value
                    .reportText,
            )

            assertEquals(
                listOf(
                    ReportRequest(
                        date = REPORT_DATE,
                        includeRecipientName =
                            false,
                    ),
                ),
                formatter.requests,
            )
        }

    @Test
    fun changingNamePreference_reformatsExactPreview() =
        runTest(dispatcher) {
            val formatter =
                RecordingTodayReportFormatter()

            val preferenceStore =
                ViewModelPrivacyPreferenceStore()

            val viewModel =
                TodayReportViewModel(
                    date = REPORT_DATE,
                    formatter = formatter,
                    privacyPreferenceStore =
                        preferenceStore,
                    textShareGateway =
                        RecordingViewModelTextShareGateway(),
                )

            advanceUntilIdle()

            viewModel.setIncludeRecipientName(
                includeRecipientName =
                    true,
            )

            advanceUntilIdle()

            assertTrue(
                viewModel
                    .state
                    .value
                    .includeRecipientName,
            )

            assertEquals(
                "report-with-name",
                viewModel
                    .state
                    .value
                    .reportText,
            )

            assertEquals(
                listOf(
                    false,
                    true,
                ),
                formatter
                    .requests
                    .map {
                        it.includeRecipientName
                    },
            )
        }

    @Test
    fun copyAndShare_useExactlyThePreviewText() =
        runTest(dispatcher) {
            val shareGateway =
                RecordingViewModelTextShareGateway()

            val viewModel =
                TodayReportViewModel(
                    date = REPORT_DATE,
                    formatter =
                        RecordingTodayReportFormatter(),
                    privacyPreferenceStore =
                        ViewModelPrivacyPreferenceStore(),
                    textShareGateway =
                        shareGateway,
                )

            advanceUntilIdle()

            val preview =
                viewModel
                    .state
                    .value
                    .reportText

            viewModel.copyReport()

            assertEquals(
                listOf(preview),
                shareGateway.copiedTexts,
            )

            assertEquals(
                TodayReportActionMessage.COPIED,
                viewModel
                    .state
                    .value
                    .actionMessage,
            )

            viewModel.consumeActionMessage()

            viewModel.shareReport()

            advanceUntilIdle()

            assertEquals(
                listOf(preview),
                shareGateway.sharedTexts,
            )

            assertEquals(
                TodayReportActionMessage.SHARE_CHOOSER_OPENED,
                viewModel
                    .state
                    .value
                    .actionMessage,
            )
        }

    @Test
    fun formatterFailure_exposesRecoverableErrorWithoutPreview() =
        runTest(dispatcher) {
            val formatter =
                RecordingTodayReportFormatter(
                    failure =
                        IOException(
                            "Read failed.",
                        ),
                )

            val viewModel =
                TodayReportViewModel(
                    date = REPORT_DATE,
                    formatter = formatter,
                    privacyPreferenceStore =
                        ViewModelPrivacyPreferenceStore(),
                    textShareGateway =
                        RecordingViewModelTextShareGateway(),
                )

            advanceUntilIdle()

            assertFalse(
                viewModel
                    .state
                    .value
                    .isLoading,
            )

            assertTrue(
                viewModel
                    .state
                    .value
                    .reportText
                    .isEmpty(),
            )

            assertNotNull(
                viewModel
                    .state
                    .value
                    .errorMessage,
            )
        }

    private companion object {

        val REPORT_DATE:
                LocalDate =
            LocalDate.of(
                2026,
                6,
                24,
            )
    }
}

private data class ReportRequest(
    val date: LocalDate,
    val includeRecipientName:
    Boolean,
)

private class RecordingTodayReportFormatter(
    private val failure:
    Throwable? = null,
) : TodayReportFormatter {

    val requests =
        mutableListOf<ReportRequest>()

    override suspend fun createTodayReport(
        date: LocalDate,
        includeRecipientName: Boolean,
    ): TodayReportText {
        requests +=
            ReportRequest(
                date = date,
                includeRecipientName =
                    includeRecipientName,
            )

        failure?.let {
            throw it
        }

        return TodayReportText(
            value =
                if (includeRecipientName) {
                    "report-with-name"
                } else {
                    "report-without-name"
                },
        )
    }
}

private class ViewModelPrivacyPreferenceStore(
    initialState:
    PrivacyPreferenceState =
        PrivacyPreferenceState(),
) : PrivacyPreferenceStore {

    private val mutableState =
        MutableStateFlow(
            initialState,
        )

    override val state:
            Flow<PrivacyPreferenceState> =
        mutableState

    override suspend fun setIncludeRecipientName(
        includeRecipientName: Boolean,
    ) {
        mutableState.value =
            mutableState
                .value
                .copy(
                    includeRecipientName =
                        includeRecipientName,
                )
    }

    override suspend fun markDeletionInProgress() {
        mutableState.value =
            mutableState
                .value
                .copy(
                    deletionInProgress = true,
                )
    }

    override suspend fun clearAllPreservingDeletionMarker() {
        mutableState.value =
            PrivacyPreferenceState(
                includeRecipientName = false,
                deletionInProgress = true,
            )
    }

    override suspend fun completeDeletion() {
        mutableState.value =
            PrivacyPreferenceState()
    }
}

private class RecordingViewModelTextShareGateway :
    TextShareGateway {

    val copiedTexts =
        mutableListOf<String>()

    val sharedTexts =
        mutableListOf<String>()

    override fun share(
        text: String,
    ): ShareTextResult {
        sharedTexts +=
            text

        return ShareTextResult.ChooserOpened
    }

    override fun copy(
        text: String,
    ): CopyTextResult {
        copiedTexts +=
            text

        return CopyTextResult.Copied
    }
}
