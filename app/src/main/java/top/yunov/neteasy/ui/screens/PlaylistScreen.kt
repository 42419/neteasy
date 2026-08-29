package top.yunov.neteasy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
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
import top.yunov.neteasy.ui.theme.ButtonShape

/**
 * 歌单详情页（全屏覆盖层）：MD3 Expressive 大封面头部 + 播放全部 + 歌曲列表。
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
    val scope = rememberCoroutineScope()

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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                // 全屏覆盖层，Scaffold 的 insets 罩不到这里，自己处理状态栏/手势导航栏
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // 顶部返回栏
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 官方 FilledTonalIconButton：自带容器 + Expressive spring 动效
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "歌单",
                    // 与其他二级页面（设置/存储/搜索）标题统一：粗体大字（headlineLarge）
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            when {
                loading ->
                    // 加载指示器盖在整页内容上，按规范用带容器变体提供对比
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                error != null ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载失败：$error")
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
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (pl != null) {
                                item {
                                    Row(
                                        modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = pl.coverUrl.thumbnail(360),
                                            contentDescription = null,
                                            modifier =
                                            Modifier
                                                .size(120.dp)
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
                                            Text(
                                                text = "${pl.trackCount} 首",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                            FilledTonalButton(
                                                onClick = {
                                                    if (songs.isNotEmpty()) {
                                                        player.playQueue(
                                                            songs.map { it.toPlayerSong() },
                                                            0
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.padding(top = 12.dp),
                                                shape = ButtonShape
                                            ) {
                                                Icon(
                                                    Icons.Filled.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    "播放",
                                                    modifier = Modifier.padding(start = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                                SongRow(
                                    song = song,
                                    onClick = {
                                        player.playQueue(songs.map { it.toPlayerSong() }, index)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
