package top.yunov.neteasy.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.ui.util.lerp
import kotlin.math.abs

/**
 * Minibar ↔ 展开播放页的拖动展开动效，纯计算部分。
 *
 * 参考对象：PixelPlayerHQ/PixelPlayer 的 `UnifiedPlayerSheetV2`
 * （scoped/SheetVerticalDragMath.kt + SheetVerticalDragGestureHandler.kt）：
 * 那边是把 Minibar 和展开播放页做成同一个会连续变形（宽高/圆角/内边距全部按进度插值）的
 * Box——拖动时进度条 1:1 跟手，松手按“拖动距离阈值 → 甩动速度 → 当前进度是否过半”三级兜底
 * 判断展开还是收起，再用弹簧动画归位，收起瞬间还会做一下压扁再弹回的“落地感”。
 *
 * neteasy 这边 Minibar（悬浮小卡片）和 NowPlayingScreen（完整全屏页）本来就是两套
 * 差异很大的布局，没有照抄“同一个 Box 连续变形”的写法（那个改动量接近重写这两个组件），
 * 而是保留两者独立布局，用同一套“拖动进度换算 + 松手判定 + 回弹参数”的物理手感，
 * 驱动它们之间的透明度 + 位移交叉过渡（PlayerOverlay.kt 里用）。跟手、甩动、回弹这几个
 * 手感层面的东西是照抄的，视觉呈现（有没有形状连续变形）做了简化。
 */

/** 按本次拖动开始时的进度 + 累计拖动像素，算出当前展开进度（0=收起，1=展开）。 */
internal fun computeDragFraction(
    startFraction: Float,
    accumulatedDragPx: Float,
    dragExtentPx: Float,
): Float {
    // 手指往上拖（dragAmount 为负）应该让进度变大，所以取负号。
    val delta = -accumulatedDragPx / dragExtentPx.coerceAtLeast(1f)
    return (startFraction + delta).coerceIn(0f, 1f)
}

/**
 * 松手时决定该展开还是收起：
 * 1. 拖动距离超过阈值：按方向直接判定（不管多快）
 * 2. 没超过距离阈值但甩得够快：按甩动方向判定（“快速一撇”不用拖多远也能触发）
 * 3. 都没有：按当前进度是否过半兜底
 */
internal fun resolveDragTarget(
    accumulatedDragPx: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThresholdPxPerSec: Float,
    currentFraction: Float,
): Boolean =
    when {
        abs(accumulatedDragPx) > minDragThresholdPx -> accumulatedDragPx < 0f
        abs(verticalVelocity) > velocityThresholdPxPerSec -> verticalVelocity < 0f
        else -> currentFraction > 0.5f
    }

/** 收起动画的阻尼比——当前展开得越多才松手，收起时越“松”，回弹感越明显。 */
internal fun collapseSpringDamping(currentFraction: Float): Float =
    lerp(Spring.DampingRatioNoBouncy, Spring.DampingRatioLowBouncy, currentFraction)

/** 收起瞬间给 Minibar 一个轻微压扁的初始状态，随后弹簧弹回 1，模拟“落地”的实感。 */
internal fun collapseInitialSquash(currentFraction: Float): Float =
    lerp(1f, 0.94f, currentFraction)
