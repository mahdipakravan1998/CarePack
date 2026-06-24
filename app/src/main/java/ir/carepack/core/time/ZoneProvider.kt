package ir.carepack.core.time

import java.time.ZoneId

fun interface ZoneProvider {
    fun currentZone(): ZoneId
}

class SystemZoneProvider : ZoneProvider {
    override fun currentZone(): ZoneId = ZoneId.systemDefault()
}