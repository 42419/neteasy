package top.yunov.neteasy.data

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 歌曲音频本地缓存：解决"刚打开 App 点播放要联网加载才能出声"的卡顿问题——播放过一次的
 * 歌曲落一份到本地磁盘，下次同一首歌、同一档音质直接从本地文件起播，
 * MediaPlayer 对本地文件 prepare 几乎是瞬时的，不用再等一轮网络握手+缓冲。
 *
 * 存放位置用 [Context.getCacheDir]（系统级"可随时清空"的缓存目录，语义上正合适：
 * 这批文件全部可以从网络重新拉到，丢了不影响功能，只是又要等一次网络）。
 * 容量上限、过期时间由 [SettingsStore] 里对应的设置项控制（对应 [CacheSizeLimit] /
 * [CacheExpiryDays]，参考 Apple Music「自动缓存歌曲」那一套）。
 *
 * 命中判断＝文件存在且未过期；下载时先写临时文件，完整下载完再原子改名成正式缓存文件，
 * 避免播放中途被杀掉进程留下的半截文件被当成"已缓存"命中。同一个 key 并发下载会去重
 * （[inFlight]），不会因为快速切歌/连点触发好几个并行下载占满带宽。
 */
class AudioCacheManager(context: Context, private val settings: SettingsStore) {
    private val cacheDir = File(context.cacheDir, "audio_cache").apply { mkdirs() }
    private val inFlight = ConcurrentHashMap<String, Boolean>()
    private val maintenanceMutex = Mutex()
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    private fun keyFor(songId: Long, quality: String) = "${songId}_$quality"

    private fun fileFor(songId: Long, quality: String) = File(cacheDir, "${keyFor(songId, quality)}.cache")

    /** 命中缓存就返回文件，没有/已过期返回 null（过期的顺手删掉，不占地方）。 */
    fun getCachedFile(songId: Long, quality: String): File? {
        val f = fileFor(songId, quality)
        if (!f.exists() || f.length() == 0L) return null
        val maxAgeMs = settings.cacheExpiryDays.days * DAY_MS
        if (System.currentTimeMillis() - f.lastModified() > maxAgeMs) {
            f.delete()
            return null
        }
        // 用 lastModified 顺带记一下"最近访问时间"，enforceSizeLimit 按这个做 LRU 淘汰
        f.setLastModified(System.currentTimeMillis())
        return f
    }

    /**
     * 后台下载缓存一份，失败/已存在/已在下载中都直接安静跳过——这是锦上添花的东西，
     * 不能因为它失败影响正在进行的正常播放，调用方不需要处理返回值/异常。
     */
    suspend fun cacheAsync(songId: Long, quality: String, url: String) {
        if (!settings.autoCacheSongs) return
        val key = keyFor(songId, quality)
        val target = fileFor(songId, quality)
        if (target.exists() && target.length() > 0) return
        if (inFlight.putIfAbsent(key, true) != null) return
        val tmp = File(cacheDir, "$key.tmp")
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            if (tmp.length() > 0) tmp.renameTo(target) else tmp.delete()
            enforceSizeLimit()
        } catch (e: Exception) {
            tmp.delete()
        } finally {
            inFlight.remove(key)
        }
    }

    /** 按当前配置的容量上限做 LRU 淘汰：超了就从最久没被访问的文件开始删，删到不超为止。 */
    private suspend fun enforceSizeLimit() =
        maintenanceMutex.withLock {
            val limitBytes = settings.cacheSizeLimit.bytes
            val files = cacheDir.listFiles { f -> f.isFile && f.name.endsWith(".cache") } ?: return@withLock
            var total = files.sumOf { it.length() }
            if (total <= limitBytes) return@withLock
            files.sortedBy { it.lastModified() }.forEach { f ->
                if (total <= limitBytes) return@forEach
                total -= f.length()
                f.delete()
            }
        }

    /** 清掉过期缓存；App 启动时跑一次做日常维护，不用等到下次播放同一首歌才触发。 */
    suspend fun clearExpired() =
        maintenanceMutex.withLock {
            val maxAgeMs = settings.cacheExpiryDays.days * DAY_MS
            val now = System.currentTimeMillis()
            cacheDir.listFiles()?.forEach { f ->
                if (f.isFile && now - f.lastModified() > maxAgeMs) f.delete()
            }
        }

    /** 当前缓存占用的总字节数，给「存储空间」页的「音乐缓存」卡片展示用。 */
    fun currentSizeBytes(): Long = cacheDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

    /** 用户在「存储空间」页手动点清空。 */
    fun clearAll() {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
