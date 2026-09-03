package top.yunov.neteasy.data.model

import org.json.JSONArray
import org.json.JSONObject
import top.yunov.neteasy.data.AudioQuality

/**
 * 网易云图片 CDN 支持在链接后加 `?param=宽y高` 直接拿服务端裁好的缩略图；
 * 不加的话给的是原图——原图动辄几百 KB 甚至更大，用来渲染列表里几十/上百 dp 的
 * 小封面纯属浪费网络带宽和解码开销，是列表快速滑动时卡顿的头号元凶。
 * [widthPx]/[heightPx] 是目标**像素**边长（不是 dp），调用处按实际显示尺寸估算传入，
 * 略大于所需即可（不需要精确到像素级）。
 */
fun String.thumbnail(widthPx: Int, heightPx: Int = widthPx): String {
    if (isBlank()) return this
    return "$this?param=${widthPx}y$heightPx"
}

/** 首页轮播 Banner */
data class Banner(val picUrl: String, val targetId: Long, val typeTitle: String?)

/** 歌单（推荐页 / 详情 / 我创建与收藏的） */
data class Playlist(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    /** 特殊歌单标记：5 = 系统「喜欢的音乐」歌单，其余为普通歌单（自建/收藏） */
    val specialType: Int = 0,
    /** 运营文案（如「最近更新」「好听到停不下来，自有它的道理」），/personalized 才有，其余接口通常为空 */
    val copywriter: String = "",
    // 以下几个只有 /playlist/detail（歌单详情页）才会填，列表场景（推荐/榜单/我的歌单）留空即可，
    // 详情页原本就在拿这个接口，不用多打请求，顺手在 parsePlaylistDetail 里把这几个字段也解出来。
    /** 创建者昵称 */
    val creatorName: String = "",
    /** 创建者头像 */
    val creatorAvatarUrl: String = "",
    /** 歌单描述，创建者没写的话是空串 */
    val description: String = "",
    /** 收藏数 */
    val subscribedCount: Long = 0,
    /** 评论数 */
    val commentCount: Long = 0,
    /** 标签（官方精选歌单才有，普通用户自建歌单通常是空列表） */
    val tags: List<String> = emptyList()
) {
    val isLikedSongs: Boolean get() = specialType == 5
}

/** 歌曲 */
data class Song(
    val id: Long,
    val name: String,
    val artists: List<String>,
    val album: String = "",
    val picUrl: String = "", // 歌曲封面（歌单内歌曲通常无，用歌单封面兜底）
    val duration: Long = 0,
    /**
     * 这首歌实际存在的音质档位（来自 /song/detail 的 l/h/sq/hr 字段是否非空判断）。
     * 搜索结果的初始响应不带这些字段，会在 NcmRepository.search() 里用同一次
     * /song/detail 补封面的请求顺带补齐；补齐前为空集合。
     */
    val availableQualities: Set<AudioQuality> = emptySet()
)

/** /song/detail 补充信息：封面 + 该歌曲实际存在的音质档位 */
data class SongDetailExtra(val picUrl: String, val qualities: Set<AudioQuality>)

/**
 * 登录用户详情（/user/detail）。等级（level，账户 LV）与听歌数（listenSongs）
 * 来自 /user/detail 顶层，其余资料在 profile 对象里。
 */
data class UserDetail(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String,
    /** 账户等级（LV），/user/detail 顶层字段；未获取到为 0 */
    val level: Int,
    /** 累计听歌数 */
    val listenSongs: Int,
    /** 网易云 vipType：0 普通，11 黑胶VIP，其余非 0 视作 VIP */
    val vipType: Int,
    /** 性别：0 保密 / 1 男 / 2 女 */
    val gender: Int,
    val signature: String,
    /** 账号创建时间（epoch 毫秒） */
    val createTime: Long
) {
    val isVip: Boolean get() = vipType > 0

    /** VIP 徽标文案；非 VIP 返回空串 */
    val vipLabel: String
        get() =
            when {
                vipType == 11 -> "黑胶VIP"
                vipType > 0 -> "VIP"
                else -> ""
            }

    /** 账户等级徽标文案；未获取到（level=0）返回空串 */
    val levelLabel: String get() = if (level > 0) "LV$level" else ""

    val genderLabel: String
        get() =
            when (gender) {
                1 -> "男"
                2 -> "女"
                else -> "保密"
            }
}

// ---------- JSON 解析 ----------

