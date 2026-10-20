package nx.browser

import android.app.Application
import nx.browser.adblock.AdblockEngine
import nx.browser.adblock.FilterListRepo
import nx.browser.browser.BrowserSettings
import nx.browser.browser.TabRegistry
import nx.browser.download.DownloadsRegistry

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
