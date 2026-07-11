package be.supsecu.app.update

import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val packageName: String,
    val minimumAndroidSdk: Int,
    val apkAssetName: String,
    val sha256: String,
)

sealed interface UpdateCheckResult {
    data class Available(
        val manifest: UpdateManifest,
        val apkAssetApiUrl: String,
    ) : UpdateCheckResult

    data class UpToDate(val latestVersionName: String) : UpdateCheckResult
}

class UpdateAccessException(message: String) : IOException(message)
class UpdateFormatException(message: String) : IOException(message)

class GitHubUpdateClient(
    private val currentVersionCode: Long,
) {
    fun check(token: String?): UpdateCheckResult {
        val release = JSONObject(requestBytes(UpdateConfig.latestReleaseApiUrl, token, JSON_ACCEPT, MAX_JSON_BYTES).decodeToString())
        val assets = release.optJSONArray("assets")
            ?: throw UpdateFormatException("La version GitHub ne contient aucun fichier.")

        var manifestApiUrl: String? = null
        val assetApiUrls = mutableMapOf<String, String>()
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            val apiUrl = asset.optString("url")
            if (name.isNotBlank() && apiUrl.startsWith("https://api.github.com/")) {
                assetApiUrls[name] = apiUrl
                if (name == UpdateConfig.MANIFEST_ASSET_NAME) manifestApiUrl = apiUrl
            }
        }

        val manifestUrl = manifestApiUrl
            ?: throw UpdateFormatException("Le manifeste de mise à jour est absent de la version GitHub.")
        val manifest = parseManifest(
            requestBytes(manifestUrl, token, BINARY_ACCEPT, MAX_MANIFEST_BYTES).decodeToString(),
        )
        val apkApiUrl = assetApiUrls[manifest.apkAssetName]
            ?: throw UpdateFormatException("Le fichier APK annoncé est absent de la version GitHub.")

        return if (manifest.versionCode > currentVersionCode) {
            UpdateCheckResult.Available(manifest, apkApiUrl)
        } else {
            UpdateCheckResult.UpToDate(manifest.versionName)
        }
    }

    fun download(update: UpdateCheckResult.Available, token: String?, destination: File): File {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        temporary.delete()

        try {
            val connection = openConnection(update.apkAssetApiUrl, token, BINARY_ACCEPT)
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_APK_BYTES) throw UpdateFormatException("Le fichier APK est anormalement volumineux.")
                        output.write(buffer, 0, read)
                    }
                }
            }
            connection.disconnect()

            val actualHash = sha256(temporary)
            if (!actualHash.equals(update.manifest.sha256, ignoreCase = true)) {
                throw UpdateFormatException("L’empreinte SHA-256 de l’APK ne correspond pas.")
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            return destination
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        }
    }

    private fun parseManifest(raw: String): UpdateManifest {
        val json = JSONObject(raw)
        val manifest = UpdateManifest(
            versionCode = json.optLong("versionCode", -1),
            versionName = json.optString("versionName"),
            packageName = json.optString("packageName"),
            minimumAndroidSdk = json.optInt("minimumAndroidSdk", -1),
            apkAssetName = json.optString("apkAssetName"),
            sha256 = json.optString("sha256").lowercase(),
        )
        if (manifest.versionCode < 1 || manifest.versionName.isBlank()) {
            throw UpdateFormatException("La version du manifeste est invalide.")
        }
        if (manifest.packageName != UpdateConfig.PACKAGE_NAME) {
            throw UpdateFormatException("Le manifeste concerne une autre application.")
        }
        if (manifest.minimumAndroidSdk > Build.VERSION.SDK_INT) {
            throw UpdateFormatException("Cette mise à jour nécessite une version plus récente d’Android.")
        }
        if (!manifest.apkAssetName.matches(Regex("[A-Za-z0-9._-]+\\.apk"))) {
            throw UpdateFormatException("Le nom du fichier APK est invalide.")
        }
        if (!manifest.sha256.matches(Regex("[a-f0-9]{64}"))) {
            throw UpdateFormatException("L’empreinte SHA-256 du manifeste est invalide.")
        }
        return manifest
    }

    private fun requestBytes(url: String, token: String?, accept: String, maximumBytes: Long): ByteArray {
        val connection = openConnection(url, token, accept)
        return try {
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maximumBytes) throw UpdateFormatException("La réponse GitHub est anormalement volumineuse.")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(initialUrl: String, token: String?, accept: String): HttpURLConnection {
        var url = initialUrl
        repeat(MAX_REDIRECTS + 1) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "SupSecu-Android")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank() && URI(url).host == "api.github.com") {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> return connection
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307,
                308,
                -> {
                    val redirect = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (redirect.isNullOrBlank() || !redirect.startsWith("https://")) {
                        throw IOException("Redirection GitHub invalide.")
                    }
                    url = redirect
                }
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN,
                HttpURLConnection.HTTP_NOT_FOUND,
                -> {
                    connection.disconnect()
                    throw UpdateAccessException("Accès GitHub refusé. Vérifiez le jeton et son accès au dépôt privé.")
                }
                else -> {
                    connection.disconnect()
                    throw IOException("GitHub a répondu avec le code $status.")
                }
            }
        }
        throw IOException("Trop de redirections pendant le téléchargement.")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val JSON_ACCEPT = "application/vnd.github+json"
        private const val BINARY_ACCEPT = "application/octet-stream"
        private const val MAX_JSON_BYTES = 2L * 1_024L * 1_024L
        private const val MAX_MANIFEST_BYTES = 64L * 1_024L
        private const val MAX_APK_BYTES = 150L * 1_024L * 1_024L
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val MAX_REDIRECTS = 5
    }
}
