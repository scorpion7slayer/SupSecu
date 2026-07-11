package be.supsecu.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNormalizerTest {
    private val normalizer = UrlNormalizer(JavaIdnaCodec)

    @Test
    fun `normalizes browser address variants without trusting path or user info`() {
        val cases = mapOf(
            "https://WWW.LIDL.BE/products" to "www.lidl.be",
            "  lidl.be  " to "lidl.be",
            "https://www.lidl.be./" to "www.lidl.be",
            "https://www。lidl．be/" to "www.lidl.be",
            "https://lidl.be@xcoemruf.shop/a" to "xcoemruf.shop",
            "https://evil.com/?next=https://www.lidl.be/" to "evil.com",
            "https://аmazon.com/" to "xn--mazon-3ve.com",
        )

        cases.forEach { (raw, expectedHost) ->
            assertEquals(raw, expectedHost, normalizer.normalize(raw)?.asciiHost)
        }
    }

    @Test
    fun `rejects unsafe malformed and unsupported inputs`() {
        val invalid = listOf(
            "",
            "amazon livraison",
            "https://www..lidl.be",
            "https://lidl.be%2eevil.com",
            "https://amazon.com\\@evil.com",
            "https://\u202Eamazon.com",
            "http://",
            "javascript:alert(1)",
            "data:text/html,lidl",
            "https://example.com:99999",
        )

        invalid.forEach { raw -> assertNull(raw, normalizer.normalize(raw)) }
    }

    @Test
    fun `extracts a web address from shared text`() {
        assertEquals(
            "https://xcoemruf.shop/products/airco?currency=EUR",
            normalizer.extractFromSharedText(
                "Regarde ceci : https://xcoemruf.shop/products/airco?currency=EUR !",
            ),
        )
    }

    @Test
    fun `records user info and explicit port`() {
        val normalized = normalizer.normalize("https://lidl.be@evil.example:8443/path")
        assertEquals("evil.example", normalized?.asciiHost)
        assertEquals(8443, normalized?.port)
        assertTrue(normalized?.hadUserInfo == true)
    }

    @Test
    fun `accepts a local IPv4 browser address with a valid port`() {
        val normalized = normalizer.normalize("10.0.2.2:8080/lidl-imitation.html")
        assertEquals("10.0.2.2", normalized?.asciiHost)
        assertEquals(8080, normalized?.port)
    }

    @Test
    fun `does not mistake a search query for a host`() {
        assertFalse(normalizer.normalize("amazon promo belgique") != null)
    }
}
