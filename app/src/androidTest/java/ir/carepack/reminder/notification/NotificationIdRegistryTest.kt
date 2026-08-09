package ir.carepack.reminder.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIdRegistryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearRegistry()
    }

    @After
    fun tearDown() {
        clearRegistry()
    }

    @Test
    fun injectedCollision_allocatesDistinctStableIds() {
        val collidingSource =
            NotificationIdCandidateSource { _, _ -> 42 }

        val registry =
            PersistentNotificationIdRegistry(
                context = context,
                candidateSource = collidingSource,
            )

        val first = registry.idFor("reminder", "occurrence-1")
        val second = registry.idFor("reminder", "occurrence-2")

        assertEquals(42, first)
        assertEquals(43, second)
        assertNotEquals(first, second)
        assertEquals(
            first,
            registry.idFor("reminder", "occurrence-1"),
        )
        assertEquals(
            second,
            registry.idFor("reminder", "occurrence-2"),
        )
    }

    @Test
    fun mapping_survivesRegistryRecreationAndSeparatesNamespaces() {
        val collidingSource =
            NotificationIdCandidateSource { _, _ -> 77 }

        val firstRegistry =
            PersistentNotificationIdRegistry(
                context = context,
                candidateSource = collidingSource,
            )

        val reminderId =
            firstRegistry.idFor("reminder", "stable-key")
        val testId =
            firstRegistry.idFor("test-reminder", "stable-key")

        val recreated =
            PersistentNotificationIdRegistry(
                context = context,
                candidateSource = collidingSource,
            )

        assertNotEquals(reminderId, testId)
        assertEquals(
            reminderId,
            recreated.idFor("reminder", "stable-key"),
        )
        assertEquals(
            testId,
            recreated.idFor("test-reminder", "stable-key"),
        )
    }

    private fun clearRegistry() {
        context
            .getSharedPreferences(
                "carepack_notification_ids",
                Context.MODE_PRIVATE,
            )
            .edit()
            .clear()
            .commit()
    }
}
