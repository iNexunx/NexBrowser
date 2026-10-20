package nx.browser.media.capture

/** Kind of a captured media resource. Mirrors Aster's MediaType. */
enum class MediaType { DIRECT_VIDEO, HLS, PAGE_VIDEO }

/**
 * One media resource sniffed from network traffic on a tab.
 * Functional equivalent of Aster's MediaCandidate.
 */
data class MediaCandidate(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val pageUrl: String,
    val frameUrl: String,
    val type: MediaType,
    val contentLength: Long,
    val userAgent: String,
    /** Headers worth replaying when downloading (cookie-ish auth included). */
    val headers: Map<String, String>,
    val detectedAt: Long = System.currentTimeMillis(),
)

/**
 * Classifies network requests the same way Aster does, so detection matches.
 *
 * Rule (from MediaCaptureRepository.Companion.classify):
 *  - ignore HLS/DASH segments (video/mp2t, .ts/.m2ts/.m4s/.cmfv/.cmfa)
 *  - application/x-page-video                     -> PAGE_VIDEO
 *  - "mpegurl" or ".m3u8"                          -> HLS
 *  - "video/" mime or a video extension           -> DIRECT_VIDEO
 *  - anything else                                 -> null (ignored)
 */
object MediaClassifier {
    val VIDEO_EXTENSIONS = listOf(".mp4", ".webm", ".m4v", ".mov", ".3gp")
    val SEGMENT_EXTENSIONS = listOf(".ts", ".m2ts", ".m4s", ".cmfv", ".cmfa")

    /** Query params Aster strips because they carry byte-range offsets, not identity. */
    val RANGE_QUERY_NAMES = setOf("bytestart", "byteend")

    const val PAGE_VIDEO_MIME = "application/x-page-video"
    const val HLS_MIME = "application/vnd.apple.mpegurl"
    const val MP4_MIME = "video/mp4"

    fun classify(url: String, mime: String?): MediaType? {
        val m = mime?.lowercase()?.trim().orEmpty()
        val combined = (url + " " + m).lowercase()
        // Segments are part of a stream, never a standalone download.
        if (m.contains("video/mp2t")) return null
        val path = url.substringBefore('?').lowercase()
        if (SEGMENT_EXTENSIONS.any { path.endsWith(it) }) return null

        if (m == PAGE_VIDEO_MIME) return MediaType.PAGE_VIDEO
        if (combined.contains("mpegurl") || path.endsWith(".m3u8")) return MediaType.HLS
        if (m.startsWith("video/") || VIDEO_EXTENSIONS.any { path.endsWith(it) }) {
            return MediaType.DIRECT_VIDEO
        }
        return null
    }

    fun normalizedMime(type: MediaType, mime: String?): String {
        val m = mime?.trim()
        if (!m.isNullOrEmpty() && m != "application/octet-stream") return m
        return when (type) {
            MediaType.HLS -> HLS_MIME
            MediaType.DIRECT_VIDEO -> MP4_MIME
            MediaType.PAGE_VIDEO -> PAGE_VIDEO_MIME
        }
    }

    /**
     * Drops byte-range query params and normalizes known CDN hosts so the same
     * video detected on several requests maps to a single candidate.
     */
    fun normalizeMediaUrl(url: String): String {
        if (!RANGE_QUERY_NAMES.any { url.lowercase().contains("$it=") }) return url
        val base = url.substringBefore('?')
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return url
        val kept = query.split('&').filter { p ->
            val k = p.substringBefore('=').lowercase()
            k !in RANGE_QUERY_NAMES
        }
        return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
    }

    /**
     * Aster only treats a page as a downloadable PAGE_VIDEO on these social sites
     * (their player hides the real media URL behind a page URL).
     */
    fun isSupportedPageVideoUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "tiktok.com" || host.endsWith(".tiktok.com") ||
            host == "instagram.com" || host.endsWith(".instagram.com") ||
            host == "facebook.com" || host.endsWith(".facebook.com") ||
            host == "fb.watch" || host.endsWith(".fb.watch")
    }
}
