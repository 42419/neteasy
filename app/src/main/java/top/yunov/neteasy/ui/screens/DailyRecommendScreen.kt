package top.yunov.neteasy.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.model.Song
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.toPlayerSong
import top.yunov.neteasy.ui.theme.ButtonShape

/**
 * 每日推荐独立页面：跟歌单详情页（[PlaylistScreen]）同一套视觉语言，不是临时糊的底部
 * 弹窗——之前图省事直接拿 QueueSheet 弹一下，跟点其他歌单进独立页面的体验不一致，
 * 改成正经页面。
 *
 * 头图用固定暖色渐变（不像歌单详情页那样跟着封面取色）——网易云这个页面本来就是
 * 固定的橙色调，不是从某张封面提取出来的，所以没有接 [top.yunov.neteasy.ui.theme.extractCoverSeedColor]。
 * 「默认推荐/风格推荐」切换 tab、「查看今日运势」、「历史日推」这几个网易云原版有的东西
 * 没做——都是没有对应后端支撑的功能（风格推荐需要额外的推荐策略参数、运势是完全
 * 不相关的另一个模块、历史日推需要本地或服务端存档），做了也是几个点不动的假按钮，
 * 不如不做。
 *
 * 数据源是 /recommend/songs（需要登录），跟首页那份是同一个接口，这里独立再拉一次，
 * 不依赖首页已经加载过的状态（用户可能是重进这个页面，首页的数据不一定还在）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DailyRecommendScreen(repository: NcmRepository, player: PlayerController, onBack: () -> Unit) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            songs =
                withContext(Dispatchers.IO) {
                    repository.recommendSongs()
                }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        loading = false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                // 全屏覆盖层，Scaffold 的 insets 罩不到这里，自己处理状态栏/手势导航栏
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }

            when {
                loading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                error != null ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载失败：$error")
                    }
                songs.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无推荐，登录后查看", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 底部留白让最后几首歌不会被悬浮 Minibar 挡住，同歌单详情页
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            Column(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFFFB74D).copy(alpha = 0.5f), MaterialTheme.colorScheme.background)
                                        )
                                    )
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd / MM")),
                                    style = MaterialTheme.typography.displaySmall
                                )
                                Text(
                                    "根据你的音乐口味生成，每日 6:00 更新",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                FilledTonalButton(
                                    onClick = { player.playQueue(songs.map { it.toPlayerSong() }, 0) },
                                    modifier = Modifier.padding(top = 12.dp),
                                    shape = ButtonShape
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("播放全部", modifier = Modifier.padding(start = 6.dp))
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
