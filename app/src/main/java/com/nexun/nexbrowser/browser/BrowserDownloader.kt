package com.nexun.nexbrowser.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.nexun.nexbrowser.download.DownloadEntry
import com.nexun.nexbrowser.download.DownloadsRegistry

/** Handles real file downloads (GitHub release assets, zip, pdf, mp4, etc.). */
object BrowserDownloader {

    fun start(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val cleanUrl = when {
            url.startsWith("blob:") -> return // blob handled via JS fetch path
            url.startsWith("data:") -> return
            else -> url
        }
        runCatching {
            val fileName = guessName(cleanUrl, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(cleanUrl)).apply {
                setTitle(fileName)
                setDescription("NexBrowser")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                userAgent?.let { addRequestHeader("User-Agent", it) }
                addRequestHeader("Accept-Encoding", "identity")
                CookieManager.getInstance().getCookie(cleanUrl)?.let {
                    addRequestHeader("Cookie", it)
                }
                mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            DownloadsRegistry.add(DownloadEntry(fileName, "Descargando", cleanUrl))
        }.onFailure {
            DownloadsRegistry.add(DownloadEntry("Descarga", "Error", it.message ?: ""))
        }
    }

    private fun guessName(url: String, contentDisposition: String?, mime: String?): String {
        var name = URLUtil.guessFileName(url, contentDisposition, mime)
        if (name.isBlank() || name == "downloadfile" || !name.contains('.')) {
            val fromUrl = url.substringBefore('?').substringAfterLast('/')
            if (fromUrl.isNotBlank() && fromUrl.contains('.')) name = fromUrl
        }
        if (!name.contains('.')) {
            val ext = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mime ?: "")
                ?: if (url.contains("/releases/download/")) "bin" else "bin"
            name = "$name.$ext"
        }
        return name.replace(Regex("[^\\w\\-. ]"), "_").take(120).ifBlank { "archivo.bin" }
    }
}
