package top.yunov.neteasy.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.CookieStore
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.model.Comment
import top.yunov.neteasy.data.model.thumbnail
import top.yunov.neteasy.ui.components.GlassTopBackBar
import top.yunov.neteasy.ui.components.isScrolledPast
import top.yunov.neteasy.ui.components.rememberPageBackdrop

/**
 * 评论区页面（歌曲 / 歌单 / 专辑共用，靠 resourceType 区分）。
 * - 列表：/comment/new 分页加载（推荐排序按 pageNo 偏移，最新排序用 cursor 游标），
 *   滚动到底自动加载下一页
 * - 点赞：/comment/like，本地乐观更新（先变色计数，失败回滚）
 * - 发评论：底部输入栏，/comment/add（需登录）；成功就地把新评论插到列表头，
 *   服务端没返回新评论对象时刷新第一页兜底
 * 楼中楼回复（/comment/floor）暂不接——回复数只做展示，点不开二级页，
 * 后续要做的话从这个页面加「N 条回复」的跳转即可。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentsScreen(
    resourceId: Long,
    resourceType: Int,
    title: String,
    coverUrl: String,
    repository: NcmRepository,
    cookieStore: CookieStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    // 排序：99=推荐（后端按 pageNo 算偏移），3=最新（用上一页返回的 cursor 翻页）
    var sortType by remember { mutableIntStateOf(99) }
    var pageNo by remember { mutableIntStateOf(1) }
    var cursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val loggedIn = remember { cookieStore.hasLogin() }

    fun load(firstPage: Boolean) {
        if (firstPage) {
            loading = true
            error = null
        } else {
            loadingMore = true
        }
        scope.launch {
            try {
                val page =
                    withContext(Dispatchers.IO) {
                        repository.comments(
                            id = resourceId,
                            type = resourceType,
                            pageNo = if (firstPage) 1 else pageNo + 1,
                            sortType = sortType,
                            cursor = if (firstPage) "" else cursor
                        )
                    }
                comments = if (firstPage) page.comments else comments + page.comments
                totalCount = page.totalCount
                hasMore = page.hasMore
                cursor = page.cursor
                pageNo = if (firstPage) 1 else pageNo + 1
                error = null
            } catch (e: Exception) {
                if (firstPage) error = e.message ?: "加载失败"
                // 翻页失败不动已有列表，下次滚到底还会再试
            }
            loading = false
            loadingMore = false
        }
    }

    // 切排序 = 从头重拉
    LaunchedEffect(sortType) { load(firstPage = true) }

    // 滚到底（剩 3 条以内）自动加载下一页
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !loading && !loadingMore) load(firstPage = false)
    }

    val backdrop = rememberPageBackdrop()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scrolledPast by listState.isScrolledPast(statusBarHeight + 24.dp)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading ->
                        Box(
                            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
                            contentAlignment = Alignment.Center
                        ) {
                            ContainedLoadingIndicator()
                        }
                    error != null ->
                        Box(
                            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("加载失败：$error")
                                Text(
                                    "重试",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp).clickable { load(firstPage = true) }
                                )
                            }
                        }
                    else ->
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
                            contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp
                            ),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                Column {
                                    // 顶部给悬浮返回按钮留净空（同其他二级页）
                                    Spacer(modifier = Modifier.height(80.dp))
                                    CommentSubjectRow(title = title, coverUrl = coverUrl, totalCount = totalCount)
                                    SingleChoiceSegmentedButtonRow(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                    ) {
                                        listOf(99 to "推荐", 3 to "最新").forEachIndexed { index, (type, label) ->
                                            SegmentedButton(
                                                selected = sortType == type,
                                                onClick = { sortType = type },
                                                shape =
                                                SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                            }
                            items(comments, key = { it.id }) { comment ->
                                CommentRow(
                                    comment = comment,
                                    onToggleLike = {
                                        // 未登录点赞必被服务端拒绝，乐观更新后立刻回滚会显得「点了闪一下
                                        // 没反应」——直接提示，不发请求也不变色
                                        if (!loggedIn) {
                                            Toast.makeText(context, "登录后才能点赞", Toast.LENGTH_SHORT).show()
                                            return@CommentRow
                                        }
                                        // 乐观更新：先本地变色计数，失败再回滚
                                        val target = !comment.liked
                                        comments =
                                            comments.map {
                                                if (it.id == comment.id) {
                                                    it.copy(
                                                        liked = target,
                                                        likedCount = it.likedCount + if (target) 1 else -1
                                                    )
                                                } else {
                                                    it
                                                }
                                            }
                                        scope.launch {
                                            val ok =
                                                withContext(Dispatchers.IO) {
                                                    runCatching {
                                                        repository.likeComment(resourceId, comment.id, target, resourceType)
                                                    }.getOrDefault(false)
                                                }
                                            if (!ok) {
                                                comments =
                                                    comments.map {
                                                        if (it.id == comment.id) {
                                                            it.copy(liked = comment.liked, likedCount = comment.likedCount)
                                                        } else {
                                                            it
                                                        }
                                                    }
                                            }
                                        }
                                    }
                                )
                            }
                            if (loadingMore) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        LoadingIndicator(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                            if (!hasMore && comments.isNotEmpty()) {
                                item {
                                    Text(
                                        "没有更多评论了",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                }
                GlassTopBackBar(backdrop = backdrop, scrolledPast = scrolledPast, onBack = onBack)
            }

            // 底部发评栏：吃手势导航栏 + 键盘 inset；未登录禁用并提示
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        enabled = loggedIn && !sending,
                        placeholder = {
                            Text(if (loggedIn) "说点什么…" else "登录后发表评论")
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent
                        )
                    )
                    IconButton(
                        onClick = {
                            val content = input.trim()
                            if (content.isEmpty() || sending) return@IconButton
                            sending = true
                            scope.launch {
                                try {
                                    val added =
                                        withContext(Dispatchers.IO) {
                                            repository.addComment(resourceId, resourceType, content)
                                        }
                                    input = ""
                                    if (added != null) {
                                        comments = listOf(added) + comments
                                        totalCount++
                                    } else {
                                        // 服务端没回新评论对象，刷新第一页保证能看到自己的评论
                                        load(firstPage = true)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "评论失败", Toast.LENGTH_SHORT).show()
                                }
                                sending = false
                            }
                        },
                        enabled = loggedIn && input.isNotBlank() && !sending
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint =
                            if (loggedIn && input.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 评论区头部：被评论对象（封面 + 标题 + 评论总数） */
