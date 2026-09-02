package top.yunov.neteasy.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.model.Banner
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.model.Playlist
import top.yunov.neteasy.data.model.Song
import top.yunov.neteasy.data.model.thumbnail
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.toPlayerSong
import top.yunov.neteasy.ui.theme.ExpressiveMotion

/**
 * 首页：MD3 Expressive 搜索栏 + 大标题 + Banner 轮播 + 快捷入口 + 推荐歌单 + 排行榜 + 每日推荐歌曲。
 * 参考网易云/QQ音乐/酷狗首页实际布局做的取舍（详见各板块注释里标的接口来源）：
 * - Banner、推荐歌单、排行榜、每日推荐歌曲：api-enhanced 有对应接口，做了
 * - 雷达歌单：点进去本质就是「每日推荐」那套 UI（播放全部 + 歌曲列表），跟每日推荐
 *   歌曲是同一个数据源（/recommend/songs），不用另外单独接一套
 * - 听过的 X 为你推荐 / 城市热门歌曲 / 漫游 / 艺人定制歌单：网易云内部的个性化推荐
 *   算法，api-enhanced 没有对应接口，做不了
 * - 播客节目推荐：点进去是完全独立的「播客」大 Tab，数据模型和播放链路都要重新搭，
 *   工作量远超首页改版本身，且用户几乎不用，不做
 *
 * 版式改版（2026-09）：原来「推荐歌单」用 2 列网格铺开，卡片下面两行文案跟下一行卡片
 * 挨得太近、观感拥挤；大厂首页这块清一色是横向滑动卡片流，不是网格——改成跟排行榜/
 * 每日推荐单曲一样的 LazyRow，行高只取决于这一行自己，不会有网格「按最高的那个对齐」
 * 导致的拥挤问题。Banner 也从简单 LazyRow 换成真正的居中露边分页轮播（HorizontalPager
 * + 圆点指示器 + 自动轮播），不再是硬切在屏幕边缘的裁切效果。
 * 配色上不再给快捷卡片写死橙红渐变——改用当前 colorScheme 的 primary/tertiary 等，
 * 封面取色开启时快捷卡片会跟着一起变色，符合「跟随封面动态取色为主」的方向。
 *
 * 数据来自本地 Node 后端 /banner /personalized /toplist /recommend/songs。
 * 冷启动时 Node 可能未就绪：失败后自动重试 5 次（间隔 2s）。/recommend/songs
 * 需要登录态，未登录时该板块直接不显示，不影响其他板块。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    repository: NcmRepository,
    player: PlayerController,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDailyRecommend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var banners by remember { mutableStateOf<List<Banner>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var toplist by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var recommendSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
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
                // 排行榜、每日推荐歌曲不阻塞首页主内容——单独拉，失败了对应板块直接不显示，
                // 不影响banner/推荐歌单已经加载出来的部分（也不用重试逻辑，这两个不是冷启动
                // 关键路径，偶尔失败下次进首页再拉就行）。
                withContext(Dispatchers.IO) {
                    runCatching { toplist = repository.toplist() }
                    runCatching { recommendSongs = repository.recommendSongs() }
                }
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
        // 首页冷启动可能超过 5s（Node 后端启动 + 重试）→ 加载指示器盖在内容上，用带容器变体提供对比
        loading -> Centered(modifier) { ContainedLoadingIndicator() }
        error != null ->
            Centered(modifier) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败：$error")
                    TextButton(onClick = { retryKey++ }) { Text("重试") }
                }
            }
        else ->
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                // section 之间留够呼吸感（24dp）
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SearchEntryBar(onClick = onOpenSearch)
                        Column {
                            // 强调排版 hero：大标题引导注意力（MD3 Expressive）
                            Text("发现音乐", style = MaterialTheme.typography.headlineLarge)
                            Text(
                                "为你推荐",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (banners.isNotEmpty()) {
                    item { BannerPager(banners) }
                }
                // 快捷入口：每日推荐 / 排行榜——网易云首页最上面那排彩色卡片的简化版，
                // 只留了两个真正有数据支撑的入口。之前点「每日推荐」直接开始播放，
                // 跟网易云的实际交互不一样（应该先看列表再选，不是点进去就唐突开始播放）——
                // 改成打开底部列表面板，跟 QueueSheet 是同一套组件（详见 QueueSheet.kt 改动）。
                // 渐变色不再写死橙红，直接取当前 colorScheme——封面取色开启时会跟着变。
                if (recommendSongs.isNotEmpty() || toplist.isNotEmpty()) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            if (recommendSongs.isNotEmpty()) {
                                ShortcutCard(
                                    title = "每日推荐",
                                    subtitle = "根据音乐口味生成",
                                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                                    coverUrl = recommendSongs.first().picUrl,
                                    onClick = onOpenDailyRecommend,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (toplist.isNotEmpty()) {
                                ShortcutCard(
                                    title = "排行榜",
                                    subtitle = "云音乐官方榜单",
                                    colors = listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary),
                                    coverUrl = toplist.first().coverUrl,
                                    onClick = { onOpenPlaylist(toplist.first().id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "推荐歌单",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            listItems(playlists, key = { it.id }) { playlist ->
                                PlaylistCard(playlist, onClick = { onOpenPlaylist(playlist.id) })
                            }
                        }
                    }
                }
                // 排行榜：/toplist 返回的每个榜单本身就是一个官方歌单，点击直接复用现有的
                // 歌单详情页（onOpenPlaylist），不用另外做一个「榜单详情」页面
                if (toplist.isNotEmpty()) {
                    item {
                        SectionHeader(title = "排行榜", modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            listItems(toplist.take(10), key = { it.id }) { chart ->
                                ChartCard(chart, onClick = { onOpenPlaylist(chart.id) })
                            }
                        }
                    }
                }
                // 每日推荐歌曲：点一首直接从那首开始播放（整份推荐列表当队列），
                // 跟网易云本身「点歌曲即播放，不用先进详情页」的操作习惯一致
                if (recommendSongs.isNotEmpty()) {
                    item {
                        SectionHeader(title = "每日推荐", modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            listItems(recommendSongs.take(15), key = { it.id }) { song ->
                                RecommendSongCard(
                                    song = song,
                                    onClick = {
                                        val index = recommendSongs.indexOf(song)
                                        player.playQueue(recommendSongs.map { it.toPlayerSong() }, index)
                                    }
                                )
                            }
                        }
                    }
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

/**
 * 首页顶部的搜索入口：整条可点击的胶囊形搜索框（非真正可输入，点击后跳转到搜索页），
 * 放在首页最上方，替代原来单独的底部「搜索」Tab。
 */
