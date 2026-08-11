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

/**
 * 极简播放器控制器：
 * - 队列：currentQueue + currentIndex，支持上/下一首
 * - 状态：PlayerUiState（isPlaying / song / position / duration）驱动 UI
 * - 播放：由外部（UI 层）先解析好可播放 URL 再调用 play()
 */
class PlayerController(private val scope: CoroutineScope) {
    data class PlayerUiState(
        val isPlaying: Boolean = false,
        val song: PlayerSong? = null,
        val positionMs: Long = 0,
        val durationMs: Long = 0
    )

    data class PlayerSong(val id: Long, val name: String, val artists: String, val picUrl: String)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var tickerGeneration = 0 // 防止旧 ticker 用新 player 的状态

    /** 播放一个 URL。url 为 null 时（无版权）不播放但保留歌曲信息 */
    fun play(url: String?, song: PlayerSong) {
        // 先释放旧 player（只释放实例，不清空状态）
        releasePlayerOnly()

        if (url.isNullOrBlank()) {
            // 无版权/VIP：保留歌曲信息供展示，但无法播放
            _state.value = PlayerUiState(song = song)
            return
        }

        _state.value = PlayerUiState(song = song) // 立即显示歌曲
        startPlayer(url)
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

    /** 完整停止：释放播放器并清空状态 */
    fun release() {
        releasePlayerOnly()
        _state.value = PlayerUiState()
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
                _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
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
