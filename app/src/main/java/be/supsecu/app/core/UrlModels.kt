package be.supsecu.app.core

enum class Verdict {
    OFFICIAL,
    IMPERSONATION,
    KNOWN_THREAT,
    SUSPICIOUS,
    NO_EVIDENCE,
    UNVERIFIABLE,
    UNSUPPORTED_SCHEME,
}

enum class Intervention {
    NONE,
    NOTIFICATION,
    FULL_SCREEN,
}

enum class UrlSource {
    ADDRESS_BAR,
    SHARE_INTENT,
    MANUAL,
    PAGE_CONTENT,
}

data class NormalizedUrl(
    val original: String,
    val scheme: String,
    val asciiHost: String,
    val unicodeHost: String,
    val port: Int?,
    val hadUserInfo: Boolean,
)

data class Assessment(
    val verdict: Verdict,
    val intervention: Intervention,
    val observedAsciiHost: String? = null,
    val brand: BrandRule? = null,
    val reasons: Set<String> = emptySet(),
) {
    val officialUrl: String?
        get() = brand?.officialUrl
}
