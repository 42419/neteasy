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
