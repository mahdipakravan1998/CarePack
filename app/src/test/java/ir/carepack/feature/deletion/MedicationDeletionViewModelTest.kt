package ir.carepack.feature.deletion

import ir.carepack.settings.deletion.MedicationDeletionCounts
import ir.carepack.settings.deletion.MedicationDeletionPreview
import ir.carepack.settings.deletion.MedicationDeletionPreviewResult
import ir.carepack.settings.deletion.MedicationDeletionResult
import ir.carepack.settings.deletion.MedicationDeletionStage
import ir.carepack.testing.QueueMedicationDeletionCoordinator
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
class MedicationDeletionViewModelTest {

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
    fun initialLoadShowsRealPreviewAndRequiresAcknowledgement() =
        runTest(dispatcher) {
            val coordinator =
                QueueMedicationDeletionCoordinator(
                    previewResults =
                        listOf(
                            MedicationDeletionPreviewResult
                                .Available(PREVIEW),
                        ),
                )

            val viewModel =
                MedicationDeletionViewModel(
                    medicationId =
                        PREVIEW.medicationId,
                    coordinator = coordinator,
                )

            advanceUntilIdle()

            val state =
                viewModel.state.value

            assertFalse(state.isLoading)
            assertEquals(PREVIEW, state.preview)
            assertFalse(state.acknowledged)
            assertFalse(state.canDelete)
            assertEquals(
                listOf(PREVIEW.medicationId),
                coordinator.previewMedicationIds,
            )
        }

    @Test
    fun deleteDoesNothingUntilAcknowledged() =
        runTest(dispatcher) {
            val coordinator =
                QueueMedicationDeletionCoordinator(
                    previewResults =
                        listOf(
                            MedicationDeletionPreviewResult
                                .Available(PREVIEW),
                        ),
                    deletionResults =
                        listOf(
                            MedicationDeletionResult
                                .Completed(COUNTS),
                        ),
                )

            val viewModel =
                MedicationDeletionViewModel(
                    medicationId =
                        PREVIEW.medicationId,
                    coordinator = coordinator,
                )

            advanceUntilIdle()
            viewModel.deleteMedication()
            advanceUntilIdle()

            assertTrue(
                coordinator.deletionPreviews.isEmpty(),
            )
            assertFalse(
                viewModel.state.value
                    .deletionCompleted,
            )
        }

    @Test
    fun acknowledgedDeletionCompletesAndDisablesRepeatedSubmission() =
        runTest(dispatcher) {
            val coordinator =
                QueueMedicationDeletionCoordinator(
                    previewResults =
                        listOf(
                            MedicationDeletionPreviewResult
                                .Available(PREVIEW),
                        ),
                    deletionResults =
                        listOf(
                            MedicationDeletionResult
                                .Completed(COUNTS),
                        ),
                )

            val viewModel =
                MedicationDeletionViewModel(
                    medicationId =
                        PREVIEW.medicationId,
                    coordinator = coordinator,
                )

            advanceUntilIdle()
            viewModel.setAcknowledged(true)
            assertTrue(viewModel.state.value.canDelete)

            viewModel.deleteMedication()
            viewModel.deleteMedication()
            advanceUntilIdle()

            assertEquals(
                listOf(PREVIEW),
                coordinator.deletionPreviews,
            )
            assertTrue(
                viewModel.state.value
                    .deletionCompleted,
            )
            assertFalse(
                viewModel.state.value
                    .isDeleting,
            )
            assertFalse(
                viewModel.state.value
                    .acknowledged,
            )
            assertFalse(viewModel.state.value.canDelete)
        }

    @Test
    fun changedSincePreviewRefreshesCountsAndRequiresAcknowledgementAgain() =
        runTest(dispatcher) {
            val refreshedPreview =
                PREVIEW.copy(
                    medicationUpdatedAtEpochMillis = 2L,
                    occurrenceCount = 8,
                    caregiverReportCount = 4,
                )

            val coordinator =
                QueueMedicationDeletionCoordinator(
                    previewResults =
                        listOf(
                            MedicationDeletionPreviewResult
                                .Available(PREVIEW),
                        ),
                    deletionResults =
                        listOf(
                            MedicationDeletionResult
                                .ChangedSincePreview(
                                    latestPreview =
                                        refreshedPreview,
                                ),
                        ),
                )

            val viewModel =
                MedicationDeletionViewModel(
                    medicationId =
                        PREVIEW.medicationId,
                    coordinator = coordinator,
                )

            advanceUntilIdle()
            viewModel.setAcknowledged(true)
            viewModel.deleteMedication()
            advanceUntilIdle()

            val state =
                viewModel.state.value

            assertEquals(refreshedPreview, state.preview)
            assertTrue(state.changedSincePreview)
            assertFalse(state.acknowledged)
            assertFalse(state.canDelete)
            assertFalse(state.deletionCompleted)
        }

