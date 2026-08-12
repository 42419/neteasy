package top.yunov.neteasy.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.SpringSpec

/**
 * MD3 Expressive 动效物理弹簧系统 token（官方 2025.05 引入）：
 * - Spatial spring（空间弹簧）：位移 / 旋转 / 尺寸 / 圆角 → 有过冲，弹跳感
 * - Effects spring（效果弹簧）：颜色 / 透明度 → 无过冲，平滑淡入淡出
 * 对应官方 expressive 方案（比 standard 更活泼）。
 */
object ExpressiveMotion {
    /** 空间弹簧·快：小组件按压/回弹（hero 交互） */
    val SpatialFast: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )

    /** 空间弹簧·默认：页面级位移、尺寸变化 */
    val SpatialDefault: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

    /** 效果弹簧·快：透明度/颜色过渡，无过冲 */
    val EffectsFast: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )

    /** 效果弹簧·默认：整页淡入淡出，无过冲 */
    val EffectsDefault: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
}
