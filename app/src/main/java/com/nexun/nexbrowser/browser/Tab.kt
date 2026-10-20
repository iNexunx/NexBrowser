package com.nexun.nexbrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

class Tab(val id: Long) {
    var webView: WebView? = null
    var url: String = "about:blank"
    var title: String = ""
    var isLoading = false
    var progress = 0
    var canGoBack = false
    var canGoForward = false
    private var savedState: android.os.Bundle? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun ensureWebView(context: Context, incognito: Boolean): WebView {
        webView?.let { return it }
        val wv = try {
            WebView(context)
        } catch (t: Throwable) {
            // Extremely rare: WebView unavailable on device.
            throw IllegalStateException("WebView no disponible en este dispositivo", t)
        }
        runCatching { wv.settings.apply { configure(this, context, incognito) } }
        runCatching {
            CookieManager.getInstance().setAcceptCookie(!incognito)
            CookieManager.getInstance().setAcceptThirdPartyCookies(
                wv, !incognito && !BrowserSettings.blockThirdPartyCookies
            )
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

        // Mixed content: strict when possible (API 21+).
        runCatching { s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW }

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

        // Third-party cookie strategy also covers API<21 gracefully.
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
            wv.settings.userAgentString =
                if (BrowserSettings.desktopMode) desktopUa() else mobileUa(wv.settings.userAgentString)
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
        val cleaned = base
            .replace(Regex("""(Version/\d+\.\d+ )?Chrome/\S+ """), "")
            .replace(Regex(""" NexBrowser/\S+"""), "")
        return "$cleaned NexBrowser/1.0"
    }

    private fun desktopUa(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 NexBrowser/1.0"
}
