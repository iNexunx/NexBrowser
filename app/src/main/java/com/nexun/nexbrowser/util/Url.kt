package com.nexun.nexbrowser.util

import com.nexun.nexbrowser.browser.BrowserSettings
import java.net.URLEncoder

object Url {
    private val hostLike = Regex("""^[\w-]+(\.[\w-]+)+(:\d+)?(/.*)?$""")

    fun normalize(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return "https://www.google.com"
        return when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.startsWith("about:") || t.startsWith("file://") || t.startsWith("data:") -> t
            t.startsWith("javascript:") -> return "about:blank"
            t.contains(" ") -> search(t)
            hostLike.matches(t) -> "https://$t"
            else -> search(t)
        }
    }

    private fun search(q: String): String =
        BrowserSettings.searchPrefix() + URLEncoder.encode(q, "UTF-8")

    fun host(url: String): String =
        runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")

    fun prettyHost(url: String): String = host(url).removePrefix("www.")

    fun isSecure(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("about:")

    fun isHttp(url: String): Boolean = url.startsWith("http://")

    fun scheme(url: String): String =
        runCatching { java.net.URI(url).scheme ?: "" }.getOrDefault("")

    fun display(url: String): String {
        if (url == "about:blank" || url.isEmpty()) return ""
        val h = prettyHost(url)
        if (h.isEmpty()) return url
        val p = runCatching {
            val path = java.net.URI(url).path ?: ""
            if (path == "/") "" else path
        }.getOrDefault("")
        return h + p
    }
}
