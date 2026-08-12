package top.yunov.neteasy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.Playlist
import top.yunov.neteasy.data.Song
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.toPlayerSong
import top.yunov.neteasy.ui.theme.ButtonShape

/**
 * 歌单详情页（全屏覆盖层）：MD3 Expressive 大封面头部 + 播放全部 + 歌曲列表。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistScreen(playlistId: Long, repository: NcmRepository, player: PlayerController, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<Playlist?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        loading = true
        error = null
        try {
            val (d, s) =
                withContext(Dispatchers.IO) {
                    repository.playlistDetail(playlistId) to repository.playlistSongs(playlistId, 100)
                }
            detail = d
            // 歌单内歌曲无封面，用歌单封面兜底
            songs = s.map { it.copy(picUrl = it.picUrl.ifEmpty { d?.coverUrl ?: "" }) }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            when {
                loading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                error != null ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载失败：$error")
                    }
                else -> {
                    val pl = detail
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
                                        model = pl.coverUrl,
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
