package top.yunov.neteasy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.yunov.neteasy.data.AudioQuality
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.RepeatMode

/**
 * 展开后的全屏播放页（点 Minibar 封面/歌名展开）：大标题 + 大封面 + 波浪进度条 + 播放控制。
 * 布局参考常见音乐 App 的“正在播放”页样式：顶部收起按钮，歌名/歌手居中，
 * 大幅封面图，音质/播放队列入口，时间 + 进度条，上一首/播放/下一首，循环模式。
 */
@Composable
fun NowPlayingScreen(
    state: PlayerController.PlayerUiState,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onQualityChange: (AudioQuality) -> Unit,
    onCycleRepeat: () -> Unit,
    onCollapse: () -> Unit
) {
    val song = state.song ?: return // 理论上不会在没有歌曲时展开；兜底不渲染空页面

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：收起按钮
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = "收起")
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = "播放队列")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 歌名 / 歌手，居中，大标题
            Text(
                text = song.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = song.artists,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // 大封面
            AsyncImage(
                model = song.picUrl.ifEmpty { null },
                contentDescription = null,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 音质切换：只列这首歌实际存在的档位；未知（空集合）时不显示
            if (song.availableQualities.isNotEmpty()) {
                QualityChip(
                    current = state.quality,
                    available = song.availableQualities,
                    onSelect = onQualityChange
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // 平滑进度：与 Minibar 共用同一套逐帧插值逻辑，避免“一顿一顿”
            val smoothPositionMs =
                rememberSmoothPositionMs(
                    positionMs = state.positionMs,
                    isPlaying = state.isPlaying,
                    durationMs = state.durationMs,
                    songId = song.id
                )
            SeekWaveProgressBar(
                progress =
                if (state.durationMs > 0) {
                    smoothPositionMs.toFloat() / state.durationMs
                } else {
                    0f
                },
                onSeek = { fraction -> onSeek((fraction * state.durationMs).toInt()) },
                animating = state.isPlaying
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(smoothPositionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 播放控制：上一首 / 播放暂停（大）/ 下一首
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.width(1.dp))
                Box(modifier = Modifier.padding(horizontal = 28.dp)) {
                    PlayButton(isPlaying = state.isPlaying, onClick = onToggle, size = 76.dp)
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首", modifier = Modifier.size(36.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 循环模式：点击依次切换 不循环 → 列表循环 → 单曲循环
            val repeatLabel =
                when (state.repeatMode) {
                    RepeatMode.OFF -> "顺序播放"
                    RepeatMode.ALL -> "列表循环"
                    RepeatMode.ONE -> "单曲循环"
                }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = repeatLabel,
                        tint =
                        if (state.repeatMode == RepeatMode.OFF) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Text(
                    repeatLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                    if (state.repeatMode == RepeatMode.OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}
