package com.nexun.nexbrowser.adblock

import java.util.concurrent.ConcurrentHashMap

object AdblockEngine {
    // Exact hosts and parent-domain suffixes kept in hash sets for O(1) lookups.
    private val exactHosts = ConcurrentHashMap.newKeySet<String>()
    private val exceptions = ConcurrentHashMap.newKeySet<String>()
    // Substring rules that can't be reduced to a host (kept small & capped).
    private val keywordRules = ConcurrentHashMap.newKeySet<String>()

    @Volatile var enabled = true
    @Volatile var blockedCount = 0L
        private set

    private var seedLoaded = false

    fun ensureSeed() {
        if (seedLoaded) return
        seedLoaded = true
        loadSeed()
    }

    private fun loadSeed() {
        listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com",
            "static.ads-twitter.com", "ads.yahoo.com", "adsrvr.org",
            "amazon-adsystem.com", "taboola.com", "outbrain.com",
            "criteo.com", "scorecardresearch.com", "quantserve.com",
            "adnxs.com", "pubmatic.com", "rubiconproject.com", "openx.net",
            "smartadserver.com", "adform.net", "moatads.com", "zedo.com",
            "tracking.miui.com", "advertising.com", "casalemedia.com",
            "contextweb.com", "bidswitch.net", "33across.com", "sharethrough.com",
            "teads.tv", "yieldmo.com", "media.net", "revcontent.com",
            "adcolony.com", "applovin.com", "unityads.unity3d.com", "chartboost.com",
            "inmobi.com", "mopub.com", "startapp.com", "vungle.com",
            "crashlytics.com", "appsflyer.com", "adjust.com", "branch.io",
        ).forEach { exactHosts.add(it) }
    }

    fun addDomainRule(domain: String) {
        val d = domain.lowercase().trim()
        if (d.isEmpty() || d.length > 253 || d.contains("/")) return
        exactHosts.add(d)
    }

    fun addKeywordRule(word: String) {
        val w = word.lowercase().trim()
        if (w.length < 4 || keywordRules.size > 5000) return
        keywordRules.add(w)
    }

    fun addException(domain: String) {
        val d = domain.lowercase().trim()
        if (d.isNotEmpty()) exceptions.add(d)
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

    fun loadEasyListRules(text: String) {
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '[') return@forEach
            when {
                line.startsWith("@@") -> hostFromRule(line.removePrefix("@@"))?.let { addException(it) }
                line.contains("##") || line.contains("#@#") -> { /* cosmetic, skip */ }
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

    fun ruleCount(): Int = exactHosts.size + keywordRules.size
    fun reset() { blockedCount = 0 }
}
