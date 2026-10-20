package nx.browser.browser

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import nx.browser.download.DownloadEntry
import nx.browser.download.DownloadService
import nx.browser.download.DownloadsRegistry

/** Handles real file downloads (GitHub release assets, zip, pdf, mp4, etc.). */
object BrowserDownloader {

    /**
     * Entry point used by the WebView DownloadListener and by the long-press menu.
     * Uses the app's own foreground DownloadService (Aster/Via style) so progress
     * and cancellation are fully under our control.
     */
    fun start(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        referer: String? = null,
    ) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            // blob:/data: cannot be fetched with a plain HTTP request; the caller
            // (TabRegistry) resolves those through JS. Record if it still reaches us.
            DownloadsRegistry.add(
                DownloadEntry(guessName(url, contentDisposition, mimeType), "No soportado",
                    "Enlace blob/data: no descargable directamente")
            )
            return
        }
        val name = guessName(url, contentDisposition, mimeType)
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        val ua = userAgent?.takeIf { it.isNotBlank() }
            ?: DEFAULT_UA
        DownloadService.start(context, url, name, ua, cookie, referer)
    }

    private fun canWritePublic(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun guessName(url: String, contentDisposition: String?, mime: String?): String {
        var name = runCatching { URLUtil.guessFileName(url, contentDisposition, mime) }
            .getOrDefault("")
        if (name.isBlank() || name == "downloadfile" || !name.contains('.')) {
            val fromUrl = url.substringBefore('?').substringAfterLast('/')
            if (fromUrl.isNotBlank() && fromUrl.contains('.')) name = fromUrl
        }
        if (!name.contains('.')) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime ?: "") ?: "bin"
            name = "$name.$ext"
        }
        return name.replace(Regex("[^\\w\\-. ]"), "_").take(120).ifBlank { "archivo.bin" }
    }

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
}
