package top.yunov.neteasy.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> player.toggle()
            ACTION_NEXT -> player.next()
            ACTION_PREVIOUS -> player.previous()
            ACTION_STOP -> player.release()
            // 蓝牙耳机/车机物理键发来的原始媒体按键广播，路由给 MediaSession 的 callback
            Intent.ACTION_MEDIA_BUTTON -> MediaButtonReceiver.handleIntent(player.mediaSession, intent)
        }
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
                    // 通知栏图标也是跨进程（System UI）渲染，硬件位图传不过去
                    .allowHardware(false)
                    .build()
            val result = imageLoader.execute(request)
            ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 通知按钮的 PendingIntent：直接指向本 Service 自己、带自定义 action，在 onStartCommand 里分发。
     * 没有用 MediaButtonReceiver.buildMediaButtonPendingIntent()——那个要在 manifest 里反查目标组件，
     * 查不到时返回 null，之前就是这里 `!!` 空指针崩的（一放歌就闪退）。这里改成完全自己掌控、
     * 不依赖任何反查机制的写法，稳。
     */
    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
                NotificationCompat.Action(R.mipmap.ic_launcher, "暂停", actionPendingIntent(ACTION_PLAY_PAUSE))
            } else {
                NotificationCompat.Action(R.mipmap.ic_launcher, "播放", actionPendingIntent(ACTION_PLAY_PAUSE))
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
                .setDeleteIntent(actionPendingIntent(ACTION_STOP))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(NotificationCompat.Action(R.mipmap.ic_launcher, "上一首", actionPendingIntent(ACTION_PREVIOUS)))
                .addAction(playPauseAction)
                .addAction(NotificationCompat.Action(R.mipmap.ic_launcher, "下一首", actionPendingIntent(ACTION_NEXT)))
                .setStyle(
                    MediaNotificationCompat.MediaStyle()
                        .setMediaSession(player.mediaSession.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
        return builder.build()
    }

    private fun ensureNotificationChannel() {
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
        private const val ACTION_PLAY_PAUSE = "top.yunov.neteasy.action.PLAY_PAUSE"
        private const val ACTION_NEXT = "top.yunov.neteasy.action.NEXT"
        private const val ACTION_PREVIOUS = "top.yunov.neteasy.action.PREVIOUS"
        private const val ACTION_STOP = "top.yunov.neteasy.action.STOP"
    }
}

