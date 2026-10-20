package com.nexun.nexbrowser.browser

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nexun.nexbrowser.adblock.AdblockEngine

object BrowserSettings {
    private const val PREFS = "nexbrowser_settings"
    private var ctx: Context? = null

    var javaScriptEnabled by mutableStateOf(true); private set
    var httpsOnly by mutableStateOf(true); private set
    var blockThirdPartyCookies by mutableStateOf(true); private set
    var doNotTrack by mutableStateOf(true); private set
    var adblockEnabled by mutableStateOf(true); private set
    var desktopMode by mutableStateOf(false); private set
    var darkMode by mutableStateOf<Boolean?>(null); private set
    var searchEngine by mutableStateOf("google"); private set
    var imageBlocking by mutableStateOf(false); private set

    val searchEngines: List<Pair<String, String>> = listOf(
        "google" to "https://www.google.com/search?q=",
        "duckduckgo" to "https://duckduckgo.com/?q=",
        "bing" to "https://www.bing.com/search?q=",
        "brave" to "https://search.brave.com/search?q=",
    )

    fun init(context: Context) {
        ctx = context.applicationContext
        val p = ctx!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        javaScriptEnabled = p.getBoolean("js", true)
        httpsOnly = p.getBoolean("https_only", true)
        blockThirdPartyCookies = p.getBoolean("block_3pc", true)
        doNotTrack = p.getBoolean("dnt", true)
        adblockEnabled = p.getBoolean("adblock", true)
        desktopMode = p.getBoolean("desktop", false)
        imageBlocking = p.getBoolean("images", false)
        searchEngine = p.getString("search", "google") ?: "google"
        darkMode = if (p.contains("dark")) p.getBoolean("dark", false) else null
        AdblockEngine.enabled = adblockEnabled
    }

    fun updateJavaScript(on: Boolean) { javaScriptEnabled = on; persist("js", on) }
    fun updateHttpsOnly(on: Boolean) { httpsOnly = on; persist("https_only", on) }
    fun updateBlockThirdPartyCookies(on: Boolean) { blockThirdPartyCookies = on; persist("block_3pc", on) }
    fun updateDoNotTrack(on: Boolean) { doNotTrack = on; persist("dnt", on) }
    fun updateAdblock(on: Boolean) {
        adblockEnabled = on
        AdblockEngine.enabled = on
        persist("adblock", on)
    }
    fun updateDesktopMode(on: Boolean) { desktopMode = on; persist("desktop", on) }
    fun updateImageBlocking(on: Boolean) { imageBlocking = on; persist("images", on) }
    fun updateSearchEngine(engine: String) {
        searchEngine = engine
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString("search", engine)?.apply()
    }
    fun updateDarkMode(on: Boolean?) {
        darkMode = on
        val e = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
        if (on == null) e?.remove("dark") else e?.putBoolean("dark", on)
        e?.apply()
    }

    fun searchPrefix(): String =
        searchEngines.firstOrNull { it.first == searchEngine }?.second ?: searchEngines[0].second

    private fun persist(key: String, value: Boolean) {
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(key, value)?.apply()
    }

    fun clearBrowsingData(context: Context) {
        runCatching {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        }
        runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
        runCatching { context.cacheDir?.deleteRecursively() }
        runCatching { java.io.File(context.filesDir, "webview").deleteRecursively() }
    }

    fun supportsSafeBrowsing(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}
