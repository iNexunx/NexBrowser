package com.nexun.nexbrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nexun.nexbrowser.adblock.FilterListRepo
import com.nexun.nexbrowser.browser.BrowserSettings
import com.nexun.nexbrowser.browser.TabRegistry
import com.nexun.nexbrowser.ui.BrowserScreen
import com.nexun.nexbrowser.ui.theme.NexBrowserTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (TabRegistry.current() == null) TabRegistry.openTab()
        intent?.dataString?.let { TabRegistry.openTab(it) }
        scope.launch(Dispatchers.IO) { runCatching { FilterListRepo.updateIfStale() } }
        setContent {
            val dark = BrowserSettings.darkMode ?: isSystemInDarkTheme()
            NexBrowserTheme(dark = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BrowserScreen(
                        onOpenExternal = { url ->
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { TabRegistry.openTab(it) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        runCatching { TabRegistry.saveAllStates() }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { TabRegistry.onTrimMemory() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) runCatching { TabRegistry.onTrimMemory() }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
