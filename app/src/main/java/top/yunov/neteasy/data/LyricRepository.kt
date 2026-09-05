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
 * 歌词数据源（按优先级）：
 * 1) 主源：网易云新版歌词接口 /lyric/new 的 yrc（逐字歌词，机器分词但覆盖率极高，
 *    几乎所有歌都有），配套 ytlrc/tlyric 作为翻译。
 * 2) 质量升级：AMLL TTML 逐词歌词库（https://api.amll.dev/v1/lyrics/get?ncmMusicId=…），
 *    社区人工校对、时间轴更精准，但只覆盖一部分热门歌；命中则用 TTML 替换 yrc。
 * 3) 兜底：/lyric/new 里的 lrc（行级 + tlyric 翻译），保证 yrc 也没有时仍有歌词可看。
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

    /** 磁盘长期缓存清空（「存储空间」页「清理数据缓存」用）；内存缓存不用管，App 还在跑就会有。 */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** 取某首歌的歌词（[LyricLine] 列表，可能为空）。IO 线程外调用（内部自行切 IO）。 */
    suspend fun load(songId: Long): List<LyricLine> {
        memory[songId]?.let { return it }
        // 1) AMLL TTML（磁盘缓存 / 负缓存 / 在线抓取）——纯阻塞，切到 IO 执行
        val ttmlLines = withContext(Dispatchers.IO) { obtainTtml(songId)?.let { parseTtml(it) } }
        if (!ttmlLines.isNullOrEmpty()) {
            memory[songId] = ttmlLines
            return ttmlLines
        }
        // 2) /lyric/new：yrc 逐字为主，行级 lrc 兜底（覆盖率远高于 AMLL TTML 库）
        val lines = yrcOrLrcFallback(songId)
        memory[songId] = lines
        return lines
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

    /**
     * /lyric/new 兜底：优先用 yrc（逐字，机器分词但覆盖率极高），yrc 没有时退化成行级 lrc。
     * 翻译优先用与 yrc 行边界对齐的 ytlrc，没有再退化到普通 tlyric。
     */
    private suspend fun yrcOrLrcFallback(songId: Long): List<LyricLine> {
        return try {
            val root = withContext(Dispatchers.IO) { ncm.lyricNew(songId) }
            val yrc = root.optJSONObject("yrc")?.optString("lyric") ?: ""
            val tlyric = root.optJSONObject("tlyric")?.optString("lyric") ?: ""
            if (yrc.isNotBlank()) {
                val ytlrc = root.optJSONObject("ytlrc")?.optString("lyric") ?: ""
                val yrcLines = YrcParser.parse(yrc, ytlrc.ifBlank { tlyric })
                if (yrcLines.isNotEmpty()) return yrcLines
            }
            val lrc = root.optJSONObject("lrc")?.optString("lyric") ?: ""
            LrcParser.parse(lrc, tlyric)
        } catch (e: Exception) {
            emptyList()
        }
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

    private fun parseToMap(src: String): LinkedHashMap<Long, String> = LrcParserInternal.parseToMap(src)
}

/**
 * 网易云 yrc 逐字歌词解析：
 * `[行起始,行时长](字起始,字时长,0)文字(字起始,字时长,0)文字...`
 * 翻译按行起始时间对齐 ytlrc/tlyric（同一份行级 LRC，时间戳取最近一条，容差 500ms）。
 */
object YrcParser {

    private val lineHeaderRegex = Regex("^\\[(\\d+),(\\d+)]")
    private val wordRegex = Regex("\\((\\d+),(\\d+),\\d+\\)([^(]*)")
    private const val TRANSLATION_TOLERANCE_MS = 500L

    fun parse(yrc: String, translation: String = ""): List<LyricLine> {
        val translationMap = LrcParserInternal.parseToMap(translation)
        val translationTimes = translationMap.keys.sorted()
        val lines = mutableListOf<LyricLine>()

        yrc.lineSequence().forEach rawLine@{ raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@rawLine
            val header = lineHeaderRegex.find(trimmed) ?: return@rawLine
            val lineStart = header.groupValues[1].toLongOrNull() ?: return@rawLine
            val lineDur = header.groupValues[2].toLongOrNull() ?: return@rawLine
            val rest = trimmed.substring(header.range.last + 1)

            val words = wordRegex.findAll(rest).mapNotNull { m ->
                val start = m.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val dur = m.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val word = m.groupValues[3]
                if (word.isEmpty()) return@mapNotNull null
                LyricWord(startTime = start, endTime = start + dur, word = word)
            }.toList()
            if (words.isEmpty()) return@rawLine

            val nearestTrans = nearestTranslation(translationTimes, lineStart)
            lines.add(
                LyricLine(
                    words = words,
                    translatedLyric = nearestTrans?.let { translationMap[it] } ?: "",
                    startTime = lineStart,
                    endTime = lineStart + lineDur
                )
            )
        }
        return lines
    }

    private fun nearestTranslation(times: List<Long>, target: Long): Long? {
        if (times.isEmpty()) return null
        var lo = 0
        var hi = times.size - 1
        var best = times[0]
        var bestDiff = Math.abs(times[0] - target)
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val diff = Math.abs(times[mid] - target)
            if (diff < bestDiff) {
                bestDiff = diff
                best = times[mid]
            }
            when {
                times[mid] < target -> lo = mid + 1
                times[mid] > target -> hi = mid - 1
                else -> return times[mid]
            }
        }
        return if (bestDiff <= TRANSLATION_TOLERANCE_MS) best else null
    }
}

/** [LrcParser] 的按时间解析逻辑复用（避免重复实现正则解析）。 */
private object LrcParserInternal {
    private val timeTagRegex = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:\\.(\\d{1,3}))?]")

    fun parseToMap(src: String): LinkedHashMap<Long, String> {
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
