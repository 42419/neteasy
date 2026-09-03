package top.yunov.neteasy.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import top.yunov.neteasy.player.PlayerController.PlayerSong
import top.yunov.neteasy.player.RepeatMode

/**
 * 播放队列持久化：记住关闭 App（甚至进程被系统杀掉）前的播放队列/下标/进度/循环模式，
 * 下次冷启动时原样恢复——恢复后是暂停状态，停在原来的进度上等用户点播放，不会一开 App
 * 就自己放起来。
 *
 * 存 SharedPreferences，跟 [SettingsStore] 一个思路：队列数据量小（几百首歌的 JSON 也就
 * 几十 KB），犯不着上 Room/DataStore 这种量级的方案。歌曲信息（名字/歌手/封面/音质档位）
 * 一起存下来，是因为恢复时要能在没有网络请求的情况下先把队列/当前歌曲信息显示出来
 * （真正解析播放 URL 是用户点了播放之后才发生的事，见 PlayerController.resumeRestoredPlayback）。
 */
class PlaybackStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("ncm_playback_state", Context.MODE_PRIVATE)

    fun save(queue: List<PlayerSong>, queueIndex: Int, positionMs: Long, repeatMode: RepeatMode) {
        if (queue.isEmpty() || queueIndex !in queue.indices) {
            clear()
            return
        }
        val arr = JSONArray()
        queue.forEach { song ->
            arr.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("name", song.name)
                    put("artists", song.artists)
                    put("picUrl", song.picUrl)
                    put("quality", song.availableQualities.joinToString(",") { it.level })
                }
            )
        }
        prefs
            .edit()
            .putString(KEY_QUEUE, arr.toString())
            .putInt(KEY_INDEX, queueIndex)
            .putLong(KEY_POSITION, positionMs)
            .putString(KEY_REPEAT_MODE, repeatMode.name)
            .apply()
    }

    fun load(): Restored? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val index = prefs.getInt(KEY_INDEX, -1)
        if (index < 0) return null
        val positionMs = prefs.getLong(KEY_POSITION, 0)
        val repeatMode =
            runCatching {
                RepeatMode.valueOf(prefs.getString(KEY_REPEAT_MODE, RepeatMode.OFF.name) ?: RepeatMode.OFF.name)
            }.getOrDefault(RepeatMode.OFF)
        return try {
            val arr = JSONArray(raw)
            val songs =
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val qualities =
                        o
                            .optString("quality")
                            .split(",")
                            .filter { it.isNotBlank() }
                            .mapNotNull { level -> AudioQuality.entries.firstOrNull { it.level == level } }
                            .toSet()
                    PlayerSong(
                        id = o.getLong("id"),
                        name = o.optString("name"),
                        artists = o.optString("artists"),
                        picUrl = o.optString("picUrl"),
                        availableQualities = qualities
                    )
                }
            if (songs.isEmpty() || index !in songs.indices) return null
            Restored(songs, index, positionMs, repeatMode)
        } catch (e: Exception) {
            // 存的 JSON 损坏（几乎不会发生，但别因为这个崩启动）——当作没存过处理
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    data class Restored(val queue: List<PlayerSong>, val queueIndex: Int, val positionMs: Long, val repeatMode: RepeatMode)

    companion object {
        private const val KEY_QUEUE = "queue"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val KEY_REPEAT_MODE = "repeat_mode"
    }
}
