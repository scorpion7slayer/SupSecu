package be.supsecu.app.reputation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreatDomainParserTest {
    @Test
    fun parsesCommonPublicFeedFormats() {
        val cases = mapOf(
            "phishing.example" to "phishing.example",
            "0.0.0.0 fake-shop.example" to "fake-shop.example",
            "https://danger.example/login" to "danger.example",
            "subdomain.example # commentaire" to "subdomain.example",
            "BÜCHER.example" to "xn--bcher-kva.example",
        )

        cases.forEach { (line, expected) ->
            assertEquals(expected, ThreatDomainParser.parseLine(line))
        }
    }

    @Test
    fun rejectsCommentsIpsAndMalformedHosts() {
        listOf("", "# commentaire", "! règle", "127.0.0.1", "localhost", "-bad.example")
            .forEach { line -> assertNull(line, ThreatDomainParser.parseLine(line)) }
    }

    @Test
    fun stopsAtSharedHostingAndPublicSuffixBoundaries() {
        assertEquals(
            listOf("shop.attacker.github.io", "attacker.github.io"),
            ThreatHostCandidates.from("shop.attacker.github.io"),
        )
        assertEquals(listOf("login.attacker.co.uk", "attacker.co.uk"), ThreatHostCandidates.from("login.attacker.co.uk"))
        assertEquals(emptyList<String>(), ThreatHostCandidates.from("github.io"))
    }
}
