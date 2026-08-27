package top.met6.amll

import kotlin.math.abs

data class LyricTimeBounds(val startTime: Long, val endTime: Long)

data class LyricTimelineSnapshot(
    val playingIndices: Set<Int>,
    val highlightedIndices: Set<Int>,
    val scrollTargetIndex: Int,
    val isSeeking: Boolean,
)

/** Stateful port of AMLL's overlapping-line playing/highlight timeline. */
class LyricTimeline(private val bounds: List<LyricTimeBounds>) {
    private val startOrder = bounds.indices.sortedWith(compareBy({ bounds[it].startTime }, { it }))
    private val endOrder = bounds.indices.sortedWith(compareBy({ bounds[it].endTime }, { it }))
    private val playing = linkedSetOf<Int>()
    private val highlighted = linkedSetOf<Int>()
    private var scrollTarget = 0
    private var previousTime: Long? = null
    private var startCursor = 0
    private var endCursor = 0
    private var cachedSnapshot = createSnapshot(isSeeking = false)

    fun sync(timeMs: Long, forceSeek: Boolean = false): LyricTimelineSnapshot {
        val lastTime = previousTime
        val seeking = forceSeek || (lastTime != null && (timeMs < lastTime || abs(timeMs - lastTime) > 1_000))

        if (lastTime == null || seeking) {
            rebuildAt(timeMs)
        } else {
            var addedPlaying: MutableSet<Int>? = null
            var playingChanged = false

            // Normal playback only processes time boundaries crossed since the last tick.
            // No full-list scan and no Set allocation occurs on ordinary render frames.
            while (startCursor < startOrder.size && bounds[startOrder[startCursor]].startTime <= timeMs) {
                val index = startOrder[startCursor++]
                if (bounds[index].endTime > timeMs && playing.add(index)) {
                    if (addedPlaying == null) addedPlaying = linkedSetOf()
                    addedPlaying += index
                    playingChanged = true
                }
            }
            while (endCursor < endOrder.size && bounds[endOrder[endCursor]].endTime <= timeMs) {
                val index = endOrder[endCursor++]
                if (playing.remove(index)) playingChanged = true
            }

            if (playingChanged) {
                reconcileHighlights(addedPlaying.orEmpty())
                cachedSnapshot = createSnapshot(isSeeking = false)
            } else if (cachedSnapshot.isSeeking) {
                cachedSnapshot = cachedSnapshot.copy(isSeeking = false)
            }
        }

        previousTime = timeMs
        if (lastTime == null || seeking) cachedSnapshot = createSnapshot(isSeeking = seeking)
        return cachedSnapshot
    }

    private fun rebuildAt(timeMs: Long) {
        playing.clear()
        bounds.indices.filterTo(playing) { index ->
            timeMs >= bounds[index].startTime && timeMs < bounds[index].endTime
        }
        highlighted.clear()
        highlighted += playing
        scrollTarget = playing.minOrNull()
            ?: bounds.indexOfFirst { it.startTime >= timeMs }.takeIf { it >= 0 }
            ?: bounds.lastIndex

        startCursor = 0
        while (startCursor < startOrder.size && bounds[startOrder[startCursor]].startTime <= timeMs) startCursor++
        endCursor = 0
        while (endCursor < endOrder.size && bounds[endOrder[endCursor]].endTime <= timeMs) endCursor++
    }

    private fun reconcileHighlights(addedPlaying: Set<Int>) {
        val expiredHighlighted = highlighted.filterTo(linkedSetOf()) { it !in playing }
        if (addedPlaying.isNotEmpty()) highlighted += addedPlaying

        val allHighlightedFinished = expiredHighlighted.isNotEmpty() &&
            expiredHighlighted.size == highlighted.size
        if ((addedPlaying.isNotEmpty() || allHighlightedFinished) && expiredHighlighted.isNotEmpty()) {
            highlighted.removeAll(expiredHighlighted)
        }
        if ((addedPlaying.isNotEmpty() || allHighlightedFinished) && highlighted.isNotEmpty()) {
            scrollTarget = highlighted.min()
        }
    }

    private fun createSnapshot(isSeeking: Boolean) =
        LyricTimelineSnapshot(
            playingIndices = playing.toSet(),
            highlightedIndices = highlighted.toSet(),
            scrollTargetIndex = scrollTarget.coerceIn(0, bounds.lastIndex.coerceAtLeast(0)),
            isSeeking = isSeeking,
        )
}
