package top.yunov.neteasy.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.yunov.neteasy.Screen
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.ui.theme.ExpressiveMotion

/** 导航状态（由 NcmApp 提供） */
data class NavState(val screen: Screen, val onNavigate: (Screen) -> Unit)

val LocalNavState =
    staticCompositionLocalOf<NavState> {
        error("LocalNavState not provided")
    }

/**
 * 底部区域：MD3 Expressive 迷你播放器（波浪式进度条 + 弹性播放键）+ 底部导航。
 */
@Composable
fun Minibar(state: PlayerController.PlayerUiState, onToggle: () -> Unit, onSeek: (Int) -> Unit, navState: NavState) {
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
                        // 波浪式进度条（官方 LinearWavyProgressIndicator + 拖动 seek）
                        SeekWaveProgressBar(
                            progress =
                            if (state.durationMs > 0) {
                                state.positionMs.toFloat() / state.durationMs
                            } else {
                                0f
                            },
                            onSeek = { fraction ->
                                onSeek((fraction * state.durationMs).toInt())
                            },
                            animating = state.isPlaying,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.picUrl.ifEmpty { null },
                                contentDescription = null,
                                modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(MaterialTheme.shapes.small),
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
                            Text(
                                text = formatTime(state.positionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            PlayButton(isPlaying = state.isPlaying, onClick = onToggle)
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
                    selected = navState.screen == Screen.SEARCH,
                    onClick = { navState.onNavigate(Screen.SEARCH) },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
                    label = { Text("搜索") }
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

/** 弹性播放/暂停键（MD3 Expressive spring 动效 + 官方 FilledTonalIconButton） */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.12f else 1f,
        // spatial spring：位移/尺寸变化有过冲（MD3 Expressive）
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "playButtonScale"
    )
    Box(
        modifier =
        Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            colors =
            filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** 毫秒 → mm:ss */
private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
