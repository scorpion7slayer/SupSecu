package be.supsecu.app.reputation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import be.supsecu.app.core.Assessment
import be.supsecu.app.core.BrandCatalog
import be.supsecu.app.core.Intervention
import be.supsecu.app.core.Verdict
import java.io.File

data class ThreatFeedStats(
    val domainCount: Int,
    val updatedAtMillis: Long,
)

data class ThreatMatch(
    val matchedDomain: String,
    val source: String,
    val claimedBrandId: String? = null,
)

class ThreatRepository(context: Context) {
    private val helper = ThreatDatabase(context.applicationContext)

    fun assessHost(asciiHost: String): Assessment? {
        val match = lookup(asciiHost) ?: return null
        val brand = BrandCatalog.byId(match.claimedBrandId)
        return Assessment(
            verdict = if (brand != null) Verdict.IMPERSONATION else Verdict.KNOWN_THREAT,
            intervention = Intervention.FULL_SCREEN,
            observedAsciiHost = asciiHost,
            brand = brand,
            reasons = setOf("threat_reputation", match.source, "matched:${match.matchedDomain}"),
        )
    }

    fun lookup(asciiHost: String): ThreatMatch? {
        ConfirmedThreatCatalog.lookup(asciiHost)?.let { threat ->
            return ThreatMatch(threat.matchedDomain, SOURCE_CONFIRMED_REPORT, threat.brandId)
        }

        val candidates = ThreatHostCandidates.from(asciiHost)

        val database = helper.readableDatabase
        candidates.forEach { candidate ->
            database.query(
                TABLE_DOMAINS,
                arrayOf(COLUMN_DOMAIN),
                "$COLUMN_DOMAIN = ?",
                arrayOf(candidate),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) return ThreatMatch(candidate, SOURCE_PUBLIC_FEEDS)
            }
        }
        return null
    }

    fun stats(): ThreatFeedStats {
        val database = helper.readableDatabase
        val count = database.rawQuery("SELECT COUNT(*) FROM $TABLE_DOMAINS", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        val updatedAt = database.query(
            TABLE_METADATA,
            arrayOf(COLUMN_VALUE),
            "$COLUMN_KEY = ?",
            arrayOf(KEY_UPDATED_AT),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).toLongOrNull() ?: 0L else 0L
        }
        return ThreatFeedStats(count, updatedAt)
    }

    fun replaceFrom(files: List<ThreatFeedFile>): ThreatFeedStats {
        require(files.isNotEmpty())
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            database.execSQL("DROP TABLE IF EXISTS $TABLE_STAGING")
            database.execSQL("CREATE TABLE $TABLE_STAGING ($COLUMN_DOMAIN TEXT PRIMARY KEY NOT NULL)")
            val insert = database.compileStatement("INSERT OR IGNORE INTO $TABLE_STAGING ($COLUMN_DOMAIN) VALUES (?)")
            var processed = 0

            files.forEach { feed ->
                var acceptedForFeed = 0
                feed.file.bufferedReader().useLines { lines ->
                    lines.forEach lineLoop@ { line ->
                        val domain = ThreatDomainParser.parseLine(line) ?: return@lineLoop
                        insert.clearBindings()
                        insert.bindString(1, domain)
                        insert.executeInsert()
                        acceptedForFeed++
                        processed++
                        if (processed > MAX_DOMAINS) error("Les listes dépassent la limite de sécurité.")
                    }
                }
                require(acceptedForFeed >= feed.minimumEntries) {
                    "La source ${feed.displayName} ne contient pas assez de domaines valides."
                }
            }

            database.execSQL("DROP TABLE IF EXISTS $TABLE_DOMAINS")
            database.execSQL("ALTER TABLE $TABLE_STAGING RENAME TO $TABLE_DOMAINS")
            val now = System.currentTimeMillis()
            database.insertWithOnConflict(
                TABLE_METADATA,
                null,
                ContentValues().apply {
                    put(COLUMN_KEY, KEY_UPDATED_AT)
                    put(COLUMN_VALUE, now.toString())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            database.setTransactionSuccessful()
            return ThreatFeedStats(domainCount = processedDistinct(database), updatedAtMillis = now)
        } finally {
            database.endTransaction()
        }
    }

    private fun processedDistinct(database: SQLiteDatabase): Int =
        database.rawQuery("SELECT COUNT(*) FROM $TABLE_DOMAINS", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private class ThreatDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL("CREATE TABLE $TABLE_DOMAINS ($COLUMN_DOMAIN TEXT PRIMARY KEY NOT NULL)")
            database.execSQL(
                "CREATE TABLE $TABLE_METADATA ($COLUMN_KEY TEXT PRIMARY KEY NOT NULL, $COLUMN_VALUE TEXT NOT NULL)",
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            database.execSQL("DROP TABLE IF EXISTS $TABLE_DOMAINS")
            database.execSQL("DROP TABLE IF EXISTS $TABLE_METADATA")
            onCreate(database)
        }
    }

    companion object {
        private const val DATABASE_NAME = "threat-reputation.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_DOMAINS = "threat_domains"
        private const val TABLE_STAGING = "threat_domains_staging"
        private const val TABLE_METADATA = "metadata"
        private const val COLUMN_DOMAIN = "domain"
        private const val COLUMN_KEY = "key"
        private const val COLUMN_VALUE = "value"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val MAX_DOMAINS = 2_000_000
        private const val SOURCE_CONFIRMED_REPORT = "confirmed_user_report"
        private const val SOURCE_PUBLIC_FEEDS = "public_threat_feeds"

    }
}

data class ThreatFeedFile(
    val displayName: String,
    val file: File,
    val minimumEntries: Int,
)
