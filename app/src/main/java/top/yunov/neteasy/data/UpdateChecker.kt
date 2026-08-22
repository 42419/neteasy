package top.yunov.neteasy.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 检查更新弹窗要展示的状态；调用方（SettingsActivity）驱动这个状态机流转 */
sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Available(val release: ReleaseInfo) : UpdateUiState()
    object PickingMirror : UpdateUiState()
    data class Downloading(val progress: Float, val mirrorLabel: String) : UpdateUiState()
    object ReadyToInstall : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

/** GitHub 最新 release 信息（只关心跟更新弹窗有关的字段） */
data class ReleaseInfo(
    val tagName: String,
    /** 去掉开头 v 的纯版本号，跟本地 versionName 是同一套格式，直接能比 */
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long
)

/** 下载节点候选：直连 GitHub 或某个镜像加速站 */
data class MirrorCandidate(val label: String, private val urlPrefix: String?) {
    /** 把原始 GitHub 链接转换成这个节点实际要访问的地址；直连时原样返回 */
    fun buildUrl(originalUrl: String): String = if (urlPrefix == null) originalUrl else urlPrefix + originalUrl
}

/**
 * 检查更新 + 挑最快下载节点。
 *
 * 镜像列表选的是几个目前（2026）还在稳定运行、社区认可度较高的 GitHub 加速反代：
 * gh-proxy.com / ghproxy.net 是同一个开源项目（hunshcn/gh-proxy）的两个域名，
 * 用得最广；ghfast.top 是另一套多 CDN 节点的反代，对国内网络路线优化较好。
 * 这类服务时不时会挂/变慢，所以不写死选哪个，每次下载前实测一遍再挑。
 */
class UpdateChecker(
    private val owner: String = "42419",
    private val repo: String = "neteasy"
) {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

    val mirrorCandidates =
        listOf(
            MirrorCandidate("GitHub 直连", null),
            MirrorCandidate("gh-proxy.com", "https://gh-proxy.com/"),
            MirrorCandidate("ghproxy.net", "https://ghproxy.net/"),
            MirrorCandidate("ghfast.top", "https://ghfast.top/")
        )

    /** 查询最新 release；网络失败或没有 apk 附件时返回 null（调用方按“检查失败”处理） */
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val req =
                Request
                    .Builder()
                    .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return@withContext null
                val assets = json.optJSONArray("assets") ?: return@withContext null
                var apkUrl: String? = null
                var apkName: String? = null
                var apkSize = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                        apkName = name
                        apkSize = a.optLong("size")
                        break
                    }
                }
                if (apkUrl == null || apkName == null) return@withContext null
                ReleaseInfo(
                    tagName = tag,
                    versionName = tag.removePrefix("v"),
                    releaseNotes = json.optString("body"),
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName,
                    apkSizeBytes = apkSize
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 并发测每个候选节点的下载速度：各拉一小段（256KB，服务器不支持 Range 就读到这么多为止，
     * 不会真把整个安装包拖下来），按耗时换算出 KB/s，选最快的。全部节点都测失败时兜底用直连。
     */
    suspend fun pickFastestMirror(originalUrl: String): MirrorCandidate = withContext(Dispatchers.IO) {
        val jobs =
            mirrorCandidates.map { candidate ->
                async {
                    val testUrl = candidate.buildUrl(originalUrl)
                    candidate to measureThroughputKbps(testUrl)
                }
            }
        val results = jobs.awaitAll().filter { it.second != null }
        results.maxByOrNull { it.second!! }?.first ?: mirrorCandidates.first()
    }

    private fun measureThroughputKbps(url: String): Double? {
        val maxBytes = 256 * 1024L
        return try {
            val start = System.currentTimeMillis()
            val req =
                Request
                    .Builder()
                    .url(url)
                    .header("Range", "bytes=0-${maxBytes - 1}")
                    .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val source = resp.body?.source() ?: return null
                val buffer = Buffer()
                var total = 0L
                while (total < maxBytes) {
                    val read = source.read(buffer, minOf(8192L, maxBytes - total))
                    if (read == -1L) break
                    total += read
                    buffer.clear()
                }
                val elapsedSec = (System.currentTimeMillis() - start) / 1000.0
                if (elapsedSec <= 0 || total <= 0) null else (total / 1024.0) / elapsedSec
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * 简单语义化版本号比较：按 "." 拆开逐段比数字，[remote] 更新返回 true。
 * 不用字符串直接比大小——"0.1.10" 字典序会排在 "0.1.9" 前面，纯字符串比较是错的。
 */
fun isNewerVersion(remote: String, local: String): Boolean {
    val r = remote.split(".").mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    val l = local.split(".").mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    val len = maxOf(r.size, l.size)
    for (i in 0 until len) {
        val rv = r.getOrElse(i) { 0 }
        val lv = l.getOrElse(i) { 0 }
        if (rv != lv) return rv > lv
    }
    return false
}
