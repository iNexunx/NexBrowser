package com.nexun.nexbrowser.adblock

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object FilterListRepo {
    private val SOURCES = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/BaseFilter/sections/adservers.txt",
        "https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/BaseFilter/sections/cosmetic.txt",
        "https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/SpywareFilter/sections/tracking_servers.txt",
        "https://easylist.to/easylist/fanboy-annoyance.txt",
    )
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val PREFS = "nexbrowser_filters"
    private const val KEY_LAST = "last_update"
    private val cacheFile get() = File(cacheDir, "rules_cache.txt")

    private var cacheDir: File = File(".")
    private var lastUpdate = 0L
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        cacheDir = context.filesDir
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lastUpdate = prefs?.getLong(KEY_LAST, 0L) ?: 0L
        // Load previously saved rules instantly (offline-friendly).
        val f = cacheFile
        if (f.exists()) runCatching { AdblockEngine.loadRules(f.readText()) }
    }

    suspend fun updateIfStale(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdate < 24 * 60 * 60 * 1000L) return@withContext false
        val sb = StringBuilder()
        var any = false
        for (src in SOURCES) {
            runCatching {
                val conn = (URL(src).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("Accept-Encoding", "gzip")
                    setRequestProperty("User-Agent", "Mozilla/5.0 NexBrowser")
                    instanceFollowRedirects = true
                }
                val enc = conn.contentEncoding
                val raw = conn.inputStream
                val stream = if (enc != null && enc.contains("gzip", true)) GZIPInputStream(raw) else raw
                val text = stream.bufferedReader().use { it.readText().take(MAX_BYTES) }
                conn.disconnect()
                AdblockEngine.loadRules(text)
                sb.append(text).append('\n')
                any = true
            }
        }
        if (any) {
            lastUpdate = now
            prefs?.edit()?.putLong(KEY_LAST, now)?.apply()
            runCatching { cacheFile.writeText(sb.toString()) }
        }
        any
    }

    fun lastUpdate(): Long = lastUpdate
}
