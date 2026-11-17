package top.yunov.neteasy.data

import android.content.Context
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 访问内嵌 Rust 后端（http://127.0.0.1:19800）的轻量客户端。
 * - 自动携带/保存 cookie（登录后由服务端 Set-Cookie 更新）
 * - GET/POST 均返回 JSONObject；非 200 抛 IOException
 */
class ApiClient(context: Context) {
    // cookie 存储（供登录态判断 / 登出）
    val cookieStore = CookieStore(context)

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .cookieJar(cookieStore)
            .build()

    /** GET 请求，query 参数会 URL 编码拼到路径后 */
    fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val url = buildUrl(path, query)
        val req =
            Request
                .Builder()
                .url(url)
                .get()
                .build()
        return execute(req)
    }

    /** POST 请求，表单参数（后端接口大多走 query/body 均可） */
    fun post(path: String, form: Map<String, String> = emptyMap()): JSONObject {
        val url = buildUrl(path, emptyMap())
        val body =
            form
                .map { (k, v) -> "${k.encodeUrl()}=${v.encodeUrl()}" }
                .joinToString("&")
                .toRequestBody(FORM_URLENCODED)
        val req =
            Request
                .Builder()
                .url(url)
                .post(body)
                .build()
        return execute(req)
    }

    /**
     * 内嵌后端是在前台服务里异步拉起的：App 冷启动时界面发出第一批请求，可能比
     * 后端绑定 19800 端口更早，此时连接会被直接拒绝（ConnectException），而不是超时。
     * 不重试会导致「我的」页第一次请求 /login/status 直接失败 → 误判成"未登录"；
     * 但等用户点播放时后端早已起好，播放请求正常带上 cookie，于是出现
     * "显示未登录却能听完整歌曲"的怪象。
     * Rust 后端绑定端口只要几毫秒，这个窗口主要是前台服务本身被系统拉起的时间，
     * 所以总共最多等 3 秒就够了（以前内嵌 Node 要等 12 秒）。
     * 这里只对"连接建立失败"这一种错误做短暂轮询重试（还没发出任何请求数据，
     * 重试是安全的、不会重复提交），其余错误（HTTP 非 200、超时等）不重试，
     * 直接抛出，错误信息里会带上后端返回的提示（见 [describeHttpError]）。
     */
    private fun execute(req: Request): JSONObject {
        var lastConnectError: ConnectException? = null
        repeat(MAX_CONNECT_RETRIES) { attempt ->
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException(describeHttpError(resp.code, req.url.toString(), resp.body?.string()))
                    }
                    val text = resp.body?.string() ?: throw IOException("空响应")
                    return JSONObject(text)
                }
            } catch (e: ConnectException) {
                lastConnectError = e
                if (attempt < MAX_CONNECT_RETRIES - 1) {
                    Thread.sleep(CONNECT_RETRY_DELAY_MS)
                }
            }
        }
        throw lastConnectError ?: IOException("连接本地服务失败")
    }

    /**
     * 把后端的错误响应整理成给人看的提示：优先用后端补充的中文 `neteasyHint`（比如触发风控），
     * 其次用网易云返回的 `msg`，都没有才退回到技术性的「HTTP 状态码 + 地址」。
     */
    private fun describeHttpError(code: Int, url: String, body: String?): String {
        val json = runCatching { JSONObject(body ?: "") }.getOrNull()
        val hint = json?.optString("neteasyHint").orEmpty()
        if (hint.isNotEmpty()) return hint
        val msg = json?.optString("msg").orEmpty()
        if (msg.isNotEmpty() && msg != "null") return "$msg（HTTP $code）"
        return "HTTP $code for $url"
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = "http://127.0.0.1:19800$path"
        if (query.isEmpty()) return base
        val qs =
            query
                .map { (k, v) -> "${k.encodeUrl()}=${v.encodeUrl()}" }
                .joinToString("&")
        return "$base?$qs"
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    companion object {
        private val FORM_URLENCODED = "application/x-www-form-urlencoded".toMediaType()

        /** 冷启动时等待本地后端 listen(19800) 的重试次数 / 间隔（合计最多约 3 秒） */
        private const val MAX_CONNECT_RETRIES = 15
        private const val CONNECT_RETRY_DELAY_MS = 200L
    }
}
