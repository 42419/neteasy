package top.met6.amll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Reconstructs a display-rate playback clock from a potentially low-frequency
 * media-position source. Fresh source samples remain authoritative; ordinary
 * clock jitter is corrected gradually while seeks snap immediately.
 */
internal class PlaybackTimeInterpolator(initialSourceTimeMs: Long) {
    private var visualTimeMs = initialSourceTimeMs.toDouble()
    private var lastFrameNanos: Long? = null
    private var anchorSourceTimeMs = initialSourceTimeMs.toDouble()
    private var anchorFrameNanos = 0L
    private var observedSourceTimeMs = initialSourceTimeMs
    private var observedSourceFrameNanos = 0L

    fun update(
        sourceTimeMs: Long,
        frameNanos: Long,
        isPlaying: Boolean,
        playbackRate: Float = 1f,
    ): Long {
        val rate = playbackRate.takeIf(Float::isFinite)?.coerceAtLeast(0f)?.toDouble() ?: 1.0
        if (!isPlaying || rate == 0.0) {
            reset(sourceTimeMs, frameNanos)
            return sourceTimeMs
        }

        val previousFrameNanos = lastFrameNanos
        if (previousFrameNanos == null) {
            reset(sourceTimeMs, frameNanos)
            return sourceTimeMs
        }

        if (sourceTimeMs != observedSourceTimeMs) {
            val elapsedSinceSampleMs = ((frameNanos - observedSourceFrameNanos).coerceAtLeast(0L) / 1_000_000.0)
            val expectedSourceTimeMs = observedSourceTimeMs + elapsedSinceSampleMs * rate
            val isDiscontinuity = abs(sourceTimeMs - expectedSourceTimeMs) > SEEK_DISCONTINUITY_MS
            anchorSourceTimeMs = sourceTimeMs.toDouble()
            anchorFrameNanos = frameNanos
            observedSourceTimeMs = sourceTimeMs
            observedSourceFrameNanos = frameNanos
            if (isDiscontinuity) {
                reset(sourceTimeMs, frameNanos)
                return sourceTimeMs
            }
        }

        val rawTimeMs = anchorSourceTimeMs +
            (frameNanos - anchorFrameNanos).coerceAtLeast(0L) / 1_000_000.0 * rate
        val frameDeltaMs = ((frameNanos - previousFrameNanos).coerceAtLeast(0L) / 1_000_000.0)
            .coerceAtMost(MAX_FRAME_STEP_MS)
        val predictedTimeMs = visualTimeMs + frameDeltaMs * rate
        val errorMs = rawTimeMs - predictedTimeMs
        visualTimeMs = if (abs(errorMs) > SNAP_ERROR_MS) {
            rawTimeMs
        } else {
            val maxCorrectionMs = max(MIN_CORRECTION_MS, frameDeltaMs * CORRECTION_RATIO)
            predictedTimeMs + errorMs.coerceIn(-maxCorrectionMs, maxCorrectionMs)
        }
        lastFrameNanos = frameNanos
        return visualTimeMs.roundToLong()
    }

    private fun reset(sourceTimeMs: Long, frameNanos: Long) {
        visualTimeMs = sourceTimeMs.toDouble()
        lastFrameNanos = frameNanos
        anchorSourceTimeMs = sourceTimeMs.toDouble()
        anchorFrameNanos = frameNanos
        observedSourceTimeMs = sourceTimeMs
        observedSourceFrameNanos = frameNanos
    }

    private companion object {
        const val SEEK_DISCONTINUITY_MS = 120.0
        const val SNAP_ERROR_MS = 250.0
        const val MAX_FRAME_STEP_MS = 64.0
        const val MIN_CORRECTION_MS = 0.25
        const val CORRECTION_RATIO = 0.25
    }
}

@Composable
internal fun rememberInterpolatedPlaybackTime(
    sourceTimeMs: Long,
    isPlaying: Boolean,
    enabled: Boolean,
    playbackRate: Float,
): Long {
    var visualTimeMs by remember { mutableLongStateOf(sourceTimeMs) }
    val latestSourceTimeMs by rememberUpdatedState(sourceTimeMs)

    LaunchedEffect(sourceTimeMs, isPlaying, enabled, playbackRate) {
        if (!enabled || !isPlaying || !playbackRate.isFinite() || playbackRate <= 0f) {
            visualTimeMs = sourceTimeMs
        }
    }
    LaunchedEffect(isPlaying, enabled, playbackRate) {
        if (!enabled || !isPlaying || !playbackRate.isFinite() || playbackRate <= 0f) return@LaunchedEffect
        val interpolator = PlaybackTimeInterpolator(latestSourceTimeMs)
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            val nextTimeMs = interpolator.update(
                sourceTimeMs = latestSourceTimeMs,
                frameNanos = frameNanos,
                isPlaying = true,
                playbackRate = playbackRate,
            )
            if (nextTimeMs != visualTimeMs) visualTimeMs = nextTimeMs
        }
    }
    return if (enabled) visualTimeMs else sourceTimeMs
}
