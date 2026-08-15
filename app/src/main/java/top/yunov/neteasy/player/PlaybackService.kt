package top.yunov.neteasy.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.launch
import top.yunov.neteasy.MainActivity
import top.yunov.neteasy.NeteasyApp
import top.yunov.neteasy.R

/**
 * 播放前台服务：只负责“通知栏/锁屏出现什么”，不重复持有播放逻辑——
 * 播放器本体（MediaPlayer/队列/MediaSession）在 App 级单例 [PlayerController] 里，
 * 这里只是观察它的状态来更新通知，并在有歌曲时保持前台存活（防止切后台被系统回收）。
 */
class PlaybackService : LifecycleService() {
    private val player: PlayerController by lazy { (application as NeteasyApp).playerController }

    private var lastCoverUrl: String? = null
    private var lastCoverBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()

        lifecycleScope.launch {
            player.state.collect { state ->
                val song = state.song
                if (song == null) {
                    // 没有任何歌曲（队列已清空）：彻底退出前台并停止服务
                    ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                if (song.picUrl != lastCoverUrl) {
                    lastCoverUrl = song.picUrl
                    lastCoverBitmap = loadCoverBitmap(song.picUrl)
                }
                ServiceCompat.startForeground(
                    this@PlaybackService,
                    NOTIFICATION_ID,
                    buildNotification(state),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else {
                        0
                    }
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // 媒体按键（蓝牙耳机/车机物理键）路由到 MediaSession 的 callback
        intent?.let { MediaButtonReceiver.handleIntent(player.mediaSession, it) }
        return START_STICKY
    }

    private suspend fun loadCoverBitmap(url: String): Bitmap? {
        if (url.isBlank()) return null
        return try {
            val request =
                ImageRequest
                    .Builder(this)
                    .data(url)
                    .size(256)
                    .build()
            val result = imageLoader.execute(request)
            ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun buildNotification(state: PlayerController.PlayerUiState): Notification {
        val song = state.song

        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val playPauseAction =
            if (state.isPlaying) {
                NotificationCompat.Action(
                    R.mipmap.ic_launcher,
                    "暂停",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)!!
                )
            } else {
                NotificationCompat.Action(
                    R.mipmap.ic_launcher,
                    "播放",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)!!
                )
            }

        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle(song?.name ?: "")
                .setContentText(song?.artists ?: "")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(lastCoverBitmap)
                .setContentIntent(contentIntent)
                .setOngoing(state.isPlaying) // 播放中不可滑掉，暂停时可以滑掉关闭
                .setDeleteIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)!!
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                    NotificationCompat.Action(
                        R.mipmap.ic_launcher,
                        "上一首",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this,
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        )!!
                    )
                ).addAction(playPauseAction)
                .addAction(
                    NotificationCompat.Action(
                        R.mipmap.ic_launcher,
                        "下一首",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this,
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        )!!
                    )
                ).setStyle(
                    MediaNotificationCompat.MediaStyle()
                        .setMediaSession(player.mediaSession.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
        return builder.build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "正在播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "播放控制通知"
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "playback_channel"
        private const val NOTIFICATION_ID = 2001
    }
}
