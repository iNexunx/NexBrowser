package com.nexun.nexbrowser.youtube

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
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
        val p = Regex("""(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/|youtube\.com/embed/)([\w-]{11})""")
        return p.find(url)?.groupValues?.get(1)
    }

    suspend fun extract(url: String): Pair<VideoMeta, List<StreamOption>> = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(url)
        val opts = mutableListOf<StreamOption>()
        runCatching {
            info.videoStreams.forEach { s ->
                if (s.content == null) return@forEach
                val res = s.resolution?.takeIf { it.isNotBlank() } ?: "${s.height}p"
                val muxed = s.isVideoOnly != true
                opts.add(
                    StreamOption(
                        quality = if (muxed) "$res (video+audio)" else "$res (solo video)",
                        url = s.content,
                        isAudio = false,
                        resolution = res,
                        bitrateKbps = s.bitrate,
                    )
                )
            }
        }
        runCatching {
            info.audioStreams.forEach { s ->
                if (s.content == null) return@forEach
                val q = s.quality?.takeIf { it.isNotBlank() } ?: "audio"
                opts.add(
                    StreamOption(
                        quality = "$q (audio)",
                        url = s.content,
                        isAudio = true,
                        resolution = "audio",
                        bitrateKbps = s.bitrate,
                    )
                )
            }
        }
        if (opts.isEmpty()) throw IllegalStateException("Sin streams disponibles")
        VideoMeta(
            info.name ?: "Video",
            info.uploaderName ?: "",
            info.duration,
            info.id,
        ) to opts
    }

    suspend fun download(context: Context, option: StreamOption, name: String): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val ext = when {
            option.isAudio -> "m4a"
            option.resolution.contains("webm", true) -> "webm"
            else -> "mp4"
        }
        val safe = name.take(60).replace(Regex("[^\\w\\- ]"), "_").trim().ifEmpty { "video" }
        val suffix = if (option.isAudio) "audio" else option.resolution.filter { it.isLetterOrDigit() }
            .ifEmpty { "video" }
        val out = File(dir, "$safe-$suffix.$ext")
        val conn = (URL(option.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 120000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
        out
    }
}

private class DownloaderImpl : Downloader() {
    override fun execute(request: Request): Response {
        val conn = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 12000
            readTimeout = 25000
            instanceFollowRedirects = true
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
        conn.disconnect()
        return Response(code, msg, headers, body, request.url())
    }
}
