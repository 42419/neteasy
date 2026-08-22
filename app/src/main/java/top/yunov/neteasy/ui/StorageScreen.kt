package top.yunov.neteasy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 存储空间页：参考网易云音乐官方「存储空间」页的版式（大数字 + 占用条 + 分项卡片），
 * 按本 App 实际的存储构成重新设计分类：
 * - 数据缓存：图片/歌单数据/临时文件等所有可安全清除、清了不影响使用的缓存合并展示
 * - 音乐缓存：本 App 播放是实时在线流式播放，不落盘缓存歌曲文件，恒为 0，纯说明用途
 * - 必要文件：内嵌后端程序文件 + 安装包本身，App 运行必需，不提供清除
 */
@Composable
fun StorageScreen(
    appTotalBytes: Long,
    deviceTotalBytes: Long,
    deviceUsedByOthersBytes: Long,
    deviceFreeBytes: Long,
    dataCacheBytes: Long,
    musicCacheBytes: Long,
    essentialFilesBytes: Long,
    onClearDataCache: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("存储空间", style = MaterialTheme.typography.headlineLarge)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                "Neteasy 占用",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatBytes(appTotalBytes),
                style = MaterialTheme.typography.displaySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 占用条：本 App / 手机已用（其他 App+系统） / 手机可用，三段拼在一起
            val total = deviceTotalBytes.coerceAtLeast(1L)
            val appFraction = (appTotalBytes.toFloat() / total).coerceIn(0f, 1f)
            val othersFraction = (deviceUsedByOthersBytes.toFloat() / total).coerceIn(0f, 1f - appFraction)
            val freeFraction = (1f - appFraction - othersFraction).coerceAtLeast(0f)
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
            ) {
                // 本 App 占比通常极小（<1%），给个可视下限，不然完全看不见这一段
                Box(
                    modifier =
                    Modifier
                        .weight(appFraction.coerceAtLeast(0.012f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
                Box(
                    modifier =
                    Modifier
                        .weight(othersFraction.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Box(
                    modifier =
                    Modifier
                        .weight(freeFraction.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(color = MaterialTheme.colorScheme.primary)
                Text(
                    "Neteasy 占用 ${formatPercent(appFraction)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                )
                LegendDot(color = MaterialTheme.colorScheme.surfaceVariant)
                Text(
                    "手机已用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                )
                LegendDot(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                Text(
                    "手机可用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            StorageCard(
                title = "数据缓存",
                sizeText = formatBytes(dataCacheBytes),
                description = "使用过程中产生的图片、歌单信息等临时数据，可提高 App 使用的流畅性，清理后不会影响正常使用。",
                buttonText = "清理",
                buttonEnabled = dataCacheBytes > 0,
                onClick = onClearDataCache
            )

            Spacer(modifier = Modifier.height(16.dp))

            StorageCard(
                title = "音乐缓存",
                sizeText = formatBytes(musicCacheBytes),
                description = "播放采用实时在线流式播放，不会把歌曲文件缓存到本地，因此这里恒为 0，也没有可清理的内容。",
                buttonText = "清理",
                buttonEnabled = false,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(16.dp))

            StorageCard(
                title = "必要文件",
                sizeText = formatBytes(essentialFilesBytes),
                description = "App 运行所需的必要文件，包含内嵌服务程序和安装包本身，无法清理。",
                buttonText = null,
                buttonEnabled = false,
                onClick = {}
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier =
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun StorageCard(
    title: String,
    sizeText: String,
    description: String,
    buttonText: String?,
    buttonEnabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (buttonText != null) {
                Button(
                    onClick = onClick,
                    enabled = buttonEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(buttonText)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(sizeText, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 字节数转人类可读的大小文本，如 "12.4 MB" */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "${value.toInt()} ${units[unitIndex]}" else "%.1f %s".format(value, units[unitIndex])
}

private fun formatPercent(fraction: Float): String {
    val percent = fraction * 100
    return if (percent < 1f) "不足 1%" else "${percent.toInt()}%"
}
