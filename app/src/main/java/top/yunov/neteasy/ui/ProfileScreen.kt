package top.yunov.neteasy.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.Playlist
import top.yunov.neteasy.data.thumbnail
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
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                Text("我的", style = MaterialTheme.typography.headlineLarge)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }

            // 用户卡片：登录后可点击进入个人信息详情页
            item {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .then(if (nickname != null) Modifier.clickable { onOpenUserDetail(uid) } else Modifier)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    checking -> LoadingIndicator()
                    nickname != null -> {
                        AsyncImage(
                            model = avatarUrl?.thumbnail(200),
                            contentDescription = "头像",
                            modifier =
                            Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(nickname!!, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (vipType > 0) {
                            // 顶部 VIP 徽标（点进详情页可见完整等级）
                            Text(
                                text = if (vipType == 11) "黑胶VIP" else "VIP",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(
                            "已登录 · 点击查看个人信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Box(
                            modifier =
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("未登录", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "登录后可播放完整歌曲（VIP 歌曲需会员）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (nickname == null && !checking) {
                    Button(
                        onClick = onLoginClick,
                        shape = ButtonShape,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("扫码登录", style = MaterialTheme.typography.titleSmall)
                    }
                } else if (nickname != null) {
                    OutlinedButton(
                        onClick = {
                            cookieStore.clear()
                            nickname = null
                            avatarUrl = null
                            logoutRefreshKey++
                        },
                        shape = ButtonShape,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("退出登录", style = MaterialTheme.typography.titleSmall)
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
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 菜单行：圆形图标容器 + 标题 + 箭头。onClick 为 null 时不响应点击 */
@Composable
private fun MenuRow(icon: ImageVector, title: String, onClick: (() -> Unit)? = null) {
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
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
