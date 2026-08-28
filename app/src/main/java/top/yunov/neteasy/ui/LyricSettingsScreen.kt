package top.yunov.neteasy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import top.yunov.neteasy.data.LyricSpringPreset

/**
 * 歌词渲染细节设置页：从「设置」进入的独立页面。
 * 这里调的每一项都是 [top.met6.amll.AppleMusicLyricPlayerStyle] 本来就支持、
 * 但之前在 NowPlayingScreen 里写死的参数，改完立即生效（PlayerOverlay onResume 重读）。
 *
 * 分四块：
 * - 显示内容：翻译 / 逐行音译 / 逐词音译 三个开关（数据本身已经带这些字段，这里只是显隐）
 * - 布局：当前行垂直锚点、逐字渐变宽度、非当前行透明度
 * - 效果：模糊、缩放呼吸
 * - 滚动手感：弹簧预设（默认按行间隔自适应 / 柔和 / 跟手 / 弹性 / 沉稳 / 无回弹）
 */
@Composable
fun LyricSettingsScreen(
    showTranslation: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    showLineRomanization: Boolean,
    onShowLineRomanizationChange: (Boolean) -> Unit,
    showWordRomanization: Boolean,
    onShowWordRomanizationChange: (Boolean) -> Unit,
    alignPosition: Float,
    onAlignPositionChange: (Float) -> Unit,
    wordFadeWidth: Float,
    onWordFadeWidthChange: (Float) -> Unit,
    inactiveAlpha: Float,
    onInactiveAlphaChange: (Float) -> Unit,
    enableBlur: Boolean,
    onEnableBlurChange: (Boolean) -> Unit,
    enableScale: Boolean,
    onEnableScaleChange: (Boolean) -> Unit,
    springPreset: LyricSpringPreset,
    onSpringPresetChange: (LyricSpringPreset) -> Unit,
    onBack: () -> Unit
) {
    var presetDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("歌词", style = MaterialTheme.typography.headlineLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))

            LyricSettingsCard(title = "显示内容") {
                LyricSwitchRow(title = "翻译", subtitle = "显示歌词的中文翻译行", checked = showTranslation, onCheckedChange = onShowTranslationChange)
                LyricSwitchRow(
                    title = "逐行音译",
                    subtitle = "如日语歌词的整行罗马音",
                    checked = showLineRomanization,
                    onCheckedChange = onShowLineRomanizationChange
                )
                LyricSwitchRow(
                    title = "逐词音译",
                    subtitle = "TTML 逐词歌词库里带的单字注音",
                    checked = showWordRomanization,
                    onCheckedChange = onShowWordRomanizationChange,
                    showDivider = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            LyricSettingsCard(title = "布局") {
                LyricSliderRow(
                    title = "当前行位置",
                    subtitle = "在歌词区域里的垂直位置，越大越靠下",
                    value = alignPosition,
                    valueRange = 0.05f..0.9f,
                    onValueChange = onAlignPositionChange
                )
                LyricSliderRow(
                    title = "逐字渐变宽度",
                    subtitle = "高亮跟着字走时的过渡柔和度",
                    value = wordFadeWidth,
                    valueRange = 0.05f..1f,
                    onValueChange = onWordFadeWidthChange
                )
                LyricSliderRow(
                    title = "非当前行透明度",
                    subtitle = "越小越暗，越能凸显当前播放行",
                    value = inactiveAlpha,
                    valueRange = 0f..1f,
                    onValueChange = onInactiveAlphaChange,
                    showDivider = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            LyricSettingsCard(title = "效果") {
                LyricSwitchRow(title = "模糊", subtitle = "非当前行做轻微模糊", checked = enableBlur, onCheckedChange = onEnableBlurChange)
                LyricSwitchRow(
                    title = "缩放呼吸",
                    subtitle = "播放时非当前行轻微缩小",
                    checked = enableScale,
                    onCheckedChange = onEnableScaleChange,
                    showDivider = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { presetDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("滚动手感", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "当前：${springPreset.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "默认（自适应）沿用 AMLL 原版按歌词密度动态计算的弹簧效果；\n其余几档是固定手感，供直接挑一个喜欢的用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (presetDialog) {
        AlertDialog(
            onDismissRequest = { presetDialog = false },
            title = { Text("滚动手感") },
            text = {
                Column {
                    LyricSpringPreset.entries.forEach { preset ->
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onSpringPresetChange(preset)
                                    presetDialog = false
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                preset.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                if (preset == springPreset) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (preset == springPreset) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { presetDialog = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun LyricSettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun LyricSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    if (showDivider) Spacer(modifier = Modifier.height(4.dp)) else Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun LyricSliderRow(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
    if (!showDivider) Spacer(modifier = Modifier.height(8.dp))
}
