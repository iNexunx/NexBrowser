package com.nexun.nexbrowser.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.mutableStateListOf

data class DownloadEntry(
    val name: String,
    val state: String,
    val detail: String,
    val downloadId: Long = -1L,
    /** 0..100, -1 when unknown. */
    val progress: Int = -1,
)

object DownloadsRegistry {
    val items = mutableStateListOf<DownloadEntry>()
    private val idToName = HashMap<Long, String>()
    private var receiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    fun register(context: Context) {
        appContext = context.applicationContext
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                val name = idToName[id] ?: return
                val path = queryPath(appContext, id)
                updateById(id, "Completado", path ?: name, 100)
                downloadComplete = id
            }
        }
        receiver = r
        val flags = if (android.os.Build.VERSION.SDK_INT >= 33)
            Context.RECEIVER_EXPORTED else 0
        runCatching {
            context.registerReceiver(r, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), flags)
            context.registerReceiver(r, IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED), flags)
        }
    }

    private fun queryPath(context: Context?, id: Long): String? = runCatching {
        val dm = context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.getUriForDownloadedFile(id)?.toString()
    }.getOrNull()

    fun track(downloadId: Long, name: String) { idToName[downloadId] = name }

    fun add(entry: DownloadEntry) { items.add(0, entry) }

    fun update(name: String, state: String, detail: String, progress: Int = -1) {
        val i = items.indexOfFirst { it.name == name }
        if (i >= 0) items[i] = items[i].copy(state = state, detail = detail, progress = progress)
    }

    private fun updateById(id: Long, state: String, detail: String, progress: Int = -1) {
        val name = idToName[id] ?: return
        val i = items.indexOfFirst { it.name == name }
        if (i >= 0) items[i] = items[i].copy(state = state, detail = detail, progress = progress)
    }

    /** Polls DownloadManager for live progress of tracked downloads. */
    fun refreshProgress() {
        val ctx = appContext ?: return
        val dm = runCatching { ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
            .getOrNull() ?: return
        for (i in items.indices) {
            val e = items[i]
            if (e.downloadId < 0 || e.state == "Completado" || e.state.startsWith("Error")) continue
            runCatching {
                dm.query(DownloadManager.Query().setFilterById(e.downloadId)).use { c ->
                    if (c != null && c.moveToFirst()) {
                        val done = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val pct = if (total > 0) (done * 100 / total) else -1
                        val st = when (status) {
                            DownloadManager.STATUS_RUNNING -> "Descargando"
                            DownloadManager.STATUS_PENDING -> "En cola"
                            DownloadManager.STATUS_PAUSED -> "Pausado"
                            DownloadManager.STATUS_SUCCESSFUL -> "Completado"
                            DownloadManager.STATUS_FAILED -> "Error"
                            else -> e.state
                        }
                        items[i] = e.copy(state = st, progress = pct)
                    }
                }
            }
        }
    }

    fun remove(index: Int) { if (index in items.indices) items.removeAt(index) }

    fun clearFinished() {
        items.removeAll { it.state == "Completado" || it.state.startsWith("Error") }
    }

    val activeCount: Int get() = items.count { it.state != "Completado" && !it.state.startsWith("Error") }

    var downloadComplete: Long = -1L
        private set
}
