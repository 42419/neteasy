package top.yunov.neteasy.data

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.met6.amll.LyricLine
import top.met6.amll.LyricWord
import top.met6.amll.TtmlParser

/**
 * 歌词数据源：
 * 1) 主源：AMLL TTML 逐词歌词库（https://api.amll.dev/v1/lyrics/get?ncmMusicId=…），
 *    拿到的 TTML 用 [TtmlParser] 直接解析成 [LyricLine]（支持逐字高亮/翻译/音译/多声部）。
 * 2) 兜底：网易云本地后端 /lyric 的 LRC（行级 + tlyric 翻译），保证 api.amll.dev 不可用时仍有歌词。
 *
 * 缓存策略（按官方建议）：TTML 内容对同一首歌是固定的，首次成功后按 songId 落盘长期缓存；
 * 内存再放一层，避免切换歌曲时重复请求。
 */
class LyricRepository(context: Context, private val ncm: NcmRepository) {

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    private val cacheDir = File(context.filesDir, "lyric_cache")
    private val memory = ConcurrentHashMap<Long, List<LyricLine>>()
    private val negativeTtlMs = 72L * 60 * 60 * 1000 // 负缓存 72h（对齐 SPlayer-Next）

    /** 取某首歌的歌词（[LyricLine] 列表，可能为空）。IO 线程外调用（内部自行切 IO）。 */
    suspend fun load(songId: Long): List<LyricLine> {
        memory[songId]?.let { return it }
        // 1) AMLL TTML（磁盘缓存 / 负缓存 / 在线抓取）——纯阻塞，切到 IO 执行
        val lines = withContext(Dispatchers.IO) { obtainTtml(songId)?.let { parseTtml(it) } }
        if (!lines.isNullOrEmpty()) {
            memory[songId] = lines
            return lines
        }
        // 2) 网易 LRC 兜底（suspend 请求本地后端）
        val lrcLines = lrcFallback(songId)
        memory[songId] = lrcLines
        return lrcLines
    }

    /**
     * 纯阻塞：拿到 AMLL TTML 字符串（能解析出歌词才返回，否则 null）。
     * - 磁盘正缓存永久（一次成功固定不变，按官方建议长期缓存）
     * - 负缓存 72h（DB 没有这首歌时短期内不再重复请求）
     */
    private fun obtainTtml(songId: Long): String? {
        val ttmlFile = File(cacheDir, "$songId.ttml")
        if (ttmlFile.exists() && parseTtml(ttmlFile.readText()).isNotEmpty()) {
            return ttmlFile.readText()
        }
        if (isNegativeCached(songId)) return null
        val fetched = fetchTtml(songId)
        if (fetched != null && parseTtml(fetched).isNotEmpty()) {
            runCatching {
                cacheDir.mkdirs()
                ttmlFile.writeText(fetched)
            }
            return fetched
        }
        setNegativeCache(songId)
        return null
    }

    /** 负缓存：72h 内不再重复请求 AMLL DB（对齐 SPlayer-Next 的负缓存 TTL） */
    private fun isNegativeCached(songId: Long): Boolean {
        val f = File(cacheDir, "$songId.miss")
        return f.exists() && (System.currentTimeMillis() - f.lastModified()) < negativeTtlMs
    }

    private fun setNegativeCache(songId: Long) {
        runCatching {
            cacheDir.mkdirs()
            File(cacheDir, "$songId.miss").writeText(System.currentTimeMillis().toString())
        }
    }

    private fun parseTtml(ttml: String): List<LyricLine> =
        try {
            TtmlParser.parseAmll(ttml).lines
        } catch (e: Exception) {
            emptyList()
        }

    private fun fetchTtml(songId: Long): String? =
        try {
            val url = "https://api.amll.dev/v1/lyrics/get?ncmMusicId=$songId&format=ttml"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                JSONObject(body)
                    .optJSONObject("data")
                    ?.optString("lyrics")
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }

    /** 网易 LRC 兜底（行级 + 翻译）：/lyric 是本地后端接口，需 suspend 调用 */
    private suspend fun lrcFallback(songId: Long): List<LyricLine> =
        try {
            val root = withContext(Dispatchers.IO) { ncm.lyric(songId) }
            val lrc = root.optJSONObject("lrc")?.optString("lyric") ?: ""
            val tlyric = root.optJSONObject("tlyric")?.optString("lyric") ?: ""
            LrcParser.parse(lrc, tlyric)
        } catch (e: Exception) {
            emptyList()
        }
}

/** 极简 LRC 解析：`[mm:ss.xx]文本` → [LyricLine]，可选翻译 LRC（与主歌词按时间对齐）合并为 translatedLyric。 */
object LrcParser {

    private val timeTagRegex = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:\\.(\\d{1,3}))?]")

    fun parse(lrc: String, translation: String = ""): List<LyricLine> {
        val main = parseToMap(lrc)
        if (main.isEmpty()) return emptyList()
        val trans = parseToMap(translation)
        val times = main.keys.sorted()
        return times.mapNotNull { t ->
            val text = main[t] ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            val end = times.firstOrNull { it > t } ?: (t + 8_000L)
            LyricLine(
                words = listOf(LyricWord(startTime = t, endTime = end, word = text)),
                translatedLyric = trans[t] ?: "",
                startTime = t,
                endTime = end
            )
        }
    }

    private fun parseToMap(src: String): LinkedHashMap<Long, String> {
        val map = LinkedHashMap<Long, String>()
        if (src.isBlank()) return map
        src.lineSequence().forEach { line ->
            val m = timeTagRegex.find(line) ?: return@forEach
            val min = m.groupValues[1].toIntOrNull() ?: return@forEach
            val sec = m.groupValues[2].toIntOrNull() ?: return@forEach
            val frac = m.groupValues[3].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.toIntOrNull() ?: 0
            val ms = (min * 60 + sec) * 1000L + frac
            val text = line.substring(m.range.last + 1).trim()
            map[ms] = text
        }
        return map
    }
}
