package nx.browser.youtube

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class VideoMeta(val title: String, val uploader: String, val duration: Long, val id: String)
data class StreamOption(
    val quality: String,
    val url: String,
    val isAudio: Boolean,
    val resolution: String = "",
    val bitrateKbps: Int = 0,
)

object YouTubeExtractor {
    @Volatile private var initialized = false

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(DownloaderImpl())
            initialized = true
        }
    }

    fun parseId(url: String): String? {
        val p = Regex("""(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/|youtube\.com/embed/|youtube\.com/live/)([\w-]{11})""")
        return p.find(url)?.groupValues?.get(1)
    }

    private fun canonical(url: String): String {
        val id = parseId(url) ?: return url
        return "https://www.youtube.com/watch?v=$id"
    }

    /**
     * Extracts stream info and returns playable options.
     * Throws on failure so the UI can show the real reason.
     */
    suspend fun extract(url: String): Pair<VideoMeta, List<StreamOption>> = withContext(Dispatchers.IO) {
        ensureInit()
        val videoUrl = canonical(url)
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val opts = ArrayList<StreamOption>()

        // Muxed video+audio (progressive) — best for a single-file download.
        info.videoStreams
            .asSequence()
            .filter { it.content != null }
            .sortedByDescending { it.height }
            .forEach { s ->
                val res = s.resolution?.takeIf { it.isNotBlank() } ?: "${s.height}p"
                opts.add(
                    StreamOption(
                        quality = "$res · video+audio",
                        url = s.content!!,
                        isAudio = false,
                        resolution = res,
                        bitrateKbps = s.bitrate,
                    )
                )
            }

        // Video-only (higher quality, no sound) as extra options.
        runCatching {
            info.videoOnlyStreams
                .asSequence()
                .filter { it.content != null }
                .sortedByDescending { it.height }
                .forEach { s ->
                    val res = s.resolution?.takeIf { it.isNotBlank() } ?: "${s.height}p"
                    opts.add(
                        StreamOption(
                            quality = "$res · solo video (sin audio)",
                            url = s.content!!,
                            isAudio = false,
                            resolution = res,
                            bitrateKbps = s.bitrate,
                        )
                    )
                }
        }

        // Audio-only.
        runCatching {
            info.audioStreams
                .asSequence()
                .filter { it.content != null }
                .sortedByDescending { it.averageBitrate }
                .forEach { s ->
                    val q = s.quality?.takeIf { it.isNotBlank() }
                        ?: "${s.averageBitrate}kbps"
                    opts.add(
                        StreamOption(
                            quality = "$q · audio",
                            url = s.content!!,
                            isAudio = true,
                            resolution = "audio",
                            bitrateKbps = s.averageBitrate,
                        )
                    )
                }
        }

        if (opts.isEmpty()) {
            val errs = runCatching { info.errors.joinToString("; ") { it.message ?: "" } }.getOrDefault("")
            throw IllegalStateException("No se pudieron resolver streams. $errs")
        }

        VideoMeta(
            info.name ?: "Video",
            info.uploaderName ?: "",
            info.duration,
            info.id ?: (parseId(url) ?: ""),
        ) to opts
    }

    suspend fun download(context: Context, option: StreamOption, name: String): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val ext = if (option.isAudio) "m4a" else "mp4"
        val safe = name.take(60).replace(Regex("[^\\w\\- ]"), "_").trim().ifEmpty { "video" }
        val suffix = if (option.isAudio) "audio" else option.resolution.filter { it.isLetterOrDigit() }
            .ifEmpty { "video" }
        val out = File(dir, "$safe-$suffix.$ext")

        var lastErr: Throwable? = null
        // Try a couple of header strategies: YouTube streaming URLs are picky.
        for (attempt in 0..1) {
            runCatching {
                val conn = (URL(option.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 180000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", BROWSER_UA)
                    if (attempt == 1) {
                        setRequestProperty("Range", "bytes=0-")
                        setRequestProperty("Referer", "https://www.youtube.com/")
                        setRequestProperty("Origin", "https://www.youtube.com")
                    }
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    conn.disconnect()
                    throw IllegalStateException("HTTP $code")
                }
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                conn.disconnect()
                return@withContext out
            }.onFailure { lastErr = it }
        }
        throw (lastErr ?: IllegalStateException("Descarga fallida"))
    }

    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
}

/**
 * Downloader used by NewPipe for its own API calls (watch page, player JS, etc.).
 * Must preserve the exact method/headers/data NewPipe sends.
 */
private class DownloaderImpl : Downloader() {
    override fun execute(request: Request): Response {
        val conn = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", BROWSER_UA)
        }
        for ((k, v) in request.headers()) {
            conn.setRequestProperty(k, v.joinToString(", "))
        }
        request.dataToSend()?.let {
            conn.doOutput = true
            conn.outputStream.use { os -> os.write(it) }
        }
        val code = conn.responseCode
        val msg = conn.responseMessage ?: ""
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        val headers = conn.headerFields.filterKeys { it != null }
        return Response(code, msg, headers, body, request.url())
    }

    private companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }
}
