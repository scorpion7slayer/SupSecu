package be.supsecu.app.reputation

object ConfirmedThreatCatalog {
    fun lookup(asciiHost: String): ConfirmedThreat? = ThreatHostCandidates.from(asciiHost)
        .firstNotNullOfOrNull { domain ->
            THREATS[domain]?.let { brandId -> ConfirmedThreat(domain, brandId) }
        }

    private val THREATS = mapOf(
        // Domaine frauduleux explicitement signalé par l'utilisateur pour le scénario Lidl.
        "xcoemruf.shop" to "lidl",
    )
}

data class ConfirmedThreat(
    val matchedDomain: String,
    val brandId: String,
)
