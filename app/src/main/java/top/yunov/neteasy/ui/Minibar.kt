package top.yunov.neteasy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.yunov.neteasy.Screen
import top.yunov.neteasy.player.PlayerController

/** 导航状态（由 NcmApp 提供） */
data class NavState(val screen: Screen, val onNavigate: (Screen) -> Unit)

val LocalNavState = staticCompositionLocalOf<NavState> {
    error("LocalNavState not provided")
}

/**
 * 底部区域：迷你播放栏（有播放中歌曲时显示）+ 底部导航（首页/搜索）。
 * 导航状态通过 CompositionLocal 从 NcmApp 注入。
 */
@Composable
fun Minibar(
    state: PlayerController.PlayerUiState,
    onToggle: () -> Unit,
    navState: NavState,
) {
    CompositionLocalProvider(LocalNavState provides navState) {
        Column {
            val song = state.song
            if (song != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = song.picUrl.ifEmpty { null },
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                        ) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artists,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onToggle) {
                            Text(
                                text = if (state.isPlaying) "⏸" else "▶",
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            }
            NavigationBar {
                NavigationBarItem(
                    selected = navState.screen == Screen.HOME,
                    onClick = { navState.onNavigate(Screen.HOME) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "首页") },
                    label = { Text("首页") },
                )
                NavigationBarItem(
                    selected = navState.screen == Screen.SEARCH,
                    onClick = { navState.onNavigate(Screen.SEARCH) },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
                    label = { Text("搜索") },
                )
            }
        }
    }
}
