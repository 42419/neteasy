package top.met6.amll

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import kotlin.math.max

/** An indivisible layout unit produced by AMLL's timed-word chunking rules. */
internal data class LyricWordChunk(val words: List<LyricWord>) {
    val text: String = words.joinToString("") { it.word }
    val isSpace: Boolean = text.isBlank()
}

internal data class LyricLayoutUnit(
    val width: Double,
    val text: String,
    val isSpace: Boolean,
)

private val whitespacePartRegex = Regex("\\s+|\\S+")
private val leadingWhitespaceRegex = Regex("^\\s+")
private val trailingWhitespaceRegex = Regex("\\s+$")
private val allWhitespaceRegex = Regex("\\s")
private val punctuationEndChars = setOf(
    ',', '.', ';', ':', '!', '?',
    '，', '。', '；', '：', '！', '？', '、',
    '）', '】', '》', '」', '』', '’', '”',
    ')', '[', ']', '}', '>', '~', '…',
)

/** Kotlin port of AMLL's `chunkAndSplitLyricWords`. */
internal fun chunkAndSplitLyricWords(words: List<LyricWord>): List<LyricWordChunk> {
    val result = mutableListOf<LyricWordChunk>()
    var currentGroup = mutableListOf<LyricWord>()

    fun flushGroup() {
        if (currentGroup.isNotEmpty()) {
            result += LyricWordChunk(currentGroup.toList())
            currentGroup = mutableListOf()
        }
    }

    fun processAtom(atom: LyricWord) {
        val mergeable = atom.word.isNotBlank() && atom.ruby.isEmpty() && !isCjkText(atom.word)
        if (mergeable) {
            currentGroup += atom
        } else {
            flushGroup()
            result += LyricWordChunk(listOf(atom))
        }
    }

    words.forEach { word ->
        val content = word.word.trim()
        if (content.isEmpty()) {
            processAtom(word)
            return@forEach
        }

        if (word.ruby.isNotEmpty()) {
            leadingWhitespaceRegex.find(word.word)?.value?.let { leading ->
                processAtom(word.copy(word = leading, romanWord = "", endTime = word.startTime, ruby = emptyList()))
            }
            processAtom(word.copy(word = content))
            trailingWhitespaceRegex.find(word.word)?.value?.let { trailing ->
                processAtom(word.copy(word = trailing, romanWord = "", startTime = word.endTime, ruby = emptyList()))
            }
            return@forEach
        }

        val parts = whitespacePartRegex.findAll(word.word).map { it.value }.toList()
        val totalLength = unicodeCodePointCount(word.word.replace(allWhitespaceRegex, "")).coerceAtLeast(1)
        val timeSpan = word.endTime - word.startTime
        val wordParts = word.word.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val romanTrimmed = word.romanWord.trim()
        val romanParts = if (romanTrimmed.isEmpty()) emptyList() else {
            romanTrimmed.split(Regex("\\s+")).filter(String::isNotEmpty)
        }
        val romanMatches = wordParts.isNotEmpty() && wordParts.size == romanParts.size
        var currentOffset = 0
        var nonSpaceIndex = 0

        fun timeAt(offset: Int): Long =
            word.startTime + (timeSpan.toDouble() * offset / totalLength).toLong()

        parts.forEach { part ->
            if (part.isBlank()) {
                val start = timeAt(currentOffset)
                processAtom(word.copy(word = part, romanWord = "", startTime = start, endTime = start))
                return@forEach
            }

            val partRomanWord = when {
                romanMatches -> romanParts.getOrElse(nonSpaceIndex) { "" }
                romanParts.isNotEmpty() && nonSpaceIndex == 0 -> word.romanWord
                else -> ""
            }
            nonSpaceIndex++

            if (isCjkText(part) && part.length > 1 && romanTrimmed.isEmpty()) {
                splitUnicodeCodePoints(part).forEach { character ->
                    val start = timeAt(currentOffset)
                    currentOffset++
                    processAtom(
                        word.copy(
                            word = character,
                            romanWord = "",
                            startTime = start,
                            endTime = timeAt(currentOffset),
                        ),
                    )
                }
            } else {
                val start = timeAt(currentOffset)
                currentOffset += unicodeCodePointCount(part)
                processAtom(
                    word.copy(
                        word = part,
                        romanWord = partRomanWord,
                        startTime = start,
                        endTime = timeAt(currentOffset),
                    ),
                )
            }
        }
    }

    flushGroup()
    return result
}

