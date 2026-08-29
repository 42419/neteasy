package top.yunov.neteasy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.isActive
import top.yunov.neteasy.data.AudioQuality
import top.yunov.neteasy.ui.theme.ExpressiveMotion

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
