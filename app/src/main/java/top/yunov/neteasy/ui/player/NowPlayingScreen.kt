package top.yunov.neteasy.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import top.yunov.neteasy.data.AudioQuality
import top.yunov.neteasy.data.model.thumbnail
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.RepeatMode
import top.yunov.neteasy.ui.components.CoverImage
import top.yunov.neteasy.ui.components.ExpandMore
import top.yunov.neteasy.ui.components.Pause
import top.yunov.neteasy.ui.components.QualityChip
import top.yunov.neteasy.ui.components.QueueMusic
import top.yunov.neteasy.ui.components.Repeat
import top.yunov.neteasy.ui.components.SeekWaveProgressBar
import top.yunov.neteasy.ui.components.SkipNext
import top.yunov.neteasy.ui.components.SkipPrevious
import top.yunov.neteasy.ui.components.TrendingFlat
import top.yunov.neteasy.ui.components.formatTime
import top.yunov.neteasy.ui.components.rememberSmoothPositionMs
import top.yunov.neteasy.ui.theme.ExpressiveMotion
import top.met6.amll.AppleMusicLyricPlayer
import top.met6.amll.AppleMusicLyricPlayerStyle

/**
 * 展开后的全屏播放页（点 Minibar 封面/歌名展开）。
 * 布局：顶部收起/队列 → 中部内容区（标题 + 封面 ↔ 小封面+标题+歌词 的连续弹簧过渡，
 * 点封面切换）→ 波浪进度条 + 时间 → 大圆角方块播放控制 → 底部辅助行（循环/音质/歌词开关）。
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
    /** 当前歌曲是否已喜欢，驱动顶部红心图标的填充状态 */
    liked: Boolean = false,
    onToggleLike: () -> Unit = {},
    /** 歌词渲染样式，由调用方（PlayerOverlay）按 SettingsStore 里的歌词设置组装好传进来。 */
    lyricStyle: AppleMusicLyricPlayerStyle = AppleMusicLyricPlayerStyle(),
    /** 已按显示设置（翻译/音译开关）裁剪过的歌词行；默认直接用未裁剪的原始数据。 */
    lyricLines: List<top.met6.amll.LyricLine> = state.lyricLines,
    /**
     * 歌词同步偏移（毫秒）：正值歌词提前显示，负值延后（设置页可调）。
     * 叠加在播放时钟上，点击歌词跳转时会反向扣除，保证“点哪行播哪行”仍然对齐。
     */
    lyricOffsetMs: Long = 0,
    /** 歌词字体：默认系统字体或内置霞鹜文楷（设置页可选）。 */
    lyricFontFamily: FontFamily = FontFamily.Default
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
        ) {
            // 顶部栏只留导航性质的两个按钮：收起 / 播放队列。
            // 之前红心、词切换也堆在这一排（收起/红心/词/队列四个挤一排），功能混杂：
            // 红心是对「这首歌」的操作，挪到歌名旁边；词切换是底部显示模式的开关，
            // 挪到底部辅助控件行，跟循环模式/音质这些「播放器状态开关」归为一类
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = "收起")
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = "播放队列")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 封面 ↔ 歌词 的连续过渡（点封面切换，也可以点底部辅助行的歌词图标）：
            // 不用两个布局交叉淡入（那是「一个消失、另一个出现」，元素之间没有关系），
            // 而是同一份封面/标题在尺寸与位置上做弹簧插值——视觉上就是封面缩小的同时
            // 带着标题一起「飞」到左上角，下方整片区域淡入滚动歌词（参考 Apple Music /
            // 参考截图的小封面+标题+全屏歌词布局）；再点左上角的小封面原路飞回大封面。
            // t=0：标题整行 + 大封面在下方剩余空间居中；t=1：56dp 小封面贴左上，
            // 标题右移到小封面旁边（收成单行小字），歌词占满下方。
            BoxWithConstraints(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val t by animateFloatAsState(
                    targetValue = if (showLyrics) 1f else 0f,
                    // 实测 ExpressiveMotion.SpatialDefault（MediumBouncy 过冲）用在这块大封面的
                    // 位移+缩放上过弹了，封面飞过目标位置再弹回来的感觉很晃；换成同档位刚度、
                    // 无过冲的弹簧——位移干净利落，靠 MediumLow 的低刚度保留一点缓和感
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "coverLyricTransition"
                )
                val areaW = maxWidth
                val areaH = maxHeight
                // 标题块实际高度（封面模式下大封面要在「标题以下」的剩余空间里居中，得知道标题占了多少）
                var titleH by remember { mutableIntStateOf(0) }
                val titleHDp = with(LocalDensity.current) { titleH.toDp() }
                val titleOffsetX = lerp(0.dp, 68.dp, t)
                val coverCorner = lerp(28.dp, 12.dp, t)

                // 歌词区：t 过半才进组合、退到一半就退出组合——挂载/卸载 AMLL 渲染器是
                // 重操作，让它发生在动画中段（运动最快、视觉最不敏感的时刻），而不是像之前
                // t>0.01 那样卡在端点：到位瞬间挂载一整棵歌词树，肉眼看到的就是「顿一下」。
                // alpha 在 0.5~1 区间重新映射到 0~1，淡入依然连续
                if (t > 0.5f) {
                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height((areaH - 72.dp).coerceAtLeast(0.dp))
                            .graphicsLayer { alpha = ((t - 0.5f) * 2f).coerceIn(0f, 1f) }
                    ) {
                        if (lyricLines.isNotEmpty()) {
                            AppleMusicLyricPlayer(
                                lyricLines = lyricLines,
                                currentTimeMs = state.positionMs + lyricOffsetMs,
                                isPlaying = state.isPlaying,
                                modifier = Modifier.fillMaxSize(),
                                style = lyricStyle,
                                fontFamily = lyricFontFamily,
                                onLineClick = { line ->
                                    // 直接 seek line.startTime 会跳不准：LyricOptimizer 会把行的显示开始
                                    // 时间提前最多 600ms 用于视觉过渡，这里用第一个有字的词的真实时间；
                                    // 再反向扣除用户设置的歌词偏移，保证“点哪行播哪行”仍然对齐。
                                    val lyricPosition =
                                        line.words.firstOrNull { it.word.isNotBlank() }?.startTime
                                            ?: line.startTime
                                    onSeek(((lyricPosition - lyricOffsetMs).coerceAtLeast(0L)).toInt())
                                }
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
                    }
                }

                // 标题块：封面模式整行靠左；歌词模式右移到小封面旁边、字号收小（逐帧插值，
                // 跟封面的位移同一条弹簧曲线，两边节奏天然对齐）
                Column(
                    modifier =
                    Modifier
                        .offset(x = titleOffsetX)
                        .fillMaxWidth()
                        .padding(end = 48.dp)
                        .onSizeChanged { titleH = it.height }
                ) {
                    Text(
                        text = song.name,
                        style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontSize = lerp(24.sp, 16.sp, t),
                            lineHeight = lerp(32.sp, 22.sp, t)
                        ),
                        fontWeight = FontWeight.Bold,
                        // maxLines 在 t≈1（到位）才收成 1 行：此时封面 y 已经贴 0，不再依赖
                        // 标题高度，重排不会让封面跳；之前在 t>0.5 中途硬切，标题从两行变一行
                        // 高度突变 → 封面 Y 目标值突变，就是到位前那一下「顿」
                        maxLines = if (t > 0.99f) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artists,
                        style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = lerp(16.sp, 13.sp, t),
                            lineHeight = lerp(24.sp, 18.sp, t)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // 红心：固定在内容区右上角，两种模式位置一致，不参与过渡
                IconButton(onClick = onToggleLike, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (liked) "取消喜欢" else "喜欢",
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 封面：尺寸/位置/圆角/阴影都随 t 插值，点击在两种模式间切换。
                // 封面模式下在「标题以下」的剩余空间里垂直居中；歌词模式下贴左上角
                val coverSize = lerp(areaW, 56.dp, t)
                val coverYCentered =
                    (titleHDp + (areaH - titleHDp - coverSize) / 2).coerceAtLeast(titleHDp)
                CoverImage(
                    url = song.picUrl.thumbnail(900),
                    contentDescription = if (showLyrics) "返回封面视图" else "查看歌词",
                    // 钉死 900px 解码：歌词态 56dp 小图是高清缩小（锐利），放大回大封面
                    // 直接用同一份缓存——歌词态切下一首再回来，大图不会是按 56dp 采样的糊图
                    requestSizePx = 900,
                    modifier =
                    Modifier
                        .offset(y = lerp(coverYCentered, 0.dp, t))
                        .size(coverSize)
                        .shadow(
                            elevation = lerp(16.dp, 0.dp, t),
                            shape = RoundedCornerShape(coverCorner),
                            ambientColor = Color.Black.copy(alpha = 0.3f)
                        )
                        .clip(RoundedCornerShape(coverCorner))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showLyrics = !showLyrics },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // 底部辅助控件行：循环模式（左）| 音质（右）| 封面/歌词切换（右）。
            // 之前循环模式单独占一整行居中孤立在页面最底，音质又单独一层在进度条上方，
            // 三个「播放器状态开关」散在三个高度上；归拢到一行后，控件区是整齐的
            // 「进度条 → 主控按钮 → 辅助开关」三层结构，纵向也省出一块给封面/歌词
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                // 循环模式：点击依次切换 顺序播放 → 列表循环 → 单曲循环，三种状态各用各的图标
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
                Spacer(modifier = Modifier.weight(1f))
                // 音质切换：只列这首歌实际存在的档位；未知（空集合）时不显示
                if (song.availableQualities.isNotEmpty()) {
                    QualityChip(
                        current = state.quality,
                        available = song.availableQualities,
                        onSelect = onQualityChange
                    )
                }
                // 封面 / 歌词切换：文字「词」按钮换成歌词图标，跟同行的循环模式图标形态一致
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        Icons.Filled.Lyrics,
                        contentDescription = if (showLyrics) "显示封面" else "显示歌词",
                        tint =
                        if (showLyrics) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
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
