package be.supsecu.app.reputation

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ThreatRefreshResult {
    data class Updated(val stats: ThreatFeedStats) : ThreatRefreshResult
    data class Current(val stats: ThreatFeedStats) : ThreatRefreshResult
    data class Failed(val error: Throwable, val previousStats: ThreatFeedStats) : ThreatRefreshResult
}

class ThreatFeedUpdater(
    context: Context,
    private val executor: Executor,
) {
    private val appContext = context.applicationContext
    private val repository = ThreatRepository(appContext)

    fun refresh(force: Boolean, callback: (ThreatRefreshResult) -> Unit) {
        val current = repository.stats()
        if (!force && current.domainCount > 0 && System.currentTimeMillis() - current.updatedAtMillis < REFRESH_INTERVAL_MS) {
            callback(ThreatRefreshResult.Current(current))
            return
        }
        synchronized(refreshLock) {
            pendingCallbacks += callback
            if (!refreshing.compareAndSet(false, true)) return
        }

        executor.execute {
            val result: ThreatRefreshResult = runCatching { downloadAndReplace() }
                .fold(
                    onSuccess = { stats -> ThreatRefreshResult.Updated(stats) },
                    onFailure = { ThreatRefreshResult.Failed(it, repository.stats()) },
                )
            val callbacks = synchronized(refreshLock) {
                refreshing.set(false)
                pendingCallbacks.toList().also { pendingCallbacks.clear() }
            }
            callbacks.forEach { pendingCallback -> pendingCallback(result) }
        }
    }

    private fun downloadAndReplace(): ThreatFeedStats {
        val directory = File(appContext.cacheDir, "threat-feeds").apply { mkdirs() }
        val downloaded = mutableListOf<ThreatFeedFile>()
        val failures = mutableListOf<String>()
        try {
            SOURCES.forEachIndexed { index, source ->
                val destination = File(directory, "feed-$index.txt")
                destination.delete()
                runCatching { download(source, destination) }
                    .onSuccess {
                        if (containsEnoughDomains(destination, source.minimumEntries)) {
                            downloaded += ThreatFeedFile(source.displayName, destination, source.minimumEntries)
                        } else {
                            destination.delete()
                            failures += "${source.displayName}: format ou contenu insuffisant"
                        }
                    }
                    .onFailure { error ->
                        failures += "${source.displayName}: ${error.message ?: error.javaClass.simpleName}"
                    }
            }
            if (downloaded.isEmpty()) throw IOException(failures.joinToString(" ; ").take(500))
            return repository.replaceFrom(downloaded)
        } finally {
            downloaded.forEach { it.file.delete() }
        }
    }

    private fun containsEnoughDomains(file: File, minimumEntries: Int): Boolean {
        var accepted = 0
        file.bufferedReader().useLines { lines ->
            val iterator = lines.iterator()
            while (iterator.hasNext() && accepted < minimumEntries) {
                if (ThreatDomainParser.parseLine(iterator.next()) != null) accepted++
            }
        }
        return accepted >= minimumEntries
    }

    private fun download(source: ThreatFeedSource, destination: File) {
        val uri = URI(source.url)
        require(uri.scheme == "https" && uri.host in ALLOWED_HOSTS)
        val temporary = File(destination.parentFile, "${destination.name}.part")
        temporary.delete()

        val connection = uri.toURL().openConnection() as HttpURLConnection
        // Ne jamais suivre une redirection vers un hôte qui n'a pas été approuvé.
        connection.instanceFollowRedirects = false
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "SupSecu-Android/1")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("${source.displayName} a répondu ${connection.responseCode}.")
            }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > source.maximumBytes) throw IOException("${source.displayName} dépasse la taille autorisée.")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private data class ThreatFeedSource(
        val displayName: String,
        val url: String,
        val minimumEntries: Int,
        val maximumBytes: Long,
    )

    companion object {
        private const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1_000L
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 120_000
        private val refreshing = AtomicBoolean(false)
        private val refreshLock = Any()
        private val pendingCallbacks = mutableListOf<(ThreatRefreshResult) -> Unit>()
        private val ALLOWED_HOSTS = setOf("raw.githubusercontent.com", "hole.cert.pl")
        private val SOURCES = listOf(
            ThreatFeedSource(
                displayName = "Phishing.Database",
                url = "https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-domains-ACTIVE.txt",
                minimumEntries = 100,
                maximumBytes = 20L * 1_024L * 1_024L,
            ),
            ThreatFeedSource(
                displayName = "CERT Polska",
                url = "https://hole.cert.pl/domains/v2/domains.txt",
                minimumEntries = 100,
                maximumBytes = 10L * 1_024L * 1_024L,
            ),
            ThreatFeedSource(
                displayName = "PhishDestroy",
                url = "https://raw.githubusercontent.com/phishdestroy/destroylist/main/domains.txt",
                minimumEntries = 100,
                maximumBytes = 20L * 1_024L * 1_024L,
            ),
        )
    }
}
