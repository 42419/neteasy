package top.yunov.neteasy.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「喜欢的音乐」红心状态（App 级单例，见 [top.yunov.neteasy.NeteasyApp]）。
 * 持有当前登录用户已喜欢的歌曲 id 集合，供各处歌曲行 / 播放页的红心图标订阅显示，
 * 并提供 [toggle] 做红心切换（乐观更新 UI，请求失败则回滚）。
 *
 * 未登录时 [likedIds] 恒为空集合，[toggle] 直接返回原状态、不发请求。
 */
class LikeRepository(
    private val scope: CoroutineScope,
    private val apiClient: ApiClient,
    private val repository: NcmRepository
) {
    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedIds: StateFlow<Set<Long>> = _likedIds.asStateFlow()

    init {
        refresh()
    }

    /** 重新拉取「喜欢的音乐」列表；未登录时清空。登录/登出后调用。 */
    fun refresh() {
        scope.launch {
            if (!apiClient.cookieStore.hasLogin()) {
                _likedIds.value = emptySet()
                return@launch
            }
            val uid =
                withContext(Dispatchers.IO) {
                    repository.loginStatus()?.optJSONObject("account")?.optLong("id")
                } ?: return@launch
            if (uid <= 0) return@launch
            _likedIds.value = withContext(Dispatchers.IO) { repository.likeList(uid) }
        }
    }

    /**
     * 切换一首歌的红心状态：先乐观更新本地状态让 UI 立即响应，请求失败再回滚。
     * 返回切换请求发出后的目标状态（不代表请求一定成功，UI 应订阅 [likedIds] 而不是这个返回值）。
     */
    suspend fun toggle(songId: Long): Boolean {
        val liked = songId in _likedIds.value
        val target = !liked
        _likedIds.value = if (target) _likedIds.value + songId else _likedIds.value - songId
        val ok = withContext(Dispatchers.IO) { repository.like(songId, target) }
        if (!ok) {
            _likedIds.value = if (target) _likedIds.value - songId else _likedIds.value + songId
        }
        return target
    }
}
