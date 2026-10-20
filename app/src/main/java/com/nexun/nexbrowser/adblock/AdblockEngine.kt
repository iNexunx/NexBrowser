package com.nexun.nexbrowser.adblock

import java.util.concurrent.ConcurrentHashMap

/**
 * Brave-style aggressive ad/tracker blocker.
 *
 * - Network blocking by exact host + parent-domain suffix (O(1) hash lookups).
 * - Keyword substring rules for path/query-based ads.
 * - Cosmetic filtering: generic CSS selectors + per-domain hide rules.
 * - Scriptlet injection (anti-adblock-bypass + first-party ad removal helpers).
 * - Exception rules (@@) so "allow" wins over block.
 * - Live counters for the UI.
 */
object AdblockEngine {
    private val exactHosts = ConcurrentHashMap.newKeySet<String>()
    private val exceptions = ConcurrentHashMap.newKeySet<String>()
    private val keywordRules = ConcurrentHashMap.newKeySet<String>()

    // Cosmetic: generic selectors applied on every page.
    private val genericHide = ConcurrentHashMap.newKeySet<String>()
    // Cosmetic: host-suffix -> selectors applied only on matching sites.
    private val domainHide = ConcurrentHashMap<String, MutableSet<String>>()

    @Volatile var enabled = true
    @Volatile var blockedCount = 0L
        private set
    @Volatile var cosmeticCount = 0L
        private set
    @Volatile var cosmeticEnabled = true

    private var seedLoaded = false

    /** Built-in aggressive seed blocklist (Brave-like categories). */
    private val SEED_HOSTS = listOf(
        // Google ads / syndication / analytics
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "partnerad.l.google.com", "ad.doubleclick.net", "stats.g.doubleclick.net",
        "googletagservices.com", "googletagmanager.com", "google-analytics.com",
        "ssl.google-analytics.com", "analytics.google.com", "googlesyndication.com",
        // Big ad networks / exchanges
        "ads.yahoo.com", "advertising.com", "adsrvr.org", "amazon-adsystem.com",
        "taboola.com", "taboolanews.com", "outbrain.com", "criteo.com", "criteo.net",
        "scorecardresearch.com", "quantserve.com", "quantcast.com", "adnxs.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "smartadserver.com",
        "adform.net", "moatads.com", "zedo.com", "casalemedia.com", "contextweb.com",
        "bidswitch.net", "33across.com", "sharethrough.com", "teads.tv", "yieldmo.com",
        "media.net", "revcontent.com", "seedtag.com", "gumgum.com", "districtm.io",
        "improvedigital.com", "smaato.net", "mopub.com", "adcolony.com", "applovin.com",
        "unityads.unity3d.com", "chartboost.com", "inmobi.com", "startapp.com",
        "vungle.com", "ironsrc.com", "supersonicads.com", "fyber.com", "tapjoy.com",
        // Trackers / attribution / crash
        "crashlytics.com", "appsflyer.com", "adjust.com", "branch.io", "kochava.com",
        "mparticle.com", "segment.io", "segment.com", "amplitude.com", "mixpanel.com",
        "flurry.com", "newrelic.com", "bugsnag.com", "sentry.io", "dsp.io",
        // Fingerprint / beacons / heatmap
        "hotjar.com", "fullstory.com", "mouseflow.com", "smartlook.com", "clarity.ms",
        "crazyegg.com", "luckyorange.com", "inspectlet.com", "userzoom.com",
        // Native/mobile ads
        "tracking.miui.com", "ads.mopub.com", "cdn.adx1.com",
    )

