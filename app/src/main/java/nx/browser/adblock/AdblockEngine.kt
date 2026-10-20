package nx.browser.adblock

import java.util.concurrent.ConcurrentHashMap

/**
 * Aggressive ad/tracker blocker.
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

    /** Built-in aggressive seed blocklist (works fully offline). */
    private val SEED_HOSTS = listOf(
        // Google ads / syndication / analytics
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com", "googleads.g.doubleclick.net",
        "partnerad.l.google.com", "ad.doubleclick.net", "stats.g.doubleclick.net",
        "googletagservices.com", "googletagmanager.com", "google-analytics.com",
        "ssl.google-analytics.com", "analytics.google.com", "www.googletagmanager.com",
        "adwords.google.com", "google.com/pagead", "fundingchoicesmessages.google.com",
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
        "pubnative.net", "verve.com", "smaato.com", "adcolony.net", "inmobi.net",
        "360yield.com", "bidr.io", "tribalfusion.com", "adroll.com", "adsafeprotected.com",
        "serving-sys.com", "sizmek.com", "flashtalking.com", "innovid.com", "spotxchange.com",
        "spotx.tv", "loopme.me", "yieldlab.net", "stickyadstv.com", "undertone.com",
        "lijit.com", "sovrn.com", "gumgum.net", "rhythmone.com", "adledge.com",
        "bluekai.com", "demdex.net", "omtrdc.net", "everesttech.net", "krxd.net",
        "adsymptotic.com", "agkn.com", "addthis.com", "mathtag.com", "netmng.com",
        "turn.com", "tapad.com", "crwdcntrl.net", "exelator.com", "liveramp.com",
        "owneriq.net", "ml314.com", "matomo.cloud", "adobedtm.com",
        // Trackers / attribution / crash
        "crashlytics.com", "appsflyer.com", "adjust.com", "branch.io", "kochava.com",
        "mparticle.com", "segment.io", "segment.com", "amplitude.com", "mixpanel.com",
        "flurry.com", "newrelic.com", "bugsnag.com", "sentry.io", "dsp.io",
        "yandex.ru/metrika", "mc.yandex.ru", "metrika.yandex.ru", "statcounter.com",
        "cdn.mxpnl.com", "api.segment.io", "logx.optimizely.com",
        // Fingerprint / beacons / heatmap
        "hotjar.com", "fullstory.com", "mouseflow.com", "smartlook.com", "clarity.ms",
        "crazyegg.com", "luckyorange.com", "inspectlet.com", "userzoom.com",
        "coinhive.com", "coin-hive.com", "cryptoloot.pro", "authedmine.com", // miners
        // Native/mobile ads
        "tracking.miui.com", "ads.mopub.com", "cdn.adx1.com", "admanmedia.com",
        "admarketplace.com", "yieldoptimizer.com", "zedo.net",
        // Popups / adult ad networks commonly deployed everywhere
        "popads.net", "popcash.net", "propellerads.com", "propellerpops.com",
        "onclickads.net", "onclckds.com", "exoclick.com", "juicyads.com",
        "trafficjunky.com", "adnium.com", "adsupply.com", "hilltopads.net",
        "adsterra.com", "clickadu.com", "mgid.com", "unknownads.com",
    )

    /** Generic cosmetic selectors (common EasyList selectors). */
    private val SEED_GENERIC = listOf(
        "[id^=\"google_ads_\"]", "[id^=\"div-gpt-ad\"]", "[id*=\"google_ads_iframe\"]",
        "ins.adsbygoogle", ".adsbygoogle", "[class*=\"ad-slot\"]", "[class*=\"ad-container\"]",
        "[id^=\"ad-\"]", "[id^=\"ads-\"]", "[class*=\"advert\"]", "[id*=\"advert\"]",
        "[class*=\"sponsored\"]", "[id*=\"taboola\"]",
        "[class*=\"taboola\"]", "[id*=\"outbrain\"]", "[class*=\"outbrain\"]",
        "[class*=\"advertisement\"]", "[id*=\"advertisement\"]", "[data-ad-slot]",
        "[data-ad-client]", "amp-ad", "amp-embed", "iframe[src*=\"doubleclick\"]",
        "iframe[src*=\"googlesyndication\"]", "[class*=\"popup-ad\"]", "[id*=\"banner-ad\"]",
        ".adBanner", ".adsbox", ".ad-placeholder", ".ad-wrapper", ".ad-leaderboard",
        "[aria-label=\"advertisement\"]", "[class*=\"native-ad\"]", "[class*=\"promoted\"]",
        "[class*=\"interstitial\"]", "[id*=\"interstitial\"]", "[class*=\"sticky-ad\"]",
        ".ad", ".ads", ".adbox", ".ad-container", ".ad-slot", ".ad-banner",
        "[class*=\"-ad\"]", "[id*=\"-ad-\"]", "[class*=\"ad_]\"]", "[id*=\"googlead\"]",
        "div[class^=\"ad\"]", "[class*=\"banner-ads\"]", "[class*=\"ads-holder\"]",
        "[id=\"adframe\"]", "[id=\"adFrame\"]", ".adthrive-ad", ".ezoic-ad",
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
        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = hostOf(url) ?: return false
        if (isExcepted(host)) return false
        // 1) Known ad/tracker host (exact or parent-domain).
        if (matchesHost(host)) { blockedCount++; return true }
        // 2) Keyword rules (path/query substrings learned from filter lists).
        if (keywordRules.isNotEmpty()) {
            val lower = url.lowercase()
            for (k in keywordRules) {
                if (lower.contains(k)) { blockedCount++; return true }
            }
        }
        // 3) Aggressive path heuristics on ANY host (Brave/Via style), except the
        //    top-level document itself.
        val pageHost = pageUrl?.let { hostOf(it) }
        val isMainDoc = pageHost != null && host == pageHost &&
            url.substringBefore('?').substringBefore('#').trimEnd('/') ==
            pageUrl?.substringBefore('?')?.substringBefore('#')?.trimEnd('/')
        if (!isMainDoc && matchesAdPath(url)) { blockedCount++; return true }
        return false
    }

    private val AD_PATH = Regex(
        """/(ad|ads|adv|advert|adserver|adservice|banner|popup|popunder|sponsor|promo|track|tracking|analytics|pixel|beacon|impression)[/_\-.]""",
        RegexOption.IGNORE_CASE
    )

    private fun matchesAdPath(url: String): Boolean {
        val noQuery = url.substringBefore('?')
        if (AD_PATH.containsMatchIn(noQuery)) return true
        val lower = url.lowercase()
        return lower.contains("ad_type=") || lower.contains("ad_slot=") ||
            lower.contains("utm_") && lower.contains("ads") ||
            lower.contains("/pagead/") || lower.contains("/gampad/") ||
            lower.contains("googlesyndication") || lower.contains("doubleclick")
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

    private fun hostOf(url: String): String? {
        runCatching { java.net.URI(url).host?.lowercase()?.takeIf { it.isNotEmpty() } }
            .getOrNull()?.let { return it }
        // Fallback for malformed URLs that URI rejects.
        val s = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
            .substringBefore('#').substringAfter('@').substringBefore(':').lowercase()
        return s.takeIf { it.isNotEmpty() && it.contains('.') }
    }

    /**
     * Builds the cosmetic CSS + scriptlet JS injected into a page on load.
     * Returns null when disabled or nothing matches (so we inject nothing).
     */
    fun cosmeticInjection(pageUrl: String?): String? {
        if (!enabled || !cosmeticEnabled) return null
        val host = pageUrl?.let { hostOf(it) } ?: ""
        val selectors = LinkedHashSet<String>()
        // Domain-scoped selectors first (most relevant, Brave-style).
        var h = host
        while (h.isNotEmpty()) {
            domainHide[h]?.let { selectors.addAll(it) }
            val idx = h.indexOf('.')
            if (idx < 0 || idx >= h.length - 1) break
            h = h.substring(idx + 1)
        }
        // Cap the generic set: injecting thousands of selectors on every page is
        // slow and rarely adds value. Keep a bounded, high-signal subset.
        if (genericHide.isNotEmpty()) {
            var g = 0
            for (s in genericHide) {
                if (selectors.size >= 400) break
                selectors.add(s); g++
                if (g > 400) break
            }
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
        // Strip trailing options ($script,third-party,...) and separators.
        val dollar = s.indexOf('$')
        if (dollar >= 0) s = s.substring(0, dollar)
        if (s.startsWith("||")) s = s.substring(2)
        s = s.trimStart('|')
        val end = s.indexOfFirst { it == '^' || it == '/' || it == '|' || it == '*' }
        if (end >= 0) s = s.substring(0, end)
        s = s.removePrefix(".").removeSuffix(".")
        if (s.isEmpty() || s.contains('*') || s.contains(' ') || !s.contains('.')) return null
        if (!s.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) return null
        return s
    }

    /**
     * Extracts path/segment keywords from path-based rules like "/adv/" and "||x.com/banner^".
     * Kept short and hash-lookup friendly so blocking stays cheap per request.
     */
    private fun keywordFromRule(rule: String): String? {
        var s = rule
        val dollar = s.indexOf('$')
        if (dollar >= 0) s = s.substring(0, dollar)
        if (s.startsWith("||")) {
            // Host part may already be captured; look for a meaningful path segment.
            val slash = s.indexOf('/')
            val caret = s.indexOf('^')
            val start = when {
                slash >= 0 && (caret < 0 || slash < caret) -> slash
                caret >= 0 -> caret
                else -> return null
            }
            s = s.substring(start)
        }
        s = s.trim('|', '^', '/', '*', '$', '.')
        if (s.length < 5 || s.length > 60 || s.contains('*') || s.contains(' ') || s.contains('#')) return null
        if (!s.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == '/' }) return null
        // Require at least one letter to avoid pure-number noise.
        if (s.none { it.isLetter() }) return null
        return s.lowercase()
    }

    fun ruleCount(): Int = exactHosts.size + keywordRules.size + genericHide.size
    fun cosmeticRuleCount(): Int = genericHide.size + domainHide.size
    fun reset() { blockedCount = 0; cosmeticCount = 0 }

    /** Serialize learned (non-seed) rules so they survive restarts. */
    fun exportRules(): String {
        val sb = StringBuilder()
        exactHosts.forEach { if (it !in SEED_HOSTS) sb.append("||").append(it).append('\n') }
        keywordRules.forEach { sb.append(it).append('\n') }
        genericHide.forEach { if (it !in SEED_GENERIC) sb.append("##").append(it).append('\n') }
        domainHide.forEach { (d, sels) -> sels.forEach { sb.append(d).append("##").append(it).append('\n') } }
        return sb.toString()
    }

    fun loadRules(text: String) = loadEasyListRules(text)
}
