package nx.browser.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import nx.browser.MainActivity
import nx.browser.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Foreground data-sync service that performs real HTTP downloads with live
 * progress, resumable output and a persistent notification. Modeled after the
 * download service used by Aster/Via, but built for WebView without relying on
 * the system DownloadManager.
 */
class DownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var worker: Thread? = null

    @Volatile private var cancelled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NexBrowser:download")
            .apply { setReferenceCounted(false) }
        runCatching { wakeLock?.acquire(60 * 60 * 1000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> { cancelled = true; stopSelf(); return START_NOT_STICKY }
        }
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val name = intent.getStringExtra(EXTRA_NAME) ?: "archivo"
        val ua = intent.getStringExtra(EXTRA_UA)
        val cookie = intent.getStringExtra(EXTRA_COOKIE)
        val referer = intent.getStringExtra(EXTRA_REFERER)
        startForegroundCompat(buildNotification(name, -1))
        if (worker == null) {
            worker = thread(name = "nex-download") { runDownload(url, name, ua, cookie, referer) }
        }
        return START_STICKY
    }

    private fun runDownload(url: String, name: String, ua: String?, cookie: String?, referer: String?) {
        val entry = DownloadsRegistry.startEntry(name)
        var conn: HttpURLConnection? = null
        var out: FileOutputStream? = null
        try {
            val target = destination(name)
            val existing = if (target.exists()) target.length() else 0L
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = true
                ua?.takeIf { it.isNotBlank() }?.let { setRequestProperty("User-Agent", it) }
                cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
                referer?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Referer", it) }
                setRequestProperty("Accept-Encoding", "identity")
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            conn.connect()
            val code = conn.responseCode
            val resumable = code == 206 && existing > 0
            val startOffset = if (resumable) existing else 0L
            if (!resumable && target.exists()) target.delete()
            val total = conn.contentLengthLong + startOffset
            out = FileOutputStream(target, resumable)
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var written = startOffset
                var lastPct = -1
                var lastNotify = 0L
                while (!cancelled) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    written += n
                    val pct = if (total > 0) ((written * 100) / total).toInt() else -1
                    val now = System.currentTimeMillis()
                    if (pct != lastPct && now - lastNotify > 400) {
                        lastPct = pct
                        lastNotify = now
                        DownloadsRegistry.updateEntry(entry, pct, "$written/$total")
                        notifyProgress(name, pct, written, total)
                    }
                }
            }
            out.flush(); out.close(); out = null
            if (cancelled) {
                DownloadsRegistry.updateEntry(entry, -1, "Cancelado", "Cancelado")
            } else {
                DownloadsRegistry.updateEntry(entry, 100, "Completado", "Completado", target.absolutePath)
                notifyDone(name, target)
            }
        } catch (t: Throwable) {
            DownloadsRegistry.updateEntry(entry, -1, "Error: ${t.message ?: t.javaClass.simpleName}", "Error")
        } finally {
            runCatching { conn?.disconnect() }
            runCatching { out?.close() }
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun destination(name: String): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val dir = File(base, "")
        if (!dir.exists()) dir.mkdirs()
        var f = File(dir, sanitize(name))
        var i = 1
        while (f.exists() && i < 1000) {
            val dot = f.name.lastIndexOf('.')
            val stem = if (dot > 0) f.name.substring(0, dot) else f.name
            val ext = if (dot > 0) f.name.substring(dot) else ""
            f = File(dir, "$stem($i)$ext"); i++
        }
        return f
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^\\w\\-. ]"), "_").take(120).ifBlank { "archivo.bin" }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    private fun buildNotification(name: String, pct: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(name)
            .setContentText(if (pct >= 0) "Descargando… $pct%" else "Preparando…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Cancelar", cancel)
        if (pct >= 0) b.setProgress(100, pct, false)
        else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun notifyProgress(name: String, pct: Int, done: Long, total: Long) {
        runCatching {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.notify(NOTIF_ID, buildNotification(name, pct))
        }
    }

    private fun notifyDone(name: String, file: File) {
        runCatching {
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(name)
                .setContentText("Descarga completada")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID + 1, n.build())
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Descargas", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            mgr.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        cancelled = true
        runCatching { wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "nx.browser.download.START"
        const val ACTION_CANCEL = "nx.browser.download.CANCEL"
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_UA = "ua"
        const val EXTRA_COOKIE = "cookie"
        const val EXTRA_REFERER = "referer"
        private const val CHANNEL_ID = "nexbrowser_download"
        private const val NOTIF_ID = 8001

        fun start(
            context: Context,
            url: String,
            name: String,
            ua: String?,
            cookie: String?,
            referer: String?,
        ) {
            val i = Intent(context, DownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_UA, ua)
                .putExtra(EXTRA_COOKIE, cookie)
                .putExtra(EXTRA_REFERER, referer)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
                else context.startService(i)
            }
        }
    }
}
