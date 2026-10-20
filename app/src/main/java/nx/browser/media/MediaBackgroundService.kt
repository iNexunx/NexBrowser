package nx.browser.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import nx.browser.MainActivity
import nx.browser.R

/**
 * Keeps WebView media playing while the app is in the background / screen off,
 * and exposes lock-screen media controls (play/pause) via a MediaSession.
 */
class MediaBackgroundService : Service() {

    private var session: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var playing = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSession(this, "NexBrowserMedia").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { sendCommand(CMD_PLAY) }
                override fun onPause() { sendCommand(CMD_PAUSE) }
                override fun onStop() { stopPlayback() }
            })
            isActive = true
        }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NexBrowser:media")
            .apply { setReferenceCounted(false) }
        runCatching { wakeLock?.acquire(6 * 60 * 60 * 1000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopPlayback(); return START_NOT_STICKY }
            ACTION_PAUSE -> playing = false
            ACTION_PLAY -> playing = true
        }
        updateTitle(intent?.getStringExtra(EXTRA_TITLE) ?: lastTitle)
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
        return START_STICKY
    }

    private var lastTitle = "Reproduciendo"

    private fun sendCommand(cmd: String) {
        MediaControl.dispatch(cmd)
        playing = cmd == CMD_PLAY
        updatePlaybackState()
    }

    private fun stopPlayback() {
        MediaControl.dispatch(CMD_STOP)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun updateTitle(title: String) {
        lastTitle = title
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "NexBrowser")
                .build()
        )
        updatePlaybackState()
    }

    private fun updatePlaybackState() {
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0L, 1f)
                .build()
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Reproducción en segundo plano", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MediaBackgroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(lastTitle)
            .setContentText("NexBrowser")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Detener", stop)
        if (Build.VERSION.SDK_INT >= 21) {
            builder.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0)
            )
        }
        return builder.build()
    }

    /** Re-arm the foreground service if the task is swiped away while playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (playing) {
            runCatching {
                val restart = Intent(applicationContext, MediaBackgroundService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_TITLE, lastTitle)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(restart)
                else startService(restart)
            }
        }
    }

    override fun onDestroy() {
        runCatching { wakeLock?.release() }
        runCatching { session?.release() }
        session = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "nx.browser.media.START"
        const val ACTION_STOP = "nx.browser.media.STOP"
        const val ACTION_PLAY = "nx.browser.media.PLAY"
        const val ACTION_PAUSE = "nx.browser.media.PAUSE"
        const val CMD_PLAY = "play"
        const val CMD_PAUSE = "pause"
        const val CMD_STOP = "stop"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "nexbrowser_media"
        private const val NOTIF_ID = 7001
    }
}
