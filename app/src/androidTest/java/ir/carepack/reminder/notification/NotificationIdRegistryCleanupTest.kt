package ir.carepack.reminder.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIdRegistryCleanupTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(
            "carepack_notification_ids",
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(
            "carepack_notification_ids",
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    @Test
    fun targetForgetPreservesOtherMedicationMappingAndClearAllRemovesEverything() {
        val registry = PersistentNotificationIdRegistry(context)
        val first = registry.idFor("occurrence", "medication-a-occurrence")
        val second = registry.idFor("occurrence", "medication-b-occurrence")

        assertEquals(
            first,
            registry.findExistingId(
                "occurrence",
                "medication-a-occurrence",
            ),
        )
        assertEquals(
            second,
            registry.findExistingId(
                "occurrence",
                "medication-b-occurrence",
            ),
        )

        registry.forget(
            namespace = "occurrence",
            stableKey = "medication-a-occurrence",
        )

        assertNull(
            registry.findExistingId(
                "occurrence",
                "medication-a-occurrence",
            ),
        )
        assertEquals(
            second,
            registry.findExistingId(
                "occurrence",
                "medication-b-occurrence",
            ),
        )

        registry.clearAll()

        assertNull(
            registry.findExistingId(
                "occurrence",
                "medication-b-occurrence",
            ),
        )
    }

    @Test
    fun cancellingUnmappedOccurrenceDoesNotAllocateNotificationId() {
        val registry = NoAllocationRegistry()
        val gateway =
            AndroidNotificationGateway(
                context = context,
                clock = Clock.systemUTC(),
                idRegistry = registry,
            )

        gateway.cancel("unmapped-occurrence")

        assertEquals(0, registry.idForCalls)
        assertEquals(1, registry.findCalls)
    }

    private class NoAllocationRegistry : NotificationIdRegistry {
        var idForCalls = 0
        var findCalls = 0

        override fun idFor(
            namespace: String,
            stableKey: String,
        ): Int {
            idForCalls += 1
            error("Cancellation of an unmapped occurrence must not allocate an ID.")
        }

        override fun findExistingId(
            namespace: String,
            stableKey: String,
        ): Int? {
            findCalls += 1
            return null
        }

        override fun forget(
            namespace: String,
            stableKey: String,
        ): Boolean = false

        override fun clearAll() = Unit
    }
}
