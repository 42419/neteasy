package top.yunov.neteasy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.CookieStore
import top.yunov.neteasy.data.LikeRepository
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.model.Playlist
import top.yunov.neteasy.data.model.thumbnail
import top.yunov.neteasy.ui.components.MainNavigationBarHeight
import top.yunov.neteasy.ui.components.MinibarClearance
import top.yunov.neteasy.ui.theme.ButtonShape

/**
 * 我的页（MD3 Expressive）：登录态卡片 + 功能菜单。
 * 登录态判定：CookieStore 里有 MUSIC_U；用户信息来自 /login/status。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    repository: NcmRepository,
    cookieStore: CookieStore,
    refreshKey: Int = 0,
    likeRepository: LikeRepository,
    onLoginClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenUserDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var nickname by remember { mutableStateOf<String?>(null) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var logoutRefreshKey by remember { mutableIntStateOf(0) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    // 登录态 profile 里的用户 id 与 VIP 类型（详情页跳转与卡片徽标用）
    var uid by remember { mutableStateOf(0L) }
    var vipType by remember { mutableStateOf(0) }

    // 外部登录成功 refreshKey 变化 或 本页登出 logoutRefreshKey 变化 都重新加载
    LaunchedEffect(refreshKey, logoutRefreshKey) {
        checking = true
        // 登录态变化时红心列表也要跟着重新拉（登出要清空，登录要拉自己的），
        // 独立触发不阻塞下面这堆 profile 信息的加载
        likeRepository.refresh()
        val info =
            withContext(Dispatchers.IO) {
                if (cookieStore.hasLogin()) repository.loginStatus() else null
            }
        val profile = info?.optJSONObject("profile")
        nickname = profile?.optString("nickname")?.takeIf { it.isNotBlank() }
        avatarUrl = profile?.optString("avatarUrl")?.takeIf { it.isNotBlank() }
        uid = profile?.optLong("userId") ?: 0L
        vipType = profile?.optInt("vipType") ?: 0
        val currentUid = uid
        playlists =
            if (currentUid != 0L) {
                try {
                    withContext(Dispatchers.IO) { repository.userPlaylists(currentUid) }
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        checking = false
    }

    // 系统「喜欢的音乐」歌单单独摘出来给上面的“喜欢”菜单行用；其余是用户自建/收藏的普通歌单
    val likedPlaylist = playlists.firstOrNull { it.isLikedSongs }
    val myPlaylists = playlists.filterNot { it.isLikedSongs }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // bottom 单独放大：跟 HomeScreen 一样得给悬浮在上面的 Minibar 留够净空，
            // 不然「我的」页面滚到底最后一块内容会被挡住
            contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = MainNavigationBarHeight + MinibarClearance
            )
        ) {
            item {
                Text("我的", style = MaterialTheme.typography.headlineLarge)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }

            // 用户卡片：横向布局（头像 + 昵称/VIP/状态），登录后整卡可点进个人信息详情页。
            // 之前是纵向居中堆叠（大头像/昵称/VIP/提示语/全宽按钮全挤一张卡），一张卡占了
            // 半个屏幕；横向卡片只占一条，「退出登录」挪到下面的功能菜单组里（跟官方 App
            // 把账号操作收进菜单一行的习惯一致），卡片本身回归纯信息展示
            item {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .then(if (nickname != null) Modifier.clickable { onOpenUserDetail(uid) } else Modifier)
                    .padding(20.dp)
            ) {
                when {
                    checking ->
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    nickname != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = avatarUrl?.thumbnail(200),
                                contentDescription = "头像",
                                modifier =
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(nickname!!, style = MaterialTheme.typography.titleLarge)
                                    if (vipType > 0) {
                                        // VIP 徽标跟在昵称后面，不再单独占一行
                                        Text(
                                            text = if (vipType == 11) "黑胶VIP" else "VIP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier =
                                            Modifier
                                                .padding(start = 8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    "已登录 · 点击查看个人信息",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                                Text("未登录", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "登录后可播放完整歌曲（VIP 歌曲需会员）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onLoginClick,
                            shape = ButtonShape,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("扫码登录", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // 功能菜单
            item {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    MenuRow(
                        icon = Icons.Filled.Favorite,
                        title = "喜欢",
                        onClick = likedPlaylist?.let { pl -> { onOpenPlaylist(pl.id) } }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    MenuRow(icon = Icons.Filled.Settings, title = "设置", onClick = onOpenSettings)
                    // 退出登录：从用户卡片里挪出来，作为菜单组最后一行（仅登录时显示）
                    if (nickname != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        MenuRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            title = "退出登录",
                            showChevron = false,
                            onClick = {
                                cookieStore.clear()
                                nickname = null
                                avatarUrl = null
                                logoutRefreshKey++
                            }
                        )
                    }
                }
            }

            // 我创建/收藏的歌单：登录后才有，系统「喜欢的音乐」歌单已经摘到上面的菜单行了。
            // 歌单数量通常不多（几十以内），包一层圆角容器整块渲染，不单独懒加载每一行
            if (myPlaylists.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(20.dp)) }
                item {
                    Text(
                        "我的歌单",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                    )
                }
                item {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        myPlaylists.forEachIndexed { index, playlist ->
                            PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
                            if (index != myPlaylists.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            item {
                Text(
                    "数据来自本地 Node 服务（127.0.0.1:19800）\n登录状态保存在本机，不会上传",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 歌单行（我的歌单列表用）：方形封面 + 名称 + 歌曲数 */
@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = playlist.coverUrl.thumbnail(150).ifEmpty { null },
            contentDescription = null,
            modifier =
            Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier =
            Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${playlist.trackCount} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 菜单行：圆形图标容器 + 标题 + chevron（[showChevron] 为 false 时不显示，用于「退出登录」这类操作行） */
@Composable
private fun MenuRow(icon: ImageVector, title: String, showChevron: Boolean = true, onClick: (() -> Unit)? = null) {
    // clickable 要放在 padding 之前，点击高亮/涟漪范围才是整行，而不是缩进后剩下的一小块
    val base = Modifier.fillMaxWidth()
    Row(
        modifier =
        (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier =
            Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
        if (showChevron) {
            // M3 惯例：进入子页面的行尾用 > 形 chevron；ArrowForward(→) 是 iOS/外部跳转语义，换掉
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
