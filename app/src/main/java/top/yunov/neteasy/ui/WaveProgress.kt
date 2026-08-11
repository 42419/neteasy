package top.yunov.neteasy.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * MD3 Expressive「波浪形进度指示器」(Wavy Progress Indicator)：
 * https://m3.material.io/components/progress-indicators/overview
 *
 * 已播放部分是一条流动的正弦波描边线，未播放部分是一条细直线轨道，
 * 两者中间留一道小间隙；轨道最末端有一个小圆点（stop indicator），
 * 标记整条轨道的终点。支持点击 / 拖动跳转（拖动中实时预览，松手才 seek）。
 */
@Composable
fun WaveProgressBar(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, playing: Boolean = true) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = MaterialTheme.colorScheme.primary

    // 拖动中的位置；-1 表示未在拖动
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragFraction >= 0f) dragFraction else progress.coerceIn(0f, 1f)

    // 波浪流动动画：仅播放时流动，暂停时静止（省电，同时形状保持波浪不塌成直线）
    val phase by if (playing) {
        val transition = rememberInfiniteTransition(label = "wave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Canvas(
        modifier =
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        if (dragFraction >= 0f) {
                            onSeek(dragFraction)
                            dragFraction = -1f
                        }
                    },
                    onDragCancel = { dragFraction = -1f }
                )
            }
    ) {
        val w = size.width
        val baseY = size.height / 2f

        val strokeWidthPx = 4.dp.toPx()
        val amplitude = 3.dp.toPx()
        val wavelength = 18.dp.toPx()
        val gap = 6.dp.toPx()
        val stopRadius = 2.5.dp.toPx()
        // 两端各留出半个描边宽度 + 停止点半径，避免圆头 / 圆点被裁切
        val inset = strokeWidthPx / 2f + stopRadius + 2.dp.toPx()

        val left = inset
        val right = (w - inset).coerceAtLeast(left)
        val usableWidth = right - left

        val filledX = left + usableWidth * shown
        val activeEndX = (filledX - gap / 2f).coerceIn(left, right)
        val trackStartX = (filledX + gap / 2f).coerceIn(left, right)

        // 未播放部分：细直线轨道
        if (trackStartX < right) {
            drawLine(
                color = trackColor,
                start = Offset(trackStartX, baseY),
                end = Offset(right, baseY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        // 已播放部分：流动的波浪描边线
        if (activeEndX > left) {
            drawWavyStroke(
                fromX = left,
                toX = activeEndX,
                baseY = baseY,
                amplitude = amplitude,
                wavelength = wavelength,
                phase = phase,
                color = activeColor,
                strokeWidthPx = strokeWidthPx
            )
        }

        // 轨道末端的「停止指示点」，标记总时长的终点
        drawCircle(color = activeColor, radius = stopRadius, center = Offset(right, baseY))
    }
}

/**
 * MD3 Expressive「圆形波浪加载指示器」(Circular Wavy Progress Indicator, 不定进度版)：
 * https://m3.material.io/components/progress-indicators/overview
 * 用一段沿圆周起伏的正弦波描边弧线代替普通实心圆弧，弧线本身持续转动 + 波形持续流动。
 * 用来统一替换 App 里原来的 [androidx.compose.material3.CircularProgressIndicator]。
 */
@Composable
fun WavyCircularLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    diameter: Dp = 40.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavyCircular")
    // 整段弧线持续旋转，模拟不定进度 spinner
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
        infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavyCircularRotation"
    )
    // 波形沿弧线流动
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec =
        infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavyCircularPhase"
    )

    Canvas(modifier = modifier.size(diameter)) {
        val d = diameter.toPx()
        val strokeWidthPx = d * 0.09f
        val amplitude = d * 0.045f
        val waveCount = 8 // 一整圈的波浪个数
        val sweepDeg = 300f // 留出缺口，形似 M3 不定进度圆环
        val baseRadius = d / 2f - strokeWidthPx / 2f - amplitude
        val cx = d / 2f
        val cy = d / 2f

        val path = Path()
        val totalSteps = 90
        for (i in 0..totalSteps) {
            val t = i / totalSteps.toFloat()
            val angleDeg = rotationDeg + t * sweepDeg
            val angleRad = (angleDeg * PI.toFloat() / 180f)
            val r = baseRadius + amplitude * sin(t * waveCount * 2f * PI.toFloat() + wavePhase)
            val x = cx + r * cos(angleRad)
            val y = cy + r * sin(angleRad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
    }
}

/** 沿 [fromX, toX] 画一条正弦波描边线（圆头线帽），y 基线为 [baseY] */
private fun DrawScope.drawWavyStroke(
    fromX: Float,
    toX: Float,
    baseY: Float,
    amplitude: Float,
    wavelength: Float,
    phase: Float,
    color: Color,
    strokeWidthPx: Float
) {
    val path = Path()
    val step = 4f
    var x = fromX
    var first = true
    while (x < toX) {
        val y = baseY + sin((x / wavelength) * (2 * PI).toFloat() + phase) * amplitude
        if (first) {
            path.moveTo(x, y)
            first = false
        } else {
            path.lineTo(x, y)
        }
        x += step
    }
    val yEnd = baseY + sin((toX / wavelength) * (2 * PI).toFloat() + phase) * amplitude
    if (first) {
        // fromX >= toX 的极端情况：画一个点，避免空 Path
        path.moveTo(toX, yEnd)
    }
    path.lineTo(toX, yEnd)

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )
}
