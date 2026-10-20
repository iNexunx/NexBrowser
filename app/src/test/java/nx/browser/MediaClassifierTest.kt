package nx.browser

import nx.browser.media.capture.MediaClassifier
import nx.browser.media.capture.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the Aster-derived media classifier. */
class MediaClassifierTest {
    @Test
    fun classifiesDirectVideoByExtension() {
        assertEquals(
            MediaType.DIRECT_VIDEO,
            MediaClassifier.classify("https://cdn.example.com/clip.mp4", null)
        )
        assertEquals(
            MediaType.DIRECT_VIDEO,
            MediaClassifier.classify("https://cdn.example.com/clip.webm?x=1", "video/webm")
        )
    }

    @Test
    fun classifiesHls() {
        assertEquals(MediaType.HLS, MediaClassifier.classify("https://x.com/master.m3u8", null))
        assertEquals(
            MediaType.HLS,
            MediaClassifier.classify("https://x.com/play", "application/vnd.apple.mpegurl")
        )
    }

    @Test
    fun ignoresSegmentsAndNonMedia() {
        assertNull(MediaClassifier.classify("https://x.com/seg.m4s", "video/mp4"))
        assertNull(MediaClassifier.classify("https://x.com/seg.ts", "video/mp2t"))
        assertNull(MediaClassifier.classify("https://x.com/seg.cmfv", null))
        assertNull(MediaClassifier.classify("https://x.com/page.html", "text/html"))
        assertNull(MediaClassifier.classify("https://x.com/app.js", "application/javascript"))
    }

    @Test
    fun pageVideoOnlyForSupportedSites() {
        assertTrue(MediaClassifier.isSupportedPageVideoUrl("https://www.tiktok.com/@a/video/1"))
        assertTrue(MediaClassifier.isSupportedPageVideoUrl("https://vm.tiktok.com/abc/"))
        assertTrue(MediaClassifier.isSupportedPageVideoUrl("https://www.instagram.com/reel/x/"))
        assertTrue(MediaClassifier.isSupportedPageVideoUrl("https://fb.watch/abc/"))
        assertFalse(MediaClassifier.isSupportedPageVideoUrl("https://example.com/watch"))
    }

    @Test
    fun normalizesRangeQuery() {
        assertEquals(
            "https://x.com/v.mp4?a=1",
            MediaClassifier.normalizeMediaUrl("https://x.com/v.mp4?a=1&bytestart=0&byteend=99")
        )
    }

    @Test
    fun normalizedMimeFillsDefaults() {
        assertEquals("video/mp4", MediaClassifier.normalizedMime(MediaType.DIRECT_VIDEO, null))
        assertEquals("video/mp4", MediaClassifier.normalizedMime(MediaType.DIRECT_VIDEO, "application/octet-stream"))
        assertEquals("video/webm", MediaClassifier.normalizedMime(MediaType.DIRECT_VIDEO, "video/webm"))
    }
}
