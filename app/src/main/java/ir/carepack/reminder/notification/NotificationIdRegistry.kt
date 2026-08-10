package ir.carepack.reminder.notification

import android.content.Context
import java.nio.ByteBuffer
import java.security.MessageDigest

fun interface NotificationIdCandidateSource {
    fun candidate(
        namespace: String,
        stableKey: String,
    ): Int
}

class Sha256NotificationIdCandidateSource : NotificationIdCandidateSource {

    override fun candidate(
        namespace: String,
        stableKey: String,
    ): Int {
        val digest = MessageDigest
                .getInstance("SHA-256").digest(
                    "$namespace\u001f$stableKey".toByteArray(Charsets.UTF_8),
                )

        return ByteBuffer.wrap(digest, 0, Int.SIZE_BYTES)
            .int and ID_MASK
    }

    private companion object {
        const val ID_MASK = 0x3fffffff
    }
}

interface NotificationIdRegistry {
    fun idFor(
        namespace: String,
        stableKey: String,
    ): Int

    fun findExistingId(
        namespace: String,
        stableKey: String,
    ): Int?

    fun forget(
        namespace: String,
        stableKey: String,
    ): Boolean

    fun clearAll()
}

class PersistentNotificationIdRegistry(
    context: Context,
    private val candidateSource: NotificationIdCandidateSource =
        Sha256NotificationIdCandidateSource(),
) : NotificationIdRegistry {

    private val preferences = context.applicationContext
            .getSharedPreferences(
                PREFERENCE_FILE,
                Context.MODE_PRIVATE,
            )

    override fun idFor(
        namespace: String,
        stableKey: String,
    ): Int = synchronized(this) {
            require(namespace.isNotBlank())
            require(stableKey.isNotBlank())

            val forwardKey = forwardKey(namespace, stableKey)

            if (preferences.contains(forwardKey)) {
                return@synchronized preferences.getInt(
                    forwardKey,
                    INVALID_ID,
                ).also { persistedId ->
                    check(persistedId != INVALID_ID)
                }
            }

            val initialCandidate = candidateSource
                    .candidate(namespace, stableKey).coerceIn(MIN_ID, MAX_ID)

            var candidate = initialCandidate

            repeat(MAX_PROBE_COUNT) {
                val reverseKey = reverseKey(candidate)
                val owner = preferences.getString(reverseKey, null)
                val expectedOwner = "$namespace\u001f$stableKey"

                if (owner == null || owner == expectedOwner) {
                    val committed = preferences
                            .edit().putInt(forwardKey, candidate)
                            .putString(reverseKey, expectedOwner).commit()

                    check(committed)
                    return@synchronized candidate
                }

                candidate = if (candidate == MAX_ID) {
                        MIN_ID
                    } else {
                        candidate + 1
                    }
            }

            error("Notification ID registry is exhausted.")
        }

    override fun findExistingId(
        namespace: String,
        stableKey: String,
    ): Int? = synchronized(this) {
            require(namespace.isNotBlank())
            require(stableKey.isNotBlank())

            val key = forwardKey(namespace, stableKey)
            if (!preferences.contains(key)) {
                return@synchronized null
            }

            preferences.getInt(key, INVALID_ID).takeIf { it != INVALID_ID }
                ?: error("Persisted notification ID is invalid.")
        }

    override fun forget(
        namespace: String,
        stableKey: String,
    ): Boolean = synchronized(this) {
            val persistedId = findExistingId(namespace, stableKey)
                    ?: return@synchronized false
            val owner = "$namespace\u001f$stableKey"
            val reverseKey = reverseKey(persistedId)

            check(preferences.getString(reverseKey, null) == owner)
            val committed = preferences.edit()
                    .remove(forwardKey(namespace, stableKey)).remove(reverseKey)
                    .commit()
            check(committed)
            true
        }

    override fun clearAll() {
        synchronized(this) {
            val committed = preferences.edit().clear().commit()
            check(committed)
        }
    }

    private fun forwardKey(
        namespace: String,
        stableKey: String,
    ): String = "forward:$namespace:${stableKey.length}:$stableKey"

    private fun reverseKey(id: Int): String = "reverse:$id"

    private companion object {
        const val PREFERENCE_FILE = "carepack_notification_ids"
        const val INVALID_ID = -1
        const val MIN_ID = 1
        const val MAX_ID = 0x3fffffff
        const val MAX_PROBE_COUNT = 65_536
    }
}
