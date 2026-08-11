package top.yunov.neteasy.data

import org.json.JSONArray
import org.json.JSONObject

/** 首页轮播 Banner */
data class Banner(
    val picUrl: String,
    val targetId: Long,
    val typeTitle: String?,
)

/** 歌单（推荐页 / 详情） */
data class Playlist(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val playCount: Long = 0,
    val trackCount: Int = 0,
)

/** 歌曲 */
data class Song(
    val id: Long,
    val name: String,
    val artists: List<String>,
    val album: String = "",
    val picUrl: String = "", // 歌曲封面（歌单内歌曲通常无，用歌单封面兜底）
    val duration: Long = 0,
)

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
                typeTitle = o.optString("typeTitle").takeIf { it.isNotBlank() },
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
            )
        }
    }

    fun parsePlaylistDetail(root: JSONObject): Playlist? {
        val p = root.optJSONObject("playlist") ?: return null
        val id = p.optLong("id")
        val name = p.optString("name")
        if (id == 0L || name.isEmpty()) return null
        return Playlist(
            id = id,
            name = name,
            coverUrl = p.optString("coverImgUrl"),
            playCount = p.optLong("playCount"),
            trackCount = p.optInt("trackCount"),
        )
    }

    /** 歌单歌曲列表（songs 数组，歌单里没有专辑封面图） */
    fun parseSongs(root: JSONObject): List<Song> {
        val arr = root.optJSONArray("songs") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            parseSong(arr.optJSONObject(i))
        }
    }

    /** 搜索结果的歌曲列表（result.songs，带专辑封面） */
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
        val artists = parseNames(o.optJSONArray("ar"))
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
        )
    }

    private fun parseNames(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * /song/detail 批量返回歌曲封面（搜索接口的 album 无 picUrl，只有 picId，
     * 无法直接拼出图片地址；song/detail 里 al.picUrl 才是可直接加载的封面）。
     * 返回 id → 封面 URL 映射。
     */
    fun parseSongDetailCovers(root: JSONObject): Map<Long, String> {
        val arr = root.optJSONArray("songs") ?: return emptyMap()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("id")
            if (id == 0L) return@mapNotNull null
            val pic = o.optJSONObject("al")?.optString("picUrl")?.takeIf { it.isNotBlank() }
            if (pic == null) null else id to pic
        }.toMap()
    }

    /** /song/url/v1 返回可播放 URL，无则返回 null（VIP/无版权） */
    fun parseSongUrl(root: JSONObject): String? {
        val arr = root.optJSONArray("data") ?: return null
        if (arr.length() == 0) return null
        val url = arr.optJSONObject(0)?.optString("url")
        return url?.takeIf { it.isNotBlank() }
    }
}
