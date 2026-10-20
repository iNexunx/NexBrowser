package com.nexun.nexbrowser.browser

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            DownloadsRegistry.add(DownloadEntry("blob", "No soportado", url.take(80)))
            return
        }
        runCatching {
            val fileName = guessName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("NexBrowser")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                // Public Downloads needs no permission on API 29+; use app dir otherwise.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                    canWritePublic(context)
                ) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } else {
                    setDestinationInExternalFilesDir(
                        context, Environment.DIRECTORY_DOWNLOADS, fileName
                    )
                }
                userAgent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
                addRequestHeader("Accept-Encoding", "identity")
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(request)
            DownloadsRegistry.add(DownloadEntry(fileName, "Iniciado", "Descargando…", id))
            DownloadsRegistry.track(id, fileName)
        }.onFailure {
            DownloadsRegistry.add(DownloadEntry("Descarga", "Error: ${it.message ?: it.javaClass.simpleName}", url.take(80)))
        }
    }

    private fun canWritePublic(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun guessName(url: String, contentDisposition: String?, mime: String?): String {
        var name = URLUtil.guessFileName(url, contentDisposition, mime)
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
}
