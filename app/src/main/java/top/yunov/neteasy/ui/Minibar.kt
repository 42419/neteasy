package top.yunov.neteasy.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.isActive
import top.yunov.neteasy.Screen
import top.yunov.neteasy.data.AudioQuality
import top.yunov.neteasy.data.thumbnail
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.ui.theme.ExpressiveMotion

/** 导航状态（由 NcmApp 提供） */
data class NavState(val screen: Screen, val onNavigate: (Screen) -> Unit)

val LocalNavState =
    staticCompositionLocalOf<NavState> {
        error("LocalNavState not provided")
    }

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
    val glassTint = MaterialTheme.colorScheme.surfaceContainerHigh
    val cardShape = RoundedCornerShape(24.dp)

    // 磨砂玻璃背景：曾经想做“卡片背后内容真实模糊”（haze 库），但那条路在 LazyColumn
    // 当模糊源时有个已知未修复的 bug（渲染出来是一坨方方正正的糊斑，见上次修复记录），
    // 换成更朴素也更稳的做法：拿当前播放歌曲的封面整张拉伸铺满、原生 Modifier.blur() 
    // 模糊一下当卡片背景，不依赖任何第三方库。跟“正在播的这首歌”强关联，
    // Spotify / Apple Music 的迷你播放条也是这个思路，观感上不比真背景模糊差。
    Surface(
        onClick = onExpand,
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = cardShape,
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Box {
            // Modifier.blur() 底层是 RenderEffect，只有 Android 12（API 31）+ 才生效；
            // minSdk 29 的老设备上这行相当于没写，图片正常显示只是不模糊，不会崩，
            // 优雅降级，不用额外判断版本。
            CoverImage(
                url = song.picUrl.thumbnail(160),
                contentDescription = null,
                modifier = Modifier.matchParentSize().blur(28.dp),
                contentScale = ContentScale.Crop
            )
            // 模糊完的封面亮度/色调不可控（有的封面很暗有的很花花绿绿），叠一层半透明
            // 卡片底色兜底文字对比度和整体基调统一，不会出现文字糊在图片上看不清的情况
            Box(
                modifier =
                Modifier
                    .matchParentSize()
                    .background(glassTint.copy(alpha = 0.55f))
            )
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

/**
 * 首页/我的两个 Tab 的底部导航（仅 MainActivity 用）。容器背景设为透明——
 * 不要那条实色背景条，图标/文字直接浮在页面背景色上，视觉上跟上方悬浮的
 * [PlayerMinibar] 融为一体，只有那张悬浮卡片本身带一点浅色底和阴影。
 */
@Composable
fun MainNavigationBar(navState: NavState) {
    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = navState.screen == Screen.HOME,
            onClick = { navState.onNavigate(Screen.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "首页") },
            label = { Text("首页") }
        )
        NavigationBarItem(
            selected = navState.screen == Screen.PROFILE,
            onClick = { navState.onNavigate(Screen.PROFILE) },
            icon = { Icon(Icons.Filled.Person, contentDescription = "我的") },
            label = { Text("我的") }
        )
    }
}

/**
 * 音质切换按钮：只展示 [available]（这首歌实际存在的音质档位）里的选项，不存在的档位不列出。
 * 当前选中档位用主题色高亮。（Minibar 折叠态已不用，展开播放页 NowPlayingScreen 在用）
 */
@Composable
internal fun QualityChip(current: AudioQuality, available: Set<AudioQuality>, onSelect: (AudioQuality) -> Unit) {
    var expandedState by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expandedState = true },
            label = { Text(current.label, style = MaterialTheme.typography.labelSmall) },
            colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary)
        )
        DropdownMenu(
            expanded = expandedState,
            onDismissRequest = { expandedState = false },
            properties = PopupProperties(focusable = true)
        ) {
            // 按标准 → Hi-Res 的固定顺序展示，只保留这首歌实际存在的档位
            AudioQuality.entries.filter { it in available }.forEach { quality ->
                DropdownMenuItem(
                    text = {
                        Text(
                            quality.label,
                            color =
                            if (quality == current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onSelect(quality)
                        expandedState = false
                    }
                )
            }
        }
    }
}

/**
 * 平滑播放进度：底层每 500ms 才轮询一次真实播放位置，直接拿去驱动进度条会“一顿一顿”前进。
 * 这里在每次拿到新的 [positionMs] 时记下时间基准，播放中逐帧用经过的真实时间插值前进，
 * 下一次轮询值到来会重新校正基准（自动纠偏，不会越走越偏）。
 */
@Composable
internal fun rememberSmoothPositionMs(
    positionMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
    songId: Long?
): Long {
    var displayed by remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying, songId) {
        displayed = positionMs
        if (!isPlaying) return@LaunchedEffect
        val baseWallClockMs = System.currentTimeMillis()
        while (isActive) {
            withFrameNanos { }
            val elapsed = System.currentTimeMillis() - baseWallClockMs
            val next = positionMs + elapsed
            displayed = if (durationMs > 0) next.coerceAtMost(durationMs) else next
        }
    }
    return displayed
}

/** 毫秒 → mm:ss（用于展开播放页的时间标签） */
internal fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/** 弹性播放/暂停键（MD3 Expressive spring 动效 + 官方 FilledTonalIconButton） */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayButton(isPlaying: Boolean, onClick: () -> Unit, size: Dp = 52.dp) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.12f else 1f,
        // spatial spring：位移/尺寸变化有过冲（MD3 Expressive）
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "playButtonScale"
    )
    Box(
        modifier =
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(size),
            colors =
            filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}
