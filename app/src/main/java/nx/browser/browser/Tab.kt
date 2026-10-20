package nx.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebSettings
import android.webkit.WebView

class Tab(val id: Long) {
    var webView: WebView? = null
    var url: String = "about:blank"
    var title: String = ""
    var isLoading = false
    var loadFinished = false
    var progress = 0
    var canGoBack = false
    var canGoForward = false
    private var savedState: android.os.Bundle? = null
    /** Last URL that was loaded, kept even if the WebView is destroyed. */
    var lastUrl: String = "about:blank"
    /** Set when the renderer process died; UI can surface a message. */
    var rendererCrashed = false
    /**
     * When true the tab runs without the JS bridge, media watcher and synthetic
     * ad responses. Used after a renderer crash so the page can still load on
     * WebView providers that reject those features.
     */
    var safeMode = false

    fun onRendererGone(crashed: Boolean) {
        rendererCrashed = crashed
        isLoading = false
        loadFinished = true
        progress = 100
        canGoBack = false
        canGoForward = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun ensureWebView(context: Context, incognito: Boolean): WebView {
        webView?.let { return it }
        val wv = WebViewCompatFactory.create(context)
        runCatching { wv.settings.apply { configure(this, context, incognito) } }
        runCatching {
            CookieManager.getInstance().setAcceptCookie(!incognito)
            CookieManager.getInstance().setAcceptThirdPartyCookies(
                wv, !incognito && !BrowserSettings.blockThirdPartyCookies
            )
        }
        runCatching {
            wv.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                val referer = runCatching { wv.url }.getOrNull()
                BrowserDownloader.start(context, url, userAgent, contentDisposition, mimetype, referer)
            })
        }
        runCatching { savedState?.let { wv.restoreState(it) } }
        webView = wv
        return wv
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(s: WebSettings, context: Context, incognito: Boolean) {
        s.javaScriptEnabled = BrowserSettings.javaScriptEnabled
        s.domStorageEnabled = true
        s.databaseEnabled = !incognito
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.loadsImagesAutomatically = !BrowserSettings.imageBlocking
        s.blockNetworkImage = BrowserSettings.imageBlocking

        // Needed so GitHub / file hosts can trigger downloads and blob navigations.
        runCatching {
            s.setSupportMultipleWindows(false)
            s.javaScriptCanOpenWindowsAutomatically = true
            s.allowFileAccessFromFileURLs = false
            s.allowUniversalAccessFromFileURLs = false
        }

        // Mixed content: strict when possible (API 21+).
        runCatching { s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE }

        // Safe browsing only API 26+.
        if (BrowserSettings.supportsSafeBrowsing()) {
            runCatching { s.safeBrowsingEnabled = true }
        }

        // Performance: prefer cache, allow fetch from network only when needed.
        s.cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT

        // Faster rendering for modern pages.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            runCatching { s.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING }
        }

        s.userAgentString = if (BrowserSettings.desktopMode) desktopUa() else mobileUa(s.userAgentString)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { s.setSupportMultipleWindows(false) }
        }
    }

    fun applySettings() {
        val wv = webView ?: return
        runCatching {
            wv.settings.javaScriptEnabled = BrowserSettings.javaScriptEnabled
            wv.settings.loadsImagesAutomatically = !BrowserSettings.imageBlocking
            wv.settings.blockNetworkImage = BrowserSettings.imageBlocking
            wv.settings.userAgentString = if (BrowserSettings.desktopMode) desktopUa() else mobileUa("")
        }
    }

    fun saveState() {
        runCatching { webView?.let { wv -> savedState = android.os.Bundle().also { wv.saveState(it) } } }
    }

    fun destroy() {
        runCatching { webView?.stopLoading() }
        runCatching { webView?.destroy() }
        webView = null
    }

    private fun mobileUa(base: String): String {
        val real = if (base.isBlank() || base.contains("wv)") || base.contains("Version/"))
            DEFAULT_MOBILE_UA else base
        return real
    }

    private fun desktopUa(): String = DEFAULT_DESKTOP_UA

    companion object {
        const val DEFAULT_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        const val DEFAULT_DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }
}
