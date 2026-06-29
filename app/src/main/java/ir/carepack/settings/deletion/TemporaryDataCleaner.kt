package ir.carepack.settings.deletion

fun interface TemporaryDataCleaner {

    suspend fun clearAllTemporaryData()
}
