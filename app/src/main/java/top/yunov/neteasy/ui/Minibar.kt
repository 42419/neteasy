package top.yunov.neteasy.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.isActive
import top.yunov.neteasy.Screen
import top.yunov.neteasy.data.AudioQuality
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.ui.theme.ExpressiveMotion

/** 导航状态（由 NcmApp 提供） */
data class NavState(val screen: Screen, val onNavigate: (Screen) -> Unit)

val LocalNavState =
    staticCompositionLocalOf<NavState> {
        error("LocalNavState not provided")
    }

/**
 * 底部区域：MD3 Expressive 迷你播放器（波浪式进度条 + 上一首/下一首/播放 + 音质切换 + 播放队列入口）
 * + 底部导航。
 */
@Composable
fun Minibar(
    state: PlayerController.PlayerUiState,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onQualityChange: (AudioQuality) -> Unit,
    onExpand: () -> Unit,
    navState: NavState
) {
    CompositionLocalProvider(LocalNavState provides navState) {
        Column {
            val song = state.song
            if (song != null) {
                // 迷你播放器卡片（顶部大圆角 + 阴影）
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        // 平滑进度：每帧按经过的真实时间插值，避免跟着 500ms 一次的轮询“一顿一顿”前进
                        val smoothPositionMs =
                            rememberSmoothPositionMs(
                                positionMs = state.positionMs,
                                isPlaying = state.isPlaying,
                                durationMs = state.durationMs,
                                songId = song.id
                            )

                        // 波浪式进度条（官方 LinearWavyProgressIndicator + 拖动 seek）
                        SeekWaveProgressBar(
                            progress =
                            if (state.durationMs > 0) {
                                smoothPositionMs.toFloat() / state.durationMs
                            } else {
                                0f
                            },
                            onSeek = { fraction ->
                                onSeek((fraction * state.durationMs).toInt())
                            },
                            animating = state.isPlaying,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // 信息行：封面 + 歌名/歌手 + 音质切换 + 播放队列入口（点封面/歌名区域展开全屏播放页）
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.picUrl.ifEmpty { null },
                                contentDescription = null,
                                modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(onClick = onExpand),
                                contentScale = ContentScale.Crop
                            )
                            Column(
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                                    .clickable(onClick = onExpand)
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
                            // 只列出这首歌实际存在的音质档位；还不知道（空集合）时不显示，不瞎列
                            if (song.availableQualities.isNotEmpty()) {
                                QualityChip(
                                    current = state.quality,
                                    available = song.availableQualities,
                                    onSelect = onQualityChange
                                )
                                Box(modifier = Modifier.size(4.dp))
                            }
                            IconButton(onClick = onOpenQueue) {
                                Icon(Icons.Filled.QueueMusic, contentDescription = "播放队列")
                            }
                        }

                        // 播放控制行：上一首 / 播放暂停 / 下一首
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
                                Icon(
                                    Icons.Filled.SkipPrevious,
                                    contentDescription = "上一首",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Box(modifier = Modifier.size(20.dp))
                            PlayButton(isPlaying = state.isPlaying, onClick = onToggle)
                            Box(modifier = Modifier.size(20.dp))
                            IconButton(onClick = onNext, enabled = state.hasNext) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    contentDescription = "下一首",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
            NavigationBar {
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
    }
}

/**
 * 音质切换按钮：只展示 [available]（这首歌实际存在的音质档位）里的选项，不存在的档位不列出。
 * 当前选中档位用主题色高亮。
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

/** 毫秒 → mm:ss（用于展开播放页的时间标签，Minibar 本身不展示时间文字，节省空间） */
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
