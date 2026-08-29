package top.yunov.neteasy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 可拖动波浪进度条：官方 material3 的 [LinearWavyProgressIndicator] 只负责展示进度，
 * 不支持拖动；这里加一层轻量手势包装（点击 / 拖动 seek），视觉完全来自官方组件。
 *
 * [animating] 控制波浪形态：播放中按官方默认振幅流动；暂停时振幅收平为直线。
 * 收平/涌起的过渡动画由官方组件内部完成（Increasing/DecreasingAmplitudeAnimationSpec，
 * 600ms tween），这里不再额外叠一层动画。
 *
 * 注意：暂停时不能把 waveSpeed 归零——官方实现里 waveSpeed=0 会把波形相位 waveOffset
 * 强制重置为 0，导致波形跳回固定相位再趴平。保持 waveSpeed 流动则相位连续，
 * 波形会从当前相位平滑融化成直线；振幅为 0 后 waveOffset 不再被绘制读取，几乎无开销。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeekWaveProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    animating: Boolean = true,
    /** 网络流已预加载到的位置（0~1，相对整首歌时长），用于在播放进度之外叠加缓存进度 */
    bufferedProgress: Float = 0f
) {
    // 拖动中的位置；-1 表示未在拖动
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragFraction >= 0f) dragFraction else progress.coerceIn(0f, 1f)
    // 缓存进度不应该比当前显示的播放进度还靠后（比如刚拖到后面还没缓冲到的位置）
    val bufferedShown = bufferedProgress.coerceIn(0f, 1f).coerceAtLeast(shown)

    Box(
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
            },
        contentAlignment = Alignment.Center
    ) {
        // waveSpeed 保持流动不归零：避免官方组件把 waveOffset 重置导致相位跳变（见 KDoc）。
        // amplitude 播放中保留官方默认的「进度两端收平、中段满振幅」特性，暂停时取 0 收平。
        LinearWavyProgressIndicator(
            progress = { shown },
            modifier = Modifier.fillMaxWidth(),
            wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
            waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
            amplitude = { if (animating) WavyProgressIndicatorDefaults.indicatorAmplitude(it) else 0f }
        )

        // 缓存进度叠加层：不改官方波浪指示器本身（播放进度那段还是它画的 wavy 前景 +
        // 官方默认轨道），只在「已播放 ~ 已缓冲」这一段上面叠一层半透明色块把它区分出来。
        // 画在官方组件之上（Box 里排在它后面），半透明色不会挡住底下的波浪/轨道，
        // 只是把这一段轻轻染色——三种状态一眼能分：波浪=正在播，染色=已缓冲未播，
        // 原色轨道=还没缓冲到。
        if (bufferedShown > shown) {
            val bufferColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                val barHeight = 4.dp.toPx()
                val y = (size.height - barHeight) / 2f
                val startX = size.width * shown
                val endX = size.width * bufferedShown
                drawRoundRect(
                    color = bufferColor,
                    topLeft = Offset(startX, y),
                    size = Size(endX - startX, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f)
                )
            }
        }
    }
}