    /** Generic cosmetic selectors (Brave/EasyList common). */
    private val SEED_GENERIC = listOf(
        "[id^=\"google_ads_\"]", "[id^=\"div-gpt-ad\"]", "[id*=\"google_ads_iframe\"]",
        "ins.adsbygoogle", ".adsbygoogle", "[class*=\"ad-slot\"]", "[class*=\"ad-container\"]",
        "[class^=\"ad-\"]", "[class*=\"-ad-\"]", "[id^=\"ad-\"]", "[id^=\"ads-\"]",
        "[class*=\"sponsored\"]", "[class*=\"Sponsored\"]", "[id*=\"taboola\"]",
        "[class*=\"taboola\"]", "[id*=\"outbrain\"]", "[class*=\"outbrain\"]",
        "[class*=\"advertisement\"]", "[id*=\"advertisement\"]", "[data-ad-slot]",
        "[data-ad-client]", "amp-ad", "amp-embed", "iframe[src*=\"doubleclick\"]",
        "iframe[src*=\"googlesyndication\"]", "[class*=\"popup-ad\"]", "[id*=\"banner-ad\"]",
        ".adBanner", ".adsbox", ".ad-placeholder", ".ad-wrapper", ".ad-leaderboard",
        "[aria-label=\"advertisement\"]", "[class*=\"native-ad\"]", "[class*=\"promoted\"]",
        "[class*=\"interstitial\"]", "[id*=\"interstitial\"]", "[class*=\"sticky-ad\"]",
    )

    fun ensureSeed() {
        if (seedLoaded) return
        synchronized(this) {
            if (seedLoaded) return
            seedLoaded = true
            SEED_HOSTS.forEach { exactHosts.add(it) }
            SEED_GENERIC.forEach { genericHide.add(it) }
        }
    }

    fun addDomainRule(domain: String) {
        val d = domain.lowercase().trim()
        if (d.isEmpty() || d.length > 253 || d.contains("/")) return
        exactHosts.add(d)
    }

    fun addKeywordRule(word: String) {
        val w = word.lowercase().trim()
        if (w.length < 4 || keywordRules.size > 8000) return
        keywordRules.add(w)
    }

    fun addException(domain: String) {
        val d = domain.lowercase().trim()
        if (d.isNotEmpty()) exceptions.add(d)
    }

    fun addGenericCosmetic(selector: String) {
        val s = selector.trim()
        if (s.length in 2..300 && genericHide.size < 20000) genericHide.add(s)
    }

    fun addDomainCosmetic(domain: String, selector: String) {
        val d = domain.lowercase().trim()
        val s = selector.trim()
        if (d.isEmpty() || s.length !in 2..300) return
        domainHide.getOrPut(d) { ConcurrentHashMap.newKeySet() }.add(s)
    }

    fun shouldBlock(url: String, pageUrl: String?): Boolean {
        if (!enabled) return false
        val host = hostOf(url) ?: return false
        if (isExcepted(host)) return false
        if (matchesHost(host)) { blockedCount++; return true }
        if (keywordRules.isNotEmpty()) {
            val lower = url.lowercase()
            for (k in keywordRules) {
                if (lower.contains(k)) { blockedCount++; return true }
            }
        }
        return false
    }

    private fun matchesHost(host: String): Boolean {
        if (exactHosts.contains(host)) return true
        var idx = host.indexOf('.')
        while (idx >= 0 && idx < host.length - 1) {
            if (exactHosts.contains(host.substring(idx + 1))) return true
            idx = host.indexOf('.', idx + 1)
        }
        return false
    }

    private fun isExcepted(host: String): Boolean {
        if (exceptions.isEmpty()) return false
        if (exceptions.contains(host)) return true
        var idx = host.indexOf('.')
        while (idx >= 0 && idx < host.length - 1) {
            if (exceptions.contains(host.substring(idx + 1))) return true
            idx = host.indexOf('.', idx + 1)
        }
        return false
    }

    private fun hostOf(url: String): String? =
        runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()

    /**
     * Builds the cosmetic CSS + scriptlet JS injected into a page on load.
     * Returns null when disabled or nothing matches (so we inject nothing).
     */
    fun cosmeticInjection(pageUrl: String?): String? {
        if (!enabled || !cosmeticEnabled) return null
        val host = pageUrl?.let { hostOf(it) } ?: ""
        val selectors = LinkedHashSet<String>()
        if (genericHide.isNotEmpty()) selectors.addAll(genericHide)
        // Domain-scoped selectors.
        var h = host
        while (h.isNotEmpty()) {
            domainHide[h]?.let { selectors.addAll(it) }
            val idx = h.indexOf('.')
            if (idx < 0 || idx >= h.length - 1) break
            h = h.substring(idx + 1)
        }
        if (selectors.isEmpty()) return null
        val css = selectors.joinToString(",") { it } + "{display:none!important;}"
        cosmeticCount += selectors.size
        return buildString {
            append("(function(){try{var s=document.createElement('style');")
            append("s.type='text/css';s.textContent=")
            append(jsQuote(css))
            append(";(document.head||document.documentElement).appendChild(s);}catch(e){}})();")
            append(SCRIPTLETS)
        }
    }

