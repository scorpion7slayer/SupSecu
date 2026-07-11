package be.supsecu.app.core

import java.net.IDN
import java.util.Locale

fun interface IdnaCodec {
    fun toAsciiHost(unicodeHost: String): String?
}

object JavaIdnaCodec : IdnaCodec {
    override fun toAsciiHost(unicodeHost: String): String? = runCatching {
        IDN.toASCII(unicodeHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    }.getOrNull()
}
