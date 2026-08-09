package ir.carepack.settings.deletion

import java.security.MessageDigest

internal object DeletionMarkerChecksum {
    fun sha256(
        components: List<String>,
    ): String {
        val canonical =
            components.joinToString(
                separator = "\u001f",
            )

        return MessageDigest
            .getInstance("SHA-256")
            .digest(
                canonical.toByteArray(
                    Charsets.UTF_8,
                ),
            )
            .joinToString(
                separator = "",
            ) { byte ->
                "%02x".format(
                    byte.toInt() and 0xff,
                )
            }
    }
}
