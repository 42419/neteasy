package top.met6.amll

import kotlin.math.max
import kotlin.math.min

data class OptimizeLyricOptions(
    val normalizeSpaces: Boolean = true,
    val resetLineTimestamps: Boolean = true,
    val convertExcessiveBackgroundLines: Boolean = true,
    val syncMainAndBackgroundLines: Boolean = true,
    val cleanUnintentionalOverlaps: Boolean = true,
    val tryAdvanceStartTime: Boolean = true,
)

/** Immutable Kotlin port of AMLL's default `optimizeLyricLines` pipeline. */
fun optimizeLyricLines(
    source: List<LyricLine>,
    options: OptimizeLyricOptions = OptimizeLyricOptions(),
): List<LyricLine> {
    val lines = source.map { line ->
        line.copy(words = line.words.map { word ->
            if (options.normalizeSpaces) word.copy(word = word.word.replace(Regex("\\s+"), " ")) else word
        })
    }.toMutableList()

    if (options.resetLineTimestamps) {
        lines.indices.forEach { index ->
            val line = lines[index]
            lines[index] = when {
                line.words.size == 1 && line.words[0].startTime == 0L && line.words[0].endTime == 0L &&
                    (line.startTime != 0L || line.endTime != 0L) -> line.copy(
                        words = listOf(line.words[0].copy(startTime = line.startTime, endTime = line.endTime)),
                    )
                line.words.isNotEmpty() -> line.copy(
                    startTime = line.words.first().startTime,
                    endTime = line.words.last().endTime,
                )
                else -> line
            }
        }
    }

    if (options.convertExcessiveBackgroundLines) {
        var backgroundCount = 0
        lines.indices.forEach { index ->
            if (lines[index].isBG) {
                backgroundCount++
                if (backgroundCount > 1) lines[index] = lines[index].copy(isBG = false)
            } else backgroundCount = 0
        }
    }

    if (options.syncMainAndBackgroundLines) {
        for (index in lines.lastIndex downTo 0) {
            val main = lines[index]
            if (main.isBG) continue
            val background = lines.getOrNull(index + 1)?.takeIf(LyricLine::isBG) ?: continue
            val words = (main.words + background.words).filter { it.word.isNotBlank() }
            if (words.isNotEmpty()) {
                val start = min(words.minOf { it.startTime }, min(main.startTime, background.startTime))
                val end = max(words.maxOf { it.endTime }, max(main.endTime, background.endTime))
                lines[index] = main.copy(startTime = start, endTime = end)
                lines[index + 1] = background.copy(startTime = start, endTime = end)
            }
        }
    }

    if (options.cleanUnintentionalOverlaps) {
        for (index in 0 until lines.lastIndex) {
            if (lines[index].isBG) continue
            var nextMain = index + 1
            while (nextMain < lines.size && lines[nextMain].isBG) nextMain++
            if (nextMain >= lines.size) continue
            val overlap = lines[index].endTime - lines[nextMain].startTime
            if (overlap > 0) {
                val nextDuration = lines[nextMain].endTime - lines[nextMain].startTime
                if (!(overlap > 100 && overlap > nextDuration * 0.1)) {
                    lines[index] = lines[index].copy(endTime = lines[nextMain].startTime)
                    if (lines.getOrNull(index + 1)?.isBG == true) {
                        lines[index + 1] = lines[index + 1].copy(endTime = lines[nextMain].startTime)
                    }
                }
            }
        }
    }

    if (options.tryAdvanceStartTime) {
        var previousStart = 0L
        var previousEnd = 0L
        var groupStart = 0L
        var groupEnd = 0L
        var hasPrevious = false
        lines.indices.forEach { index ->
            val original = lines[index]
            if (original.isBG) return@forEach
            val advance = if (!hasPrevious || original.startTime >= previousEnd) 600L else 400L
            val boundary = when {
                !hasPrevious -> 0L
                original.startTime >= previousEnd -> groupEnd
                else -> previousStart + ((previousEnd - previousStart) * 0.3).toLong()
            }
            val newStart = max(boundary, original.startTime - advance)
            if (newStart < original.startTime) lines[index] = original.copy(startTime = newStart)
            if (lines.getOrNull(index + 1)?.isBG == true) lines[index + 1] = lines[index + 1].copy(startTime = lines[index].startTime)

            if (hasPrevious && original.startTime < groupEnd && original.endTime > groupStart) {
                groupStart = min(groupStart, original.startTime)
                groupEnd = max(groupEnd, original.endTime)
            } else {
                groupStart = original.startTime
                groupEnd = original.endTime
            }
            previousStart = original.startTime
            previousEnd = original.endTime
            hasPrevious = true
        }
    }
    return lines
}
