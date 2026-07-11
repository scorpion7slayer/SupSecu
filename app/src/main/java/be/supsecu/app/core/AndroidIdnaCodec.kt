package be.supsecu.app.core

import android.icu.text.IDNA
import java.util.Locale

object AndroidIdnaCodec : IdnaCodec {
    private val uts46 = IDNA.getUTS46Instance(
        IDNA.USE_STD3_RULES or
            IDNA.CHECK_BIDI or
            IDNA.CHECK_CONTEXTJ or
            IDNA.NONTRANSITIONAL_TO_ASCII,
    )

    override fun toAsciiHost(unicodeHost: String): String? {
        val info = IDNA.Info()
        val output = StringBuilder()
        uts46.nameToASCII(unicodeHost, output, info)
        return output
            .takeUnless { info.hasErrors() }
            ?.toString()
            ?.lowercase(Locale.ROOT)
    }
}
