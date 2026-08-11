package top.yunov.neteasy.player

import top.yunov.neteasy.data.NcmRepository

/**
 * 便捷扩展：按歌曲 id 解析可播放 URL 后播放。
 * 需在 IO 可调度上下文调用（内部自行切 IO）。
 */
suspend fun PlayerController.playSongById(
    repository: NcmRepository,
    id: Long,
    name: String,
    artists: List<String>,
    picUrl: String
) {
    val url =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repository.songUrl(id)
        }
    play(
        url,
        PlayerController.PlayerSong(
            id = id,
            name = name,
            artists = artists.joinToString(" / "),
            picUrl = picUrl
        )
    )
}
