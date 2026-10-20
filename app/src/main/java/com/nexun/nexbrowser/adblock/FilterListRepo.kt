package com.nexun.nexbrowser.adblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    )
    private const val MAX_BYTES = 12 * 1024 * 1024
    private var lastUpdate = 0L

    suspend fun updateIfStale(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdate < 12 * 60 * 60 * 1000L) return@withContext false
        var any = false
        for (src in SOURCES) {
            runCatching {
                val conn = (URL(src).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 25000
                    setRequestProperty("Accept-Encoding", "gzip")
                    instanceFollowRedirects = true
                }
                val enc = conn.contentEncoding
                val raw = conn.inputStream
                val stream = if (enc != null && enc.contains("gzip", true)) GZIPInputStream(raw) else raw
                val text = stream.bufferedReader().use { it.readText().take(MAX_BYTES) }
                conn.disconnect()
                AdblockEngine.loadEasyListRules(text)
                any = true
            }
        }
        if (any) lastUpdate = now
        any
    }
}
