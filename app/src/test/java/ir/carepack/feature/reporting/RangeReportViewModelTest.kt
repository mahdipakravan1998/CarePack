package ir.carepack.feature.reporting

import ir.carepack.core.time.ZoneProvider
import ir.carepack.data.preferences.PrivacyPreferenceState
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.report.RangeOccurrenceEntry
import ir.carepack.domain.report.RangeOccurrenceReportState
import ir.carepack.domain.report.RangeReportPeriod
import ir.carepack.reporting.share.CopyTextResult
import ir.carepack.reporting.share.ShareTextResult
import ir.carepack.testing.InMemoryPrivacyPreferenceStore
import ir.carepack.testing.InMemoryUserExperiencePreferenceStore
import ir.carepack.testing.RecordingRangeReportFormatter
import ir.carepack.testing.RecordingTextShareGateway
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RangeReportViewModelTest {

    private val dispatcher =
        StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_defaultsToSevenDaysAndBuildsReport() =
        runTest(dispatcher) {
            val formatter =
                RecordingRangeReportFormatter(
                    initialEntries =
                        listOf(entry()),
                )

            val viewModel =
                viewModel(formatter = formatter)

            advanceUntilIdle()

            val state =
                viewModel.state.value

            assertEquals(
                RangeReportPeriod.SEVEN_DAYS,
                state.period,
            )
            assertFalse(state.isLoading)
            assertEquals(1, state.summary?.totalOccurrenceCount)
            assertEquals(
                "SEVEN_DAYS|2026-06-24|false|1",
                state.reportText,
            )
            assertEquals(1, formatter.requests.size)
            assertEquals(
                RangeReportPeriod.SEVEN_DAYS,
                formatter.requests.single().period,
            )
        }

    @Test
    fun switchingToThirtyDaysReloadsExactlyThatPeriod() =
        runTest(dispatcher) {
            val formatter =
                RecordingRangeReportFormatter(
                    initialEntries =
                        listOf(entry()),
                )

            val viewModel =
                viewModel(formatter = formatter)

            advanceUntilIdle()
            viewModel.selectPeriod(
                RangeReportPeriod.THIRTY_DAYS,
            )
            advanceUntilIdle()

            assertEquals(
                RangeReportPeriod.THIRTY_DAYS,
                viewModel.state.value.period,
            )
            assertEquals(
                "THIRTY_DAYS|2026-06-24|false|1",
                viewModel.state.value.reportText,
            )
            assertEquals(
                RangeReportPeriod.THIRTY_DAYS,
                formatter.requests.last().period,
            )
        }

    @Test
    fun recipientNamePreferenceReloadsReportOnlyThroughExistingPrivacyStore() =
        runTest(dispatcher) {
            val formatter =
                RecordingRangeReportFormatter()

            val privacyStore =
                InMemoryPrivacyPreferenceStore()

            val viewModel =
                viewModel(
                    formatter = formatter,
                    privacyStore = privacyStore,
                )

            advanceUntilIdle()
            viewModel.setIncludeRecipientName(true)
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.includeRecipientName,
            )
            assertTrue(
                formatter.requests.last()
                    .includeRecipientName,
            )
            assertEquals(
                "SEVEN_DAYS|2026-06-24|true|0",
                viewModel.state.value.reportText,
            )
        }

    @Test
    fun copyOccursOnlyAfterExplicitAction() =
        runTest(dispatcher) {
            val shareGateway =
                RecordingTextShareGateway()

            val viewModel =
                viewModel(
                    formatter =
                        RecordingRangeReportFormatter(),
                    shareGateway = shareGateway,
                )

            advanceUntilIdle()

            assertTrue(shareGateway.copiedTexts.isEmpty())

            viewModel.copyReport()

            assertEquals(
                listOf(
                    "SEVEN_DAYS|2026-06-24|false|0",
                ),
                shareGateway.copiedTexts,
            )
            assertEquals(
                RangeReportActionMessage.COPIED,
                viewModel.state.value.actionMessage,
            )
            assertNull(viewModel.state.value.failure)
        }

    @Test
    fun copyFailureIsRepresentedWithoutSuccessMessage() =
        runTest(dispatcher) {
            val shareGateway =
                RecordingTextShareGateway(
                    copyResult =
                        CopyTextResult.Blocked,
                )

            val viewModel =
                viewModel(
                    formatter =
                        RecordingRangeReportFormatter(),
                    shareGateway = shareGateway,
                )

            advanceUntilIdle()
            viewModel.copyReport()

            assertEquals(
                RangeReportFailure.COPY_FAILED,
                viewModel.state.value.failure,
            )
            assertNull(
                viewModel.state.value.actionMessage,
            )
        }

    @Test
    fun shareOccursOnlyAfterExplicitActionAndUsesCurrentPreview() =
        runTest(dispatcher) {
            val shareGateway =
                RecordingTextShareGateway()

            val viewModel =
                viewModel(
                    formatter =
                        RecordingRangeReportFormatter(),
                    shareGateway = shareGateway,
                )

            advanceUntilIdle()

            assertTrue(shareGateway.sharedTexts.isEmpty())

            viewModel.shareReport()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "SEVEN_DAYS|2026-06-24|false|0",
                ),
                shareGateway.sharedTexts,
            )
            assertEquals(
                RangeReportActionMessage
                    .SHARE_CHOOSER_OPENED,
                viewModel.state.value.actionMessage,
            )
            assertFalse(viewModel.state.value.isSharing)
        }

    @Test
    fun noShareTargetIsRepresentedSeparately() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    formatter =
                        RecordingRangeReportFormatter(),
                    shareGateway =
                        RecordingTextShareGateway(
                            shareResult =
                                ShareTextResult
                                    .NoShareTarget,
                        ),
                )

            advanceUntilIdle()
            viewModel.shareReport()
            advanceUntilIdle()

            assertEquals(
                RangeReportFailure.NO_SHARE_TARGET,
                viewModel.state.value.failure,
            )
            assertNull(
                viewModel.state.value.actionMessage,
            )
        }

    @Test
    fun formatterFailureProducesEmptyErrorState() =
        runTest(dispatcher) {
            val formatter =
                RecordingRangeReportFormatter().apply {
                    failure =
                        IllegalStateException(
                            "Report load failed.",
                        )
                }

            val viewModel =
                viewModel(formatter = formatter)

            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(
                RangeReportFailure.LOAD_FAILED,
                viewModel.state.value.failure,
            )
            assertNull(viewModel.state.value.summary)
            assertEquals("", viewModel.state.value.reportText)
        }

    @Test
    fun simpleModeChangesPresentationStateWithoutChangingPeriodOrReport() =
        runTest(dispatcher) {
            val experienceStore =
                InMemoryUserExperiencePreferenceStore(
                    UserExperiencePreferenceState(
                        seniorMode = SeniorMode.SIMPLE,
                    ),
                )

            val viewModel =
                viewModel(
                    formatter =
                        RecordingRangeReportFormatter(),
                    experienceStore = experienceStore,
                )

            advanceUntilIdle()

            assertEquals(
                SeniorMode.SIMPLE,
                viewModel.state.value.seniorMode,
            )
            assertEquals(
                RangeReportPeriod.SEVEN_DAYS,
                viewModel.state.value.period,
            )
            assertEquals(
                "SEVEN_DAYS|2026-06-24|false|0",
                viewModel.state.value.reportText,
            )
        }

    @Test
    fun switchingBackToStandardRestoresPresentationOnly() =
        runTest(dispatcher) {
            val experienceStore =
                InMemoryUserExperiencePreferenceStore(
                    UserExperiencePreferenceState(
                        seniorMode = SeniorMode.SIMPLE,
                    ),
                )

            val viewModel =
                viewModel(
                    formatter =
                        RecordingRangeReportFormatter(),
                    experienceStore = experienceStore,
                )

            advanceUntilIdle()
            experienceStore.setSeniorMode(
                SeniorMode.STANDARD,
            )
            advanceUntilIdle()

            assertEquals(
                SeniorMode.STANDARD,
                viewModel.state.value.seniorMode,
            )
            assertEquals(
                RangeReportPeriod.SEVEN_DAYS,
                viewModel.state.value.period,
            )
        }

    @Test
    fun consumeActionMessageClearsOnlyTransientMessage() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    formatter =
                        RecordingRangeReportFormatter(),
                )

            advanceUntilIdle()
            viewModel.copyReport()
            assertEquals(
                RangeReportActionMessage.COPIED,
                viewModel.state.value.actionMessage,
            )

            viewModel.consumeActionMessage()

            assertNull(
                viewModel.state.value.actionMessage,
            )
            assertTrue(
                viewModel.state.value.reportText.isNotBlank(),
            )
        }

    private fun viewModel(
        formatter: RecordingRangeReportFormatter,
        privacyStore:
        InMemoryPrivacyPreferenceStore =
            InMemoryPrivacyPreferenceStore(
                PrivacyPreferenceState(
                    includeRecipientName = false,
                ),
            ),
        experienceStore:
        InMemoryUserExperiencePreferenceStore =
            InMemoryUserExperiencePreferenceStore(),
        shareGateway:
        RecordingTextShareGateway =
            RecordingTextShareGateway(),
    ): RangeReportViewModel =
        RangeReportViewModel(
            formatter = formatter,
            privacyPreferenceStore = privacyStore,
            userExperiencePreferenceStore =
                experienceStore,
            textShareGateway = shareGateway,
            clock =
                Clock.fixed(
                    NOW,
                    ZoneOffset.UTC,
                ),
            zoneProvider =
                ZoneProvider {
                    ZoneId.of("UTC")
                },
        )

    private fun entry(): RangeOccurrenceEntry =
        RangeOccurrenceEntry(
            occurrenceId = "occurrence-1",
            localDate = TODAY,
            localTime = LocalTime.of(8, 0),
            zoneIdSnapshot = "UTC",
            scheduledAt = NOW,
            medicationName = "داروی آزمون",
            instruction = "بعد از غذا",
            medicationType = "قرص",
            dosageText = "یک",
            doseUnit = "عدد",
            reportState =
                RangeOccurrenceReportState.GIVEN,
        )

    private companion object {
        val NOW: Instant =
            Instant.parse(
                "2026-06-24T08:00:00Z",
            )

        val TODAY: LocalDate =
            LocalDate.parse(
                "2026-06-24",
            )
    }
}