@Composable
private fun SearchEntryBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "searchBarScale"
    )
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.height(52.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            "搜索歌曲、歌手、专辑",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/**
 * 统一的板块标题：标题 + 右侧「更多」入口（可选）。之前每个板块各写一遍 Row，
 * 样式细节（间距/图标大小）容易走样，抽出来保证「推荐歌单/排行榜/每日推荐」视觉一致。
 */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier, onMore: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (onMore != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(percent = 50)).clickable(onClick = onMore)
            ) {
                Text(
                    "更多",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Banner 轮播：真正的分页轮播（居中当前张 + 左右各露一点下一/上一张），配圆点指示器 +
 * 自动轮播（4s 一张，手动划走后计时器跟着重新排期，不会跟手势打架）。
 * 之前用 LazyRow 硬摆一排、右侧固定留白 40dp，视觉上是「硬切在屏幕边缘」而不是真正的
 * 分页轮播——网易云/QQ音乐首页 Banner 都是这种居中露边 + 指示器的样式。
 */
@Composable
private fun BannerPager(banners: List<Banner>) {
    val pagerState = rememberPagerState(pageCount = { banners.size })
    // key 里带上 currentPage：每次翻页（不管是手动划还是自动轮播触发的）都重新起一个
    // 4s 倒计时，翻页之间的间隔永远是「稳定在当前页 4s 后才切换」，不会因为自动触发的
    // 那次翻页而让下一次计时错乱。
    LaunchedEffect(pagerState.currentPage, banners.size) {
        if (banners.size > 1) {
            delay(4000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % banners.size)
        }
    }
    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 28.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val banner = banners[page]
            AsyncImage(
                model = banner.picUrl.thumbnail(1200, 600),
                contentDescription = banner.typeTitle,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.1f)
                    .clip(MaterialTheme.shapes.extraLarge),
                contentScale = ContentScale.Crop
            )
        }
        if (banners.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(banners.size) { index ->
                    val selected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 20.dp else 6.dp,
                        // ExpressiveMotion 里的弹簧 token 都是 SpringSpec<Float>（给颜色/透明度/位移用），
                        // 这里动画的是 Dp 类型，类型不匹配没法直接复用，本地起一个参数相同的 Dp 弹簧
                        // （对应 EffectsFast：无过冲，用于这种小尺寸变化的过渡）。
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "bannerDotWidth"
                    )
                    Box(
                        modifier =
                        Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                    )
                }
            }
        }
    }
}

