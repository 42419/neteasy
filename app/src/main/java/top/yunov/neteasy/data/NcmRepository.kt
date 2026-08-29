package top.yunov.neteasy.data

import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import top.yunov.neteasy.data.model.Banner
import top.yunov.neteasy.data.model.JsonParser
import top.yunov.neteasy.data.model.Playlist
import top.yunov.neteasy.data.model.Song
import top.yunov.neteasy.data.model.UserDetail

/**
 * 网易云数据仓库：对 ApiClient 的接口调用做结构化封装。
 * 所有方法在 IO 线程调用（UI 侧用 withContext(Dispatchers.IO)）。
 */
class NcmRepository(private val api: ApiClient) {
    /**
     * 歌单详情 + 歌曲列表内存缓存：App 存活期间有效（这个 NcmRepository 本身就是
     * App 级单例）。同一个歌单再次打开直接用缓存秒开，不用每次都重新等一轮网络请求；
     * 配合 [playlistDetailAndSongs] 的 forceRefresh 参数支持下拉刷新强制重新拉取。
     */
    private val playlistCache = ConcurrentHashMap<Long, Pair<Playlist, List<Song>>>()

    suspend fun banners(): List<Banner> = JsonParser.parseBanners(api.get("/banner", mapOf("type" to "2")))

    suspend fun personalized(limit: Int = 20): List<Playlist> =
        JsonParser.parsePersonalized(api.get("/personalized", mapOf("limit" to "$limit")))

    suspend fun playlistDetail(id: Long): Playlist? = JsonParser.parsePlaylistDetail(
        api.get(
            "/playlist/detail",
            mapOf(
                "id" to "$id"
            )
        )
    )

    suspend fun playlistSongs(id: Long, limit: Int = 100): List<Song> = JsonParser.parseSongs(
        api.get("/playlist/track/all", mapOf("id" to "$id", "limit" to "$limit"))
    )

    /** 不发请求，只读内存缓存；没有缓存返回 null。打开歌单页时先用它秒出上次的内容。 */
    fun cachedPlaylistOrNull(id: Long): Pair<Playlist, List<Song>>? = playlistCache[id]

    /** 当前缓存了多少个歌单（设置页「缓存管理」展示用） */
    val playlistCacheCount: Int get() = playlistCache.size

    /** 清空歌单数据内存缓存（设置页「清除」按钮用） */
    fun clearPlaylistCache() {
        playlistCache.clear()
    }

    /**
     * 歌单详情 + 歌曲列表一起拿，带内存缓存。
     * [forceRefresh]=false（默认）：有缓存直接返回缓存，没有才真正发请求；
     * [forceRefresh]=true：无视缓存强制重新拉取并刷新缓存（下拉刷新 / 后台静默刷新用）。
     */
    suspend fun playlistDetailAndSongs(id: Long, forceRefresh: Boolean = false): Pair<Playlist, List<Song>> {
        if (!forceRefresh) {
            playlistCache[id]?.let { return it }
        }
        val detail = playlistDetail(id) ?: error("歌单不存在或已被删除")
        val songs = playlistSongs(id, 100)
        val result = detail to songs
        playlistCache[id] = result
        return result
    }

    /** 当前登录用户创建/收藏的歌单（含系统「喜欢的音乐」歌单）。未登录调用会返回空列表。 */
    suspend fun userPlaylists(uid: Long): List<Playlist> = JsonParser.parseUserPlaylists(
        api.get("/user/playlist", mapOf("uid" to "$uid", "limit" to "1000"))
    )

    suspend fun search(keywords: String, limit: Int = 30): List<Song> {
        val songs =
            JsonParser.parseSearchSongs(
                api.get("/search", mapOf("keywords" to keywords, "limit" to "$limit"))
            )
        if (songs.isEmpty()) return songs
        // 搜索接口字段精简：没有封面 URL，也没有 l/h/sq/hr 音质字段。
        // 批量用 /song/detail 补一次，封面和「这首歌实际有哪些音质」一起补齐，不用多打一次请求。
        // 补齐失败只降级（无封面、音质档位未知），不丢搜索结果。
        return try {
            val ids = songs.joinToString(",") { it.id.toString() }
            val extras =
                JsonParser.parseSongDetailExtras(
                    api.get("/song/detail", mapOf("ids" to ids))
                )
            songs.map { song ->
                extras[song.id]?.let { extra ->
                    song.copy(
                        picUrl = extra.picUrl.ifEmpty { song.picUrl },
                        availableQualities = extra.qualities
                    )
                } ?: song
            }
        } catch (e: Exception) {
            songs
        }
    }

