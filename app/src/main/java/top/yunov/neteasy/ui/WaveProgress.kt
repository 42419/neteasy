package top.yunov.neteasy.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * MD3 Expressive「线性波浪进度指示器」(Linear Wavy Progress Indicator)：
 * https://m3.material.io/components/progress-indicators/guidelines
 *
 * 直接用官方组件 androidx.compose.material3.LinearWavyProgressIndicator 画图形
 * （从 material3 1.4.0-alpha 起就有，稳定版 1.4.0 里也有，不是只在实验性
 * 更高版本才出现的 LoadingIndicator）。这层只负责叠加点击 / 拖动跳转的手势，
 * 图形样式完全交给官方实现，不用再自己算正弦波、间隙、终点圆点这些细节。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WaveProgressBar(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, playing: Boolean = true) {
    // 拖动中的位置；-1 表示未在拖动
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragFraction >= 0f) dragFraction else progress.coerceIn(0f, 1f)

    // 暂停时把波速设为 0，让波形静止而不是持续流动（省电，也更符合“暂停”的直觉）
    val wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength

    LinearWavyProgressIndicator(
        progress = { shown },
        wavelength = wavelength,
        waveSpeed = if (playing) wavelength else 0.dp,
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
    )
}

/**
 * MD3 Expressive「圆形波浪加载指示器」(Circular Wavy Progress Indicator, 不定进度版)：
 * https://m3.material.io/components/progress-indicators/guidelines
 *
 * 同样直接委托给官方组件 androidx.compose.material3.CircularWavyProgressIndicator
 * 的不带 progress 参数的重载（不定进度 / loading 场景专用）。
 * 用来统一替换 App 里原来的 [androidx.compose.material3.CircularProgressIndicator]。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyCircularLoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    CircularWavyProgressIndicator(modifier = modifier, color = color)
}
