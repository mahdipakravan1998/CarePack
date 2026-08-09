package ir.carepack.settings.deletion

fun interface PreferenceDataCleaner {
    suspend fun clearAllPreservingOperationMarkers()
}

fun interface AuxiliaryDeletionStateCleaner {
    suspend fun clearAllAuxiliaryState()
}