@Composable
private fun CommentSubjectRow(title: String, coverUrl: String, totalCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = coverUrl.thumbnail(120).ifEmpty { null },
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (totalCount > 0) "共 $totalCount 条评论" else "评论",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单条评论：头像 + 昵称（VIP 徽标）+ 时间 + 内容 + 引用回复 + 点赞 */
@Composable
private fun CommentRow(comment: Comment, onToggleLike: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        AsyncImage(
            model = comment.userAvatarUrl.thumbnail(96).ifEmpty { null },
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.userNickname,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (comment.vipType > 0) {
                    Text(
                        text = if (comment.vipType == 11) "黑胶VIP" else "VIP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            if (comment.timeStr.isNotBlank()) {
                Text(
                    comment.timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                comment.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 6.dp)
            )
            // 引用回复：这条评论回复了别人，带上原评论摘要
            if (comment.beRepliedContent != null) {
                Text(
                    text = "回复 @${comment.beRepliedNickname ?: "用户"}：${comment.beRepliedContent}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Icon(
                    imageVector = if (comment.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = if (comment.liked) "取消点赞" else "点赞",
                    tint = if (comment.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).clickable(onClick = onToggleLike)
                )
                if (comment.likedCount > 0) {
                    Text(
                        "${comment.likedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                // 楼中楼回复暂未接二级页，只做数量展示
                if (comment.replyCount > 0) {
                    Text(
                        "${comment.replyCount} 条回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }
}
