package top.yunov.neteasy.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.AudioQuality
import top.yunov.neteasy.data.NcmRepository

/** 循环模式：不循环（播完队列停止）/ 列表循环（播完最后一首回到第一首）/ 单曲循环 */
enum class RepeatMode { OFF, ALL, ONE }

/**
 * 播放器控制器（App 级单例，不跟着 Activity 走）：
 * - 队列：currentQueue + currentIndex，支持整队播放 / 上一首 / 下一首 / 自动连播 / 跳转到队列中任意一首
 * - 状态：PlayerUiState（isPlaying / song / position / duration / 队列）驱动 UI
 * - 音质：qualityLevel，切换时重新解析当前歌曲 URL 并尽量从原位置续播
 * - URL 解析：内部持有 repository，播队列时自己按需解析每首歌的可播放地址
 * - 系统集成：音频焦点（来电/其他 App 播放时暂停或降音）、拔耳机自动暂停、
 *   MediaSession（驱动锁屏/通知栏/蓝牙耳机的播放控制），配合 [PlaybackService] 提供前台通知
 */
class PlayerController(
    private val scope: CoroutineScope,
    private val repository: NcmRepository,
    private val appContext: Context,
    initialQuality: AudioQuality = AudioQuality.EXHIGH
) {
    data class PlayerUiState(
        val isPlaying: Boolean = false,
        val song: PlayerSong? = null,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        /** 当前歌曲在队列中的下标，-1 表示不在队列播放（如单曲直接 play） */
        val queueIndex: Int = -1,
        val queueSize: Int = 0,
        /** 当前播放队列的完整快照，供「播放队列」面板展示 */
        val queue: List<PlayerSong> = emptyList(),
        /** 当前播放音质档位 */
        val quality: AudioQuality = AudioQuality.EXHIGH,
        /** 循环模式 */
        val repeatMode: RepeatMode = RepeatMode.OFF
    ) {
        // 列表循环模式下首尾相接，按钮不应该显示为「到头了」
        val hasNext: Boolean get() = (queueIndex in 0 until queueSize - 1) || (repeatMode == RepeatMode.ALL && queueSize > 0)
        val hasPrevious: Boolean get() = queueIndex > 0 || (repeatMode == RepeatMode.ALL && queueSize > 0)
    }

    data class PlayerSong(
        val id: Long,
        val name: String,
        val artists: String,
        val picUrl: String,
        /** 这首歌实际存在的音质档位（空集合代表尚未从 /song/detail 获知，不代表真的没有） */
        val availableQualities: Set<AudioQuality> = emptySet()
    )

    private val _state = MutableStateFlow(PlayerUiState(quality = initialQuality))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var tickerGeneration = 0 // 防止旧 ticker 用新 player 的状态

    // 播放队列
    private var queue: List<PlayerSong> = emptyList()
    private var queueIndex: Int = -1
    private var loadGeneration = 0 // 防止旧的异步 URL 解析在新一首播放请求之后回来，覆盖新状态
    private var qualityLevel: String = initialQuality.level

    // ---------- 系统音频集成 ----------

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var resumeOnFocusGain = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private val focusChangeListener =
        AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                // 彻底失去焦点（别的 App 要长期播放）：暂停并交还焦点，不自动恢复
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnFocusGain = false
                    pauseInternal()
                    abandonAudioFocus()
                }
                // 短暂失去（来电、语音助手等）：暂停，对方结束后如果原本在播就自动恢复
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeOnFocusGain = _state.value.isPlaying
                    pauseInternal()
                }
                // 短暂共享（导航提示音等）：降音量而不是暂停
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    mediaPlayer?.setVolume(0.2f, 0.2f)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    mediaPlayer?.setVolume(1f, 1f)
                    if (resumeOnFocusGain) {
                        resumeOnFocusGain = false
                        resumeInternal()
                    }
                }
            }
        }

    /** 拔耳机 / 蓝牙耳机断开：立即暂停，避免突然外放吓到人 */
    private val becomingNoisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    pauseInternal()
                }
            }
        }

    /** 系统媒体会话：驱动锁屏/通知栏/蓝牙耳机/车机的播放控制，[PlaybackService] 用它的 sessionToken 挂通知 */
    val mediaSession: MediaSessionCompat =
        MediaSessionCompat(appContext, "NeteasyPlayback").apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() = resumeInternal()

                    override fun onPause() = pauseInternal()

                    override fun onSkipToNext() = next()

                    override fun onSkipToPrevious() = previous()

                    override fun onSeekTo(pos: Long) = seekTo(pos.toInt())

                    override fun onStop() = release()
                }
            )
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
        }

    init {
        ContextCompat.registerReceiver(
            appContext,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // 每次状态变化都同步一份给系统媒体会话——锁屏/通知栏/耳机显示的标题、进度、可用操作全靠这个
        scope.launch {
            state.collect { updateMediaSession(it) }
        }
    }

    private fun updateMediaSession(s: PlayerUiState) {
        val song = s.song
        mediaSession.isActive = song != null
        if (song == null) return
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.name)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artists)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.picUrl)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, s.durationMs)
                .build()
        )
        val actions =
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP or
                (if (s.hasNext) PlaybackStateCompat.ACTION_SKIP_TO_NEXT else 0L) or
                (if (s.hasPrevious) PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS else 0L)
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (s.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    s.positionMs,
                    1f
                )
                .build()
        )
    }

    /** 请求音频焦点（API 26+ 用 AudioFocusRequest，更低版本用旧版 API）。返回是否拿到焦点。 */
    private fun requestAudioFocus(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val attrs =
            AudioAttributes
                .Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else {
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            focusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    /** 启动播放前台服务：让通知栏/锁屏出现控制条，并让播放不因切后台被系统回收 */
    private fun ensurePlaybackServiceRunning() {
        val intent = Intent(appContext, PlaybackService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
    }

    // ---------- 播放控制 ----------

    /**
     * 用一份新队列替换当前队列，并从 [startIndex] 开始播放（自动解析 URL）。
     * 用于「歌单播放全部」「点某一首歌」等场景——始终把整份列表设为队列，
     * 这样播完当前曲目会自动接着播列表里的下一首，而不是只能播被点的那一首。
     */
    fun playQueue(songs: List<PlayerSong>, startIndex: Int) {
        if (songs.isEmpty()) return
        queue = songs
        queueIndex = startIndex.coerceIn(0, songs.size - 1)
        playCurrentQueueEntry()
    }

    /** 下一首（队列到底：列表循环则回到第一首，否则停在最后一首不动） */
    fun next() {
        if (queueIndex < 0) return
        if (queueIndex + 1 < queue.size) {
            queueIndex++
            playCurrentQueueEntry()
        } else if (_state.value.repeatMode == RepeatMode.ALL && queue.isNotEmpty()) {
            queueIndex = 0
            playCurrentQueueEntry()
        }
    }

    /** 上一首（第一首时：列表循环则跳到最后一首，否则不动） */
    fun previous() {
        if (queueIndex < 0) return
        if (queueIndex > 0) {
            queueIndex--
            playCurrentQueueEntry()
        } else if (_state.value.repeatMode == RepeatMode.ALL && queue.isNotEmpty()) {
            queueIndex = queue.size - 1
            playCurrentQueueEntry()
        }
    }

    /** 依次切换循环模式：不循环 → 列表循环 → 单曲循环 → 不循环 */
    fun cycleRepeatMode() {
        val next =
            when (_state.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        _state.value = _state.value.copy(repeatMode = next)
    }

    /** 跳转到队列中指定下标的歌曲（「播放队列」面板点某一首直接播） */
    fun playAt(index: Int) {
        if (index !in queue.indices || index == queueIndex) return
        queueIndex = index
        playCurrentQueueEntry()
    }

    /** 单曲播放（URL 已由外部解析好）。会清空队列上下文，播完不自动连播。 */
    fun play(url: String?, song: PlayerSong) {
        queue = emptyList()
        queueIndex = -1
        playResolved(url, song, queueIndex = -1, queueSize = 0)
    }

    fun toggle() {
        if (_state.value.isPlaying) pauseInternal() else resumeInternal()
    }

    private fun pauseInternal() {
        val p = mediaPlayer ?: return
        if (p.isPlaying) {
            p.pause()
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    private fun resumeInternal() {
        val p = mediaPlayer ?: return
        if (!p.isPlaying) {
            if (!requestAudioFocus()) return // 拿不到焦点（比如通话中）就不硬播
            ensurePlaybackServiceRunning()
            p.start()
            _state.value = _state.value.copy(isPlaying = true)
            startProgressTicker()
        }
    }

    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
        // 立即同步 UI 进度：暂停时 ticker 不运行，且避免松手后滑块弹回旧位置
        _state.value = _state.value.copy(positionMs = ms.toLong())
    }

    /**
     * 切换音质：重新解析当前歌曲在新档位下的 URL，并尽量从原播放位置续播
     * （暂停状态切换音质后仍保持暂停，不会突然自动播放）。
     * 若这首歌没有该档位，服务端会自动降级返回可播放的最高音质。
     */
    fun setQuality(quality: AudioQuality) {
        if (qualityLevel == quality.level) return
        qualityLevel = quality.level
        val song = _state.value.song ?: run {
            // 还没开始播放：只记下偏好，等下一首生效
            _state.value = _state.value.copy(quality = quality)
            return
        }
        val resumeAtMs = _state.value.positionMs
        val wasPlaying = _state.value.isPlaying
        val myQueueIndex = queueIndex
        val gen = ++loadGeneration
        _state.value = _state.value.copy(quality = quality)
        scope.launch {
            val url =
                try {
                    withContext(Dispatchers.IO) { repository.songUrl(song.id, qualityLevel) }
                } catch (e: Exception) {
                    Log.e("Player", "songUrl (quality switch) failed for ${song.id}", e)
                    null
                }
            if (gen != loadGeneration) return@launch
            playResolved(
                url,
                song,
                queueIndex = myQueueIndex,
                queueSize = queue.size,
                resumeAtMs = resumeAtMs,
                autoPlay = wasPlaying
            )
        }
    }

    /** 完整停止：释放播放器、交还音频焦点、清空状态（音质/循环模式偏好保留） */
    fun release() {
        releasePlayerOnly()
        abandonAudioFocus()
        _state.value = PlayerUiState(quality = _state.value.quality, repeatMode = _state.value.repeatMode)
        queue = emptyList()
        queueIndex = -1
    }

    /** 解析队列里当前下标对应的歌曲 URL 并播放；异步解析期间已带上代际号防止过期回调乱序覆盖状态 */
    private fun playCurrentQueueEntry() {
        val song = queue.getOrNull(queueIndex) ?: return
        val myIndex = queueIndex
        val gen = ++loadGeneration
        scope.launch {
            val url =
                try {
                    withContext(Dispatchers.IO) { repository.songUrl(song.id, qualityLevel) }
                } catch (e: Exception) {
                    Log.e("Player", "songUrl failed for ${song.id}", e)
                    null
                }
            // 解析期间用户可能又切了别的歌（连续点了好几首/上一首下一首连点），
            // 此时这个已经过期的结果不能再覆盖当前状态
            if (gen != loadGeneration) return@launch
            playResolved(url, song, queueIndex = myIndex, queueSize = queue.size)
        }
    }

    private fun playResolved(
        url: String?,
        song: PlayerSong,
        queueIndex: Int,
        queueSize: Int,
        resumeAtMs: Long = 0,
        autoPlay: Boolean = true
    ) {
        // 先释放旧 player（只释放实例，不清空状态）
        releasePlayerOnly()

        if (url.isNullOrBlank()) {
            // 无版权/VIP：保留歌曲信息供展示，但无法播放
            _state.value =
                _state.value.copy(
                    isPlaying = false,
                    song = song,
                    positionMs = 0,
                    durationMs = 0,
                    queueIndex = queueIndex,
                    queueSize = queueSize,
                    queue = queue
                )
            return
        }

        // 立即显示歌曲信息，播放器准备好后再补上时长并开始播放
        _state.value =
            _state.value.copy(
                isPlaying = false,
                song = song,
                positionMs = resumeAtMs,
                durationMs = 0,
                queueIndex = queueIndex,
                queueSize = queueSize,
                queue = queue
            )
        startPlayer(url, resumeAtMs, autoPlay)
    }

    private fun releasePlayerOnly() {
        progressJob?.cancel()
        progressJob = null
        tickerGeneration++
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startPlayer(url: String, resumeAtMs: Long = 0, autoPlay: Boolean = true) {
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            p.setDataSource(url)
            p.setOnPreparedListener { mp ->
                if (resumeAtMs > 0) mp.seekTo(resumeAtMs.toInt())
                // 新的一首开始播放前先抢音频焦点；没抢到（比如正在通话）就只加载不出声
                val granted = if (autoPlay) requestAudioFocus() else true
                val willPlay = autoPlay && granted
                _state.value =
                    _state.value.copy(
                        isPlaying = willPlay,
                        durationMs = mp.duration.toLong(),
                        positionMs = resumeAtMs
                    )
                if (willPlay) {
                    ensurePlaybackServiceRunning()
                    mp.start()
                    startProgressTicker()
                }
            }
            p.setOnCompletionListener {
                when {
                    // 单曲循环：不用重新解析 URL，直接从头再播一遍（PlaybackCompleted 状态下
                    // seekTo/start 都是合法调用，瞬间生效）
                    _state.value.repeatMode == RepeatMode.ONE -> {
                        val mp = mediaPlayer
                        if (mp != null) {
                            mp.seekTo(0)
                            mp.start()
                            _state.value = _state.value.copy(isPlaying = true, positionMs = 0)
                            startProgressTicker()
                        }
                    }
                    // 队列还有下一首，或者开了列表循环（next() 内部已处理回绕到第一首）
                    queueIndex in 0 until queue.size - 1 ||
                        (_state.value.repeatMode == RepeatMode.ALL && queue.isNotEmpty()) -> next()
                    else -> _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
                }
            }
            p.setOnErrorListener { _, what, extra ->
                Log.e("Player", "MediaPlayer error: what=$what extra=$extra")
                _state.value = _state.value.copy(isPlaying = false)
                true
            }
            mediaPlayer = p
            p.prepareAsync()
        } catch (e: IOException) {
            Log.e("Player", "setDataSource failed", e)
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    /** 进度轮询：带代际标记，旧的轮询协程遇到新代际立即退出 */
    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = null
        val gen = ++tickerGeneration
        progressJob =
            scope.launch(Dispatchers.Main) {
                while (isActive && tickerGeneration == gen) {
                    val p = mediaPlayer ?: break
                    if (!p.isPlaying) break
                    _state.value =
                        _state.value.copy(
                            positionMs = p.currentPosition.toLong(),
                            durationMs = p.duration.toLong()
                        )
                    delay(500)
                }
            }
    }
}
