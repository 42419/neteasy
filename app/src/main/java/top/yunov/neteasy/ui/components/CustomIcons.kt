package top.yunov.neteasy.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

// material-icons-core 未收录 Pause 图标，这里使用 Material 官方 pause 路径自定义（形状与官方完全一致）
private var pauseIcon: ImageVector? = null

val Icons.Filled.Pause: ImageVector
    get() {
        if (pauseIcon != null) return pauseIcon!!
        pauseIcon =
            materialIcon(name = "Filled.Pause") {
                materialPath {
                    moveTo(6f, 19f)
                    horizontalLineToRelative(4f)
                    verticalLineTo(5f)
                    horizontalLineTo(6f)
                    close()
                    moveTo(14f, 5f)
                    verticalLineToRelative(14f)
                    horizontalLineToRelative(4f)
                    verticalLineTo(5f)
                    close()
                }
            }
        return pauseIcon!!
    }

// material-icons-core 未收录 DarkMode 图标，使用 Material 官方 dark_mode 路径自定义
private var darkModeIcon: ImageVector? = null

val Icons.Filled.DarkMode: ImageVector
    get() {
        if (darkModeIcon != null) return darkModeIcon!!
        darkModeIcon =
            materialIcon(name = "Filled.DarkMode") {
                materialPath {
                    moveTo(12f, 3f)
                    curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                    reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                    reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
                    curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                    curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
                    curveToRelative(-2.98f, 0f, -5.4f, -2.42f, -5.4f, -5.4f)
                    curveToRelative(0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
                    curveToRelative(-0.44f, -0.06f, -0.9f, -0.1f, -1.36f, -0.1f)
                    close()
                }
            }
        return darkModeIcon!!
    }

// material-icons-core 未收录 SkipNext 图标，使用 Material 官方 skip_next 路径自定义
private var skipNextIcon: ImageVector? = null

val Icons.Filled.SkipNext: ImageVector
    get() {
        if (skipNextIcon != null) return skipNextIcon!!
        skipNextIcon =
            materialIcon(name = "Filled.SkipNext") {
                materialPath {
                    moveTo(6f, 18f)
                    lineToRelative(8.5f, -6f)
                    lineTo(6f, 6f)
                    verticalLineToRelative(12f)
                    close()
                    moveTo(16f, 6f)
                    verticalLineToRelative(12f)
                    horizontalLineToRelative(2f)
                    verticalLineTo(6f)
                    close()
                }
            }
        return skipNextIcon!!
    }

// material-icons-core 未收录 SkipPrevious 图标，使用 Material 官方 skip_previous 路径自定义
private var skipPreviousIcon: ImageVector? = null

val Icons.Filled.SkipPrevious: ImageVector
    get() {
        if (skipPreviousIcon != null) return skipPreviousIcon!!
        skipPreviousIcon =
            materialIcon(name = "Filled.SkipPrevious") {
                materialPath {
                    moveTo(6f, 6f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(12f)
                    horizontalLineTo(6f)
                    close()
                    moveTo(9.5f, 12f)
                    lineToRelative(8.5f, 6f)
                    verticalLineTo(6f)
                    close()
                }
            }
        return skipPreviousIcon!!
    }

// material-icons-core 未收录播放队列图标，自绘三条列表线（末行收短，代表“播放列表”）
private var queueMusicIcon: ImageVector? = null

val Icons.Filled.QueueMusic: ImageVector
    get() {
        if (queueMusicIcon != null) return queueMusicIcon!!
        queueMusicIcon =
            materialIcon(name = "Filled.QueueMusic") {
                materialPath {
                    moveTo(4f, 6f)
                    horizontalLineToRelative(16f)
                    verticalLineToRelative(2f)
                    horizontalLineTo(4f)
                    close()
                    moveTo(4f, 11f)
                    horizontalLineToRelative(16f)
                    verticalLineToRelative(2f)
                    horizontalLineTo(4f)
                    close()
                    moveTo(4f, 16f)
                    horizontalLineToRelative(10f)
                    verticalLineToRelative(2f)
                    horizontalLineTo(4f)
                    close()
                }
            }
        return queueMusicIcon!!
    }

// material-icons-core 未收录音质/均衡器图标，自绘五条高低不一的柱状条
private var equalizerIcon: ImageVector? = null

val Icons.Filled.Equalizer: ImageVector
    get() {
        if (equalizerIcon != null) return equalizerIcon!!
        equalizerIcon =
            materialIcon(name = "Filled.Equalizer") {
                materialPath {
                    moveTo(3f, 20f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-6f)
                    horizontalLineToRelative(-2f)
                    close()
                    moveTo(7f, 20f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-10f)
                    horizontalLineToRelative(-2f)
                    close()
                    moveTo(11f, 20f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-16f)
                    horizontalLineToRelative(-2f)
                    close()
                    moveTo(15f, 20f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-10f)
                    horizontalLineToRelative(-2f)
                    close()
                    moveTo(19f, 20f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-6f)
                    horizontalLineToRelative(-2f)
                    close()
                }
            }
        return equalizerIcon!!
    }

// material-icons-core 未收录 ExpandMore（收起箭头 ⌄）图标，使用 Material 官方路径自定义
private var expandMoreIcon: ImageVector? = null

val Icons.Filled.ExpandMore: ImageVector
    get() {
        if (expandMoreIcon != null) return expandMoreIcon!!
        expandMoreIcon =
            materialIcon(name = "Filled.ExpandMore") {
                materialPath {
                    moveTo(16.59f, 8.59f)
                    lineTo(12f, 13.17f)
                    lineToRelative(-4.59f, -4.58f)
                    lineTo(6f, 10f)
                    lineToRelative(6f, 6f)
                    lineToRelative(6f, -6f)
                    close()
                }
            }
        return expandMoreIcon!!
    }

// material-icons-core 未收录 Repeat（循环播放）图标，使用 Material 官方 repeat 路径自定义
private var repeatIcon: ImageVector? = null

val Icons.Filled.Repeat: ImageVector
    get() {
        if (repeatIcon != null) return repeatIcon!!
        repeatIcon =
            materialIcon(name = "Filled.Repeat") {
                materialPath {
                    moveTo(7f, 7f)
                    horizontalLineToRelative(10f)
                    verticalLineToRelative(3f)
                    lineToRelative(4f, -4f)
                    lineToRelative(-4f, -4f)
                    verticalLineToRelative(3f)
                    horizontalLineTo(5f)
                    verticalLineToRelative(6f)
                    horizontalLineToRelative(2f)
                    close()
                    moveTo(17f, 17f)
                    horizontalLineTo(7f)
                    verticalLineToRelative(-3f)
                    lineToRelative(-4f, 4f)
                    lineToRelative(4f, 4f)
                    verticalLineToRelative(-3f)
                    horizontalLineToRelative(12f)
                    verticalLineToRelative(-6f)
                    horizontalLineToRelative(-2f)
                    close()
                }
            }
        return repeatIcon!!
    }

// material-icons-core 未收录 TrendingFlat（一条直箭头，代表「顺序播放/不循环」）图标，使用 Material 官方路径自定义
private var trendingFlatIcon: ImageVector? = null

val Icons.Filled.TrendingFlat: ImageVector
    get() {
        if (trendingFlatIcon != null) return trendingFlatIcon!!
        trendingFlatIcon =
            materialIcon(name = "Filled.TrendingFlat") {
                materialPath {
                    moveTo(22f, 12f)
                    lineToRelative(-4f, -4f)
                    verticalLineToRelative(3f)
                    horizontalLineTo(3f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(15f)
                    verticalLineToRelative(3f)
                    close()
                }
            }
        return trendingFlatIcon!!
    }

// material-icons-core 未收录 Storage（存储空间）图标，自绘简化版磁盘图标
private var storageIcon: ImageVector? = null

val Icons.Filled.Storage: ImageVector
    get() {
        if (storageIcon != null) return storageIcon!!
        storageIcon =
            materialIcon(name = "Filled.Storage") {
                materialPath {
                    // 上层磁盘（圆角矩形 + 一个小圆点代表指示灯）
                    moveTo(2f, 3f)
                    horizontalLineTo(22f)
                    verticalLineToRelative(6f)
                    horizontalLineTo(2f)
                    close()
                    moveTo(19f, 7.5f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(-2f)
                    close()
                    // 下层磁盘
                    moveTo(2f, 11f)
                    horizontalLineTo(22f)
                    verticalLineToRelative(6f)
                    horizontalLineTo(2f)
                    close()
                    moveTo(19f, 15.5f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(-2f)
                    close()
                }
            }
        return storageIcon!!
    }

// material-icons-core 未收录 Palette 图标，使用 Material 官方 palette 路径自定义
private var paletteIcon: ImageVector? = null

val Icons.Filled.Palette: ImageVector
    get() {
        if (paletteIcon != null) return paletteIcon!!
        paletteIcon =
            materialIcon(name = "Filled.Palette") {
                materialPath {
                    moveTo(12f, 3f)
                    curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                    reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                    curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
                    curveToRelative(0f, -0.39f, -0.15f, -0.74f, -0.39f, -1.01f)
                    curveToRelative(-0.23f, -0.26f, -0.38f, -0.61f, -0.38f, -0.99f)
                    curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
                    horizontalLineTo(16f)
                    curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                    curveToRelative(0f, -4.42f, -4.03f, -8f, -9f, -8f)
                    close()
                    moveTo(6.5f, 12f)
                    curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                    reflectiveCurveTo(5.67f, 9f, 6.5f, 9f)
                    reflectiveCurveTo(8f, 9.67f, 8f, 10.5f)
                    reflectiveCurveTo(7.33f, 12f, 6.5f, 12f)
                    close()
                    moveTo(9.5f, 8f)
                    curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                    reflectiveCurveTo(8.67f, 5f, 9.5f, 5f)
                    reflectiveCurveTo(11f, 5.67f, 11f, 6.5f)
                    reflectiveCurveTo(10.33f, 8f, 9.5f, 8f)
                    close()
                    moveTo(14.5f, 8f)
                    curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                    reflectiveCurveTo(13.67f, 5f, 14.5f, 5f)
                    reflectiveCurveTo(16f, 5.67f, 16f, 6.5f)
                    reflectiveCurveTo(15.33f, 8f, 14.5f, 8f)
                    close()
                    moveTo(17.5f, 12f)
                    curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                    reflectiveCurveTo(16.67f, 9f, 17.5f, 9f)
                    reflectiveCurveTo(19f, 9.67f, 19f, 10.5f)
                    reflectiveCurveTo(18.33f, 12f, 17.5f, 12f)
                    close()
                }
            }
        return paletteIcon!!
    }
