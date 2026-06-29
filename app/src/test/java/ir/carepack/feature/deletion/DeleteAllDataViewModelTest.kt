package ir.carepack.feature.deletion

import ir.carepack.settings.deletion.DataDeletionCoordinator
import ir.carepack.settings.deletion.DataDeletionResult
import ir.carepack.settings.deletion.DataDeletionStage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAllDataViewModelTest {

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
    fun requestDeletion_requiresExplicitConfirmation() {
        val viewModel =
            DeleteAllDataViewModel(
                dataDeletionCoordinator =
                    DeferredDataDeletionCoordinator(),
            )

        assertFalse(
            viewModel
                .state
                .value
                .showConfirmation,
        )

        viewModel.requestDeletion()

        assertTrue(
            viewModel
                .state
                .value
                .showConfirmation,
        )

        viewModel.dismissConfirmation()

        assertFalse(
            viewModel
                .state
                .value
                .showConfirmation,
        )
    }

    @Test
    fun confirmation_doesNotReportSuccessBeforeCoordinatorCompletes() =
        runTest(dispatcher) {
            val coordinator =
                DeferredDataDeletionCoordinator()

            val viewModel =
                DeleteAllDataViewModel(
                    dataDeletionCoordinator =
                        coordinator,
                )

            viewModel.requestDeletion()
            viewModel.confirmDeletion()

            runCurrent()

            assertEquals(
                1,
                coordinator.deleteCount,
            )

            assertTrue(
                viewModel
                    .state
                    .value
                    .isDeleting,
            )

            assertFalse(
                viewModel
                    .state
                    .value
                    .deletionCompleted,
            )

            coordinator
                .deleteResult
                .complete(
                    DataDeletionResult.Completed,
                )

            advanceUntilIdle()

            assertFalse(
                viewModel
                    .state
                    .value
                    .isDeleting,
            )

            assertTrue(
                viewModel
                    .state
                    .value
                    .deletionCompleted,
            )
        }

    @Test
    fun failedDeletion_exposesStageAndRetryUsesRecoveryPath() =
        runTest(dispatcher) {
            val coordinator =
                DeferredDataDeletionCoordinator()

            coordinator
                .deleteResult
                .complete(
                    DataDeletionResult.Failed(
                        stage =
                            DataDeletionStage
                                .CLEARING_TEMPORARY_DATA,
                    ),
                )

            val viewModel =
                DeleteAllDataViewModel(
                    dataDeletionCoordinator =
                        coordinator,
                )

            viewModel.requestDeletion()
            viewModel.confirmDeletion()

            advanceUntilIdle()

            assertFalse(
                viewModel
                    .state
                    .value
                    .deletionCompleted,
            )

            assertEquals(
                DataDeletionStage
                    .CLEARING_TEMPORARY_DATA,
                viewModel
                    .state
                    .value
                    .failedStage,
            )

            viewModel.retryDeletion()

            runCurrent()

            assertEquals(
                1,
                coordinator.resumeCount,
            )

            assertTrue(
                viewModel
                    .state
                    .value
                    .isDeleting,
            )

            coordinator
                .resumeResult
                .complete(
                    DataDeletionResult.Completed,
                )

            advanceUntilIdle()

            assertTrue(
                viewModel
                    .state
                    .value
                    .deletionCompleted,
            )

            assertEquals(
                null,
                viewModel
                    .state
                    .value
                    .failedStage,
            )
        }
}

private class DeferredDataDeletionCoordinator :
    DataDeletionCoordinator {

    val deleteResult =
        CompletableDeferred<
                DataDeletionResult
                >()

    val resumeResult =
        CompletableDeferred<
                DataDeletionResult
                >()

    var deleteCount =
        0

    var resumeCount =
        0

    override suspend fun deleteEverything():
            DataDeletionResult {
        deleteCount += 1

        return deleteResult.await()
    }

    override suspend fun resumeIncompleteDeletionIfNeeded():
            DataDeletionResult {
        resumeCount += 1

        return resumeResult.await()
    }
}
