package top.yunov.neteasy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yunov.neteasy.data.model.thumbnail
import top.yunov.neteasy.player.PlayerController

/**
 * 悬浮迷你播放器（紧凑单行：封面 + 歌名/歌手 + 播放暂停 + 播放队列，点击展开全屏播放页）。
 * 上一首/下一首/进度条/音质切换这些完整控制留在展开播放页（NowPlayingScreen），
 * 折叠态只做最核心的信息展示和播放/暂停，不重复堆控件。
 *
 * 这是个纯粹的悬浮卡片，不含底部导航——由 [PlayerAwareContent] 统一以覆盖层形式
 * 挂到每个 Activity 的内容之上，这样首页/我的/搜索/歌单详情都能显示同一个悬浮条，
 * 不必每个页面各自实现一遍。仅设置页（含存储空间等设置子页）和登录页不接入。
 */
@Composable
fun PlayerMinibar(
    state: PlayerController.PlayerUiState,
    onToggle: () -> Unit,
    onOpenQueue: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = state.song ?: return
    val cardShape = RoundedCornerShape(24.dp)

    // 纯色块背景，跟随全局动态取色（MaterialTheme.colorScheme 本身就是由封面取色驱动的，
    // 见 NeteasyTheme/CoverThemeController；这里不用额外去拿封面颜色，直接用主题色即可，
    // 「跟随封面取色」开关在设置页关掉的话，这里也会自动跟着变回默认配色，行为统一）。
    // 背景模糊这条路走过两轮都不理想：Haze 在 LazyColumn 当模糊源时有个已知未修复的
    // 渲染 bug（#865，边缘/文字不模糊，糊出方块痕迹）；换 Cloudy 之后虽然是真背景模糊，
    // 但实测颜色不对+有杆状痕迹，而且列表滚动时会因为持续重新采样背景整体卡顿。
    // 两条路子都不划算，改成简单可靠的纯色块——没有实时采样/合成开销，也没有渲染 bug
    // 空间，观感上跟着每首歌的封面色调换，同样是「跟这首歌的封面联动」的效果。
    Surface(
        onClick = onExpand,
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = cardShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 6.dp
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    url = song.picUrl.thumbnail(160),
                    contentDescription = null,
                    modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artists,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PlayButton(isPlaying = state.isPlaying, onClick = onToggle, size = 40.dp)
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = "播放队列")
                }
            }
        }
    }
}