/** 快捷入口卡片：渐变色背景 + 大标题，对应网易云首页最上面那排彩色卡片。 */
@Composable
private fun ShortcutCard(
    title: String,
    subtitle: String,
    colors: List<Color>,
    coverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "shortcutCardScale"
    )
    Box(
        modifier =
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.aspectRatio(1.8f)
            .clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        // 真封面图打底 + 渐变兜底色：封面还没加载出来（或者干脆没有）之前，
        // 先用纯色渐变占位，图片加载完直接叠上去盖住，不会有一下子的跳变。
        Box(modifier = Modifier.matchParentSize().background(Brush.linearGradient(colors)))
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl.thumbnail(400),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
        // 暗角渐变：保证不管封面本身多花哨，底部的白字标题都能看清楚
        Box(
            modifier =
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
        Box(
            modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 榜单卡片：横向滑动里的一张，没有专门配图就退回渐变底色，跟快捷入口视觉呼应。 */
@Composable
private fun ChartCard(chart: Playlist, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "chartCardScale"
    )
    Box(
        modifier =
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.size(width = 140.dp, height = 140.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        if (chart.coverUrl.isNotBlank()) {
            AsyncImage(
                model = chart.coverUrl.thumbnail(280),
                contentDescription = chart.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                    )
            )
        }
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
        )
        Text(
            text = chart.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
        )
    }
}

/** 每日推荐歌曲卡片：小方封面 + 歌名/歌手，点击直接从这首开始播放整份推荐列表。 */
@Composable
private fun RecommendSongCard(song: Song, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "recommendSongCardScale"
    )
    Column(
        modifier =
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.width(112.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = song.picUrl.thumbnail(224),
            contentDescription = song.name,
            modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop
        )
        Text(
            text = song.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = song.artists.joinToString("/"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 推荐歌单卡片：横向滑动流里的一张，固定宽度（140dp）+ 固定文字区高度（两行文案对齐），
 * 不再放进 2 列网格——网格按「这一行最高的卡片」统一对齐行高，一旦某张卡片文案换行更多，
 * 整行看起来就会挤；横向单排没有这个问题，每张卡片的高度只取决于它自己。
 */
@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        // spatial spring：按压回弹有过冲
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "playlistCardScale"
    )
    Column(
        modifier =
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.width(140.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        // 封面 + 播放量角标：网易云「推荐歌单」这块卡片上不单独露标题文字，
        // 靠封面右下角一个小小的播放量标签 + 下面一行运营文案（copywriter）撑内容，
        // 点进去才看到真正的歌单名——跟着这个思路做，而不是「大标题+播放量」那种通用卡片。
        Box {
            AsyncImage(
                model = playlist.coverUrl.thumbnail(400),
                contentDescription = playlist.name,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop
            )
            if (playlist.playCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color.White
                    )
                    Text(
                        text = formatCount(playlist.playCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
        Text(
            text = playlist.copywriter.ifBlank { playlist.name },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // 固定两行的高度（跟 bodySmall 的 lineHeight 对齐），不管文案实际是一行还是两行，
            // 卡片总高度都一致——横向滑动的一排卡片底部才能对得整齐。
            modifier = Modifier.padding(top = 8.dp).height(32.dp)
        )
    }
}

private fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> "$count"
}
