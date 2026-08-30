package top.yunov.neteasy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.yunov.neteasy.data.model.thumbnail
import top.yunov.neteasy.player.PlayerController

/**
 * 播放队列面板：展示一份歌曲列表，点某一首直接跳转播放，正在播放的一项高亮。
 * 除了「当前播放队列」，也复用给「每日推荐」这类先看列表、点哪首播哪首的场景——
 * [title] 换个文案、[onPlayAll] 传非 null 就会多一行「播放全部」，
 * 不用为每种列表场景单独写一个几乎一样的底部面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<PlayerController.PlayerSong>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    title: String = "播放队列",
    onPlayAll: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "$title · ${queue.size} 首",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        if (onPlayAll != null) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "播放全部",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
        LazyColumn(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            items(queue.size, key = { i -> "${queue[i].id}_$i" }) { index ->
                val song = queue[index]
                val playing = index == currentIndex
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = song.picUrl.thumbnail(150).ifEmpty { null },
                        contentDescription = null,
                        modifier =
                        Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            song.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                            color =
                            if (playing) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artists,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (playing) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "正在播放",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
