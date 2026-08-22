package top.yunov.neteasy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yunov.neteasy.data.ThemeMode

/**
 * 设置页（MD3 Expressive）：
 * - 深色模式：跟随系统 / 浅色 / 深色（三选一 SegmentedButton）
 * - 动态取色：Material You 壁纸色开关（仅 Android 12+ 显示）
 * - 存储空间：点进去是独立页面（StorageScreen），参考网易云官方存储空间页样式，
 *   这里只是一个跳转入口
 * 所有选择经 MainActivity 提升到主题层，切换即时生效并持久化。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    dynamicColorSupported: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenStorage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                // 本页作为全屏覆盖层渲染在 Scaffold 之外，需要自己处理状态栏/手势导航栏安全区，
                // 否则标题会被状态栏遮住（见截图）。
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 顶部返回栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 官方 FilledTonalIconButton：自带容器 + Expressive spring 动效
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("设置", style = MaterialTheme.typography.headlineLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 外观卡片：深色模式三选一
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                SettingRow(
                    icon = Icons.Filled.DarkMode,
                    title = "深色模式",
                    subtitle = "当前：${themeMode.label()}"
                )
                SingleChoiceSegmentedButtonRow(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    themeModeOptions.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModeOptions.size),
                            label = { Text(label) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 动态取色：仅 Android 12+ 支持，低版本隐藏该设置
            if (dynamicColorSupported) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    SettingRow(
                        icon = Icons.Filled.Palette,
                        title = "动态取色",
                        subtitle = "跟随系统壁纸主题色"
                    ) {
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Text(
                "关闭动态取色后使用网易云品牌红配色\n动态取色需 Android 12 及以上",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 存储空间：跳转到独立页面，参考网易云官方存储空间页样式
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                SettingRow(
                    icon = Icons.Filled.Storage,
                    title = "存储空间",
                    subtitle = "查看并清理占用的存储空间",
                    modifier = Modifier.clickable(onClick = onOpenStorage)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 深色模式三选项的唯一数据源：段按钮展示 + 副标题“当前：”共用 */
private val themeModeOptions =
    listOf(
        ThemeMode.SYSTEM to "跟随系统",
        ThemeMode.LIGHT to "浅色",
        ThemeMode.DARK to "深色"
    )

private fun ThemeMode.label(): String =
    themeModeOptions.firstOrNull { it.first == this }?.second ?: themeModeOptions.first().second

/** 设置行：圆形图标容器 + 标题/副标题 + 可选尾部组件（Switch 等） */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
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
        Column(
            modifier =
            Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}
