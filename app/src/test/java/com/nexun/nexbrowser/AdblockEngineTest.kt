package com.nexun.nexbrowser

import com.nexun.nexbrowser.adblock.AdblockEngine
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
    fun blocksAdPathsOnFirstPartyHost() {
        val page = "https://site.example.com/"
        assertTrue(AdblockEngine.shouldBlock("https://site.example.com/ads/banner.png", page))
        assertTrue(AdblockEngine.shouldBlock("https://site.example.com/tracking/pixel.gif", page))
    }

    @Test
    fun allowsNormalContent() {
        val page = "https://site.example.com/"
        assertFalse(AdblockEngine.shouldBlock("https://site.example.com/style.css", page))
        assertFalse(AdblockEngine.shouldBlock("https://cdn.example.com/app.js", page))
        assertFalse(AdblockEngine.shouldBlock("https://site.example.com/products/123", page))
    }

    @Test
    fun exceptionsWinOverBlocks() {
        AdblockEngine.addException("allowed.com")
        assertFalse(AdblockEngine.shouldBlock("https://ads.allowed.com/ad.js", "https://x.com"))
    }

    @Test
    fun parsesEasyListSyntax() {
        // Trailing options + path must still yield the host.
        AdblockEngine.loadRules("||evil-ads.net^$script,third-party")
        assertTrue(AdblockEngine.shouldBlock("https://sub.evil-ads.net/x.js", "https://x.com"))
    }
}
