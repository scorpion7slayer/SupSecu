package be.supsecu.app.core

import java.net.URI
import java.util.Locale

class UrlNormalizer(
    private val idnaCodec: IdnaCodec = JavaIdnaCodec,
) {
    fun normalize(rawValue: String): NormalizedUrl? {
        if (rawValue.length > MAX_INPUT_LENGTH) return null

        val trimmed = rawValue.trim()
        if (trimmed.isEmpty() || containsForbiddenCharacter(trimmed)) return null

        val normalizedDots = trimmed
            .replace('\u3002', '.')
            .replace('\uFF0E', '.')
            .replace('\uFF61', '.')

        val candidate = if (SCHEME_PREFIX.containsMatchIn(normalizedDots)) {
            normalizedDots
        } else {
            if (!looksLikeHostInput(normalizedDots)) return null
            "https://$normalizedDots"
        }

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme !in SUPPORTED_SCHEMES) return null

        val authority = uri.rawAuthority ?: return null
        if ('%' in authority) return null
        val hadUserInfo = '@' in authority
        val hostAndPort = authority.substringAfterLast('@')
        val parsedAuthority = parseAuthority(hostAndPort) ?: return null
        var unicodeHost = parsedAuthority.first
        val port = parsedAuthority.second

        unicodeHost = unicodeHost.trimEndSingleDot() ?: return null
        if (unicodeHost.isEmpty() || ".." in unicodeHost) return null

        val asciiHost = when {
            isIpv4(unicodeHost) -> normalizeIpv4(unicodeHost) ?: return null
            isIpv6(unicodeHost) -> unicodeHost.lowercase(Locale.ROOT)
            else -> idnaCodec.toAsciiHost(unicodeHost) ?: return null
        }

        if (!validAsciiHost(asciiHost)) return null

        return NormalizedUrl(
            original = trimmed,
            scheme = scheme,
            asciiHost = asciiHost,
            unicodeHost = unicodeHost.lowercase(Locale.ROOT),
            port = port,
            hadUserInfo = hadUserInfo,
        )
    }

    fun extractFromSharedText(sharedText: String): String? {
        normalize(sharedText)?.let { return sharedText.trim() }

        val match = URL_IN_TEXT.find(sharedText)?.value ?: return null
        val cleaned = match.trim().trim('<', '>', '[', ']', '(', ')', '"', '\'', ',', ';')
            .trimEnd('.', '!', '?')
        return cleaned.takeIf { normalize(it) != null }
    }

    private fun containsForbiddenCharacter(value: String): Boolean = value.any { char ->
        char == '\\' ||
            char.isISOControl() ||
            char.code in 0x202A..0x202E ||
            char.code in 0x2066..0x2069
    }

    private fun looksLikeHostInput(value: String): Boolean {
        if (value.any(Char::isWhitespace) || value.startsWith('/')) return false
        val authority = value.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBeforeLast(':', authority.substringAfterLast('@'))
        return host.contains('.') || isIpv4(host) || isIpv6(host.trim('[', ']'))
    }

    private fun parseAuthority(authority: String): Pair<String, Int?>? {
        if (authority.isEmpty()) return null

        if (authority.startsWith('[')) {
            val closingBracket = authority.indexOf(']')
            if (closingBracket <= 1) return null
            val host = authority.substring(1, closingBracket)
            val suffix = authority.substring(closingBracket + 1)
            val port = when {
                suffix.isEmpty() -> null
                suffix.startsWith(':') -> parsePort(suffix.drop(1)) ?: return null
                else -> return null
            }
            return host to port
        }

        val lastColon = authority.lastIndexOf(':')
        if (lastColon >= 0 && authority.indexOf(':') != lastColon) return null
        if (lastColon < 0) return authority to null

        val host = authority.substring(0, lastColon)
        val portText = authority.substring(lastColon + 1)
        val port = parsePort(portText) ?: return null
        return host to port
    }

    private fun parsePort(value: String): Int? = value
        .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?.toIntOrNull()
        ?.takeIf { it in 1..65535 }

    private fun String.trimEndSingleDot(): String? = when {
        !endsWith('.') -> this
        dropLast(1).endsWith('.') -> null
        else -> dropLast(1)
    }

    private fun validAsciiHost(host: String): Boolean {
        if (host.length > 253 || host.isBlank()) return false
        if (isIpv4(host) || isIpv6(host)) return true
        if (!host.contains('.')) return false
        return host.split('.').all { label ->
            label.isNotEmpty() &&
                label.length <= 63 &&
                !label.startsWith('-') &&
                !label.endsWith('-') &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() &&
                part.all(Char::isDigit) &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun normalizeIpv4(host: String): String? {
        val parts = host.split('.').map { it.toIntOrNull() ?: return null }
        return parts.joinToString(".")
    }

    private fun isIpv6(host: String): Boolean = ':' in host && host.all { char ->
        char.isDigit() || char.lowercaseChar() in 'a'..'f' || char == ':' || char == '.'
    }

    companion object {
        private const val MAX_INPUT_LENGTH = 8_192
        private val SUPPORTED_SCHEMES = setOf("http", "https")
        private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
        private val URL_IN_TEXT = Regex(
            "(?i)(?:https?://)?(?:[a-z0-9\\p{L}](?:[a-z0-9\\p{L}-]{0,61}[a-z0-9\\p{L}])?\\.)+[a-z\\p{L}]{2,63}(?::\\d{1,5})?(?:/[^\\s<>]*)?",
        )
    }
}
