package ir.carepack.settings.deletion

import ir.carepack.data.local.CarePackDatabase

fun interface DomainDataCleaner {

    suspend fun clearAllDomainData()
}

class RoomDomainDataCleaner(
    private val database:
    CarePackDatabase,
) : DomainDataCleaner {

    override suspend fun clearAllDomainData() {
        database.clearAllTables()
    }
}
