package be.supsecu.app.core

import java.text.Normalizer
import java.util.Locale

class FraudAnalyzer(
    private val urlNormalizer: UrlNormalizer,
    private val brands: List<BrandRule> = BrandCatalog.brands,
) {
    fun analyze(
        rawUrl: String,
        source: UrlSource,
        signals: List<AccessibleSignal> = emptyList(),
        claimedBrandId: String? = null,
    ): Assessment {
        if (source == UrlSource.PAGE_CONTENT) {
            return Assessment(Verdict.UNVERIFIABLE, Intervention.NONE, reasons = setOf("untrusted_url_source"))
        }

        val normalized = urlNormalizer.normalize(rawUrl)
            ?: return Assessment(Verdict.UNVERIFIABLE, Intervention.NONE, reasons = setOf("invalid_url"))

        val claimedBrand = brands.firstOrNull { it.id == claimedBrandId }
        if (claimedBrand != null) {
            val matchesClaimedBrand = claimedBrand.officialHosts.any { hostRule ->
                matchesHost(normalized.asciiHost, hostRule)
            }
            if (!matchesClaimedBrand) {
                return Assessment(
                    verdict = Verdict.IMPERSONATION,
                    intervention = Intervention.FULL_SCREEN,
                    observedAsciiHost = normalized.asciiHost,
                    brand = claimedBrand,
                    reasons = setOf("user_confirmed_brand", "non_official_domain"),
                )
            }
            return Assessment(
                verdict = Verdict.OFFICIAL,
                intervention = Intervention.NONE,
                observedAsciiHost = normalized.asciiHost,
                brand = claimedBrand,
                reasons = setOf("official_domain"),
            )
        }

        val officialBrand = brands.firstOrNull { brand ->
            brand.officialHosts.any { hostRule -> matchesHost(normalized.asciiHost, hostRule) }
        }
        if (officialBrand != null) {
            return Assessment(
                verdict = Verdict.OFFICIAL,
                intervention = Intervention.NONE,
                observedAsciiHost = normalized.asciiHost,
                brand = officialBrand,
                reasons = setOf("official_domain"),
            )
        }

        val strongClaim = brands.firstOrNull { hasStrongBrandClaim(it, signals) }
        if (strongClaim != null) {
            return Assessment(
                verdict = Verdict.IMPERSONATION,
                intervention = Intervention.FULL_SCREEN,
                observedAsciiHost = normalized.asciiHost,
                brand = strongClaim,
                reasons = setOf("strong_brand_claim", "non_official_domain"),
            )
        }

        val resemblingBrand = brands.firstOrNull { resemblesBrand(normalized, it) }
        if (resemblingBrand != null) {
            return Assessment(
                verdict = Verdict.SUSPICIOUS,
                intervention = Intervention.NOTIFICATION,
                observedAsciiHost = normalized.asciiHost,
                brand = resemblingBrand,
                reasons = setOf("lookalike_domain"),
            )
        }

        return Assessment(
            verdict = Verdict.NO_EVIDENCE,
            intervention = Intervention.NONE,
            observedAsciiHost = normalized.asciiHost,
            reasons = setOf("no_brand_evidence"),
        )
    }

    fun matchesHost(host: String, rule: HostRule): Boolean =
        host == rule.asciiHost ||
            (rule.includeSubdomains && host.length > rule.asciiHost.length && host.endsWith(".${rule.asciiHost}"))

    private fun hasStrongBrandClaim(brand: BrandRule, rawSignals: List<AccessibleSignal>): Boolean {
        val signals = rawSignals
            .asSequence()
            .filter { it.visibleToUser && it.text.isNotBlank() }
            .take(MAX_SIGNAL_COUNT)
            .map { it to normalizeText(it.text.take(MAX_SIGNAL_LENGTH)) }
            .toList()
        if (signals.isEmpty()) return false

        val aliases = brand.aliases.map(::normalizeText)
        val legalPhrases = brand.legalPhrases.map(::normalizeText)
        val titlePhrases = brand.titlePhrases.map(::normalizeText)
        fun String.hasAlias(): Boolean = aliases.any { alias -> containsPhrase(this, alias) }

        val brandSignals = signals.filter { (_, text) -> text.hasAlias() }
        if (brandSignals.isEmpty()) return false

        val logoInHeader = brandSignals.any { (signal, _) ->
            signal.role == NodeRole.IMAGE_DESCRIPTION && signal.zone == PageZone.HEADER
        }
        val exactTitle = signals.any { (signal, text) ->
            signal.role == NodeRole.PAGE_TITLE && titlePhrases.any { title ->
                text == title || text.startsWith("$title ") || text.endsWith(" $title")
            }
        }
        val brandedAction = brandSignals.any { (signal, text) ->
            signal.interactive && ACTION_WORDS.any { action -> containsPhrase(text, action) }
        }
        val transactionalControl = signals.any { (signal, text) ->
            signal.interactive && TRANSACTION_WORDS.any { action -> containsPhrase(text, action) }
        }
        val credentialForm = signals.any { (signal, _) -> signal.passwordField }
        val legalMention = signals.any { (signal, text) ->
            signal.zone == PageZone.FOOTER && legalPhrases.any { phrase -> containsPhrase(text, phrase) }
        }
        val identityZones = brandSignals.map { it.first.zone }.filter { it != PageZone.UNKNOWN }.toSet().size

        return (logoInHeader && (transactionalControl || credentialForm)) ||
            (exactTitle && brandedAction) ||
            (identityZones >= 2 && legalMention) ||
            (credentialForm && (logoInHeader || exactTitle || brandedAction))
    }

    private fun resemblesBrand(url: NormalizedUrl, brand: BrandRule): Boolean {
        if (brand.id == "amazon" && url.asciiHost == "amazonaws.com") return false

        val rawLabels = url.unicodeHost.split('.') + url.asciiHost.split('.')
        val brandTokens = brand.aliases
            .map(::normalizeText)
            .filter { ' ' !in it && '.' !in it && it.length >= 3 }

        if (url.hadUserInfo && brand.officialHosts.any { url.original.contains(it.asciiHost, ignoreCase = true) }) {
            return true
        }

        return rawLabels.any { label ->
            val normalizedLabel = confusableSkeleton(label)
            val separatedTokens = normalizedLabel.split('-', '_').filter(String::isNotBlank)
            brandTokens.any { brandToken ->
                separatedTokens.any { token ->
                    token == brandToken ||
                        leetSkeleton(token) == brandToken ||
                        (brandToken.length >= 4 && damerauLevenshteinAtMostOne(token, brandToken))
                }
            }
        }
    }

    private fun normalizeText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}.]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun containsPhrase(text: String, phrase: String): Boolean =
        text == phrase || text.startsWith("$phrase ") || text.endsWith(" $phrase") || " $phrase " in text

    private fun confusableSkeleton(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .map { char ->
            when (char) {
                'а' -> 'a'
                'е' -> 'e'
                'о' -> 'o'
                'р' -> 'p'
                'с' -> 'c'
                'х' -> 'x'
                'у' -> 'y'
                'і' -> 'i'
                else -> char
            }
        }
        .joinToString("")

    private fun leetSkeleton(value: String): String = value.map { char ->
        when (char) {
            '0' -> 'o'
            '1' -> 'l'
            '3' -> 'e'
            '5' -> 's'
            '7' -> 't'
            else -> char
        }
    }.joinToString("")

    private fun damerauLevenshteinAtMostOne(first: String, second: String): Boolean {
        if (first == second) return true
        if (kotlin.math.abs(first.length - second.length) > 1) return false

        if (first.length == second.length) {
            val mismatches = first.indices.filter { first[it] != second[it] }
            if (mismatches.size == 1) return true
            return mismatches.size == 2 &&
                mismatches[1] == mismatches[0] + 1 &&
                first[mismatches[0]] == second[mismatches[1]] &&
                first[mismatches[1]] == second[mismatches[0]]
        }

        val shorter = if (first.length < second.length) first else second
        val longer = if (first.length < second.length) second else first
        var shortIndex = 0
        var longIndex = 0
        var skipped = false
        while (shortIndex < shorter.length && longIndex < longer.length) {
            if (shorter[shortIndex] == longer[longIndex]) {
                shortIndex++
                longIndex++
            } else if (!skipped) {
                skipped = true
                longIndex++
            } else {
                return false
            }
        }
        return true
    }

    companion object {
        private const val MAX_SIGNAL_COUNT = 500
        private const val MAX_SIGNAL_LENGTH = 1_024
        private val ACTION_WORDS = listOf(
            "se connecter",
            "connexion",
            "login",
            "sign in",
            "mon compte",
            "my account",
            "payer avec",
        )
        private val TRANSACTION_WORDS = listOf(
            "acheter",
            "ajouter au panier",
            "panier",
            "commander",
            "paiement",
            "buy",
            "add to cart",
            "checkout",
            "bestellen",
            "winkelwagen",
        )
    }
}
