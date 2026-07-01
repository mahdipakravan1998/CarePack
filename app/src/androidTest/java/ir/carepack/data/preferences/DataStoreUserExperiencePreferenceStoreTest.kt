package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.calendar.FirstDayOfWeekPreference
import ir.carepack.domain.experience.SeniorMode
import ir.carepack.domain.experience.UserExperiencePreferenceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreUserExperiencePreferenceStoreTest {

    private lateinit var context:
            Context

    private lateinit var store:
            DataStoreUserExperiencePreferenceStore

    @Before
    fun setUp() =
        runBlocking {
            context =
                ApplicationProvider
                    .getApplicationContext()

            clearPreferences()

            store =
                DataStoreUserExperiencePreferenceStore(
                    context = context,
                )
        }

    @After
    fun tearDown() =
        runBlocking {
            clearPreferences()
        }

    @Test
    fun defaultState_usesSystemWeekStartAndStandardMode() =
        runBlocking {
            assertEquals(
                UserExperiencePreferenceState(),
                store.state.first(),
            )
        }

    @Test
    fun firstDayOfWeekPreference_isPersisted() =
        runBlocking {
            store.setFirstDayOfWeekPreference(
                FirstDayOfWeekPreference.SATURDAY,
            )

            assertEquals(
                FirstDayOfWeekPreference.SATURDAY,
                store
                    .state
                    .first()
                    .firstDayOfWeekPreference,
            )

            val reopenedStore =
                DataStoreUserExperiencePreferenceStore(
                    context = context,
                )

            assertEquals(
                FirstDayOfWeekPreference.SATURDAY,
                reopenedStore
                    .state
                    .first()
                    .firstDayOfWeekPreference,
            )
        }

    @Test
    fun seniorMode_isPersisted() =
        runBlocking {
            store.setSeniorMode(
                SeniorMode.SIMPLE,
            )

            assertEquals(
                SeniorMode.SIMPLE,
                store
                    .state
                    .first()
                    .seniorMode,
            )

            val reopenedStore =
                DataStoreUserExperiencePreferenceStore(
                    context = context,
                )

            assertEquals(
                SeniorMode.SIMPLE,
                reopenedStore
                    .state
                    .first()
                    .seniorMode,
            )
        }

    @Test
    fun preferencesCanReturnToDefaults() =
        runBlocking {
            store.setFirstDayOfWeekPreference(
                FirstDayOfWeekPreference.MONDAY,
            )

            store.setSeniorMode(
                SeniorMode.SIMPLE,
            )

            store.setFirstDayOfWeekPreference(
                FirstDayOfWeekPreference.SYSTEM_DEFAULT,
            )

            store.setSeniorMode(
                SeniorMode.STANDARD,
            )

            assertEquals(
                UserExperiencePreferenceState(),
                store.state.first(),
            )
        }

    private suspend fun clearPreferences() {
        context
            .carePackDataStore
            .edit { preferences ->
                preferences.clear()
            }
    }
}
