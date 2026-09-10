package top.yunov.neteasy.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

/**
 * 二级页通用的「返回按钮固定悬浮 + 顶部渐进模糊」组合，从 SettingsScreen 那版摸出来的效果
 * 抽出来复用，照抄 Cresto AboutScreen / GlasenseDynamicSmallTitle 的写法（细节见各处注释）。
 *
 * 用法（页面结构套路固定）：
 * ```
 * val backdrop = rememberPageBackdrop()
 * val scrollState = rememberScrollState()
 * val scrolledPast by scrollState.isScrolledPast(statusBarHeight + 24.dp)
 * Surface(...) {
 *     Box(Modifier.fillMaxSize()) {
 *         Column(
 *             Modifier.fillMaxSize().layerBackdrop(backdrop).verticalScroll(scrollState)...
 *         ) { /* 页面内容，不再需要行内的返回按钮 */ }
 *         GlassTopBackBar(backdrop = backdrop, scrolledPast = scrolledPast, onBack = onBack)
 *     }
 * }
 * ```
 * LazyColumn 页面同理，把 `layerBackdrop` 挂 LazyColumn 上，`scrolledPast` 换用
 * [LazyListState.isScrolledPast]。
 */

/** 捕获前先垫一层 3 倍大、居中偏移的页面背景色矩形再 drawContent()——避免模糊卷积在内容
 *  边缘/被缩放时采样到透明或越界像素，照抄 Cresto 的写法。 */
@Composable
fun rememberPageBackdrop(pageBackgroundColor: Color = MaterialTheme.colorScheme.background): LayerBackdrop =
    rememberLayerBackdrop {
        drawRect(
            color = pageBackgroundColor,
            size = Size(this.size.width * 3, this.size.height * 3),
            topLeft = Offset(-this.size.width, -this.size.height)
        )
        drawContent()
    }

/** 滚过 [threshold] 距离才算"过阈值"，给渐进模糊条带的淡入淡出用。 */
@Composable
fun ScrollState.isScrolledPast(threshold: Dp): State<Boolean> {
    val density = LocalDensity.current
    val thresholdPx = remember(threshold, density) { with(density) { threshold.toPx() } }
    return remember(this, thresholdPx) {
        derivedStateOf { value > thresholdPx }
    }
}

/** [LazyListState] 版本：滚过第一屏或者第一个可见 item 的偏移超过阈值。 */
@Composable
fun LazyListState.isScrolledPast(threshold: Dp): State<Boolean> {
    val density = LocalDensity.current
    val thresholdPx = remember(threshold, density) { with(density) { threshold.toPx() } }
    return remember(this, thresholdPx) {
        derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > thresholdPx }
    }
}

/**
 * 返回按钮（固定左上角，不跟随滚动）+ 顶部渐进模糊条带。作为 [BoxScope] 的最后一个孩子调用，
 * 保证画在滚动内容之上。
 */
@Composable
fun BoxScope.GlassTopBackBar(
    backdrop: Backdrop,
    scrolledPast: Boolean,
    onBack: () -> Unit,
    pageBackgroundColor: Color = MaterialTheme.colorScheme.background
) {
    val statusBarHeight = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val blurStripAlpha by animateFloatAsState(
        targetValue = if (scrolledPast) 1f else 0f,
        animationSpec = tween(300),
        label = "blurStripAlpha"
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Box(
            modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBarHeight + 80.dp)
                .graphicsLayer { alpha = blurStripAlpha }
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        blur(8.dp.toPx())
                        runtimeShaderEffect(
                            "AlphaMask",
                            """
                            uniform shader content;
                            uniform float2 size;
                            layout(color) uniform half4 tint;
                            uniform float tintIntensity;
                            half4 main(float2 coord) {
                                float blurAlpha = smoothstep(size.y, size.y * 0.7, coord.y);
                                float tintAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
                                return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
                            }
                            """.trimIndent(),
                            "content"
                        ) {
                            apply {
                                setFloatUniform("size", size.width, size.height)
                                setColorUniform("tint", pageBackgroundColor)
                                setFloatUniform("tintIntensity", 0.7f)
                            }
                        }
                    }
                )
        )
    }

    val backInteraction = remember { MutableInteractionSource() }
    val backPressed by backInteraction.collectIsPressedAsState()
    val backScale by animateFloatAsState(
        targetValue = if (backPressed) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.0001f),
        label = "backButtonScale"
    )
    val backOverlayAlpha by animateFloatAsState(
        targetValue = if (backPressed) 0.2f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f),
        label = "backButtonOverlay"
    )
    val backContainerColor =
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSystemInDarkTheme()) 0.1f else 0.05f)
    Box(
        modifier =
        Modifier
            .align(Alignment.TopStart)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(20.dp)
            .size(48.dp)
            .graphicsLayer {
                scaleX = backScale
                scaleY = backScale
                transformOrigin = TransformOrigin.Center
            }
            .clip(CircleShape)
            .background(backContainerColor)
            .drawBehind {
                if (backOverlayAlpha > 0f) {
                    drawRect(Color.White, alpha = backOverlayAlpha, blendMode = BlendMode.Plus)
                }
            }
            .clickable(
                interactionSource = backInteraction,
                indication = null,
                onClick = onBack
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.primary,
            // 默认 Icon 24dp，在 48dp 容器里稍微显小，调大到 28dp
            modifier = Modifier.size(28.dp)
        )
    }
}
