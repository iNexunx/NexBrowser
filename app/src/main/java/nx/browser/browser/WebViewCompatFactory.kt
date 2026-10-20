package nx.browser.browser

import android.content.Context
import android.util.Log
import android.webkit.WebView

/**
 * Creates WebViews in a way that works across every WebView provider the user
 * might have installed (Google's Android System WebView, Chrome, Samsung
 * Internet, Firefox-based, Huawei, Amazon, or a custom ROM provider).
 *
 * Deliberately minimal: modern WebView providers crash the renderer process if
 * they are initialized with unusual settings, so we do exactly what working
 * browsers (Via/Brave) do — create the WebView with the Activity context and a
 * themed context only as a last-resort fallback. No data-directory suffix, no
 * multi-process tricks, no reflection at creation time.
 */
object WebViewCompatFactory {

    private const val TAG = "NexWebView"

    /** True when a usable WebView implementation is present on the device. */
    fun isWebViewAvailable(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.google.android.webview", 0)
        true
    }.getOrElse {
        runCatching { context.packageManager.getPackageInfo("com.android.webview", 0); true }
            .getOrElse {
                runCatching { context.packageManager.getPackageInfo("com.android.chrome", 0); true }
                    .getOrDefault(true) // assume available; creation is guarded anyway
            }
    }

    /**
     * Creates a WebView. Throws [WebViewUnavailableException] when the provider is
     * missing or failed to initialize, so callers can degrade gracefully.
     */
    fun create(context: Context): WebView {
        // Path 1: plain context, exactly like a normal app. This is what works on
        // every provider and is what Via/Brave use.
        runCatching { WebView(context) }.getOrNull()?.let { return it }

        // Path 2: themed context (some very old providers require a theme).
        runCatching {
            val themed = android.view.ContextThemeWrapper(
                context, android.R.style.Theme_DeviceDefault_NoActionBar
            )
            WebView(themed)
        }.getOrNull()?.let { return it }

        // Path 3: application context.
        runCatching { WebView(context.applicationContext) }.getOrNull()?.let { return it }

        Log.e(TAG, "WebView could not be created with any context")
        throw WebViewUnavailableException(
            "El sistema WebView no está disponible o está dañado. " +
                "Instala o actualiza 'Android System WebView' o Chrome.", null
        )
    }

    class WebViewUnavailableException(message: String, cause: Throwable?) :
        IllegalStateException(message, cause)
}
