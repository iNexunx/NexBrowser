package nx.browser.ext

import android.webkit.WebView

object UserScriptEngine {
    data class Script(val name: String, val domains: List<String>, val js: String)

    private val scripts = mutableListOf<Script>()
    private val customCss = mutableListOf<String>()

    fun addScript(s: Script) { scripts.add(s) }
    fun addCss(css: String) { customCss.add(css) }
    fun clear() { scripts.clear(); customCss.clear() }
    fun count() = scripts.size

    fun injectAll(view: WebView?, url: String?) {
        if (view == null || url == null) return
        val host = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")
        val sb = StringBuilder()
        scripts.filter { s -> s.domains.any { host == it || host.endsWith(".$it") } }
            .forEach { sb.append(it.js).append('\n') }
        if (customCss.isNotEmpty()) {
            sb.append("var __s=document.createElement('style');__s.textContent='")
                .append(customCss.joinToString("\n").replace("'", "\\'"))
                .append("';document.head.appendChild(__s);")
        }
        if (sb.isNotEmpty()) view.evaluateJavascript(sb.toString(), null)
    }
}
