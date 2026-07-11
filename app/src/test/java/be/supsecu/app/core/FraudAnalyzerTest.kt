package be.supsecu.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FraudAnalyzerTest {
    private val analyzer = FraudAnalyzer(UrlNormalizer(JavaIdnaCodec))

    @Test
    fun `official Lidl address remains silent`() {
        val result = analyzer.analyze(
            rawUrl = "https://www.lidl.be/products",
            source = UrlSource.ADDRESS_BAR,
            signals = fakeLidlSignals,
        )

        assertEquals(Verdict.OFFICIAL, result.verdict)
        assertEquals(Intervention.NONE, result.intervention)
        assertEquals("lidl", result.brand?.id)
    }

    @Test
    fun `domain boundary rejects deceptive Lidl subdomain`() {
        val result = analyzer.analyze(
            rawUrl = "https://www.lidl.be.evil.com/products",
            source = UrlSource.ADDRESS_BAR,
            signals = fakeLidlSignals,
        )

        assertEquals(Verdict.IMPERSONATION, result.verdict)
        assertEquals(Intervention.FULL_SCREEN, result.intervention)
        assertEquals("www.lidl.be.evil.com", result.observedAsciiHost)
    }

    @Test
    fun `provided fake Lidl case triggers full screen alert with strong page evidence`() {
        val result = analyzer.analyze(
            rawUrl = "https://xcoemruf.shop/products/inventor-mobiele-airco-chilly-dc690?currency=EUR",
            source = UrlSource.ADDRESS_BAR,
            signals = fakeLidlSignals,
        )

        assertEquals(Verdict.IMPERSONATION, result.verdict)
        assertEquals(Intervention.FULL_SCREEN, result.intervention)
        assertEquals("https://www.lidl.be/", result.officialUrl)
    }

    @Test
    fun `unknown shop with no accessible brand proof is not accused`() {
        val result = analyzer.analyze(
            rawUrl = "https://xcoemruf.shop/products/airco",
            source = UrlSource.ADDRESS_BAR,
            signals = listOf(
                AccessibleSignal("Climatiseur mobile", NodeRole.HEADING, PageZone.MAIN),
                AccessibleSignal("69,00 €", NodeRole.BODY, PageZone.MAIN),
            ),
        )

        assertEquals(Verdict.NO_EVIDENCE, result.verdict)
        assertEquals(Intervention.NONE, result.intervention)
        assertNull(result.brand)
    }

    @Test
    fun `a single brand mention in an article is insufficient`() {
        val result = analyzer.analyze(
            rawUrl = "https://news.example/article/lidl",
            source = UrlSource.ADDRESS_BAR,
            signals = listOf(
                AccessibleSignal("Lidl ouvre un nouveau magasin", NodeRole.HEADING, PageZone.MAIN),
                AccessibleSignal("Un reportage indépendant", NodeRole.BODY, PageZone.MAIN),
            ),
        )

        assertEquals(Verdict.NO_EVIDENCE, result.verdict)
    }

    @Test
    fun `Amazon credential page on another host is impersonation`() {
        val result = analyzer.analyze(
            rawUrl = "https://accounts-security.example/login",
            source = UrlSource.ADDRESS_BAR,
            signals = listOf(
                AccessibleSignal("Amazon", NodeRole.IMAGE_DESCRIPTION, PageZone.HEADER),
                AccessibleSignal("Se connecter à Amazon", NodeRole.BUTTON, PageZone.MAIN, interactive = true),
                AccessibleSignal("Mot de passe", NodeRole.INPUT, PageZone.MAIN, passwordField = true),
            ),
        )

        assertEquals(Verdict.IMPERSONATION, result.verdict)
        assertEquals("amazon", result.brand?.id)
    }

    @Test
    fun `lookalike Amazon domains produce a cautious notification`() {
        listOf(
            "https://amaz0n.com/",
            "https://amazon-login.shop/",
            "https://amazom.com/",
            "https://amazon.com.fr/",
        ).forEach { url ->
            val result = analyzer.analyze(url, UrlSource.ADDRESS_BAR)
            assertEquals(url, Verdict.SUSPICIOUS, result.verdict)
            assertEquals(url, Intervention.NOTIFICATION, result.intervention)
            assertEquals(url, "amazon", result.brand?.id)
        }
    }

    @Test
    fun `Amazon Web Services and similar words are not Amazon retail lookalikes`() {
        listOf("https://amazonaws.com/", "https://amazonie.fr/", "https://amazonas.example/").forEach { url ->
            assertEquals(url, Verdict.NO_EVIDENCE, analyzer.analyze(url, UrlSource.ADDRESS_BAR).verdict)
        }
    }

    @Test
    fun `official domain inside user info never authenticates the real host`() {
        val result = analyzer.analyze("https://amazon.com@evil.example/", UrlSource.ADDRESS_BAR)
        assertEquals(Verdict.SUSPICIOUS, result.verdict)
        assertEquals("evil.example", result.observedAsciiHost)
    }

    @Test
    fun `verified special official hosts remain silent without widening shared domains`() {
        assertEquals(
            Verdict.OFFICIAL,
            analyzer.analyze("https://track.bpost.cloud/", UrlSource.ADDRESS_BAR).verdict,
        )
        assertEquals(
            Verdict.SUSPICIOUS,
            analyzer.analyze("https://evil.track.bpost.cloud/", UrlSource.ADDRESS_BAR).verdict,
        )
        assertEquals(
            Verdict.OFFICIAL,
            analyzer.analyze("https://www.kbc.com/", UrlSource.ADDRESS_BAR).verdict,
        )
    }

    @Test
    fun `manual brand confirmation turns a mismatch into an explicit warning`() {
        val result = analyzer.analyze(
            rawUrl = "https://xcoemruf.shop/products/airco",
            source = UrlSource.MANUAL,
            claimedBrandId = "lidl",
        )

        assertEquals(Verdict.IMPERSONATION, result.verdict)
        assertEquals("https://www.lidl.be/", result.officialUrl)
    }

    @Test
    fun `manual claim is checked against the selected brand rather than any official brand`() {
        val result = analyzer.analyze(
            rawUrl = "https://www.amazon.com/",
            source = UrlSource.MANUAL,
            claimedBrandId = "lidl",
        )

        assertEquals(Verdict.IMPERSONATION, result.verdict)
        assertEquals("lidl", result.brand?.id)
    }

    @Test
    fun `all supported Amazon marketplaces are accepted explicitly`() {
        val marketplaces = listOf(
            "amazon.com",
            "amazon.com.be",
            "amazon.fr",
            "amazon.de",
            "amazon.nl",
            "amazon.co.uk",
            "amazon.es",
            "amazon.it",
            "amazon.ca",
            "amazon.co.jp",
            "amazon.com.au",
            "amazon.com.mx",
            "amazon.com.br",
            "amazon.in",
            "amazon.sg",
            "amazon.ae",
            "amazon.sa",
            "amazon.com.tr",
            "amazon.se",
            "amazon.pl",
            "amazon.eg",
        )

        marketplaces.forEach { host ->
            val result = analyzer.analyze("https://www.$host/", UrlSource.ADDRESS_BAR)
            assertEquals(host, Verdict.OFFICIAL, result.verdict)
            assertEquals(host, "amazon", result.brand?.id)
        }
    }

    @Test
    fun `every catalog primary URL is covered by its own host rules`() {
        BrandCatalog.brands.forEach { brand ->
            val host = UrlNormalizer(JavaIdnaCodec).normalize(brand.officialUrl)?.asciiHost
            val covered = host != null && brand.officialHosts.any { analyzer.matchesHost(host, it) }
            assertEquals(brand.id, true, covered)
        }
    }

    @Test
    fun `KBC Brussels official site is not mistaken for a KBC copy`() {
        val result = analyzer.analyze(
            rawUrl = "https://www.kbcbrussels.be/",
            source = UrlSource.ADDRESS_BAR,
            signals = listOf(
                AccessibleSignal("KBC Brussels", NodeRole.IMAGE_DESCRIPTION, PageZone.HEADER),
                AccessibleSignal("Se connecter", NodeRole.BUTTON, PageZone.MAIN, interactive = true),
            ),
        )

        assertEquals(Verdict.OFFICIAL, result.verdict)
        assertEquals("kbc_brussels", result.brand?.id)
    }

    @Test
    fun `page content can never be used as the visited URL source`() {
        val result = analyzer.analyze(
            rawUrl = "https://www.lidl.be/",
            source = UrlSource.PAGE_CONTENT,
            signals = fakeLidlSignals,
        )

        assertEquals(Verdict.UNVERIFIABLE, result.verdict)
        assertEquals(Intervention.NONE, result.intervention)
    }

    @Test
    fun `domain matching requires an exact label boundary`() {
        val rule = HostRule("lidl.be", includeSubdomains = true)
        val cases = mapOf(
            "lidl.be" to true,
            "www.lidl.be" to true,
            "shop.www.lidl.be" to true,
            "notlidl.be" to false,
            "lidl.be.evil.com" to false,
            "lidl-be.shop" to false,
        )

        cases.forEach { (host, expected) -> assertEquals(host, expected, analyzer.matchesHost(host, rule)) }
    }

    private val fakeLidlSignals = listOf(
        AccessibleSignal(
            text = "Lidl",
            role = NodeRole.IMAGE_DESCRIPTION,
            zone = PageZone.HEADER,
        ),
        AccessibleSignal(
            text = "Ajouter au panier",
            role = NodeRole.BUTTON,
            zone = PageZone.MAIN,
            interactive = true,
        ),
        AccessibleSignal(
            text = "Lidl Belgium",
            role = NodeRole.BODY,
            zone = PageZone.FOOTER,
        ),
    )
}
