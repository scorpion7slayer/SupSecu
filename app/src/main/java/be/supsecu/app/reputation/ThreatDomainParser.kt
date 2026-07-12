package be.supsecu.app.reputation

import java.net.IDN
import java.net.URI
import java.util.Locale

object ThreatDomainParser {
    fun parseLine(rawLine: String): String? {
        var value = rawLine.trim()
        if (value.isEmpty() || value.startsWith("#") || value.startsWith("!") || value.startsWith("[") || value.startsWith("{")) {
            return null
        }
        value = value.substringBefore('#').trim()
        if (' ' in value || '\t' in value) {
            value = value.split(Regex("\\s+")).lastOrNull().orEmpty()
        }
        value = value.trim('"', '\'', ',', '[', ']')
        val host = if (value.startsWith("http://") || value.startsWith("https://")) {
            runCatching { URI(value).host }.getOrNull()
        } else {
            value.substringBefore('/').substringBefore(':')
        }?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT) ?: return null

        if (host.isEmpty() || host.length > 253 || host == "localhost" || IPV4.matches(host)) return null
        val ascii = runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }.getOrNull() ?: return null
        if (ascii.length > 253 || '.' !in ascii) return null
        val labels = ascii.split('.')
        if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }) return null
        return ascii
    }

    private val IPV4 = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
}

object ThreatHostCandidates {
    fun from(host: String): List<String> {
        val normalized = host.trimEnd('.').lowercase(Locale.ROOT)
        if (normalized in BOUNDARY_DOMAINS) return emptyList()
        val labels = normalized.split('.').filter(String::isNotEmpty)
        if (labels.size < 2) return listOf(normalized)

        return buildList {
            add(normalized)
            for (index in 1..labels.size - 2) {
                val candidate = labels.drop(index).joinToString(".")
                if (candidate !in BOUNDARY_DOMAINS) add(candidate)
            }
        }.distinct()
    }

    private val BOUNDARY_DOMAINS = setOf(
        "co.uk", "com.au", "co.jp", "co.kr", "com.br", "com.mx", "com.tr",
        "github.io", "pages.dev", "vercel.app", "netlify.app", "blogspot.com",
        "wordpress.com", "firebaseapp.com", "web.app", "workers.dev",
    )
}
