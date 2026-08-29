package top.yunov.neteasy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yunov.neteasy.data.AudioQuality
import top.yunov.neteasy.data.model.thumbnail
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.RepeatMode
import top.yunov.neteasy.ui.components.CoverImage
import top.yunov.neteasy.ui.components.Pause
import top.yunov.neteasy.ui.components.QualityChip
import top.yunov.neteasy.ui.components.SeekWaveProgressBar
import top.yunov.neteasy.ui.components.formatTime
import top.yunov.neteasy.ui.components.rememberSmoothPositionMs
import top.yunov.neteasy.ui.theme.ExpressiveMotion
import top.met6.amll.AppleMusicLyricPlayer
import top.met6.amll.AppleMusicLyricPlayerStyle

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
    onCollapse: () -> Unit,
    /** 歌词渲染样式，由调用方（PlayerOverlay）按 SettingsStore 里的歌词设置组装好传进来。 */
    lyricStyle: AppleMusicLyricPlayerStyle = AppleMusicLyricPlayerStyle(),
    /** 已按显示设置（翻译/音译开关）裁剪过的歌词行；默认直接用未裁剪的原始数据。 */
    lyricLines: List<top.met6.amll.LyricLine> = state.lyricLines
) {
    val song = state.song ?: return // 理论上不会在没有歌曲时展开；兜底不渲染空页面
    // 歌词 / 封面切换：默认显示封面，点「词」切换为歌词视图
    var showLyrics by remember { mutableStateOf(false) }

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
                // 歌词 / 封面切换
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Text(
                        "词",
                        style = MaterialTheme.typography.titleMedium,
                        color =
                        if (showLyrics) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
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

            // 中部：封面 ↔ 歌词 切换（占据剩余高度，下方控件固定在底部）
            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showLyrics) {
                    if (lyricLines.isNotEmpty()) {
                        AppleMusicLyricPlayer(
                            lyricLines = lyricLines,
                            currentTimeMs = state.positionMs,
                            isPlaying = state.isPlaying,
                            modifier = Modifier.fillMaxSize(),
                            style = lyricStyle,
                            onLineClick = { line -> onSeek(line.startTime.toInt()) }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (state.lyricLoading) "歌词加载中…" else "暂无歌词",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // 大封面：用统一样式的占位（底色 + 音符），加载出图后 crossfade 淡入
                    CoverImage(
                        url = song.picUrl.thumbnail(900),
                        contentDescription = null,
                        modifier =
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(28.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

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
                bufferedProgress =
                if (state.durationMs > 0) {
                    state.bufferedPositionMs.toFloat() / state.durationMs
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

            // 播放控制：参考 M3 Expressive 官方博客的大圆角方块按钮样式——
            // 两侧浅色 tonal 方块（上一首/下一首）比中间实心方块（播放/暂停）窄，
            // 宽度比例照抄参考图（中间明显更宽，不是三等分）
            Row(
                modifier = Modifier.fillMaxWidth().height(88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BigSquareButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "上一首",
                    onClick = onPrevious,
                    enabled = state.hasPrevious,
                    filled = false,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                BigSquareButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    onClick = onToggle,
                    filled = true,
                    modifier = Modifier.weight(1.5f).fillMaxHeight()
                )
                BigSquareButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    onClick = onNext,
                    enabled = state.hasNext,
                    filled = false,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 循环模式：点击依次切换 顺序播放 → 列表循环 → 单曲循环，三种状态各用各的图标
            val repeatLabel =
                when (state.repeatMode) {
                    RepeatMode.OFF -> "顺序播放"
                    RepeatMode.ALL -> "列表循环"
                    RepeatMode.ONE -> "单曲循环"
                }
            val repeatTint =
                if (state.repeatMode == RepeatMode.OFF) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                IconButton(onClick = onCycleRepeat) {
                    when (state.repeatMode) {
                        // 顺序播放：一条直箭头，跟“循环”的环形箭头明显区分
                        RepeatMode.OFF -> Icon(Icons.Filled.TrendingFlat, contentDescription = repeatLabel, tint = repeatTint)
                        // 列表循环：环形箭头
                        RepeatMode.ALL -> Icon(Icons.Filled.Repeat, contentDescription = repeatLabel, tint = repeatTint)
                        // 单曲循环：环形箭头 + 角标“1”
                        RepeatMode.ONE ->
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Repeat, contentDescription = repeatLabel, tint = repeatTint)
                                Text(
                                    "1",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = repeatTint
                                )
                            }
                    }
                }
                Text(repeatLabel, style = MaterialTheme.typography.labelMedium, color = repeatTint)
            }
        }
    }
}

/**
 * 大圆角方块控制按钮：参考 M3 Expressive 官方博客的播放控制样式。
 * [filled] 为 true 时是实心主色方块（播放/暂停这种主操作），否则是浅色 tonal 方块。
 * 按下时有轻微缩小的弹性反馈（spatial spring，带回弹），是 M3 Expressive 按钮动效的核心手感。
 */
@Composable
private fun BigSquareButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false
) {
    val containerColor =
        if (filled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val contentColor =
        if (filled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        // spatial spring：位移/尺寸变化带回弹，是 MD3 Expressive 按钮按压反馈的标准手感
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "controlButtonScale"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor.copy(alpha = if (enabled) 1f else 0.38f),
        interactionSource = interactionSource,
        modifier =
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(32.dp))
        }
    }
}
