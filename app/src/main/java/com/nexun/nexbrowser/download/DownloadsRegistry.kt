package com.nexun.nexbrowser.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf

data class DownloadEntry(
    val name: String,
    val state: String,
    val detail: String,
    val downloadId: Long = -1L,
)

object DownloadsRegistry {
    val items = mutableStateListOf<DownloadEntry>()
    private val idToName = HashMap<Long, String>()
    private var receiver: BroadcastReceiver? = null

    fun register(context: Context) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                val name = idToName[id] ?: return
                val path = queryPath(ctx, id)
                updateById(id, "Completado", path ?: name)
                DownloadsRegistry.downloadComplete = id
            }
        }
        receiver = r
        val flags = if (android.os.Build.VERSION.SDK_INT >= 33)
            Context.RECEIVER_EXPORTED else 0
        runCatching {
            context.registerReceiver(r, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), flags)
        }
    }

    private fun queryPath(context: Context?, id: Long): String? = runCatching {
        val dm = context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.getUriForDownloadedFile(id)?.toString()
    }.getOrNull()

    fun track(downloadId: Long, name: String) { idToName[downloadId] = name }

    fun add(entry: DownloadEntry) {
        items.add(0, entry)
    }

    fun update(name: String, state: String, detail: String) {
        val i = items.indexOfFirst { it.name == name }
        if (i >= 0) items[i] = items[i].copy(state = state, detail = detail)
    }

    private fun updateById(id: Long, state: String, detail: String) {
        val name = idToName[id] ?: return
        val i = items.indexOfFirst { it.name == name }
        if (i >= 0) items[i] = items[i].copy(state = state, detail = detail)
    }

    var downloadComplete: Long = -1L
        private set
}
