package nx.browser

import nx.browser.adblock.AdblockEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM simulation of the adblock engine (no Android APIs used by the engine). */
class AdblockEngineTest {
    @Test
    fun blocksKnownAdAndTrackerHosts() {
        AdblockEngine.ensureSeed()
        val page = "https://news.example.com/article"
        val ads = listOf(
            "https://securepubads.g.doubleclick.net/gampad/ads?sz=300x250",
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://googleads.g.doubleclick.net/pagead/id",
            "https://static.criteo.net/js/ld/publishertag.js",
            "https://cdn.taboola.com/libtrc/somepublisher/loader.js",
            "https://www.google-analytics.com/analytics.js",
            "https://tracking.miui.com/track",
            "https://ads.yahoo.com/ad",
            "https://sb.scorecardresearch.com/beacon.js",
            "https://cdn.juicyads.com/ad.js",
        )
        for (u in ads) {
            assertTrue("should block $u", AdblockEngine.shouldBlock(u, page))
        }
    }

    @Test
    fun neverBlocksSameHostAsPage() {
        // First-party resources are functional (site CSS/JS/ads under its own host);
        // blocking them was what produced "sin conexión". They must pass through.
        val page = "https://site.example.com/"
        assertFalse(AdblockEngine.shouldBlock("https://site.example.com/ads/banner.png", page))
        assertFalse(AdblockEngine.shouldBlock("https://www.site.example.com/tracking/pixel.gif", page))
        assertFalse(AdblockEngine.shouldBlock("https://site.example.com/pagead/loader.js", page))
    }

    @Test
    fun blocksOnlyClearAdServersThirdParty() {
        val page = "https://site.example.com/"
        assertTrue(AdblockEngine.shouldBlock("https://adserver.example.net/adset.js", page))
        assertTrue(AdblockEngine.shouldBlock("https://cdn.doubleclick.net/x", page))
        assertTrue(AdblockEngine.shouldBlock("https://x.com/pagead/js/adsbygoogle.js", page))
    }

    @Test
    fun allowsNormalContent() {
        val page = "https://site.example.com/"
        assertFalse(AdblockEngine.shouldBlock("https://site.example.com/style.css", page))
        assertFalse(AdblockEngine.shouldBlock("https://cdn.example.com/app.js", page))
        assertFalse(AdblockEngine.shouldBlock("https://site.example.com/products/123", page))
        // Generic analytics-like third-party assets must NOT be path-blocked anymore.
        assertFalse(AdblockEngine.shouldBlock("https://cdn.example.com/assets/analytics.js", page))
    }

    @Test
    fun exceptionsWinOverBlocks() {
        AdblockEngine.addException("allowed.com")
        assertFalse(AdblockEngine.shouldBlock("https://ads.allowed.com/ad.js", "https://x.com"))
    }

    @Test
    fun parsesEasyListSyntax() {
        // Trailing options + path must still yield the host.
        AdblockEngine.loadRules("||evil-ads.net^\$script,third-party")
        assertTrue(AdblockEngine.shouldBlock("https://sub.evil-ads.net/x.js", "https://x.com"))
    }
}
