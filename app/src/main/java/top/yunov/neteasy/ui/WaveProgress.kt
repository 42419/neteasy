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
import androidx.compose.ui.geometry.Size
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
 * MD3 Expressive「线性波浪进度指示器」(Linear Wavy Progress Indicator)：
 * https://m3.material.io/components/progress-indicators/guidelines
 *
 * 注意：这里没有用 androidx.compose.material3 自带的 LinearWavyProgressIndicator——
 * 连续两次实测证明，不管是 material3 隐式跟随 compose-bom 还是显式锁定到 1.4.0，
 * 这个组件（以及 ExperimentalMaterial3ExpressiveApi、WavyProgressIndicatorDefaults）
 * 在实际拉取到的 jar 里都是 unresolved / internal，无法使用。所以这里完全用
 * 稳定的 Canvas/Path API 自己实现，不依赖任何实验性 material3 组件，
 * 保证不管 material3 具体解析到哪个小版本都能编译通过。
 *
 * 视觉规则参照官网 guidelines：已播放部分是流动的正弦波描边线，未播放部分是
 * 细直线轨道，两者之间留一道间隙，轨道末端有一个小圆点（stop indicator）
 * 标记 100% 位置。
 */
@Composable
fun WaveProgressBar(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, playing: Boolean = true) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = MaterialTheme.colorScheme.primary

    // 拖动中的位置；-1 表示未在拖动
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragFraction >= 0f) dragFraction else progress.coerceIn(0f, 1f)

    // 波浪流动动画：仅播放时流动，暂停时静止（省电，形状仍保持波浪不塌成直线）
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

/**
 * MD3 Expressive「圆形波浪加载指示器」(Circular Wavy Progress Indicator, 不定进度版)：
 * https://m3.material.io/components/progress-indicators/guidelines
 *
 * 同样出于稳定性考虑，不依赖 material3 的 CircularWavyProgressIndicator（unresolved），
 * 完全自己用 Canvas 画：一条沿圆周起伏的正弦波描边弧线（占部分圆周，代表"进行中"），
 * 加上剩余部分的静态轨道圆弧，两者间留间隙，整体持续旋转模拟不定进度 spinner。
 * 用来统一替换 App 里原来的 [androidx.compose.material3.CircularProgressIndicator]。
 */
@Composable
fun WavyCircularLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    diameter: Dp = 40.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "circularWavy")
    // 整段波浪弧持续旋转，模拟不定进度 spinner
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
        infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "circularWavyRotation"
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
        label = "circularWavyPhase"
    )

    Canvas(modifier = modifier.size(diameter)) {
        val d = diameter.toPx()
        val strokeW = d * 0.10f
        val amplitude = d * 0.05f
        val cx = d / 2f
        val cy = d / 2f
        val baseRadius = d / 2f - strokeW / 2f - amplitude
        val activeSweepDeg = 110f
        val gapDeg = 14f
        val waveCount = 3f

        // 静态轨道弧（剩余部分，两端各扣掉一个 gap）
        val trackSweepDeg = 360f - activeSweepDeg - gapDeg * 2f
        if (trackSweepDeg > 0f) {
            val trackStartDeg = rotationDeg + activeSweepDeg + gapDeg
            drawArc(
                color = trackColor,
                startAngle = trackStartDeg,
                sweepAngle = trackSweepDeg,
                useCenter = false,
                topLeft = Offset(cx - baseRadius, cy - baseRadius),
                size = Size(baseRadius * 2, baseRadius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }

        // 波浪主弧
        val path = Path()
        val steps = 48
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val angleDeg = rotationDeg + t * activeSweepDeg
            val angleRad = angleDeg * PI.toFloat() / 180f
            val r = baseRadius + amplitude * sin(t * waveCount * 2f * PI.toFloat() + wavePhase)
            val x = cx + r * cos(angleRad)
            val y = cy + r * sin(angleRad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}
