package top.yunov.neteasy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.LikeRepository
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.model.Song
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.toPlayerSong
import top.yunov.neteasy.ui.components.SongActionSheet
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
 *
 * 歌曲行用的是歌单详情页那套 [IndexedSongRow]（序号 + 时长），不是搜索页那个大圆形播放键
 * 的 [SongRow]——之前这页一直没跟着歌单详情页一起改，还是老样式，现在补上，两个「有序歌曲
 * 列表」页面（歌单详情、每日推荐）视觉语言保持一致。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DailyRecommendScreen(
    repository: NcmRepository,
    player: PlayerController,
    likeRepository: LikeRepository,
    onBack: () -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionSheetSong by remember { mutableStateOf<Song?>(null) }
    val likedIds by likeRepository.likedIds.collectAsState()
    val scope = rememberCoroutineScope()

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
        Column(modifier = Modifier.fillMaxSize()) {
            when {
                // loading/error/暂无推荐这三个状态没有渐变头图可用，各自的 Box 自己吃 systemBars，
                // error/empty 里另外塞一个返回按钮（不然用户会卡在这个状态出不去）
                loading ->
                    Box(
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                error != null ->
                    Box(
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("加载失败：$error")
                            FilledTonalIconButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    }
                songs.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂无推荐，登录后查看", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalIconButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    }
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 底部同时吃手势导航栏实际高度 + 96dp 悬浮 Minibar 净空，最后几首歌能滚动到
                        // 系统导航栏下面，而不是被一块固定 inset 拦住（同歌单详情页的改法）
                        contentPadding =
                        PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
                        )
                    ) {
                        item {
                            // 返回按钮之前是 LazyColumn 外面单独一条固定栏，跟这张渐变头图卡片是两块
                            // 东西——渐变怎么改都贴不到屏幕最顶，因为它上面永远压着那条固定栏。改成
                            // 跟歌单详情页一样，把返回按钮塞进渐变 Column 内部，渐变 Box 本身不吃
                            // 顶部 inset（一路铺到状态栏底下），只有里面的返回按钮单独吃 statusBars
                            Column(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFFFB74D).copy(alpha = 0.5f), MaterialTheme.colorScheme.background)
                                        )
                                    )
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                FilledTonalIconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                                Text(
                                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd / MM")),
                                    style = MaterialTheme.typography.displaySmall,
                                    modifier = Modifier.padding(top = 12.dp)
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
                                    Text(
                                        if (songs.isNotEmpty()) "播放全部 (${songs.size})" else "播放全部",
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                        }
                        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                            IndexedSongRow(
                                index = index + 1,
                                song = song,
                                onClick = {
                                    player.playQueue(songs.map { it.toPlayerSong() }, index)
                                },
                                onLongClick = { actionSheetSong = song },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                            )
                        }
                    }
            }
        }
    }

    val sheetSong = actionSheetSong
    if (sheetSong != null) {
        SongActionSheet(
            songName = sheetSong.name,
            songArtists = sheetSong.artists.joinToString(" / "),
            songPicUrl = sheetSong.picUrl,
            liked = sheetSong.id in likedIds,
            onToggleLike = {
                scope.launch { likeRepository.toggle(sheetSong.id) }
            },
            onDismiss = { actionSheetSong = null }
        )
    }
}
