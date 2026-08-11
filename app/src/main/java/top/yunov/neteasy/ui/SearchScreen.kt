package top.yunov.neteasy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.Song
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.playSongById

/**
 * 搜索页：输入关键词搜索歌曲，点击结果播放。
 * 防抖 500ms，前一个搜索 job 会被取消避免竞态。
 */
@Composable
fun SearchScreen(
    repository: NcmRepository,
    player: PlayerController,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun doSearch(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            results = emptyList()
            searching = false
            return
        }
        searchJob = scope.launch {
            searching = true
            try {
                val list = withContext(Dispatchers.IO) { repository.search(q, 30) }
                results = list
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                results = emptyList()
            } finally {
                searching = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                // 防抖：先取消旧的，500ms 后真正搜索
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(500)
                    doSearch(it)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索歌曲、歌手") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        if (searching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(results, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = {
                            scope.launch {
                                player.playSongById(
                                    repository,
                                    song.id,
                                    song.name,
                                    song.artists,
                                    song.picUrl,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 歌曲列表行（搜索 / 歌单详情共用） */
@Composable
fun SongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.picUrl.ifEmpty { null }, // 歌单内歌曲无封面
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artists.joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("▶", style = MaterialTheme.typography.titleMedium)
    }
}
