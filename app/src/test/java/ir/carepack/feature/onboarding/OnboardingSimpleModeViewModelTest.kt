package ir.carepack.feature.onboarding

import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingSimpleModeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun failedSimpleSelection_keepsPersistedStandardModeAndRetriesSameSelection() =
        runTest(dispatcher) {
            val store = RecordingExperienceStore(
                initialMode = SeniorMode.STANDARD,
                failure = IOException("write failed"),
            )
            val viewModel = OnboardingSimpleModeViewModel(store)

            viewModel.selectSimpleMode()
            advanceUntilIdle()

            assertEquals(SeniorMode.STANDARD, viewModel.state.value.preferenceState.seniorMode)
            assertFalse(viewModel.state.value.isSaving)
            assertNotNull(viewModel.state.value.errorMessage)
            assertEquals(SeniorMode.SIMPLE, viewModel.state.value.retrySelection)

            store.failure = null
            viewModel.retryLastSelection()
            advanceUntilIdle()

            assertEquals(SeniorMode.SIMPLE, viewModel.state.value.preferenceState.seniorMode)
            assertFalse(viewModel.state.value.isSaving)
            assertNull(viewModel.state.value.errorMessage)
            assertNull(viewModel.state.value.retrySelection)
            assertEquals(listOf(SeniorMode.SIMPLE, SeniorMode.SIMPLE), store.requests)
        }

    @Test
    fun failedStandardSelection_keepsPersistedSimpleModeAndRetriesSameSelection() =
        runTest(dispatcher) {
            val store = RecordingExperienceStore(
                initialMode = SeniorMode.SIMPLE,
                failure = IOException("write failed"),
            )
            val viewModel = OnboardingSimpleModeViewModel(store)

            viewModel.keepStandardMode()
            advanceUntilIdle()

            assertEquals(SeniorMode.SIMPLE, viewModel.state.value.preferenceState.seniorMode)
            assertFalse(viewModel.state.value.isSaving)
            assertNotNull(viewModel.state.value.errorMessage)
            assertEquals(SeniorMode.STANDARD, viewModel.state.value.retrySelection)

            store.failure = null
            viewModel.retryLastSelection()
            advanceUntilIdle()

            assertEquals(SeniorMode.STANDARD, viewModel.state.value.preferenceState.seniorMode)
            assertFalse(viewModel.state.value.isSaving)
            assertNull(viewModel.state.value.errorMessage)
            assertEquals(
                listOf(SeniorMode.STANDARD, SeniorMode.STANDARD),
                store.requests,
            )
        }

    @Test
    fun saveInProgress_ignoresDuplicateModeRequests() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val store = RecordingExperienceStore(
            initialMode = SeniorMode.STANDARD,
            completion = completion,
        )
        val viewModel = OnboardingSimpleModeViewModel(store)

        viewModel.selectSimpleMode()
        runCurrent()
        viewModel.selectSimpleMode()
        runCurrent()

        assertTrue(viewModel.state.value.isSaving)
        assertEquals(listOf(SeniorMode.SIMPLE), store.requests)

        completion.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSaving)
        assertEquals(SeniorMode.SIMPLE, viewModel.state.value.preferenceState.seniorMode)
    }
}

private class RecordingExperienceStore(
    initialMode: SeniorMode,
    var failure: Throwable? = null,
    private val completion: CompletableDeferred<Unit>? = null,
) : UserExperiencePreferenceStore {

    private val mutableState = MutableStateFlow(
        UserExperiencePreferenceState(seniorMode = initialMode),
    )

    override val state: Flow<UserExperiencePreferenceState> = mutableState

    val requests = mutableListOf<SeniorMode>()

    override suspend fun setFirstDayOfWeekPreference(
        preference: FirstDayOfWeekPreference,
    ) = Unit

    override suspend fun setSeniorMode(seniorMode: SeniorMode) {
        requests += seniorMode
        completion?.await()
        failure?.let { throw it }
        mutableState.value = mutableState.value.copy(seniorMode = seniorMode)
    }
}
