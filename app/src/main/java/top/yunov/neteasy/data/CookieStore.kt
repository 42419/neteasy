package top.yunov.neteasy.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 把 cookie 持久化到 SharedPreferences：
 * - Node 后端的 Set-Cookie 会被保存（如登录后的 MUSIC_U）
 * - 重启 App / 重启 Node 后依然携带，登录态不丢
 */
class CookieStore(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("ncm_cookies", Context.MODE_PRIVATE)

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val current = loadAll()
        for (cookie in cookies) {
            if (cookie.expiresAt < System.currentTimeMillis()) {
                current.remove(cookie.name)
            } else {
                current[cookie.name] = cookie.value
            }
        }
        persist(current)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = loadAll().map { (name, value) ->
        Cookie
            .Builder()
            .name(name)
            .value(value)
            .domain("127.0.0.1")
            .path("/")
            .build()
    }

    private fun loadAll(): MutableMap<String, String> = prefs.all
        .filterValues { it is String }
        .mapValues { it.value as String }
        .toMutableMap()

    private fun persist(map: Map<String, String>) {
        prefs
            .edit()
            .clear()
            .apply {
                map.forEach { (k, v) -> putString(k, v) }
            }.apply()
    }

    /** 清空 cookie（登出用） */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /** 是否已登录：存在 MUSIC_U（网易云登录态 cookie）即视为已登录 */
    fun hasLogin(): Boolean = prefs.contains("MUSIC_U")
}
