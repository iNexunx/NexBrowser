package com.nexun.nexbrowser

import android.app.Application
import com.nexun.nexbrowser.adblock.AdblockEngine
import com.nexun.nexbrowser.adblock.FilterListRepo
import com.nexun.nexbrowser.browser.BrowserSettings
import com.nexun.nexbrowser.browser.TabRegistry
import com.nexun.nexbrowser.download.DownloadsRegistry

class NexBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        TabRegistry.init(this)
        BrowserSettings.init(this)
        AdblockEngine.ensureSeed()
        FilterListRepo.init(this)
        DownloadsRegistry.register(this)
    }

    companion object { lateinit var instance: NexBrowserApp; private set }
}