object JsonParser {
    fun parseBanners(root: JSONObject): List<Banner> {
        val arr = root.optJSONArray("banners") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val pic = o.optString("pic") ?: ""
            if (pic.isEmpty()) return@mapNotNull null
            Banner(
                picUrl = pic,
                targetId = o.optLong("targetId"),
                typeTitle = o.optString("typeTitle").takeIf { it.isNotBlank() }
            )
        }
    }

    fun parsePersonalized(root: JSONObject): List<Playlist> {
        val arr = root.optJSONArray("result") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("id")
            val name = o.optString("name")
            if (id == 0L || name.isEmpty()) return@mapNotNull null
            Playlist(
                id = id,
                name = name,
                coverUrl = o.optString("picUrl"),
                playCount = o.optLong("playCount"),
                trackCount = o.optInt("trackCount"),
                copywriter = o.optString("copywriter")
            )
        }
    }

    /** 当前登录用户创建/收藏的歌单列表（含系统「喜欢的音乐」歌单，通常排第一个） */
    fun parseUserPlaylists(root: JSONObject): List<Playlist> {
        val arr = root.optJSONArray("playlist") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("id")
            val name = o.optString("name")
            if (id == 0L || name.isEmpty()) return@mapNotNull null
            Playlist(
                id = id,
                name = name,
                coverUrl = o.optString("coverImgUrl"),
                playCount = o.optLong("playCount"),
                trackCount = o.optInt("trackCount"),
                specialType = o.optInt("specialType")
            )
        }
    }

    fun parsePlaylistDetail(root: JSONObject): Playlist? {
        val p = root.optJSONObject("playlist") ?: return null
        val id = p.optLong("id")
        val name = p.optString("name")
        if (id == 0L || name.isEmpty()) return null
        val creator = p.optJSONObject("creator")
        val tagsArr = p.optJSONArray("tags")
        val tags =
            if (tagsArr != null) {
                (0 until tagsArr.length()).mapNotNull { i -> tagsArr.optString(i).takeIf { it.isNotBlank() } }
            } else {
                emptyList()
            }
        return Playlist(
            id = id,
            name = name,
            coverUrl = p.optString("coverImgUrl"),
            playCount = p.optLong("playCount"),
            trackCount = p.optInt("trackCount"),
            creatorName = creator?.optString("nickname").orEmpty(),
            creatorAvatarUrl = creator?.optString("avatarUrl").orEmpty(),
            description = p.optString("description"),
            subscribedCount = p.optLong("subscribedCount"),
            commentCount = p.optLong("commentCount"),
            tags = tags
        )
    }

    /**
     * 歌单歌曲列表（songs 数组）。/playlist/track/all 内部实际是拿 trackIds 转调
     * /api/v3/song/detail 换来的，所以这里的对象跟 /song/detail 是同一套字段结构，
     * l/h/sq/hr 音质字段是否存在可以直接判断。
     */
    fun parseSongs(root: JSONObject): List<Song> {
        val arr = root.optJSONArray("songs") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            parseSong(arr.optJSONObject(i))
        }
    }

    /** 榜单列表（/toplist）：每个榜单本质上就是一个官方维护的歌单，直接复用 Playlist。 */
    fun parseToplist(root: JSONObject): List<Playlist> {
        val arr = root.optJSONArray("list") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("id")
            val name = o.optString("name")
            if (id == 0L || name.isEmpty()) return@mapNotNull null
            Playlist(
                id = id,
                name = name,
                coverUrl = o.optString("coverImgUrl"),
                playCount = o.optLong("playCount"),
                trackCount = o.optInt("trackCount")
            )
        }
    }

    /**
     * 每日推荐歌曲（/recommend/songs，data.dailySongs）。需要登录态 Cookie，
     * 未登录会返回没有 data 字段的错误响应——这里直接返回空列表，调用方按“没有这个板块”处理，
     * 不额外弹错误提示（首页其他板块不受影响）。
     */
    fun parseRecommendSongs(root: JSONObject): List<Song> {
        val data = root.optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("dailySongs") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            parseSong(arr.optJSONObject(i))
        }
    }

    /** 搜索结果的歌曲列表（result.songs，字段是精简版，没有封面也没有音质档位字段） */
    fun parseSearchSongs(root: JSONObject): List<Song> {
        val result = root.optJSONObject("result") ?: return emptyList()
        val arr = result.optJSONArray("songs") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            parseSong(arr.optJSONObject(i))
        }
    }

    private fun parseSong(o: JSONObject?): Song? {
        if (o == null) return null
        val id = o.optLong("id")
        val name = o.optString("name")
        if (id == 0L || name.isEmpty()) return null
        val artists =
            parseNames(o.optJSONArray("ar"))
                .ifEmpty { parseNames(o.optJSONArray("artists")) }
        // 专辑封面：搜索接口在 al 里；歌单接口没有
        var pic = ""
        var album = ""
        val al = o.optJSONObject("al") ?: o.optJSONObject("album")
        if (al != null) {
            album = al.optString("name")
            pic = al.optString("picUrl")
        }
        return Song(
            id = id,
            name = name,
            artists = artists,
            album = album,
            picUrl = pic,
            duration = o.optLong("dt"),
            availableQualities = parseAvailableQualities(o)
        )
    }

    /**
     * 判断一首歌实际存在哪些音质档位：/song/detail 返回的 h/l/sq/hr 字段，
     * 存在（非 null）代表服务端真有这一档的母带，不存在就是这首歌根本没有这个音质
     * （不是权限问题，是压根没这个文件）。m（较高）字段不映射到任何选项——
     * 新版 song/url/v1 的 level 已不支持 higher，跟网易云新客户端界面一致。
     */
    private fun parseAvailableQualities(o: JSONObject): Set<AudioQuality> {
        val set = mutableSetOf<AudioQuality>()
        if (o.optJSONObject("l") != null) set += AudioQuality.STANDARD
        if (o.optJSONObject("h") != null) set += AudioQuality.EXHIGH
        if (o.optJSONObject("sq") != null) set += AudioQuality.LOSSLESS
        if (o.optJSONObject("hr") != null) set += AudioQuality.HIRES
        return set
    }

    private fun parseNames(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * /song/detail 批量返回歌曲的封面 + 实际存在的音质档位，用于给搜索结果（字段精简，
     * 没有封面/音质信息）补齐——搜索接口的 album 只有 picId 没有 picUrl，无法直接拼出
     * 图片地址，必须靠这个接口补；音质档位顺带一起补，不用多打一次请求。
     * 返回 id → (封面 URL, 音质档位集合) 映射。
     */
    fun parseSongDetailExtras(root: JSONObject): Map<Long, SongDetailExtra> {
        val arr = root.optJSONArray("songs") ?: return emptyMap()
        return (0 until arr.length())
            .mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optLong("id")
                if (id == 0L) return@mapNotNull null
                val pic = o.optJSONObject("al")?.optString("picUrl")?.takeIf { it.isNotBlank() } ?: ""
                id to SongDetailExtra(pic, parseAvailableQualities(o))
            }.toMap()
    }

    /** /song/url/v1 返回可播放 URL，无则返回 null（VIP/无版权，或请求的音质档位不存在时服务端已自动降级仍无法播放） */
    fun parseSongUrl(root: JSONObject): String? {
        val arr = root.optJSONArray("data") ?: return null
        if (arr.length() == 0) return null
        val url = arr.optJSONObject(0)?.optString("url")
        return url?.takeIf { it.isNotBlank() }
    }

    /** /search/suggest 返回的关键词建议（result.allMatch[].keyword） */
    fun parseSearchSuggest(root: JSONObject): List<String> {
        val result = root.optJSONObject("result") ?: return emptyList()
        val arr = result.optJSONArray("allMatch") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("keyword")?.takeIf { it.isNotBlank() }
        }
    }

    /** /search/hot 返回的热门搜索词（result.hots[].first） */
    fun parseSearchHot(root: JSONObject): List<String> {
        val result = root.optJSONObject("result") ?: return emptyList()
        val arr = result.optJSONArray("hots") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("first")?.takeIf { it.isNotBlank() }
        }
    }

    /** /user/detail 返回用户详情：level/listenSongs 在顶层，资料在 profile */
    fun parseUserDetail(root: JSONObject): UserDetail? {
        val profile = root.optJSONObject("profile") ?: return null
        val userId = profile.optLong("userId")
        if (userId == 0L) return null
        return UserDetail(
            userId = userId,
            nickname = profile.optString("nickname"),
            avatarUrl = profile.optString("avatarUrl"),
            level = root.optInt("level", 0),
            listenSongs = root.optInt("listenSongs", 0),
            vipType = profile.optInt("vipType", 0),
            gender = profile.optInt("gender", 0),
            signature = profile.optString("signature"),
            createTime = profile.optLong("createTime", 0L)
        )
    }
}
