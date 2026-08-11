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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * MD3 Expressive 波浪式进度条：
 * 已播放部分以两层流动的正弦波填充，未播放部分为浅色轨道；
 * 支持点击 / 拖动跳转（拖动中实时预览，松手才 seek）。
 */
@Composable
fun WaveProgressBar(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, playing: Boolean = true) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val waveColor = MaterialTheme.colorScheme.primary

    // 拖动中的位置；-1 表示未在拖动
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragFraction >= 0f) dragFraction else progress.coerceIn(0f, 1f)

    // 波浪流动动画：仅播放时流动，暂停时静止（省电）
    val phase by if (playing) {
        val transition = rememberInfiniteTransition(label = "wave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
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
            .height(36.dp)
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
        val h = size.height
        val radius = CornerRadius(h / 2f, h / 2f)
        val thumbRadius = h * 0.34f

        // 轨道
        drawRoundRect(color = trackColor, cornerRadius = radius)

        val filledW = w * shown
        if (filledW > 0f) {
            clipRect(right = filledW) {
                // 两层波浪错位叠加，营造立体流动感
                drawWave(
                    phase = phase,
                    color = waveColor,
                    alpha = 0.45f,
                    phaseSpeed = 1f,
                    ampRatio = 0.22f,
                    verticalShift = -h * 0.06f
                )
                drawWave(
                    phase = phase,
                    color = waveColor,
                    alpha = 0.95f,
                    phaseSpeed = 1.6f,
                    ampRatio = 0.15f,
                    verticalShift = 0f
                )
            }
        }

        // 圆形拇指
        drawCircle(
            color = waveColor,
            radius = thumbRadius,
            center = Offset(filledW.coerceIn(thumbRadius, w - thumbRadius), h / 2f)
        )
    }
}

/** 绘制一条正弦波填充（从波峰到底部闭合），宽度铺满整个画布 */
private fun DrawScope.drawWave(
    phase: Float,
    color: Color,
    alpha: Float,
    phaseSpeed: Float,
    ampRatio: Float,
    verticalShift: Float
) {
    val amp = size.height * ampRatio
    val baseY = size.height / 2f + verticalShift
    val waveLen = size.width / 1.7f
    val endX = size.width
    val path = Path()
    path.moveTo(0f, size.height)
    var x = 0f
    val step = 6f
    while (x <= endX) {
        val y = baseY + sin((x / waveLen) * (2 * PI).toFloat() + phase * phaseSpeed) * amp
        path.lineTo(x, y)
        x += step
    }
    path.lineTo(endX, size.height)
    path.close()
    drawPath(path = path, color = color.copy(alpha = alpha))
}
