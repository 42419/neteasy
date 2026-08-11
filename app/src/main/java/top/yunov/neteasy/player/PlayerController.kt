package top.yunov.neteasy.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
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
import top.yunov.neteasy.data.NcmRepository

/**
 * 播放器控制器：
 * - 队列：currentQueue + currentIndex，支持整队播放 / 上一首 / 下一首 / 自动连播
 * - 状态：PlayerUiState（isPlaying / song / position / duration / 队列位置）驱动 UI
 * - URL 解析：内部持有 repository，播队列时自己按需解析每首歌的可播放地址
 */
class PlayerController(private val scope: CoroutineScope, private val repository: NcmRepository) {
    data class PlayerUiState(
        val isPlaying: Boolean = false,
        val song: PlayerSong? = null,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        /** 当前歌曲在队列中的下标，-1 表示不在队列播放（如单曲直接 play） */
        val queueIndex: Int = -1,
        val queueSize: Int = 0
    ) {
        val hasNext: Boolean get() = queueIndex in 0 until queueSize - 1
        val hasPrevious: Boolean get() = queueIndex > 0
    }

    data class PlayerSong(val id: Long, val name: String, val artists: String, val picUrl: String)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var tickerGeneration = 0 // 防止旧 ticker 用新 player 的状态

    // 播放队列
    private var queue: List<PlayerSong> = emptyList()
    private var queueIndex: Int = -1
    private var loadGeneration = 0 // 防止旧的异步 URL 解析在新一首播放请求之后回来，覆盖新状态

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

    /** 下一首（队列到底则停止在最后一首，不循环） */
    fun next() {
        if (queueIndex < 0 || queueIndex + 1 >= queue.size) return
        queueIndex++
        playCurrentQueueEntry()
    }

    /** 上一首 */
    fun previous() {
        if (queueIndex <= 0) return
        queueIndex--
        playCurrentQueueEntry()
    }

    /** 单曲播放（URL 已由外部解析好）。会清空队列上下文，播完不自动连播。 */
    fun play(url: String?, song: PlayerSong) {
        queue = emptyList()
        queueIndex = -1
        playResolved(url, song, queueIndex = -1, queueSize = 0)
    }

    fun toggle() {
        val p = mediaPlayer ?: return
        if (p.isPlaying) {
            p.pause()
            _state.value = _state.value.copy(isPlaying = false)
        } else {
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

    /** 完整停止：释放播放器并清空状态（含队列） */
    fun release() {
        releasePlayerOnly()
        queue = emptyList()
        queueIndex = -1
        _state.value = PlayerUiState()
    }

    /** 解析队列里当前下标对应的歌曲 URL 并播放；异步解析期间已带上代际号防止过期回调乱序覆盖状态 */
    private fun playCurrentQueueEntry() {
        val song = queue.getOrNull(queueIndex) ?: return
        val myIndex = queueIndex
        val gen = ++loadGeneration
        scope.launch {
            val url =
                try {
                    withContext(Dispatchers.IO) { repository.songUrl(song.id) }
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

    private fun playResolved(url: String?, song: PlayerSong, queueIndex: Int, queueSize: Int) {
        // 先释放旧 player（只释放实例，不清空状态）
        releasePlayerOnly()

        if (url.isNullOrBlank()) {
            // 无版权/VIP：保留歌曲信息供展示，但无法播放
            _state.value = PlayerUiState(song = song, queueIndex = queueIndex, queueSize = queueSize)
            return
        }

        // 立即显示歌曲信息，播放器准备好后再补上时长并开始播放
        _state.value = PlayerUiState(song = song, queueIndex = queueIndex, queueSize = queueSize)
        startPlayer(url)
    }

    private fun releasePlayerOnly() {
        progressJob?.cancel()
        progressJob = null
        tickerGeneration++
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startPlayer(url: String) {
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
                _state.value =
                    _state.value.copy(
                        isPlaying = true,
                        durationMs = mp.duration.toLong()
                    )
                mp.start()
                startProgressTicker()
            }
            p.setOnCompletionListener {
                // 队列里还有下一首就自动连播；没有（或不在队列里播放）就停在结尾
                if (queueIndex in 0 until queue.size - 1) {
                    next()
                } else {
                    _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
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
