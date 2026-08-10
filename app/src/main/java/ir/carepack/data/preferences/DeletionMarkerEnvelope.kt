package ir.carepack.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

internal enum class DeletionMarkerPresence {
    ABSENT,
    COMPLETE,
    PARTIAL,
}

internal fun Preferences.deletionMarkerPresence(
    markerKeys: Set<Preferences.Key<*>>,
): DeletionMarkerPresence {
    val storedKeys = asMap().keys
    return when {
        storedKeys.none { it in markerKeys } -> DeletionMarkerPresence.ABSENT
        storedKeys.containsAll(markerKeys) -> DeletionMarkerPresence.COMPLETE
        else -> DeletionMarkerPresence.PARTIAL
    }
}

internal fun MutablePreferences.removeDeletionMarkerKeys(
    markerKeys: Set<Preferences.Key<*>>,
) {
    markerKeys.forEach { key -> remove(key) }
}

internal inline fun <reified E : Enum<E>> enumValueOrNull(
    storedName: String?,
): E? = enumValues<E>().firstOrNull { it.name == storedName }
