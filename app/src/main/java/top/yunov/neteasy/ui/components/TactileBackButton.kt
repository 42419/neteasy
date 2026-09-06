package top.yunov.neteasy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 带「按压回弹 + 高光闪一下」反馈的返回按钮，替代原来纯 Material3 默认水波纹的
 * [androidx.compose.material3.FilledTonalIconButton]/[androidx.compose.material3.IconButton]。
 *
 * 按压反馈的具体机制（弹簧参数、TransformOrigin、drawBehind 叠加顺序）照抄自
 * Nevodev/Cresto 项目 `glasense-ui` 模块的 `ButtonPressEffect.Scale`
 * （https://github.com/Nevodev/Cresto ，Apache License 2.0）并按本项目的图标按钮
 * 场景简化：去掉了原版的 role/disabled 等按钮语义，只保留返回按钮需要的按压反馈本身。
 *
 * 两个弹簧动画叠加，而不是简单换个颜色：
 * 1. 按下放大到 1.2 倍（[TransformOrigin.Center] 缩放，不是从触点位置放大）；
 *    阻尼比 0.5、刚度特意压到 300——刚度越低回弹过程拉得越长，才有肉眼可见的
 *    "弹一下"，刚度调太高（比如项目里给别处用的 `ExpressiveMotion.SpatialFast`）
 *    会因为回正太快而感觉不出弹性，偏生硬；
 * 2. 按下时用 [BlendMode.Plus]（加法混合）叠一层白色，且是 `drawBehind`——画在
 *    容器背景之上、图标内容之下，只提亮底色，不会把图标本身也刷白。
 * 用 `indication = null` 的 [Modifier.clickable] 完全替掉默认水波纹，避免两套反馈叠在一起显脏。
 *
 * @param filled true 用跟 FilledTonalIconButton 一样的 secondaryContainer 底色圆形容器
 *   （独立二级页头部用）；false 用透明底（贴在别的控件旁边、比如搜索页标题行里用）。
 */
@Composable
fun TactileBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.0001f),
        label = "backButtonScale"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.2f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f),
        label = "backButtonOverlay"
    )
    Box(
        modifier =
        modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    Modifier
                }
            )
            .drawBehind {
                if (overlayAlpha > 0f) {
                    drawRect(color = Color.White, alpha = overlayAlpha, blendMode = BlendMode.Plus)
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint =
            if (filled) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
