package nx.browser.media.capture

import android.content.Context
import android.webkit.CookieManager
import nx.browser.download.DownloadEntry
import nx.browser.download.DownloadService
import nx.browser.download.DownloadsRegistry
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns a captured [MediaCandidate] into a real download, replaying the same
 * headers/UA/cookies the browser used (Aster-style). Direct video files download
 * as-is; HLS playlists are fetched so the playlist itself is saved (and its media
 * segments are chained when the playlist is a simple VOD list).
 */
object MediaDownloader {

    fun download(context: Context, candidate: MediaCandidate) {
        val cookie = candidate.headers["cookie"]
            ?: runCatching { CookieManager.getInstance().getCookie(candidate.url) }.getOrNull()
        when (candidate.type) {
            MediaType.DIRECT_VIDEO -> startFile(context, candidate, cookie)
            MediaType.HLS -> startHls(context, candidate, cookie)
            MediaType.PAGE_VIDEO -> {
                // The page video's real URL is resolved elsewhere (YouTube/extractor).
                DownloadsRegistry.add(
                    DownloadEntry(
                        candidate.fileName, "No soportado",
                        "Abre el video en la página y usa el botón de descarga"
                    )
                )
            }
        }
    }

    private fun startFile(context: Context, candidate: MediaCandidate, cookie: String?) {
        DownloadService.start(
            context = context,
            url = candidate.url,
            name = candidate.fileName,
            ua = candidate.userAgent.ifBlank { null },
            cookie = cookie,
            referer = candidate.pageUrl.ifBlank { null },
            headers = candidate.headers,
        )
    }

    /**
     * Downloads an HLS playlist. A simple VOD playlist (media sequence of .ts/.m4s
     * segments) is fetched and its segments are concatenated into one file by a
     * background worker; master playlists are saved as-is so the user can pick.
     */
    private fun startHls(context: Context, candidate: MediaCandidate, cookie: String?) {
        val outName = candidate.fileName.ifBlank { "video.m3u8" }
        val entry = DownloadsRegistry.startEntry(outName)
        kotlin.concurrent.thread(name = "nex-hls", isDaemon = true) {
            runCatching {
                val playlist = fetchText(candidate.url, candidate.userAgent, candidate.headers, cookie)
                val segments = parseSegments(playlist, candidate.url)
                if (segments.size >= 2) {
                    val target = java.io.File(
                        context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                            ?: context.filesDir,
                        outName.removeSuffix(".m3u8") + ".ts"
                    )
                    target.outputStream().use { out ->
                        segments.forEachIndexed { i, seg ->
                            val data = fetchBytes(seg, candidate.userAgent, candidate.headers, cookie)
                            out.write(data)
                            val pct = ((i + 1) * 100) / segments.size
                            DownloadsRegistry.updateEntry(entry, pct, "${i + 1}/${segments.size}", "Descargando")
                        }
                    }
                    DownloadsRegistry.updateEntry(entry, 100, "Completado", "Completado", target.absolutePath)
                } else {
                    // Save the playlist file itself.
                    val target = java.io.File(
                        context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                            ?: context.filesDir,
                        outName
                    )
                    target.writeText(playlist)
                    DownloadsRegistry.updateEntry(entry, 100, "Completado", "Completado", target.absolutePath)
                }
            }.onFailure {
                DownloadsRegistry.updateEntry(entry, -1, "Error: ${it.message ?: "desconocido"}", "Error")
            }
        }
    }

    private fun fetchText(
        url: String,
        ua: String,
        headers: Map<String, String>,
        cookie: String?,
    ): String = openConn(url, ua, headers, cookie).let { c ->
        c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun fetchBytes(
        url: String,
        ua: String,
        headers: Map<String, String>,
        cookie: String?,
    ): ByteArray = openConn(url, ua, headers, cookie).let { c ->
        c.inputStream.use { it.readBytes() }.also { c.disconnect() }
    }

    /** Returns absolute segment URLs from a media playlist, or empty for a master list. */
    private fun parseSegments(playlist: String, baseUrl: String): List<String> {
        val base = baseUrl.substringBefore('?').substringBeforeLast('/', "")
        val out = ArrayList<String>()
        for (line in playlist.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            if (t.endsWith(".m3u8")) return emptyList() // master playlist
            out.add(if (t.startsWith("http")) t else "$base/$t")
        }
        return out
    }

    private fun openConn(
        url: String,
        ua: String,
        headers: Map<String, String>,
        cookie: String?,
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20000
        readTimeout = 30000
        instanceFollowRedirects = true
        if (ua.isNotBlank()) setRequestProperty("User-Agent", ua)
        cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
        for ((k, v) in headers) {
            if (k.equals("range", true) || k.equals("accept-encoding", true)) continue
            runCatching { setRequestProperty(k, v) }
        }
        setRequestProperty("Accept-Encoding", "identity")
        connect()
    }
}
