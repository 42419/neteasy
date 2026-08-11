package top.yunov.neteasy.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.Banner
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.Playlist

/**
 * 首页：MD3 Expressive 大标题 + Banner 轮播 + 推荐歌单卡片。
 * 数据来自本地 Node 后端 /banner 与 /personalized。
 * 冷启动时 Node 可能未就绪：失败后自动重试 5 次（间隔 2s）。
 */
@Composable
fun HomeScreen(repository: NcmRepository, onOpenPlaylist: (Long) -> Unit, modifier: Modifier = Modifier) {
    var banners by remember { mutableStateOf<List<Banner>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        loading = true
        error = null
        // 冷启动重试：Node 启动需要 2~5 秒，最多重试 5 次
        var attempt = 0
        while (attempt < 5) {
            try {
                val (b, p) =
                    withContext(Dispatchers.IO) {
                        repository.banners() to repository.personalized(20)
                    }
                banners = b
                playlists = p
                loading = false
                return@LaunchedEffect
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt >= 5) {
                    error = e.message ?: "加载失败"
                    loading = false
                    return@LaunchedEffect
                }
                delay(2000)
            }
        }
    }

    when {
        loading -> Centered(modifier) { WavyCircularLoadingIndicator() }
        error != null ->
            Centered(modifier) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败：$error")
                    TextButton(onClick = { retryKey++ }) { Text("重试") }
                }
            }
        else ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = modifier,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                        Text("发现音乐", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "为你推荐",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (banners.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { BannerStrip(banners) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "推荐歌单",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                gridItems(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(playlist, onClick = { onOpenPlaylist(playlist.id) })
                }
            }
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

/** Banner 横向滑动（LazyRow + 固定宽度，适配不同屏宽） */
@Composable
private fun BannerStrip(banners: List<Banner>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bannerWidth = maxWidth // 网格已有左右 16dp padding
        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            listItems(banners.take(5), key = { it.picUrl }) { banner ->
                AsyncImage(
                    model = banner.picUrl,
                    contentDescription = banner.typeTitle,
                    modifier =
                    Modifier
                        .width(bannerWidth)
                        .aspectRatio(2f)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/** 推荐歌单卡片：大圆角 + 按压弹性缩放 */
@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playlistCardScale"
    )
    Column(
        modifier =
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = playlist.name,
            modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " ${formatCount(playlist.playCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> "$count"
}
