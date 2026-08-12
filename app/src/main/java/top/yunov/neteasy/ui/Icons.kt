package top.yunov.neteasy.ui

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
