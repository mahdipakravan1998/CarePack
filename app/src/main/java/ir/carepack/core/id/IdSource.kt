package ir.carepack.core.id

import java.util.UUID

fun interface IdSource {
    fun nextId(): String
}

class UuidIdSource : IdSource {
    override fun nextId(): String = UUID.randomUUID().toString()
}