/**
 * Port of AMLL's balanced line-break dynamic programming algorithm.
 * Each input unit is atomic, so a multi-timestamp English word can never be split.
 */
internal fun calculateBalancedBreaks(
    children: List<LyricLayoutUnit>,
    containerWidth: Double,
): List<Int> {
    val count = children.size
    if (count == 0 || containerWidth <= 0.0) return emptyList()

    val prefixWidth = DoubleArray(count + 1)
    for (index in children.indices) {
        prefixWidth[index + 1] = prefixWidth[index] + children[index].width
    }
    if (prefixWidth[count] <= containerWidth) return emptyList()

    val costs = DoubleArray(count + 1) { Double.POSITIVE_INFINITY }
    val nextBreak = IntArray(count + 1) { -1 }
    costs[count] = 0.0
    val cjkPenalty = square(containerWidth * 0.15)
    val normalPenalty = square(containerWidth * 0.5)
    val breakPenalties = DoubleArray(count + 1)
    for (end in 1 until count) {
        val previous = children[end - 1]
        breakPenalties[end] = when {
            previous.text.lastOrNull()?.let { it in punctuationEndChars } == true ->
                -square(containerWidth * 0.6)
            previous.isSpace -> -square(containerWidth * 0.4)
            isCjkText(previous.text) || isCjkText(children[end].text) -> cjkPenalty
            else -> normalPenalty
        }
    }

    for (start in count - 1 downTo 0) {
        for (end in start + 1..count) {
            val lineWidth = prefixWidth[end] - prefixWidth[start]
            val lineCost = if (lineWidth > containerWidth) {
                if (end == start + 1) square(lineWidth - containerWidth) * 1_000.0 else break
            } else {
                square(containerWidth - lineWidth)
            }

            val totalCost = lineCost + breakPenalties[end] + costs[end]
            if (totalCost < costs[start]) {
                costs[start] = totalCost
                nextBreak[start] = end
            }
        }
    }

    val breaks = mutableListOf<Int>()
    var current = 0
    while (current < count) {
        val next = nextBreak[current]
        if (next <= current) break
        current = next
        if (current < count) breaks += current
    }
    return breaks
}

