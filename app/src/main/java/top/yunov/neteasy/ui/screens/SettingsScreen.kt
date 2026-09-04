package top.yunov.neteasy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yunov.neteasy.data.AudioQuality
import top.yunov.neteasy.data.CacheExpiryDays
import top.yunov.neteasy.data.CacheSizeLimit
import top.yunov.neteasy.data.ThemeMode
import top.yunov.neteasy.data.UpdateUiState
import top.yunov.neteasy.ui.components.DarkMode
import top.yunov.neteasy.ui.components.Palette
import top.yunov.neteasy.ui.components.Storage

/**
 * 设置页（MD3 Expressive）：按功能分了三类，不再是七八张卡片平铺一遍：
 * - 外观：深色模式（跟随系统/浅色/深色）、动态取色（Material You 壁纸色，仅
 *   Android 12+ 显示）、封面取色（跟随当前播放歌曲封面，优先级高于动态取色）
 * - 播放：默认播放音质、歌词渲染细节（跳转独立页面）
 * - 存储与更新：存储空间管理（跳转独立页面，参考网易云官方样式）、检查更新
 *   （对比 GitHub Releases，有更新弹窗展示更新内容 + 下载安装）
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
    followCoverColor: Boolean,
    onFollowCoverColorChange: (Boolean) -> Unit,
    onOpenStorage: () -> Unit,
    onOpenLyricSettings: () -> Unit,
    currentVersion: String,
    defaultQuality: AudioQuality,
    onDefaultQualityChange: (AudioQuality) -> Unit,
    autoCacheSongs: Boolean,
    onAutoCacheSongsChange: (Boolean) -> Unit,
    cacheSizeLimit: CacheSizeLimit,
    onCacheSizeLimitChange: (CacheSizeLimit) -> Unit,
    cacheExpiryDays: CacheExpiryDays,
    onCacheExpiryDaysChange: (CacheExpiryDays) -> Unit,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onStartDownload: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 默认播放音质选择弹窗开关
    var qualityDialog by remember { mutableStateOf(false) }
    var cacheSizeDialog by remember { mutableStateOf(false) }
    var cacheExpiryDialog by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                // insets 挪到 verticalScroll 后面：放前面会把系统栏高度从可滚动视口本身抠掉，
                // 页面内容永远滚不到状态栏/手势导航栏底下，整页看着像上下各被裁掉一条边
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
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

            // ---- 外观：深色模式 / 动态取色 / 封面取色 ----
            SettingsCategory(title = "外观") {
                Column {
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
                // 动态取色：仅 Android 12+ 支持，低版本隐藏该设置
                if (dynamicColorSupported) {
                    SettingsRowDivider()
                    SettingRow(
                        icon = Icons.Filled.Palette,
                        title = "动态取色",
                        subtitle = "跟随系统壁纸主题色"
                    ) {
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                    }
                }
                // 封面取色：跟着当前播放歌曲的封面变，优先级比动态取色高（开启后忽略动态取色）
                SettingsRowDivider()
                SettingRow(
                    icon = Icons.Filled.Palette,
                    title = "封面取色",
                    subtitle = "跟随当前播放歌曲的封面变化"
                ) {
                    Switch(checked = followCoverColor, onCheckedChange = onFollowCoverColorChange)
                }
            }
            Text(
                "封面取色开启后会覆盖动态取色\n关闭动态取色后使用网易云品牌红配色\n动态取色需 Android 12 及以上",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ---- 播放：默认音质 / 歌词渲染细节 ----
            SettingsCategory(title = "播放") {
                SettingRow(
                    icon = Icons.Filled.GraphicEq,
                    title = "默认播放音质",
                    subtitle = "当前：${defaultQuality.label}",
                    modifier = Modifier.clickable { qualityDialog = true }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SettingsRowDivider()
                SettingRow(
                    icon = Icons.Filled.Download,
                    title = "自动缓存歌曲",
                    subtitle = "播放时保存到本地，下次播放同一首不用等联网"
                ) {
                    Switch(checked = autoCacheSongs, onCheckedChange = onAutoCacheSongsChange)
                }
                // 缓存上限/有效期在自动缓存关闭时也保留可调（用户可能只是想临时关掉，
                // 之前存的缓存该怎么淘汰还是怎么淘汰，不因为开关状态隐藏这两项）
                SettingsRowDivider()
                SettingRow(
                    icon = Icons.Filled.Storage,
                    title = "歌曲缓存上限",
                    subtitle = cacheSizeLimit.label,
                    modifier = Modifier.clickable { cacheSizeDialog = true }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SettingsRowDivider()
                SettingRow(
                    icon = Icons.Filled.Refresh,
                    title = "歌曲缓存有效期",
                    subtitle = cacheExpiryDays.label,
                    modifier = Modifier.clickable { cacheExpiryDialog = true }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SettingsRowDivider()
                SettingRow(
                    icon = Icons.Filled.Lyrics,
                    title = "歌词",
                    subtitle = "对齐位置、模糊、翻译音译显隐、滚动手感",
                    modifier = Modifier.clickable(onClick = onOpenLyricSettings)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ---- 存储与更新 ----
            SettingsCategory(title = "存储与更新") {
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
                SettingsRowDivider()
                val checking = updateState == UpdateUiState.Checking
                SettingRow(
                    icon = Icons.Filled.Refresh,
                    title = "检查更新",
                    subtitle = "当前版本 $currentVersion",
                    modifier = Modifier.clickable(enabled = !checking, onClick = onCheckUpdate)
                ) {
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
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

    UpdateDialog(
        state = updateState,
        onStartDownload = onStartDownload,
        onInstall = onInstallUpdate,
        onDismiss = onDismissUpdateDialog
    )

    // 默认播放音质选择弹窗：列出全部 4 档
    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text("默认播放音质") },
            text = {
                Column {
                    AudioQuality.entries.forEach { quality ->
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onDefaultQualityChange(quality)
                                    qualityDialog = false
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                quality.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                if (quality == defaultQuality) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (quality == defaultQuality) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "某首歌没有该音质时，会自动降级为邻近的可播放音质。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qualityDialog = false }) { Text("关闭") } }
        )
    }

    // 歌曲缓存上限选择弹窗
    if (cacheSizeDialog) {
        AlertDialog(
            onDismissRequest = { cacheSizeDialog = false },
            title = { Text("歌曲缓存上限") },
            text = {
                Column {
                    CacheSizeLimit.entries.forEach { option ->
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onCacheSizeLimitChange(option)
                                    cacheSizeDialog = false
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                if (option == cacheSizeLimit) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (option == cacheSizeLimit) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "达到容量上限后会清理最早没再用过的歌曲缓存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { cacheSizeDialog = false }) { Text("关闭") } }
        )
    }

    // 歌曲缓存有效期选择弹窗
    if (cacheExpiryDialog) {
        AlertDialog(
            onDismissRequest = { cacheExpiryDialog = false },
            title = { Text("歌曲缓存有效期") },
            text = {
                Column {
                    CacheExpiryDays.entries.forEach { option ->
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onCacheExpiryDaysChange(option)
                                    cacheExpiryDialog = false
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                if (option == cacheExpiryDays) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (option == cacheExpiryDays) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "从缓存生成时间开始计算，过期后自动清理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { cacheExpiryDialog = false }) { Text("关闭") } }
        )
    }
}

/** 检查更新弹窗：按当前状态展示「有更新详情」「测速中」「下载进度」「下载完成待安装」「出错」 */
@Composable
private fun UpdateDialog(
    state: UpdateUiState,
    onStartDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateUiState.Available ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("发现新版本 ${state.release.versionName}") },
                text = {
                    Text(
                        state.release.releaseNotes.ifBlank { "（这次更新没有附带说明）" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                        Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = { Button(onClick = onStartDownload) { Text("下载更新") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
            )
        UpdateUiState.PickingMirror ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("准备下载") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在测速，自动选择最快的下载节点…")
                    }
                },
                confirmButton = {}
            )
        is UpdateUiState.Downloading ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("正在下载（${state.mirrorLabel}）") },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${(state.progress * 100).toInt()}%")
                    }
                },
                confirmButton = {}
            )
        UpdateUiState.ReadyToInstall ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("下载完成") },
                text = { Text("安装包已保存到「下载」目录，点击安装继续更新，安装完成后会自动清理安装包。") },
                confirmButton = { Button(onClick = onInstall) { Text("安装") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
            )
        is UpdateUiState.Error ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("更新失败") },
                text = { Text(state.message) },
                confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
            )
        UpdateUiState.Idle, UpdateUiState.Checking -> Unit
        // 没新版本也要让用户知道“我查过了，是最新的”，否则点完按钮什么都没发生，像卡死
        UpdateUiState.UpToDate ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("检查更新") },
                text = { Text("当前已是最新版本。") },
                confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
            )
    }
}

/** 设置分类：分类名（强调色小标题）+ 一张卡片装下这一类里的所有设置项。 */
@Composable
private fun SettingsCategory(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = content
    )
}

/** 同一张卡片里相邻设置项之间的分隔线，跟图标对齐（左边空出图标+间距的宽度）。 */
@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
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
