package nx.browser.media

import android.content.Context
import android.content.Intent
import android.os.Build
import nx.browser.browser.TabRegistry

/**
 * Bridges lock-screen/media-notification controls to the active WebView,
 * and manages the foreground service lifetime.
 */
object MediaControl {
    private var callback: ((String) -> Unit)? = null

    @Volatile private var serviceRunning = false

    fun registerCallback(cb: (String) -> Unit) { callback = cb }
    fun unregisterCallback() { callback = null }

    fun dispatch(cmd: String) {
        callback?.invoke(cmd) ?: defaultDispatch(cmd)
    }

    private fun defaultDispatch(cmd: String) {
        val wv = TabRegistry.current()?.webView ?: return
        val js = when (cmd) {
            MediaBackgroundService.CMD_PLAY ->
                "(function(){var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++){try{m[i].play()}catch(e){}}})();"
            MediaBackgroundService.CMD_PAUSE ->
                "(function(){var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++){try{m[i].pause()}catch(e){}}})();"
            MediaBackgroundService.CMD_STOP ->
                "(function(){var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++){try{m[i].pause();m[i].currentTime=0}catch(e){}}})();"
            else -> return
        }
        runCatching { wv.evaluateJavascript(js, null) }
    }

    fun start(context: Context, title: String) {
        if (serviceRunning) return
        serviceRunning = true
        val i = Intent(context, MediaBackgroundService::class.java)
            .setAction(MediaBackgroundService.ACTION_START)
            .putExtra(MediaBackgroundService.EXTRA_TITLE, title)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }
    }

    fun stop(context: Context) {
        if (!serviceRunning) return
        serviceRunning = false
        runCatching {
            context.startService(
                Intent(context, MediaBackgroundService::class.java)
                    .setAction(MediaBackgroundService.ACTION_STOP)
            )
        }
    }
}
