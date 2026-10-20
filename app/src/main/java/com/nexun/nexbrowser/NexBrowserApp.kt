package com.nexun.nexbrowser

import android.app.Application
import com.nexun.nexbrowser.adblock.AdblockEngine
import com.nexun.nexbrowser.browser.BrowserSettings
import com.nexun.nexbrowser.browser.TabRegistry

class NexBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        TabRegistry.init(this)
        BrowserSettings.init(this)
        AdblockEngine.ensureSeed()
    }

    companion object { lateinit var instance: NexBrowserApp; private set }
}
