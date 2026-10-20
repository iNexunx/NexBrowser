package com.nexun.nexbrowser.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Saves a base64 blob/data payload captured from JS (fetch -> FileReader) into
 * the public Downloads collection. This is the Via-style bridge for downloads
 * whose bytes only exist inside the page (blob:, data:, generated files).
 */
object BlobDownloader {

    fun save(context: Context, base64: String, rawName: String, mime: String?) {
        val entryId = DownloadsRegistry.startEntry(rawName)
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val name = withExtension(rawName, mime)
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(context, name, mime, bytes)
            } else {
                saveToExternalFiles(context, name, bytes)
            }
            if (ok) {
                DownloadsRegistry.updateEntry(entryId, 100, "Completado", "Completado")
            } else {
                DownloadsRegistry.updateEntry(entryId, -1, "Error al guardar", "Error")
            }
        } catch (t: Throwable) {
            DownloadsRegistry.updateEntry(
                entryId, -1, "Error: ${t.message ?: t.javaClass.simpleName}", "Error"
            )
        }
    }

    private fun withExtension(name: String, mime: String?): String {
        if (name.contains('.')) return name
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime ?: "")
            ?: when {
                mime?.startsWith("image/") == true -> "png"
                mime?.startsWith("text/") == true -> "txt"
                mime == "application/pdf" -> "pdf"
                else -> "bin"
            }
        return "$name.$ext"
    }

    private fun saveToMediaStore(context: Context, name: String, mime: String?, bytes: ByteArray): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime ?: "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri).use { out ->
            out ?: return false
            out.write(bytes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun saveToExternalFiles(context: Context, name: String, bytes: ByteArray): Boolean {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        return runCatching {
            File(dir, name).outputStream().use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }
}