    @Test
    fun failureBeforeDatabaseDeletionKeepsPreviewAndAcknowledgementForRetry() =
        runTest(dispatcher) {
            val coordinator =
                QueueMedicationDeletionCoordinator(
                    previewResults =
                        listOf(
                            MedicationDeletionPreviewResult
                                .Available(PREVIEW),
                        ),
                    deletionResults =
                        listOf(
                            MedicationDeletionResult.Failed(
                                stage =
                                    MedicationDeletionStage
                                        .CANCELLING_SCHEDULE_ALARMS,
                                databaseDeleted = false,
                            ),
                        ),
                )

            val viewModel =
                MedicationDeletionViewModel(
                    medicationId =
                        PREVIEW.medicationId,
                    coordinator = coordinator,
                )

            advanceUntilIdle()
            viewModel.setAcknowledged(true)
            viewModel.deleteMedication()
            advanceUntilIdle()

            val state =
                viewModel.state.value

            assertEquals(
                MedicationDeletionStage
                    .CANCELLING_SCHEDULE_ALARMS,
                state.deletionFailureStage,
            )
            assertFalse(state.databaseDeletedAfterFailure)
            assertTrue(state.acknowledged)
            assertTrue(state.canDelete)
        }

    @Test
    fun failureAfterDatabaseDeletionIsRepresentedSeparately() =
        runTest(dispatcher) {
            val coordinator =
                QueueMedicationDeletionCoordinator(
                    previewResults =
                        listOf(
                            MedicationDeletionPreviewResult
                                .Available(PREVIEW),
                        ),
                    deletionResults =
                        listOf(
                            MedicationDeletionResult.Failed(
                                stage =
                                    MedicationDeletionStage
                                        .RECONCILING_REMAINING_REMINDERS,
                                databaseDeleted = true,
                            ),
                        ),
                )

            val viewModel =
                MedicationDeletionViewModel(
                    medicationId =
                        PREVIEW.medicationId,
                    coordinator = coordinator,
                )

            advanceUntilIdle()
            viewModel.setAcknowledged(true)
            viewModel.deleteMedication()
            advanceUntilIdle()

            assertEquals(
                MedicationDeletionStage
                    .RECONCILING_REMAINING_REMINDERS,
                viewModel.state.value
                    .deletionFailureStage,
            )
            assertTrue(
                viewModel.state.value
                    .databaseDeletedAfterFailure,
            )
        }

    @Test
    fun missingMedicationProducesSafeNotFoundState() =
        runTest(dispatcher) {
            val viewModel =
                MedicationDeletionViewModel(
                    medicationId = "missing",
                    coordinator =
                        QueueMedicationDeletionCoordinator(
                            previewResults =
                                listOf(
                                    MedicationDeletionPreviewResult
                                        .NotFound,
                                ),
                        ),
                )

            advanceUntilIdle()

            val state =
                viewModel.state.value

            assertFalse(state.isLoading)
            assertTrue(state.medicationNotFound)
            assertNull(state.preview)
            assertFalse(state.canDelete)
        }

    @Test
    fun previewFailureCanBeRetried() =
        runTest(dispatcher) {
            val coordinator =
                QueueMedicationDeletionCoordinator(
                    previewResults =
                        listOf(
                            MedicationDeletionPreviewResult
                                .Failed(),
                            MedicationDeletionPreviewResult
                                .Available(PREVIEW),
                        ),
                )

            val viewModel =
                MedicationDeletionViewModel(
                    medicationId =
                        PREVIEW.medicationId,
                    coordinator = coordinator,
                )

            advanceUntilIdle()
            assertTrue(
                viewModel.state.value
                    .previewLoadFailed,
            )

            viewModel.loadPreview()
            advanceUntilIdle()

            assertFalse(
                viewModel.state.value
                    .previewLoadFailed,
            )
            assertEquals(PREVIEW, viewModel.state.value.preview)
        }

    @Test
    fun alreadyDeletedCompletesSafely() =
        runTest(dispatcher) {
            val viewModel =
                MedicationDeletionViewModel(
                    medicationId =
                        PREVIEW.medicationId,
                    coordinator =
                        QueueMedicationDeletionCoordinator(
                            previewResults =
                                listOf(
                                    MedicationDeletionPreviewResult
                                        .Available(PREVIEW),
                                ),
                            deletionResults =
                                listOf(
                                    MedicationDeletionResult
                                        .AlreadyDeleted,
                                ),
                        ),
                )

            advanceUntilIdle()
            viewModel.setAcknowledged(true)
            viewModel.deleteMedication()
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value
                    .deletionCompleted,
            )
            assertFalse(viewModel.state.value.canDelete)
        }

    private companion object {
        val PREVIEW =
            MedicationDeletionPreview(
                medicationId = "medication-1",
                medicationName = "داروی آزمون",
                medicationUpdatedAtEpochMillis = 1L,
                scheduleSeriesCount = 2,
                scheduleVersionCount = 3,
                scheduleTimeCount = 4,
                occurrenceCount = 5,
                caregiverReportCount = 2,
            )

        val COUNTS =
            MedicationDeletionCounts(
                caregiverReportCount = 2,
                occurrenceCount = 5,
                scheduleTimeCount = 4,
                scheduleVersionCount = 3,
                scheduleSeriesCount = 2,
                medicationCount = 1,
            )
    }
}
