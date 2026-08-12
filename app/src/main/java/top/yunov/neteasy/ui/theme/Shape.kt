package top.yunov.neteasy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MD3 Expressive 形状体系 —— 对齐官方 10 级圆角刻度：
 * 4 / 8 / 12 / 16 / 20(Large increased, Expressive 新增) / 28 / 32(XL increased, Expressive 新增) / 48
 * 这里取表达性更强的档位：8 / 12 / 16 / 20 / 32，均落在官方刻度上。
 */
val Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp), // Small
        small = RoundedCornerShape(12.dp), // Medium
        medium = RoundedCornerShape(16.dp), // Large
        large = RoundedCornerShape(20.dp), // Large increased（Expressive）
        extraLarge = RoundedCornerShape(32.dp) // Extra large increased（Expressive）
    )

/** 按钮全圆角（pill）：MD3 规范中按钮默认映射到 full 圆角 */
val ButtonShape = RoundedCornerShape(percent = 50)
