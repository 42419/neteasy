package top.yunov.neteasy.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 可拖动波浪进度条：官方 material3 的 [LinearWavyProgressIndicator] 只负责展示进度，
 * 不支持拖动；这里加一层轻量手势包装（点击 / 拖动 seek），视觉完全来自官方组件。
 *
 * [animating] 控制波浪是否流动：暂停时停止流动（省电），与播放状态同步。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeekWaveProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    animating: Boolean = true
) {
    // 拖动中的位置；-1 表示未在拖动
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragFraction >= 0f) dragFraction else progress.coerceIn(0f, 1f)

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
        if (animating) {
            // 播放中：官方默认波浪流动
            LinearWavyProgressIndicator(
                progress = { shown },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // 暂停：waveSpeed=0 停止流动（省电），波形保持
            LinearWavyProgressIndicator(
                progress = { shown },
                modifier = Modifier.fillMaxWidth(),
                waveSpeed = 0.dp
            )
        }
    }
}
