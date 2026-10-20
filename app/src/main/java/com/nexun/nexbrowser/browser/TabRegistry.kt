package com.nexun.nexbrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.nexun.nexbrowser.adblock.AdblockEngine
import com.nexun.nexbrowser.ext.UserScriptEngine
import com.nexun.nexbrowser.youtube.YouTubeExtractor
import java.util.concurrent.atomic.AtomicLong

object TabRegistry {
    const val HOME = "https://www.google.com"
    private const val MAX_LIVE_TABS = 12

    private val tabs = mutableStateListOf<Tab>()
    val activeIndex = mutableStateOf(0)
    var incognito = mutableStateOf(false)
        private set
    var webViewAvailable = mutableStateOf(true)
        private set
    /** URL of a currently playable YouTube video, or null. Drives the floating download button. */
    val detectedVideoUrl = mutableStateOf<String?>(null)
    private var appContext: Context? = null
    private val idGen = AtomicLong(1)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun current(): Tab? = tabs.getOrNull(activeIndex.value)

    fun openTab(url: String = HOME, background: Boolean = false) {
        val ctx = appContext ?: return
        if (tabs.size >= MAX_LIVE_TABS) {
            // Recycle the oldest non-active tab to keep memory bounded.
            val victim = tabs.indices.firstOrNull { it != activeIndex.value }
            if (victim != null) { tabs[victim].destroy(); tabs.removeAt(victim) }
        }
        val tab = Tab(idGen.getAndIncrement())
        tabs.add(tab)
        if (!background) activeIndex.value = tabs.lastIndex
        attach(tab, ctx)
        runCatching {
            tab.webView?.loadUrl(if (url == "about:blank") HOME else url)
        }
        if (activeIndex.value > tabs.lastIndex) activeIndex.value = tabs.lastIndex
    }

    fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        runCatching { tabs[index].destroy() }
        tabs.removeAt(index)
        if (tabs.isEmpty()) openTab()
        activeIndex.value = activeIndex.value.coerceIn(0, tabs.lastIndex)
    }

    fun select(index: Int) { activeIndex.value = index.coerceIn(0, tabs.lastIndex) }
    fun count(): Int = tabs.size
    fun tabsSnapshot(): List<Tab> = tabs.toList()

    fun setIncognito(on: Boolean) {
        if (incognito.value == on) return
        incognito.value = on
        tabs.forEach { runCatching { it.destroy() } }
        tabs.clear()
        openTab()
    }

    fun saveAllStates() { tabs.forEach { runCatching { it.saveState() } } }

    fun onTrimMemory() {
        if (tabs.size > 3) {
            tabs.take(tabs.size - 3).forEach { runCatching { it.saveState() } }
        }
        if (tabs.size > MAX_LIVE_TABS) {
            while (tabs.size > MAX_LIVE_TABS) {
                val victim = tabs.indices.firstOrNull { it != activeIndex.value } ?: break
                tabs[victim].destroy(); tabs.removeAt(victim)
            }
            activeIndex.value = activeIndex.value.coerceIn(0, tabs.lastIndex)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun attach(tab: Tab, ctx: Context) {
        val wv = try {
            tab.ensureWebView(ctx, incognito.value)
        } catch (t: Throwable) {
            webViewAvailable.value = false
            return
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean =
                handleUrlOverride(tab, view, req.url.toString())

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean =
                handleUrlOverride(tab, view, url ?: return false)

            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (!BrowserSettings.adblockEnabled) return null
                if (req.isForMainFrame) return null
                val u = req.url?.toString() ?: return null
                return runCatching {
                    if (AdblockEngine.shouldBlock(u, view.url ?: tab.url)) {
                        val empty = EMPTY_BODY
                        WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(empty))
                    } else null
                }.getOrNull()
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tab.isLoading = true
                tab.loadFinished = false
                tab.progress = 0
                url?.let { tab.url = it }
                // Inject as early as possible so cosmetics hide ads during render.
                applyContentBlocking(view, url)
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                applyContentBlocking(view, url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                tab.isLoading = false
                tab.loadFinished = true
                tab.progress = 100
                url?.let { tab.url = it }
                runCatching {
                    tab.canGoBack = view?.canGoBack() ?: false
                    tab.canGoForward = view?.canGoForward() ?: false
                }
                runCatching { UserScriptEngine.injectAll(view, url) }
                applyContentBlocking(view, url)
                detectPlayableVideo(view, url)
            }

            override fun onReceivedError(view: WebView?, req: WebResourceRequest?, err: WebResourceError?) {
                if (req?.isForMainFrame == true) {
                    tab.isLoading = false
                    tab.loadFinished = true
                    tab.progress = 100
                }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress
                if (newProgress >= 100) {
                    tab.isLoading = false
                } else if (!tab.loadFinished) {
                    tab.isLoading = true
                }
            }
            override fun onReceivedTitle(view: WebView?, title: String?) { tab.title = title ?: "" }
        }
    }

    private val EMPTY_BODY = ByteArray(0)

    private fun applyContentBlocking(view: WebView?, pageUrl: String?) {
        if (view == null) return
        if (!BrowserSettings.adblockEnabled) return
        val js = AdblockEngine.cosmeticInjection(pageUrl) ?: return
        runCatching { view.evaluateJavascript(js, null) }
    }

    /** Detects a playable video on the page and exposes it to the UI. */
    private fun detectPlayableVideo(view: WebView?, pageUrl: String?) {
        val u = pageUrl ?: return
        val isYt = Regex("""(?:youtube\.com/(?:watch|shorts|embed|live)|youtu\.be/)""").containsMatchIn(u)
        if (isYt && YouTubeExtractor.parseId(u) != null) {
            detectedVideoUrl.value = u
            return
        }
        // Generic: page itself is a media file.
        if (Regex("""\.(mp4|m3u8|webm|mkv|mov|mp3|m4a)(\?|$)""", RegexOption.IGNORE_CASE)
                .containsMatchIn(u)
        ) {
            detectedVideoUrl.value = u
            return
        }
        // Otherwise scan the DOM for a video element / media source.
        runCatching {
            view?.evaluateJavascript(SCAN_VIDEO_JS) { result ->
                val found = result?.trim('"')
                detectedVideoUrl.value = found?.takeIf { it.startsWith("http") }
            }
        }
    }

    private const val SCAN_VIDEO_JS =
        "(function(){try{" +
            "var v=document.querySelector('video');" +
            "if(v){if(v.currentSrc&&/^https?:/.test(v.currentSrc))return v.currentSrc;" +
            "var s=v.querySelector('source');if(s&&s.src&&/^https?:/.test(s.src))return s.src;}" +
            "var m=document.querySelector('video[src],audio[src],source[src]');" +
            "if(m&&m.src&&/^https?:/.test(m.src))return m.src;" +
            "var links=document.querySelectorAll('a[href]');" +
            "for(var i=0;i<links.length;i++){var h=links[i].href;" +
            "if(/\\.(mp4|m3u8|webm|mkv|mov|mp3|m4a)(\\?|$)/i.test(h))return h;}" +
            "return '';}catch(e){return '';}})()"

    private fun handleUrlOverride(tab: Tab, view: WebView, raw: String): Boolean {
        val u = raw.trim()
        if (u.isEmpty()) return false
        // Non-web schemes: hand off to the system (mailto, tel, intent, market...).
        if (u.startsWith("intent:") || u.startsWith("mailto:") || u.startsWith("tel:") ||
            u.startsWith("sms:") || u.startsWith("market:") || u.startsWith("whatsapp:")
        ) {
            runCatching {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext?.startActivity(intent)
            }
            return true
        }
        if (!u.startsWith("http://") && !u.startsWith("https://") && !u.startsWith("about:") &&
            !u.startsWith("blob:") && !u.startsWith("data:") && !u.startsWith("file:")
        ) {
            return false
        }
        // blob:/data: cannot be navigated to directly; resolve via JS download.
        if (u.startsWith("blob:")) {
            runCatching {
                view.evaluateJavascript(
                    "(function(){var a=document.createElement('a');a.href=${jsString(u)};" +
                        "a.download='archivo';document.body.appendChild(a);a.click();a.remove();})();",
                    null
                )
            }
            return true
        }
        if (BrowserSettings.httpsOnly && u.startsWith("http://")) {
            runCatching { view.loadUrl(u.replaceFirst("http://", "https://")) }
            return true
        }
        tab.url = u
        return false
    }

    private fun jsString(s: String): String {
        val e = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        return "'$e'"
    }
}
