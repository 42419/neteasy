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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.SearchHistoryStore
import top.yunov.neteasy.data.model.Song
import top.yunov.neteasy.data.model.thumbnail
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.player.toPlayerSong
import top.yunov.neteasy.ui.theme.ButtonShape
import top.yunov.neteasy.ui.theme.ExpressiveMotion

/**
 * 搜索页：改为「引导式」搜索，而不是一输入就直接出结果——
 * - 输入时：弹出【搜索建议】（/search/suggest），引导用户选词；
 * - 未输入时：展示【热搜】【搜索历史】；
 * - 点选建议/热搜/历史，或按 IME 的“搜索”键，才真正发起搜索。
 * 这样避免了输入一半就拉一屏可能不对的结果，也更贴近网易云官方的搜索交互。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(repository: NcmRepository, player: PlayerController, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val historyStore = remember { SearchHistoryStore(context) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var hotKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var suggestJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }

    // 打开时：自动聚焦 + 加载热搜与历史
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        hotKeywords = withContext(Dispatchers.IO) { repository.searchHot() }
        history = historyStore.load()
    }

    /** 真正发起搜索：设词、记历史、拉结果 */
    fun commitSearch(keyword: String) {
        if (keyword.isBlank()) return
        query = keyword
        suggestJob?.cancel()
        suggestions = emptyList()
        showResults = true
        historyStore.add(keyword)
        history = historyStore.load()
        searching = true
        scope.launch {
            val list =
                try {
                    withContext(Dispatchers.IO) { repository.search(keyword, 30) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            results = list
            searching = false
        }
    }

    fun onQueryChange(newQuery: String) {
        query = newQuery
        showResults = false
        results = emptyList()
        suggestJob?.cancel()
        if (newQuery.isBlank()) {
            suggestions = emptyList()
            return
        }
        // 防抖拉取建议（短等待用 Loading 交给建议列表自身的空态；这里只做建议，不直接搜结果）
        suggestJob =
            scope.launch {
                delay(250)
                suggestions = withContext(Dispatchers.IO) { repository.searchSuggest(newQuery) }
            }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { onQueryChange(it) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("搜索歌曲、歌手") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = ButtonShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { commitSearch(query) }),
                colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (showResults) {
                // 已提交搜索：展示结果
                when {
                    searching ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    results.isEmpty() ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "没有找到相关结果",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    else ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                            // 底部留白让最后几首歌不会被悬浮 Minibar 挡住
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(results, key = { _, song -> song.id }) { index, song ->
                                SongRow(
                                    song = song,
                                    onClick = {
                                        player.playQueue(results.map { it.toPlayerSong() }, index)
                                    }
                                )
                            }
                        }
                }
            } else if (query.isBlank()) {
                // 空态：热搜 + 搜索历史
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    if (hotKeywords.isNotEmpty()) {
                        item {
                            KeywordSection(title = "热门搜索") {
                                KeywordFlow(keywords = hotKeywords, onPick = { commitSearch(it) })
                            }
                        }
                    }
                    if (history.isNotEmpty()) {
                        item {
                            KeywordSection(
                                title = "搜索历史",
                                onClearHistory = {
                                    historyStore.clear()
                                    history = emptyList()
                                }
                            ) {
                                KeywordFlow(keywords = history, onPick = { commitSearch(it) })
                            }
                        }
                    }
                }
            } else {
                // 输入中：展示建议词
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(suggestions, key = { _, s -> s }) { _, keyword ->
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { commitSearch(keyword) }
                                .padding(horizontal = 4.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                keyword,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 14.dp)
                            )
                        }
                    }
                    if (suggestions.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (query.isNotBlank()) "输入更多以获取建议" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 热搜 / 历史区：小标题（带可选的清空按钮）+ 词条 FlowRow */
@Composable
private fun KeywordSection(
    title: String,
    onClearHistory: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (onClearHistory != null) {
                TextButton(onClick = onClearHistory) { Text("清空") }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}

/** 关键词胶囊流式布局 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordFlow(keywords: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keywords.forEach { keyword ->
            AssistChip(onClick = { onPick(keyword) }, label = { Text(keyword) })
        }
    }
}

/**
 * 歌曲列表行（搜索 / 歌单详情共用）：Expressive 圆角容器 + 圆形播放图标。
 * 按下时弹性缩小（spring）。
 */
@Composable
fun SongRow(song: Song, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        // spatial spring：按压回弹有过冲
        animationSpec = ExpressiveMotion.SpatialFast,
        label = "songRowScale"
    )
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.picUrl.thumbnail(160).ifEmpty { null },
            contentDescription = null,
            modifier =
            Modifier
                .size(52.dp)
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
        Box(
            modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
