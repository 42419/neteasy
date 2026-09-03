package top.yunov.neteasy.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.model.Playlist
import top.yunov.neteasy.data.model.Song
import top.yunov.neteasy.data.model.thumbnail
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.toPlayerSong
import top.yunov.neteasy.ui.components.formatTime
import top.yunov.neteasy.ui.theme.ButtonShape
import top.yunov.neteasy.ui.theme.ExpressiveMotion
import top.yunov.neteasy.ui.theme.extractCoverSeedColor

/**
 * 歌单详情页（全屏覆盖层）：MD3 Expressive 头图 + 播放全部 + 歌曲列表。
 * 头图背景用封面取色（[extractCoverSeedColor]，跟全局动态取色是同一套逻辑）铺一层
 * 渐变色，往下过渡到正常背景色——参考网易云歌单页那种「跟着封面色调走」的头图效果，
 * 不是随便配的固定颜色。取不到色（图片加载失败/颜色太灰被判定不适合当主题色）就
 * 老老实实用 surfaceContainer 兜底，不强求。
 *
 * 版式改版（2026-09）：之前头图只有封面+歌名+曲数，/playlist/detail 实际返回的创建者、
 * 简介、收藏数、评论数、标签这些字段一直有但没解析进 [Playlist] 模型、更没往 UI 上摆——
 * 现在都补上了（改动见 Models.kt 的 parsePlaylistDetail），头图更接近网易云歌单页的
 * 信息密度。顶部原来单独一行「歌单」大标题跟头图里真正的歌单名重复，去掉了，只留一个
 * 悬浮返回按钮（官方 App 这块通常也是半透明返回箭头，标题信息都在头图本体里）。
 * 歌曲行新增序号 + 时长，从跟搜索结果共用的 [SongRow] 换成本页专属的 [PlaylistSongRow]——
 * 序号是歌单场景特有的东西，搜索结果那边不需要，不去改共用组件影响搜索页。
 *
 * 加载策略：
 * - 有内存缓存（[NcmRepository.cachedPlaylistOrNull]）：立即用缓存内容渲染，不显示加载动画，
 *   同时在后台静默重新拉取一次最新数据（拉取失败也不影响已展示的内容，安静忽略）。
 * - 没有缓存（第一次打开这个歌单）：正常显示加载动画，等首次网络请求。
 * - 下拉刷新：无视缓存强制重新拉取，参照系统惯例的下拉手势。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(playlistId: Long, repository: NcmRepository, player: PlayerController, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<Playlist?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var headerColor by remember { mutableStateOf<Color?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun fetch(forceRefresh: Boolean): Boolean {
        return try {
            val (d, s) =
                withContext(Dispatchers.IO) {
                    repository.playlistDetailAndSongs(playlistId, forceRefresh)
                }
            detail = d
            // 歌单内歌曲无封面，用歌单封面兜底
            songs = s.map { it.copy(picUrl = it.picUrl.ifEmpty { d.coverUrl }) }
            error = null
            true
        } catch (e: Exception) {
            // 已经有内容在展示的话（缓存命中过/之前加载成功过），刷新失败就安静忽略，
            // 不拿一个后台失败去打断用户正在看的内容；只有完全没数据时才报错阻断
            if (detail == null) error = e.message ?: "加载失败"
            false
        }
    }

    LaunchedEffect(playlistId) {
        val cached = repository.cachedPlaylistOrNull(playlistId)
        if (cached != null) {
            detail = cached.first
            songs = cached.second.map { it.copy(picUrl = it.picUrl.ifEmpty { cached.first.coverUrl }) }
            loading = false
            fetch(forceRefresh = true) // 后台静默刷新一次，不影响已经秒出的内容
        } else {
            loading = true
            fetch(forceRefresh = false)
            loading = false
        }
    }

    LaunchedEffect(detail?.coverUrl) {
        headerColor = detail?.coverUrl?.let { extractCoverSeedColor(context, it) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                // 全屏覆盖层，Scaffold 的 insets 罩不到这里，自己处理状态栏/手势导航栏
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            when {
                loading ->
                    // 加载指示器盖在整页内容上，按规范用带容器变体提供对比
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                error != null ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("加载失败：$error")
                            FilledTonalIconButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    }
                else -> {
                    val pl = detail
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = {
                            scope.launch {
                                refreshing = true
                                fetch(forceRefresh = true)
                                refreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            // 底部留白让最后几首歌不会被悬浮 Minibar 挡住，同上
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            if (pl != null) {
                                item {
                                    PlaylistHeader(
                                        pl = pl,
                                        headerColor = headerColor,
                                        onBack = onBack,
                                        onPlayAll = {
                                            if (songs.isNotEmpty()) {
                                                player.playQueue(songs.map { it.toPlayerSong() }, 0)
                                            }
                                        }
                                    )
                                }
                            }
                            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                                PlaylistSongRow(
                                    index = index + 1,
                                    song = song,
                                    onClick = {
                                        player.playQueue(songs.map { it.toPlayerSong() }, index)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 头图区：封面取色渐变 → 背景色。封面 + 歌单名/创建者/播放·收藏·评论数 并排，
 * 下面依次是简介（超过 2 行截断）、标签（有的话）、播放全部按钮——参考网易云歌单页
 * 从上到下的信息顺序。返回按钮悬浮在最上面，不跟歌单名再重复一遍标题。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistHeader(pl: Playlist, headerColor: Color?, onBack: () -> Unit, onPlayAll: () -> Unit) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        (headerColor ?: MaterialTheme.colorScheme.surfaceContainer).copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Row(modifier = Modifier.padding(top = 12.dp)) {
                AsyncImage(
                    model = pl.coverUrl.thumbnail(360),
                    contentDescription = null,
                    modifier =
                    Modifier
                        .size(132.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = pl.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pl.creatorName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            if (pl.creatorAvatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = pl.creatorAvatarUrl.thumbnail(60),
                                    contentDescription = null,
                                    modifier =
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                text = pl.creatorName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = if (pl.creatorAvatarUrl.isNotBlank()) 6.dp else 0.dp)
                            )
                        }
                    }
                    // 播放量一直有意义就一直显示；收藏/评论数为 0 大概率是接口没返回（比如榜单类
                    // 歌单本身就没有这两个字段），不是「真的 0 人收藏 0 条评论」，显示 0 反而误导人
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StatItem(Icons.Filled.PlayArrow, formatCount(pl.playCount))
                        if (pl.subscribedCount > 0) StatItem(Icons.Filled.Favorite, formatCount(pl.subscribedCount))
                        if (pl.commentCount > 0) StatItem(Icons.AutoMirrored.Filled.Comment, formatCount(pl.commentCount))
                    }
                }
            }
            if (pl.description.isNotBlank()) {
                Text(
                    text = pl.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            if (pl.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    pl.tags.forEach { tag -> TagChip(tag) }
                }
            }
            FilledTonalButton(
                onClick = onPlayAll,
                modifier = Modifier.padding(top = 16.dp),
                shape = ButtonShape
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    // 带上曲数，网易云播放全部按钮就是这个「播放全部 (23)」的写法
                    if (pl.trackCount > 0) "播放全部 (${pl.trackCount})" else "播放全部",
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** 头图里播放量/收藏数/评论数那种「小图标 + 数字」的小组件。 */
@Composable
private fun StatItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** 标签胶囊：只有官方精选歌单的 /playlist/detail 才会带 tags，普通用户自建歌单通常没有。 */
@Composable
private fun TagChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * 歌单详情页专属的歌曲行：序号 + 封面 + 标题/歌手 + 时长。跟搜索结果共用的 [SongRow] 相比
 * 多了序号、少了那个占位用的圆形播放图标（整行本来就能点，同样的播放动作没必要在行尾再放
 * 一个图标占地方，腾出来放时长——网易云/QQ音乐的歌单列表都是「序号+标题+时长」这个结构，
 * 不是「封面+标题+播放键」）。序号是歌单场景特有的信息，不下沉到共用组件里影响搜索页。
 */
@Composable
private fun PlaylistSongRow(index: Int, song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "playlistSongRowScale"
    )
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
        AsyncImage(
            model = song.picUrl.thumbnail(160).ifEmpty { null },
            contentDescription = null,
            modifier =
            Modifier
                .size(48.dp)
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
                text = song.artists.joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (song.duration > 0) {
            Text(
                text = formatTime(song.duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> "$count"
}