@Composable
internal fun BalancedLyricWords(
    words: List<LyricWord>,
    currentTimeMs: Long,
    active: Boolean,
    duet: Boolean,
    dynamic: Boolean,
    style: AppleMusicLyricPlayerStyle,
    fontFamily: FontFamily,
    darkColor: Color,
    brightColor: Color,
) {
    val chunks = remember(words) { chunkAndSplitLyricWords(words) }
    val hasRubyLine = remember(words) { words.any { it.ruby.isNotEmpty() } }
    val hasRomanLine = remember(words) { words.any { it.romanWord.isNotBlank() } }
    val spaceStyle = remember(style, fontFamily) {
        TextStyle(
            color = style.color.copy(alpha = style.activeDarkMaskAlpha),
            fontSize = style.fontSize,
            lineHeight = style.fontSize * 1.2f,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily,
        )
    }
    val measurePolicy = remember(chunks, duet) {
        var cachedLayoutWidth = -1
        var cachedChildWidths = IntArray(0)
        var cachedBreaks = emptyList<Int>()
        MeasurePolicy { measurables, constraints ->
            val childConstraints = Constraints(
                minWidth = 0,
                maxWidth = Constraints.Infinity,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )
            val placeables = measurables.map { it.measure(childConstraints) }
            val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else {
                placeables.sumOf { it.width }.coerceAtLeast(constraints.minWidth)
            }
            val widthsUnchanged = cachedChildWidths.size == placeables.size &&
                placeables.indices.all { cachedChildWidths[it] == placeables[it].width }
            val breaks = if (layoutWidth == cachedLayoutWidth && widthsUnchanged) {
                cachedBreaks
            } else {
                val childWidths = IntArray(placeables.size) { placeables[it].width }
                calculateBalancedBreaks(
                    children = chunks.mapIndexed { index, chunk ->
                        LyricLayoutUnit(childWidths[index].toDouble(), chunk.text, chunk.isSpace)
                    },
                    containerWidth = layoutWidth.toDouble(),
                ).also {
                    cachedLayoutWidth = layoutWidth
                    cachedChildWidths = childWidths
                    cachedBreaks = it
                }
            }
            val rowStarts = IntArray(breaks.size + 1)
            val rowEnds = IntArray(breaks.size + 1)
            for (row in rowStarts.indices) {
                rowStarts[row] = if (row == 0) 0 else breaks[row - 1]
                rowEnds[row] = if (row < breaks.size) breaks[row] else placeables.size
            }
            val rowHeights = IntArray(rowStarts.size) { row ->
                var height = 0
                for (index in rowStarts[row] until rowEnds[row]) height = max(height, placeables[index].height)
                height
            }
            val measuredHeight = rowHeights.sum()
            val layoutHeight = measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

            layout(layoutWidth, layoutHeight) {
                var y = 0
                for (row in rowStarts.indices) {
                    var rowWidth = 0
                    for (index in rowStarts[row] until rowEnds[row]) rowWidth += placeables[index].width
                    var x = if (duet) (layoutWidth - rowWidth).coerceAtLeast(0) else 0
                    for (index in rowStarts[row] until rowEnds[row]) {
                        val placeable = placeables[index]
                        placeable.place(x, y + rowHeights[row] - placeable.height)
                        x += placeable.width
                    }
                    y += rowHeights[row]
                }
            }
        }
    }

    Layout(
        modifier = Modifier,
        content = {
            chunks.forEachIndexed { index, chunk ->
                key(index, chunk.text) {
                    UnbreakableLyricChunk(
                        chunk = chunk,
                        currentTimeMs = currentTimeMs,
                        active = active,
                        dynamic = dynamic,
                        style = style,
                        fontFamily = fontFamily,
                        spaceStyle = spaceStyle,
                        darkColor = darkColor,
                        brightColor = brightColor,
                        hasRubyLine = hasRubyLine,
                        hasRomanLine = hasRomanLine,
                    )
                }
            }
        },
        measurePolicy = measurePolicy,
    )
}

@Composable
private fun UnbreakableLyricChunk(
    chunk: LyricWordChunk,
    currentTimeMs: Long,
    active: Boolean,
    dynamic: Boolean,
    style: AppleMusicLyricPlayerStyle,
    fontFamily: FontFamily,
    spaceStyle: TextStyle,
    darkColor: Color,
    brightColor: Color,
    hasRubyLine: Boolean,
    hasRomanLine: Boolean,
) {
    val measurePolicy = remember {
        MeasurePolicy { measurables, constraints ->
            val loose = Constraints(
                minWidth = 0,
                maxWidth = Constraints.Infinity,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )
            val placeables = measurables.map { it.measure(loose) }
            val width = placeables.sumOf { it.width }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.place(x, height - placeable.height)
                    x += placeable.width
                }
            }
        }
    }
    Layout(
        content = {
            if (chunk.isSpace) {
                BasicText(chunk.text, style = spaceStyle)
            } else if (!dynamic) {
                val displayText = remember(chunk.words) {
                    chunk.words.joinToString("") { maskObscene(it) }
                }
                BasicText(
                    text = displayText,
                    style = spaceStyle.copy(
                        color = if (active) brightColor else darkColor,
                        textAlign = TextAlign.Start,
                    ),
                )
            } else {
                val mergedWord = remember(chunk.words) {
                    LyricWord(
                        startTime = chunk.words.minOf(LyricWord::startTime),
                        endTime = chunk.words.maxOf(LyricWord::endTime),
                        word = chunk.text,
                    )
                }
                val emphasize = remember(chunk.words, mergedWord) {
                    chunk.words.any(::shouldEmphasize) ||
                        (!isCjkText(mergedWord.word) && shouldEmphasize(mergedWord))
                }
                val characterOffsets = remember(chunk.words) {
                    IntArray(chunk.words.size + 1).also { offsets ->
                        chunk.words.forEachIndexed { index, word ->
                            offsets[index + 1] = offsets[index] +
                                splitGraphemeClusters(maskObscene(word)).size
                        }
                    }
                }
                val characterCount = characterOffsets.last().coerceAtLeast(1)
                LyricChunkView(
                    words = chunk.words,
                    currentTimeMs = currentTimeMs,
                    emphasisDurationMs = mergedWord.endTime - mergedWord.startTime,
                    emphasisCharacterOffsets = characterOffsets,
                    emphasisCharacterCount = characterCount,
                    emphasize = emphasize,
                    active = active,
                    style = style,
                    fontFamily = fontFamily,
                    darkColor = darkColor,
                    brightColor = brightColor,
                    hasRubyLine = hasRubyLine,
                    hasRomanLine = hasRomanLine,
                )
            }
        },
        measurePolicy = measurePolicy,
    )
}