    /**
     * 搜索关键词建议（/search/suggest，type=mobile 走 keyword），用于输入时弹出的引导。
     * 失败返回空列表，不打断输入。
     */
    suspend fun searchSuggest(keywords: String): List<String> =
        try {
            JsonParser.parseSearchSuggest(
                api.get("/search/suggest", mapOf("keywords" to keywords, "type" to "mobile"))
            )
        } catch (e: Exception) {
            emptyList()
        }

    /** 热门搜索词（/search/hot），用于搜索页空态引导。失败返回空列表。 */
    suspend fun searchHot(): List<String> =
        try {
            JsonParser.parseSearchHot(api.get("/search/hot"))
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * 获取歌曲可播放 URL。
     * 免费歌曲返回真实地址；无版权/VIP 歌曲 url 为空字符串或缺失。
     * [level] 对应 song/url/v1 的音质档位（standard/exhigh/lossless/hires）；
     * 若这首歌没有该档位或账号权限不够，服务端会自动降级返回可播放的最高音质。
     */
    suspend fun songUrl(id: Long, level: String = AudioQuality.EXHIGH.level): String? = JsonParser.parseSongUrl(
        api.get(
            "/song/url/v1",
            mapOf(
                "id" to "$id",
                "level" to level
            )
        )
    )

    /** 供调试：原始响应 */
    suspend fun raw(path: String, query: Map<String, String>): JSONObject = api.get(path, query)

    // ---------- 登录 ----------

    /** 获取二维码登录 key（带 timestamp 绕过 apicache 缓存） */
    suspend fun qrKey(): String {
        val root = api.get("/login/qr/key", mapOf("timestamp" to System.currentTimeMillis().toString()))
        return root.optJSONObject("data")?.optString("unikey") ?: ""
    }

    /** 生成二维码：返回 (qrurl, base64 图片 data url)。qrimg 由后端生成 */
    suspend fun qrCreate(key: String): Pair<String, String> {
        val root =
            api.get(
                "/login/qr/create",
                mapOf(
                    "key" to key,
                    "qrimg" to "true",
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )
        val d = root.optJSONObject("data") ?: return "" to ""
        return d.optString("qrurl") to d.optString("qrimg")
    }

    /**
     * 轮询二维码状态（每次带不同 timestamp，确保不被 apicache 缓存）：
     * 801 等待扫码 / 800 二维码过期 / 802 已扫码待确认 / 803 登录成功。
     * 803 时服务端已通过 Set-Cookie 下发登录 cookie（CookieStore 自动保存）。
     */
    suspend fun qrCheck(key: String): Int {
        val root =
            api.get(
                "/login/qr/check",
                mapOf("key" to key, "timestamp" to System.currentTimeMillis().toString())
            )
        return root.optInt("code", -1)
    }

    /**
     * 获取登录态：已登录返回含 profile 的 data 对象，未登录返回 null。
     * 注意响应结构是 { data: { code, account, profile } }（外层无 code，code 在 data 内）。
     */
    suspend fun loginStatus(): JSONObject? {
        return try {
            val root = api.get("/login/status")
            val data = root.optJSONObject("data") ?: return null
            if (data.optInt("code", -1) == 200) data else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 用户详情（账户等级 / 听歌数 / VIP / 资料），来自 /user/detail?uid=。
     * 用于「我的」页个人信息与用户详情页。
     */
    suspend fun userDetail(uid: Long): UserDetail? = JsonParser.parseUserDetail(
        api.get("/user/detail", mapOf("uid" to "$uid"))
    )

    /** 网易云歌词（/lyric）：返回含 lrc/tlyric 的原始 JSON，用作歌词兜底解析。 */
    suspend fun lyric(id: Long): JSONObject = api.get("/lyric", mapOf("id" to "$id"))
}
