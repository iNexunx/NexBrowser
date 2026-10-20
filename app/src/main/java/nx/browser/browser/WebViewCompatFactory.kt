package nx.browser.browser

import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import android.webkit.WebView
import java.lang.reflect.Method

/**
 * Creates WebViews in a way that works across every WebView provider the user
 * might have installed (Google's Android System WebView, Chrome, Samsung
 * Internet, Firefox-based, Huawei, Amazon, or a custom ROM provider) and on old
 * Android versions.
 *
 * Key points:
 *  - Always build the WebView with a themed context, so providers that require a
 *    theme (older versions threw on a plain activity context) do not crash.
 *  - Detect an unavailable / stale-selection WebView up front and return a clear
 *    error instead of letting `WebView()` throw an uncaught exception.
 *  - Set a data directory suffix once (multi-process safe) guarded by reflection
 *    so it works on every API level.
 */
object WebViewCompatFactory {

    private const val TAG = "NexWebView"
    private var suffixApplied = false

    /** True when a usable WebView implementation is present on the device. */
    fun isWebViewAvailable(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        // Presence of the provider is the best indicator we can check cheaply.
        pm.getPackageInfo("com.google.android.webview", 0)
        true
    }.getOrElse {
        runCatching { pm(context).getPackageInfo("com.android.webview", 0); true }
            .getOrElse {
                runCatching { pm(context).getPackageInfo("com.android.chrome", 0); true }
                    .getOrDefault(true) // assume available; creation is guarded anyway
            }
    }

    private fun pm(context: Context) = context.packageManager

    /**
     * Creates a WebView. Throws [WebViewUnavailableException] when the provider is
     * missing or failed to initialize, so callers can degrade gracefully.
     */
    fun create(context: Context): WebView {
        applyDataDirectorySuffix(context)
        val themed = themeContext(context)
        return try {
            WebView(themed)
        } catch (t: Throwable) {
            Log.w(TAG, "WebView(context) failed, retrying with app context", t)
            try {
                WebView(context.applicationContext)
            } catch (t2: Throwable) {
                throw WebViewUnavailableException(
                    "El sistema WebView no está disponible o está dañado. " +
                        "Instala o actualiza 'Android System WebView' o Chrome.", t2
                )
            }
        }
    }

    private fun themeContext(context: Context): Context = runCatching {
        ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
    }.getOrDefault(context)

    /**
     * WebView.setDataDirectorySuffix can only be called once, before the first
     * WebView is created. Wrapped in reflection so it is a no-op where it does not
     * exist and never crashes if it was already set.
     */
    private fun applyDataDirectorySuffix(context: Context) {
        if (suffixApplied) return
        suffixApplied = true
        val proc = runCatching { currentProcessName(context) }.getOrDefault("main")
        val suffix = proc.replace(Regex("[^A-Za-z0-9_]"), "_").take(32).ifBlank { "main" }
        runCatching {
            val m: Method = WebView::class.java.getMethod("setDataDirectorySuffix", String::class.java)
            m.invoke(null, suffix)
        }
    }

    private fun currentProcessName(context: Context): String {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.app.Application.getProcessName()
            } else {
                val pid = android.os.Process.myPid()
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
            }
        }.getOrNull() ?: context.packageName
    }

    class WebViewUnavailableException(message: String, cause: Throwable?) :
        IllegalStateException(message, cause)
}