internal fun isCjkText(text: String): Boolean {
    if (text.isEmpty()) return false
    var index = 0
    while (index < text.length) {
        val codePoint = unicodeCodePointAt(text, index)
        val isCjk = codePoint in 0x0800..0x9ffc ||
            codePoint in 0xf900..0xfaff ||
            codePoint in 0x20000..0x2ebef ||
            codePoint in 0x30000..0x323af
        if (!isCjk) return false
        index += if (codePoint > 0xffff) 2 else 1
    }
    return true
}

internal fun splitUnicodeCodePoints(text: String): List<String> {
    val result = ArrayList<String>(text.length)
    var index = 0
    while (index < text.length) {
        val first = text[index]
        val hasLowSurrogate = first.code in 0xd800..0xdbff &&
            index + 1 < text.length && text[index + 1].code in 0xdc00..0xdfff
        val length = if (hasLowSurrogate) 2 else 1
        result += text.substring(index, index + length)
        index += length
    }
    return result
}

/** Lightweight common-code grapheme splitter for animated lyric characters. */
internal fun splitGraphemeClusters(text: String): List<String> {
    val codePoints = splitUnicodeCodePoints(text)
    if (codePoints.size < 2) return codePoints
    val result = mutableListOf<String>()
    var regionalIndicatorCount = 0
    codePoints.forEach { character ->
        val codePoint = unicodeCodePointAt(character, 0)
        val previousEndsWithJoiner = result.lastOrNull()?.endsWith("\u200d") == true
        val joinsPrevious = result.isNotEmpty() && (
            isCombiningCodePoint(codePoint) ||
                codePoint == 0x200d ||
                previousEndsWithJoiner ||
                (codePoint in 0x1f1e6..0x1f1ff && regionalIndicatorCount % 2 == 1)
            )
        if (joinsPrevious) {
            result[result.lastIndex] += character
        } else {
            result += character
        }
        regionalIndicatorCount = if (codePoint in 0x1f1e6..0x1f1ff) regionalIndicatorCount + 1 else 0
    }
    return result
}

private fun isCombiningCodePoint(codePoint: Int): Boolean =
    codePoint in 0x0300..0x036f ||
        codePoint in 0x1ab0..0x1aff ||
        codePoint in 0x1dc0..0x1dff ||
        codePoint in 0x20d0..0x20ff ||
        codePoint in 0xfe00..0xfe0f ||
        codePoint in 0xfe20..0xfe2f ||
        codePoint in 0x1f3fb..0x1f3ff ||
        codePoint in 0xe0100..0xe01ef

private fun unicodeCodePointCount(text: String): Int {
    var count = 0
    var index = 0
    while (index < text.length) {
        val first = text[index]
        index += if (
            first.code in 0xd800..0xdbff &&
            index + 1 < text.length && text[index + 1].code in 0xdc00..0xdfff
        ) 2 else 1
        count++
    }
    return count
}

private fun unicodeCodePointAt(text: String, index: Int): Int {
    val high = text[index].code
    if (high !in 0xd800..0xdbff || index + 1 >= text.length) return high
    val low = text[index + 1].code
    if (low !in 0xdc00..0xdfff) return high
    return 0x10000 + ((high - 0xd800) shl 10) + (low - 0xdc00)
}

private fun square(value: Double): Double = value * value
