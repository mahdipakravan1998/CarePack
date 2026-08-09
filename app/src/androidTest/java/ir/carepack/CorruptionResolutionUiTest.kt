package ir.carepack

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ir.carepack.data.preferences.MedicationDeletionPreferenceKeys
import ir.carepack.data.preferences.carePackDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CorruptionResolutionUiTest {

    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context.carePackDataStore.edit { preferences ->
            preferences.clear()
            preferences[MedicationDeletionPreferenceKeys.version] = 1
            preferences[MedicationDeletionPreferenceKeys.medicationId] =
                "corrupted-target"
        }
    }

    @After
    fun tearDown() = runBlocking {
        context.carePackDataStore.edit { it.clear() }
    }

    @Test
    fun corruptedMarkerBlocksOrdinaryRetryAndRequiresDoubleConfirmedStorageResetPath() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val corruptionTitle =
                context.getString(R.string.startup_recovery_corruption_title)
            val resetAction =
                context.getString(R.string.startup_recovery_reset_action)
            val retry = context.getString(R.string.retry_action)

            assertNotNull(
                device.wait(
                    Until.findObject(By.text(corruptionTitle)),
                    TIMEOUT_MILLIS,
                ),
            )
            assertNull(device.findObject(By.text(retry)))

            val reset =
                device.wait(
                    Until.findObject(By.text(resetAction)),
                    TIMEOUT_MILLIS,
                )
            assertNotNull(reset)
            reset.click()

            val firstTitle =
                context.getString(R.string.startup_recovery_reset_confirm_title)
            assertNotNull(
                device.wait(
                    Until.findObject(By.text(firstTitle)),
                    TIMEOUT_MILLIS,
                ),
            )

            val continueAction = context.getString(R.string.continue_action)
            val continueButton =
                device.wait(
                    Until.findObject(By.text(continueAction)),
                    TIMEOUT_MILLIS,
                )
            assertNotNull(continueButton)
            continueButton.click()

            val finalTitle =
                context.getString(R.string.startup_recovery_reset_final_title)
            assertNotNull(
                device.wait(
                    Until.findObject(By.text(finalTitle)),
                    TIMEOUT_MILLIS,
                ),
            )
            assertNull(device.findObject(By.text(retry)))
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
