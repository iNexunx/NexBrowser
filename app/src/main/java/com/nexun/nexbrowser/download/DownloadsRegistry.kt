package com.nexun.nexbrowser.download

import androidx.compose.runtime.mutableStateListOf

data class DownloadEntry(val name: String, val state: String, val detail: String)

object DownloadsRegistry {
    val items = mutableStateListOf<DownloadEntry>()

    fun add(entry: DownloadEntry) {
        items.add(0, entry)
    }

    fun update(name: String, state: String, detail: String) {
        val i = items.indexOfFirst { it.name == name }
        if (i >= 0) items[i] = items[i].copy(state = state, detail = detail)
    }
}