    /** Scriptlets: neutralize common anti-adblock detection. */
    private val SCRIPTLETS = """
        (function(){
        try{
          var noop=function(){return {close:noop,remove:noop,style:{},classList:{add:noop,remove:noop}};};
          window.canRunAds=true;
          Object.defineProperty(window,'blockAdBlock',{value:undefined,configurable:true});
          Object.defineProperty(window,'adblock',{get:function(){return false;},configurable:true});
          Object.defineProperty(window,'AdBlock',{value:undefined,configurable:true});
          window.adsbygoogle=window.adsbygoogle||[];window.adsbygoogle.loaded=true;
          window.adsbygoogle.push=function(){};
          if(!window.googletag){window.googletag={cmd:[],apiReady:true,defineSlot:function(){},pubads:function(){return{}},enableServices:function(){},display:function(){}};}
        }catch(e){}
        })();
    """.trimIndent()

    private fun jsQuote(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r")
        return "\"$escaped\""
    }

    fun loadEasyListRules(text: String) {
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '[') return@forEach
            when {
                line.contains("##") && !line.startsWith("@@") -> parseCosmetic(line, false)
                line.contains("#@#") -> parseCosmetic(line.replace("#@#", "##"), true)
                line.startsWith("@@") -> hostFromRule(line.removePrefix("@@"))?.let { addException(it) }
                else -> {
                    val host = hostFromRule(line)
                    if (host != null) addDomainRule(host)
                    else {
                        val kw = keywordFromRule(line)
                        if (kw != null) addKeywordRule(kw)
                    }
                }
            }
        }
    }

    private fun parseCosmetic(line: String, isException: Boolean) {
        val idx = line.indexOf("##")
        if (idx <= 0) return
        val domainPart = line.substring(0, idx)
        val selector = line.substring(idx + 2)
        if (selector.isEmpty() || selector.startsWith("+js(") || selector.startsWith("^")) return
        val cleanSel = selector.substringBefore(":style(").substringBefore(":remove(")
        if (cleanSel.isEmpty()) return
        if (domainPart.isEmpty()) {
            if (!isException) addGenericCosmetic(cleanSel)
        } else {
            domainPart.split(',').forEach { d ->
                val dom = d.trim().removePrefix("~")
                if (dom.isNotEmpty() && !d.trim().startsWith("~")) addDomainCosmetic(dom, cleanSel)
            }
        }
    }

    private fun hostFromRule(rule: String): String? {
        var s = rule
        if (s.startsWith("||")) s = s.substring(2)
        s = s.trimStart('|')
        val end = s.indexOfFirst { it == '^' || it == '/' || it == '|' || it == '$' || it == '*' }
        if (end >= 0) s = s.substring(0, end)
        s = s.removePrefix(".").removeSuffix(".")
        if (s.isEmpty() || s.contains('*') || s.contains(' ') || !s.contains('.')) return null
        if (!s.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) return null
        return s
    }

    private fun keywordFromRule(rule: String): String? {
        var s = rule
        if (s.startsWith("||")) return null
        s = s.trim('|', '^', '/', '*', '$', '.')
        if (s.length < 5 || s.contains('*') || s.contains(' ')) return null
        if (s.matches(Regex("""[\w\-./]+"""))) return s.lowercase()
        return null
    }

    fun ruleCount(): Int = exactHosts.size + keywordRules.size + genericHide.size
    fun cosmeticRuleCount(): Int = genericHide.size + domainHide.size
    fun reset() { blockedCount = 0; cosmeticCount = 0 }
}
