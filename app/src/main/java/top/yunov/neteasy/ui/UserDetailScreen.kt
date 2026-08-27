package top.yunov.neteasy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.UserDetail
import top.yunov.neteasy.data.thumbnail

/**
 * 用户详情页（从「我的」页点头像/个人信息进入）：
 * 顶部大头像 + 昵称 + VIP / 等级徽标，下面是资料行（听歌数、性别、加入时间、签名）。
 * 数据来自 /user/detail（含账户等级 LV 与听歌数，登录态接口不返回这些）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserDetailScreen(repository: NcmRepository, uid: Long, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<UserDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        loading = true
        try {
            detail = withContext(Dispatchers.IO) { repository.userDetail(uid) }
            error = if (detail == null) "获取用户信息失败" else null
        } catch (e: Exception) {
            error = e.message ?: "获取用户信息失败"
        } finally {
            loading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 顶部返回栏（标题与其他二级页面统一：粗体大字）
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("个人信息", style = MaterialTheme.typography.headlineLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))

            when {
                loading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                error != null ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }
                detail != null -> UserDetailContent(detail!!)
            }
        }
    }
}

@Composable
private fun UserDetailContent(detail: UserDetail) {
    // 个人信息卡片：头像 + 昵称 + 徽标
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = detail.avatarUrl.thumbnail(200).ifEmpty { null },
            contentDescription = "头像",
            modifier =
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(detail.nickname, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

        if (detail.vipLabel.isNotBlank() || detail.levelLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.vipLabel.takeIf { it.isNotBlank() }?.let {
                    Badge(text = it, highlighted = true)
                }
                detail.levelLabel.takeIf { it.isNotBlank() }?.let {
                    Badge(text = it, highlighted = false)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 资料详情行
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        InfoRow(
            icon = Icons.Filled.Stars,
            label = "账户等级",
            value = detail.levelLabel.ifBlank { "—" },
            isFirst = true
        )
        InfoRow(icon = Icons.Filled.EmojiEvents, label = "听歌数", value = "${detail.listenSongs} 首")
        InfoRow(icon = Icons.Filled.Person, label = "性别", value = detail.genderLabel)
        InfoRow(
            icon = Icons.Filled.CalendarMonth,
            label = "加入时间",
            value = formatDate(detail.createTime)
        )
        InfoRow(
            icon = Icons.Filled.GraphicEq,
            label = "个性签名",
            value = detail.signature.ifBlank { "这个人很懒，什么都没留下" },
            isLast = true,
            alignTop = true
        )
    }
}

/** 圆形徽标（VIP / LV） */
@Composable
private fun Badge(text: String, highlighted: Boolean) {
    Box(
        modifier =
        Modifier
            .clip(CircleShape)
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color =
            if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 资料行：小图标 + 标签 + 值 */
@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    alignTop: Boolean = false
) {
    Column {
        if (!isFirst) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp).weight(1f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp).align(Alignment.CenterVertically)
            )
        }
    }
}

private fun formatDate(epochMs: Long): String {
    if (epochMs <= 0) return "—"
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
    } catch (e: Exception) {
        "—"
    }
}
