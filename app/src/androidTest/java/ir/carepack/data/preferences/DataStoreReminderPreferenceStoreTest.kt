package ir.carepack.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.domain.reminder.TimezoneObservation
import ir.carepack.domain.reminder.TimezoneWarning
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreReminderPreferenceStoreTest {

    private lateinit var context:
            Context

    private lateinit var store:
            DataStoreReminderPreferenceStore

    @Before
    fun setUp() =
        runBlocking {
            context =
                ApplicationProvider
                    .getApplicationContext()

            clearPreferences()

            store =
                DataStoreReminderPreferenceStore(
                    context = context,
                )
        }

    @After
    fun tearDown() =
        runBlocking {
            clearPreferences()
        }

    @Test
    fun remindersDefaultToDisabled_andPersistExplicitValue() =
        runBlocking {
            assertFalse(
                store
                    .state
                    .first()
                    .remindersEnabled,
            )

            store.setRemindersEnabled(
                enabled = true,
            )

            assertTrue(
                store
                    .state
                    .first()
                    .remindersEnabled,
            )

            val reopenedStore =
                DataStoreReminderPreferenceStore(
                    context = context,
                )

            assertTrue(
                reopenedStore
                    .state
                    .first()
                    .remindersEnabled,
            )
        }

    @Test
    fun firstObservedTimezone_initializesWithoutWarning() =
        runBlocking {
            val observation =
                store.observeDeviceZone(
                    zoneId =
                        "Asia/Tehran",
                )

            assertEquals(
                TimezoneObservation.Initialized,
                observation,
            )

            val persistedState =
                store.state.first()

            assertEquals(
                "Asia/Tehran",
                persistedState
                    .lastObservedZoneId,
            )

            assertNull(
                persistedState
                    .timezoneWarning,
            )
        }

    @Test
    fun changedTimezone_persistsWarningUntilDismissed() =
        runBlocking {
            store.observeDeviceZone(
                zoneId =
                    "Asia/Tehran",
            )

            val expectedWarning =
                TimezoneWarning(
                    previousZoneId =
                        "Asia/Tehran",
                    currentZoneId =
                        "Europe/Berlin",
                )

            val observation =
                store.observeDeviceZone(
                    zoneId =
                        "Europe/Berlin",
                )

            assertEquals(
                TimezoneObservation
                    .Changed(
                        expectedWarning,
                    ),
                observation,
            )

            val changedState =
                store.state.first()

            assertEquals(
                "Europe/Berlin",
                changedState
                    .lastObservedZoneId,
            )

            assertEquals(
                expectedWarning,
                changedState
                    .timezoneWarning,
            )

            store.dismissTimezoneWarning()

            val dismissedState =
                store.state.first()

            assertEquals(
                "Europe/Berlin",
                dismissedState
                    .lastObservedZoneId,
            )

            assertNull(
                dismissedState
                    .timezoneWarning,
            )
        }

    @Test
    fun observingSameTimezone_doesNotCreateWarning() =
        runBlocking {
            store.observeDeviceZone(
                zoneId =
                    "Asia/Tehran",
            )

            val observation =
                store.observeDeviceZone(
                    zoneId =
                        "Asia/Tehran",
                )

            assertEquals(
                TimezoneObservation.Unchanged,
                observation,
            )

            assertNull(
                store
                    .state
                    .first()
                    .timezoneWarning,
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
