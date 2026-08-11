package top.yunov.neteasy.data

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 访问内嵌 Node 后端（http://127.0.0.1:19800）的轻量客户端。
 * - 自动携带/保存 cookie（登录后由服务端 Set-Cookie 更新）
 * - GET/POST 均返回 JSONObject；非 200 抛 IOException
 */
class ApiClient(context: Context) {

    private val cookieStore = CookieStore(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cookieJar(cookieStore)
        .build()

    /** GET 请求，query 参数会 URL 编码拼到路径后 */
    fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val url = buildUrl(path, query)
        val req = Request.Builder().url(url).get().build()
        return execute(req)
    }

    /** POST 请求，表单参数（后端接口大多走 query/body 均可） */
    fun post(path: String, form: Map<String, String> = emptyMap()): JSONObject {
        val url = buildUrl(path, emptyMap())
        val body = form
            .map { (k, v) -> "${k.encodeUrl()}=${v.encodeUrl()}" }
            .joinToString("&")
            .toRequestBody(FORM_URLENCODED)
        val req = Request.Builder().url(url).post(body).build()
        return execute(req)
    }

    private fun execute(req: Request): JSONObject {
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} for ${req.url}")
            }
            val text = resp.body?.string() ?: throw IOException("空响应")
            return JSONObject(text)
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = "http://127.0.0.1:19800$path"
        if (query.isEmpty()) return base
        val qs = query.map { (k, v) -> "${k.encodeUrl()}=${v.encodeUrl()}" }
            .joinToString("&")
        return "$base?$qs"
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")

    companion object {
        private val FORM_URLENCODED = "application/x-www-form-urlencoded".toMediaType()
    }
}
