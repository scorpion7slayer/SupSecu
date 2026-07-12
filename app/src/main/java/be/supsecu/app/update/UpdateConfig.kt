package be.supsecu.app.update

object UpdateConfig {
    const val REPOSITORY_OWNER = "scorpion7slayer"
    const val REPOSITORY_NAME = "SupSecu"
    const val MANIFEST_ASSET_NAME = "supsecu-update.json"
    const val PACKAGE_NAME = "be.supsecu.app"
    const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1_000L

    val latestReleaseApiUrl: String =
        "https://api.github.com/repos/$REPOSITORY_OWNER/$REPOSITORY_NAME/releases/latest"
}
