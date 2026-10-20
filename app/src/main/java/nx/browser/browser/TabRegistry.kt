package nx.browser.browser

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
import nx.browser.adblock.AdblockEngine
import nx.browser.ext.UserScriptEngine
import nx.browser.youtube.YouTubeExtractor
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

    fun markWebViewUnavailable() { webViewAvailable.value = false }
    /** URL of a currently playable YouTube video, or null. Drives the floating download button. */
    val detectedVideoUrl = mutableStateOf<String?>(null)
    /** Long-press context menu target (kind, url, extra). */
    val longPress = mutableStateOf<LongPress?>(null)
    private var appContext: Context? = null
    private val idGen = AtomicLong(1)

    data class LongPress(val kind: Kind, val url: String, val label: String) {
        enum class Kind { LINK, IMAGE, MEDIA, GENERIC }
    }

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

    /**
     * Rebuilds the active tab's WebView after its renderer process died, reloading
     * the last URL. Called from WebViewClient.onRenderProcessGone so the app keeps
     * running instead of being killed by the framework.
     */
    fun reopenActiveTab() {
        val ctx = appContext ?: return
        val tab = current() ?: return
        runCatching {
            val target = tab.lastUrl.takeIf { it.isNotBlank() && it != "about:blank" } ?: tab.url
            attach(tab, ctx)
            tab.webView?.loadUrl(if (target == "about:blank") HOME else target)
        }
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
        runCatching {
            if (!tab.safeMode) wv.addJavascriptInterface(MediaBridge, "NexMedia")
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean =
                handleUrlOverride(tab, view, req.url.toString())

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean =
                handleUrlOverride(tab, view, url ?: return false)

            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (tab.safeMode) return null
                val pageUrl = view.url ?: tab.url
                if (!BrowserSettings.shieldsEnabledFor(hostOfSafe(pageUrl))) return null
                val u = req.url?.toString() ?: return null
                return runCatching {
                    if (!AdblockEngine.shouldBlock(u, pageUrl)) return@runCatching null
                    // Never blank out a top-level document; only block subresources.
                    if (req.isForMainFrame) return@runCatching null
                    // Only synthesize responses for safe request types/mimes. Returning a
                    // bogus body for scripts/XHR/documents is a common cause of renderer
                    // crashes across WebView providers, so those are passed through.
                    val accept = req.requestHeaders?.get("Accept") ?: ""
                    val isImage = accept.contains("image") ||
                        Regex("""\.(png|jpe?g|gif|webp|svg|ico|bmp)(\?|$)""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(u)
                    if (isImage) {
                        WebResourceResponse("image/gif", "utf-8", java.io.ByteArrayInputStream(PIXEL_GIF))
                    } else if (accept.contains("text/css")) {
                        WebResourceResponse("text/css", "utf-8", java.io.ByteArrayInputStream(EMPTY_BODY))
                    } else if (accept.contains("application/javascript") || accept.contains("text/javascript") ||
                        Regex("""\.js(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(u)
                    ) {
                        WebResourceResponse(
                            "application/javascript", "utf-8",
                            java.io.ByteArrayInputStream("/* blocked */".toByteArray())
                        )
                    } else if (accept.contains("text/html") && !req.isForMainFrame) {
                        WebResourceResponse("text/html", "utf-8", java.io.ByteArrayInputStream(EMPTY_BODY))
                    } else {
                        // Unknown/binary subresource (XHR, fonts, media): return a valid
                        // empty 204 instead of a malformed body.
                        WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(EMPTY_BODY))
                            .apply { setStatusCodeAndReasonPhrase(204, "No Content") }
                    }
                }.getOrNull()
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tab.isLoading = true
                tab.loadFinished = false
                tab.progress = 0
                tab.rendererCrashed = false
                url?.let { tab.url = it; tab.lastUrl = it }
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
                url?.let { tab.url = it; tab.lastUrl = it }
                runCatching {
                    tab.canGoBack = view?.canGoBack() ?: false
                    tab.canGoForward = view?.canGoForward() ?: false
                }
                runCatching { UserScriptEngine.injectAll(view, url) }
                applyContentBlocking(view, url)
                applyMediaWatch(view)
                detectPlayableVideo(view, url)
            }

            override fun onReceivedError(view: WebView?, req: WebResourceRequest?, err: WebResourceError?) {
                if (req?.isForMainFrame == true) {
                    tab.isLoading = false
                    tab.loadFinished = true
                    tab.progress = 100
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                // Returning true prevents Android from killing the whole app when the
                // renderer dies (incompatible WebView / OOM / vendor bug). Enable safe
                // mode for this tab (no JS bridge / synthetic responses) and rebuild.
                val crashed = detail?.didCrash() ?: false
                tab.onRendererGone(crashed)
                tab.safeMode = true
                runCatching {
                    (view?.parent as? android.view.ViewGroup)?.removeView(view)
                    view?.destroy()
                }
                tab.webView = null
                webViewAvailable.value = true
                runCatching { reopenActiveTab() }
                return true
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
        runCatching {
            wv.setOnLongClickListener { v ->
                val web = v as? WebView ?: return@setOnLongClickListener false
                val r = web.hitTestResult
                val extra = r.extra ?: ""
                val kind = when (r.type) {
                    android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE,
                    android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ->
                        LongPress.Kind.LINK
                    android.webkit.WebView.HitTestResult.IMAGE_TYPE ->
                        LongPress.Kind.IMAGE
                    else -> if (Regex("""\.(mp4|m3u8|webm|mp3|m4a)(\?|$)""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(extra)
                    ) LongPress.Kind.MEDIA else LongPress.Kind.GENERIC
                }
                val url = when (kind) {
                    LongPress.Kind.LINK -> r.extra ?: ""
                    LongPress.Kind.IMAGE -> r.extra ?: ""
                    else -> r.extra ?: ""
                }
                if (url.isBlank() && kind == LongPress.Kind.GENERIC) return@setOnLongClickListener false
                longPress.value = LongPress(kind, url, url)
                true
            }
        }
    }

    private val EMPTY_BODY = ByteArray(0)

    /** 1x1 transparent GIF (43 bytes) returned in place of blocked ad images. */
    private val PIXEL_GIF = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00,
        0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21, 0xF9.toByte(),
        0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01,
        0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
    )

    /** JS bridge: web media events toggle the background playback service. */
    private object MediaBridge {
        @android.webkit.JavascriptInterface
        fun onMediaPlaying(title: String?) {
            val ctx = appContext ?: return
            nx.browser.media.MediaControl.start(
                ctx, title?.takeIf { it.isNotBlank() } ?: "Reproduciendo"
            )
        }

        @android.webkit.JavascriptInterface
        fun onMediaStopped() {
            val ctx = appContext ?: return
            nx.browser.media.MediaControl.stop(ctx)
        }

        @android.webkit.JavascriptInterface
        fun onDownloadBlob(base64: String?, name: String?, mime: String?) {
            val ctx = appContext ?: return
            if (base64.isNullOrBlank()) return
            nx.browser.download.BlobDownloader.save(ctx, base64, name ?: "archivo", mime)
        }
    }

    private val MEDIA_WATCH_JS = """
        (function(){
          if(window.__nexMediaWatch) return; window.__nexMediaWatch=true;
          function report(){
            try{
              var list=document.querySelectorAll('video,audio');var playing=false;
              for(var i=0;i<list.length;i++){
                var m=list[i];
                if(!m.paused && !m.ended && m.readyState>2){playing=true;}
                if(!m.__nexHooked){
                  m.__nexHooked=true;
                  m.addEventListener('play',function(){ if(window.NexMedia) NexMedia.onMediaPlaying(document.title); });
                  m.addEventListener('playing',function(){ if(window.NexMedia) NexMedia.onMediaPlaying(document.title); });
                  m.addEventListener('pause',function(){ setTimeout(check,300); });
                  m.addEventListener('ended',function(){ setTimeout(check,300); });
                }
              }
              if(playing && window.NexMedia) NexMedia.onMediaPlaying(document.title);
              else check();
            }catch(e){}
          }
          function check(){
            try{
              var list=document.querySelectorAll('video,audio');var p=false;
              for(var i=0;i<list.length;i++){var m=list[i];if(!m.paused&&!m.ended&&m.readyState>2){p=true;break;}}
              if(!p && window.NexMedia) NexMedia.onMediaStopped();
            }catch(e){}
          }
          document.addEventListener('play',function(){ if(window.NexMedia) NexMedia.onMediaPlaying(document.title); },true);
          document.addEventListener('pause',function(){ setTimeout(check,300); },true);
          document.addEventListener('ended',function(){ setTimeout(check,300); },true);
          setInterval(report, 2000);
        })();
    """.trimIndent()

    private fun applyMediaWatch(view: WebView?) {
        view ?: return
        if (current()?.safeMode == true) return
        runCatching { view.evaluateJavascript(MEDIA_WATCH_JS, null) }
    }

    private fun applyContentBlocking(view: WebView?, pageUrl: String?) {
        if (view == null) return
        if (current()?.safeMode == true) return
        if (!BrowserSettings.shieldsEnabledFor(hostOfSafe(pageUrl))) return
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
        if (u.startsWith("blob:") || u.startsWith("data:")) {
            runCatching {
                view.evaluateJavascript(nexBlobDownloadJs(u), null)
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

    private fun hostOfSafe(url: String?): String? {
        val u = url ?: return null
        runCatching { java.net.URI(u).host?.lowercase()?.takeIf { it.isNotEmpty() } }
            .getOrNull()?.let { return it }
        val s = u.substringAfter("://", "").substringBefore('/').substringBefore('?')
            .substringBefore('#').substringAfter('@').substringBefore(':').lowercase()
        return s.takeIf { it.isNotEmpty() && it.contains('.') }
    }

    private fun jsString(s: String): String {
        val e = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        return "'$e'"
    }

    /**
     * Resolves a blob:/data: URL into a base64 payload and hands it to the native
     * side via the NexMedia bridge, which writes it to the Downloads folder.
     */
    private fun nexBlobDownloadJs(url: String): String = """
        (function(){
          try{
            var u = ${jsString(url)};
            var name = 'archivo';
            try {
              var seg = u.substring(u.lastIndexOf('/') + 1).split('?')[0];
              if (u.indexOf('blob:') === 0) {
                var a = document.querySelector('a[href="' + u + '"]');
                if (a && a.download) seg = a.download;
              }
              if (seg && seg.length) name = decodeURIComponent(seg);
            } catch(e) {}
            fetch(u).then(function(r){ return r.blob(); }).then(function(b){
              var fr = new FileReader();
              fr.onload = function(){
                var data = fr.result;
                var comma = data.indexOf(',');
                var b64 = data.substring(comma + 1);
                var mime = b.type || 'application/octet-stream';
                if(window.NexMedia && NexMedia.onDownloadBlob) NexMedia.onDownloadBlob(b64, name, mime);
              };
              fr.readAsDataURL(b);
            }).catch(function(e){});
          }catch(e){}
        })();
    """.trimIndent()
}
