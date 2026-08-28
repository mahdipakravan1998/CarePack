package ir.carepack.feature.settings

import ir.carepack.core.time.ZoneProvider
import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import ir.carepack.domain.experience.UserExperiencePreferenceStore
import java.io.IOException
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun failedSettingsPreferenceWrites_keepPersistedValuesAuthoritativeAndShowError() =
        runTest(dispatcher) {
            val store = FailingSettingsExperienceStore()
            val viewModel = SettingsViewModel(
                userExperiencePreferenceStore = store,
                zoneProvider = ZoneProvider { ZoneOffset.UTC },
                appVersion = "test",
            )
            backgroundScope.launch { viewModel.state.collect {} }
            advanceUntilIdle()

            viewModel.setSeniorMode(SeniorMode.SIMPLE)
            advanceUntilIdle()

            assertEquals(SeniorMode.STANDARD, viewModel.state.value.preferenceState.seniorMode)
            assertFalse(viewModel.state.value.isSaving)
            assertNotNull(viewModel.state.value.errorMessage)

            store.failure = null
            viewModel.setFirstDayOfWeekPreference(FirstDayOfWeekPreference.SATURDAY)
            advanceUntilIdle()

            assertEquals(FirstDayOfWeekPreference.SATURDAY, viewModel.state.value.preferenceState.firstDayOfWeekPreference)
            assertFalse(viewModel.state.value.isSaving)
        }
}

private class FailingSettingsExperienceStore : UserExperiencePreferenceStore {
    private val mutableState = MutableStateFlow(UserExperiencePreferenceState())
    override val state: Flow<UserExperiencePreferenceState> = mutableState
    var failure: Throwable? = IOException("injected preference failure")

    override suspend fun setFirstDayOfWeekPreference(preference: FirstDayOfWeekPreference) {
        failure?.let { throw it }
        mutableState.value = mutableState.value.copy(firstDayOfWeekPreference = preference)
    }

    override suspend fun setSeniorMode(seniorMode: SeniorMode) {
        failure?.let { throw it }
        mutableState.value = mutableState.value.copy(seniorMode = seniorMode)
    }
}
