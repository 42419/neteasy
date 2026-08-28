package top.yunov.neteasy.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import top.yunov.neteasy.player.PlayerController

/**
 * 全局封面取色：跟着「当前播放的歌」变，不属于某个 Activity/页面——App 级单例
 * （由 [top.yunov.neteasy.NeteasyApp] 持有），所有页面的主题读同一份种子色，
 * 这样切 Activity 也不会颜色跳变、也不用每个页面各自重新提取一遍。
 *
 * 只做「提取种子色」这一件事，不管配色具体怎么用——那是 [NeteasyTheme] 的事，
 * 播放器逻辑和主题逻辑互相不需要知道对方的存在。
 */
class CoverThemeController(
    scope: CoroutineScope,
    appContext: Context,
    player: PlayerController
) {
    private val _seedColor = MutableStateFlow<Color?>(null)

    /** 当前种子色；没有播放中的歌、封面加载失败、或封面颜色太灰太单调时为 null */
    val seedColor: StateFlow<Color?> = _seedColor

    // 用请求序号防旧请求覆盖新结果：切歌很快时，上一首的取色请求可能比这一首晚返回
    private var requestToken = 0

    init {
        scope.launch {
            player.state
                .map { it.song?.picUrl }
                .distinctUntilChanged()
                .collect { url ->
                    val token = ++requestToken
                    val color = extractCoverSeedColor(appContext, url)
                    if (token == requestToken) {
                        _seedColor.value = color
                    }
                }
        }
    }
}
