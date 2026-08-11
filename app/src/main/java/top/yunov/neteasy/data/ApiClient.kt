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
 * 访问内嵌 Node 后端（http://127.0.0.1:19800）的轻量客户端。
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
     * 内嵌 Node 后端是异步起来的：App 冷启动时（尤其是刚安装/刚更新后首次拉起）
     * 界面发出第一批请求时，Node 可能还没 listen(19800)，此时连接会被直接拒绝
     * （ConnectException），而不是超时。以前这里不重试，会导致「我的」页第一次
     * 请求 /login/status 直接失败 → 误判成"未登录"；但等用户点播放时 Node 早已
     * 起好，播放请求正常带上 cookie，于是出现"显示未登录却能听完整歌曲"的怪象。
     * 这里只对"连接建立失败"这一种错误做短暂轮询重试（还没发出任何请求数据，
     * 重试是安全的、不会重复提交），其余错误（HTTP 非 200、超时等）不重试，
     * 直接按原样抛出。
     */
    private fun execute(req: Request): JSONObject {
        var lastConnectError: ConnectException? = null
        repeat(MAX_CONNECT_RETRIES) { attempt ->
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("HTTP ${resp.code} for ${req.url}")
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

        /** 冷启动时等待本地 Node 后端 listen(19800) 的重试次数 / 间隔 */
        private const val MAX_CONNECT_RETRIES = 30
        private const val CONNECT_RETRY_DELAY_MS = 400L
    }
}
