package nx.browser.media.capture

import androidx.compose.runtime.mutableStateListOf

/**
 * Per-tab registry of sniffed media candidates. Functional equivalent of Aster's
 * MediaCaptureRepository: bounded list per tab, newest first, deduplicated by URL.
 */
object MediaCaptureRepository {
    const val DEFAULT_VIDEO_FILE_NAME = "video"
    private const val MAX_CANDIDATES_PER_TAB = 50
    private const val MAX_COOKIE_LENGTH = 0x4000
    private const val MAX_HEADER_LENGTH = 0x400
    private const val MAX_FILE_NAME_LENGTH = 0x78

    /** Currently visible candidates for the active tab (newest first). */
    val candidates = mutableStateListOf<MediaCandidate>()

    private val byTab = HashMap<String, MutableList<MediaCandidate>>()
    private val pageByTab = HashMap<String, String>()
    private var activeTab: String = ""

    fun updatePage(tabId: String, pageUrl: String) {
        pageByTab[tabId] = pageUrl
    }

    fun clear(tabId: String) {
        byTab.remove(tabId)
        pageByTab.remove(tabId)
        if (tabId == activeTab) candidates.clear()
    }

    fun setActiveTab(tabId: String) {
        if (tabId == activeTab) return
        activeTab = tabId
        refresh()
    }

    /** Keeps only the tabs that are still open. */
    fun retainTabs(tabIds: Set<String>) {
        byTab.keys.retainAll(tabIds)
        pageByTab.keys.retainAll(tabIds)
        refresh()
    }

    fun add(tabId: String, candidate: MediaCandidate) {
        val list = byTab.getOrPut(tabId) { ArrayList() }
        val idx = list.indexOfFirst { it.url == candidate.url }
        if (idx >= 0) {
            // Promote the already-seen entry (it may now carry a length/headers).
            list.removeAt(idx)
        }
        list.add(0, candidate)
        while (list.size > MAX_CANDIDATES_PER_TAB) list.removeAt(list.lastIndex)
        if (tabId == activeTab) refresh()
    }

    fun forTab(tabId: String): List<MediaCandidate> = byTab[tabId]?.toList().orEmpty()

    private fun refresh() {
        candidates.clear()
        candidates.addAll(byTab[activeTab].orEmpty())
    }

    /**
     * Builds a candidate from a sniffed request, or null when it is not media.
     * Enforces the same limits Aster applies (file name, header, cookie caps).
     */
    fun createCandidate(
        url: String,
        mime: String?,
        pageUrl: String,
        frameUrl: String,
        contentLength: Long,
        userAgent: String,
        headers: Map<String, String>,
    ): MediaCandidate? {
        if (!isHttpUrl(url)) return null
        val normalized = MediaClassifier.normalizeMediaUrl(url)
        val type = MediaClassifier.classify(normalized, mime) ?: return null
        val mimeNorm = MediaClassifier.normalizedMime(type, mime)
        val safeHeaders = headers
            .asSequence()
            .filter { it.key.length <= MAX_HEADER_LENGTH && it.value.length <= MAX_HEADER_LENGTH }
            .take(40)
            .associate { it.key.lowercase() to it.value.take(MAX_HEADER_LENGTH) }
        val cookie = safeHeaders["cookie"]?.take(MAX_COOKIE_LENGTH)
        return MediaCandidate(
            url = normalized,
            fileName = fileName(normalized, type),
            mimeType = mimeNorm,
            pageUrl = pageUrl,
            frameUrl = frameUrl,
            type = type,
            contentLength = contentLength,
            userAgent = userAgent,
            headers = if (cookie != null) safeHeaders + mapOf("cookie" to cookie) else safeHeaders,
        )
    }

    /** Derives a sane download name from the URL path, like Aster's fileName(). */
    fun fileName(url: String, type: MediaType): String {
        val raw = runCatching {
            java.net.URI(url).path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val decoded = runCatching { java.net.URLDecoder.decode(raw ?: "", "UTF-8") }
            .getOrDefault(raw ?: "")
        val cleaned = decoded.replace(Regex("""[^\w\-. ]"""), "_").take(MAX_FILE_NAME_LENGTH)
        if (cleaned.isNotBlank() && cleaned.contains('.')) return cleaned
        val ext = when (type) {
            MediaType.HLS -> "m3u8"
            MediaType.PAGE_VIDEO -> "mp4"
            MediaType.DIRECT_VIDEO -> "mp4"
        }
        val base = cleaned.ifBlank { DEFAULT_VIDEO_FILE_NAME }
        return if (base.contains('.')) base else "$base.$ext"
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("http://")
}
