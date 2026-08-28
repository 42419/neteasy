package top.met6.amll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
data class AppleMusicLyricPlayerStyle(
    val color: Color = Color.White,
    val fontSize: TextUnit = 32.sp,
    val lineWidthFraction: Float = 0.8f,
    val horizontalPadding: Dp = 16.dp,
    val lineSpacing: Dp = 6.dp,
    /** Vertical focus point of the playing line within the lyric viewport. */
    val alignPosition: Float = 0.2f,
    val activeMaskAlpha: Float = 1f,
    val inactiveMaskAlpha: Float = 0.2f,
    val activeDarkMaskAlpha: Float = 0.4f,
    val backgroundLineScale: Float = 0.7f,
    val wordFadeWidth: Float = 0.5f,
    val enableBlur: Boolean = true,
    val enableScale: Boolean = true,
    /** Interpolate coarse media-position samples at the display refresh rate. */
    val enableFrameInterpolation: Boolean = true,
    /** Playback speed used by frame interpolation. */
    val playbackRate: Float = 1f,
    val alwaysPostpositionBackground: Boolean = false,
    val hoverColor: Color = Color.White.copy(alpha = 0.067f),
    /** Delay before blur and automatic step-following resume after a user drag. */
    val userScrollResumeDelayMs: Long = 5_000,
    /**
     * Fixed spring parameters for the line-scroll motion. When null (default), the analytic
     * [lyricScrollSpringPolicy] picks stiffness/damping dynamically per line based on the gap
     * to the next line (AMLL's original behavior). When non-null, every line transition uses
     * these fixed params instead — lets callers offer a "feel" preset (smooth/responsive/etc.)
     * without touching the per-line density calculation.
     */
    val scrollSpringOverride: AmllSpringParams? = null,
)

internal class LyricScrollMotion {
    val spring = AmllSpring(0.0)
    var consumedScroll: Double = 0.0

    fun snapToCurrentPosition() {
        spring.snapTo(consumedScroll)
    }
}

/**
 * Compose Multiplatform port of the original AMLL DOM lyric player.
 *
 * The public model mirrors AMLL's `LyricLine`/`LyricWord` interfaces, including
 * duet lines, background vocals, translations, line/word romanization and ruby.
 */
@Composable
fun AppleMusicLyricPlayer(
    lyricLines: List<LyricLine>,
    currentTimeMs: Long,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    style: AppleMusicLyricPlayerStyle = AppleMusicLyricPlayerStyle(),
    fontFamily: FontFamily = FontFamily.Default,
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    optimizeOptions: OptimizeLyricOptions = OptimizeLyricOptions(),
    onLineClick: (LyricLine) -> Unit = {},
    emptyContent: @Composable () -> Unit = {},
) {
    val playbackTimeMs = rememberInterpolatedPlaybackTime(
        sourceTimeMs = currentTimeMs,
        isPlaying = isPlaying,
        enabled = style.enableFrameInterpolation,
        playbackRate = style.playbackRate,
    )
    val processedLines = remember(lyricLines, optimizeOptions) { optimizeLyricLines(lyricLines, optimizeOptions) }
    val groups = remember(processedLines) { groupLyrics(processedLines) }
    val timeline = remember(groups) {
        LyricTimeline(groups.map { LyricTimeBounds(it.main.startTime, it.main.endTime) })
    }
    // A composition can restart without the playback time changing. Avoid advancing the
    // stateful timeline more than once for the same timestamp.
    val timelineSnapshot = remember(timeline, playbackTimeMs) { timeline.sync(playbackTimeMs) }
    val highlightedIndices = timelineSnapshot.highlightedIndices
    val scrollTargetIndex = timelineSnapshot.scrollTargetIndex
    val dynamic = remember(processedLines) { processedLines.any { it.words.size > 1 } }
    val hasDuet = remember(processedLines) { processedLines.any(LyricLine::isDuet) }
    val onLineClickState = rememberUpdatedState(onLineClick)
    val listState = rememberLazyListState()
    val scrollMotion = remember(listState) { LyricScrollMotion() }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var autoFollow by remember { mutableStateOf(true) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            autoFollow = false
        } else if (!autoFollow) {
            delay(style.userScrollResumeDelayMs.coerceAtLeast(0L))
            autoFollow = true
        }
    }
    LaunchedEffect(scrollTargetIndex, autoFollow, groups.size) {
        if (!autoFollow || scrollTargetIndex !in groups.indices) return@LaunchedEffect

        // A large seek may place the target outside LazyColumn's measured window.
        // Snap it into the window once; normal line stepping then uses AMLL's analytic spring.
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == scrollTargetIndex }) {
            listState.scrollToItem(scrollTargetIndex)
            scrollMotion.snapToCurrentPosition()
        }

        val previousIndex = (scrollTargetIndex - 1).takeIf { it in groups.indices }
        val interval = previousIndex?.let { groups[scrollTargetIndex].main.startTime - groups[it].main.startTime }
        val interlude = previousIndex?.let { groups[scrollTargetIndex].main.startTime - groups[it].main.endTime >= 4_000 } == true
        val policy = style.scrollSpringOverride ?: lyricScrollSpringPolicy(
            isSeeking = timelineSnapshot.isSeeking,
            isInterludeActive = interlude,
            intervalMs = interval,
        )
        var lastFrameNanos = withFrameNanos { it }

        while (isActive && autoFollow) {
            val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == scrollTargetIndex }
                ?: break
            // offset=0 is the focus origin when beforeContentPadding is used.
            scrollMotion.spring.updateTarget(scrollMotion.consumedScroll + targetItem.offset, policy)
            val frameNanos = withFrameNanos { it }
            val next = scrollMotion.spring.step((frameNanos - lastFrameNanos) / 1_000_000_000.0)
            lastFrameNanos = frameNanos
            val requested = (next - scrollMotion.consumedScroll).toFloat()
            val consumed = listState.scrollBy(requested)
            scrollMotion.consumedScroll += consumed

            if (scrollMotion.spring.isSettled() && abs(targetItem.offset) <= 0.5f) break
            if (abs(consumed - requested) > 0.5f) {
                scrollMotion.snapToCurrentPosition()
                break
            }
            if (abs(consumed) < 0.001f && abs(requested) > 0.5f) break
        }
    }

    BoxWithConstraints(modifier) {
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { emptyContent() }
            return@BoxWithConstraints
        }
        val topPadding = maxHeight * style.alignPosition.coerceIn(0f, 1f)
        val bottomPadding = maxHeight - topPadding
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(style.lineSpacing),
        ) {
            itemsIndexed(groups, key = { index, group -> "${group.main.startTime}:$index" }) { index, group ->
                val active = index in highlightedIndices
                // Past/future rows receive a stable timestamp, so Compose can skip their
                // complete lyric subtree while the player clock keeps ticking.
                val renderTimeMs = playbackTimeMs.coerceIn(group.renderStartTime, group.renderEndTime)
                val distance = abs(index - scrollTargetIndex)
                val targetBlurRadius = when {
                    !style.enableBlur || !autoFollow || active -> 0.dp
                    index < scrollTargetIndex -> min(5f, (distance + 2) * 0.8f).dp
                    else -> min(5f, (distance + 1) * 0.8f).dp
                }
                val blurRadius by animateDpAsState(
                    targetValue = targetBlurRadius,
                    animationSpec = tween(durationMillis = 400),
                    label = "amll-line-blur",
                )
                val clickHandler = remember(group.main) {
                    {
                        autoFollow = true
                        onLineClickState.value(group.main)
                    }
                }
                LyricGroupView(
                    group = group,
                    currentTimeMs = renderTimeMs,
                    active = active,
                    dynamic = dynamic,
                    hasDuet = hasDuet,
                    isPlaying = isPlaying,
                    blurRadius = blurRadius,
                    style = style,
                    fontFamily = fontFamily,
                    showTranslation = showTranslation,
                    showRomanization = showRomanization,
                    onClick = clickHandler,
                    modifier = Modifier,
                )
                val nextStart = groups.getOrNull(index + 1)?.main?.startTime
                if (nextStart != null && nextStart - group.main.endTime >= 5_000 && playbackTimeMs in group.main.endTime..nextStart) {
                    InterludeDots(
                        animation = calculateInterludeDotsAnimation(
                            elapsedMs = playbackTimeMs - group.main.endTime,
                            durationMs = nextStart - group.main.endTime,
                        ),
                        color = style.color,
                        duet = group.main.isDuet,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricGroupView(
    group: LyricGroup,
    currentTimeMs: Long,
    active: Boolean,
    dynamic: Boolean,
    hasDuet: Boolean,
    isPlaying: Boolean,
    blurRadius: Dp,
    style: AppleMusicLyricPlayerStyle,
    fontFamily: FontFamily,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val targetScale = if (!active && isPlaying && style.enableScale) 0.97f else 1f
    val scale by animateFloatAsState(
        targetScale,
        spring(dampingRatio = 0.78f, stiffness = 90f),
        label = "amll-line-scale",
    )
    val targetOpacity = when {
        active -> 0.85f
        dynamic -> 1f
        else -> 0.2f
    }
    val opacity by animateFloatAsState(
        targetValue = targetOpacity,
        animationSpec = tween(durationMillis = 400),
        label = "amll-line-opacity",
    )
    val alignment = if (group.main.isDuet) Alignment.End else Alignment.Start
    val backgroundFirst = !style.alwaysPostpositionBackground &&
        (group.background?.words?.firstOrNull()?.startTime ?: Long.MAX_VALUE) <
        (group.main.words.firstOrNull()?.startTime ?: group.main.startTime)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val interactionColor by animateColorAsState(
        when {
            pressed -> Color.White.copy(alpha = 0.02f)
            hovered -> style.hoverColor
            else -> Color.Transparent
        },
        label = "amll-line-interaction",
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (group.main.isDuet) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(style.lineWidthFraction.coerceIn(0.1f, 1f))
                .then(
                    if (hasDuet) {
                        if (group.main.isDuet) Modifier.padding(start = style.horizontalPadding * 2)
                        else Modifier.padding(end = style.horizontalPadding * 2)
                    } else Modifier
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = if (group.main.isDuet) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)
                    alpha = opacity
                }
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
                .semantics { selected = active }
                .background(interactionColor, RoundedCornerShape(style.fontSize.value.dp * 0.25f))
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = style.horizontalPadding, vertical = style.fontSize.value.dp * 0.4f),
            horizontalAlignment = alignment,
            verticalArrangement = Arrangement.spacedBy(style.fontSize.value.dp * 0.3f),
        ) {
            if (backgroundFirst) BackgroundLine(
                group.background,
                currentTimeMs,
                active,
                isPlaying,
                dynamic,
                true,
                style,
                fontFamily,
                showTranslation,
                showRomanization,
            )
            LyricLineView(group.main, currentTimeMs, active, dynamic, style, fontFamily, showTranslation, showRomanization)
            if (!backgroundFirst) BackgroundLine(
                group.background,
                currentTimeMs,
                active,
                isPlaying,
                dynamic,
                false,
                style,
                fontFamily,
                showTranslation,
                showRomanization,
            )
        }
    }
}

@Composable
private fun BackgroundLine(
    line: LyricLine?,
    currentTimeMs: Long,
    active: Boolean,
    isPlaying: Boolean,
    dynamic: Boolean,
    isBeforeMain: Boolean,
    style: AppleMusicLyricPlayerStyle,
    fontFamily: FontFamily,
    showTranslation: Boolean,
    showRomanization: Boolean,
) {
    if (line == null) return
    val collapseEdge = if (isBeforeMain) Alignment.Bottom else Alignment.Top
    val hiddenOffset: (Int) -> Int = remember(isBeforeMain) {
        if (isBeforeMain) {
            { height -> height * 4 / 5 }
        } else {
            { height -> -height * 4 / 5 }
        }
    }
    val backgroundStyle = remember(style) {
        style.copy(fontSize = style.fontSize * style.backgroundLineScale)
    }
    AnimatedVisibility(
        visible = active || !isPlaying,
        // Match AMLL: a line above the main lyric expands upward and retracts
        // downward; a line below it expands downward and retracts upward.
        enter = fadeIn() +
            expandVertically(expandFrom = collapseEdge) +
            slideInVertically(initialOffsetY = hiddenOffset),
        exit = fadeOut() +
            shrinkVertically(shrinkTowards = collapseEdge) +
            slideOutVertically(targetOffsetY = hiddenOffset),
    ) {
        val scale by animateFloatAsState(
            if (active || !isPlaying) 1f else 0.75f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 50f),
            label = "amll-bg-scale",
        )
        Box(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = 0.4f
                transformOrigin = if (line.isDuet) TransformOrigin(1f, 0f) else TransformOrigin(0f, 0f)
            }
        ) {
            LyricLineView(
                line = line,
                currentTimeMs = currentTimeMs,
                active = active,
                dynamic = dynamic,
                style = backgroundStyle,
                fontFamily = fontFamily,
                showTranslation = showTranslation,
                showRomanization = showRomanization,
            )
        }
    }
}

@Composable
private fun LyricLineView(
    line: LyricLine,
    currentTimeMs: Long,
    active: Boolean,
    dynamic: Boolean,
    style: AppleMusicLyricPlayerStyle,
    fontFamily: FontFamily,
    showTranslation: Boolean,
    showRomanization: Boolean,
) {
    val alignment = if (line.isDuet) Alignment.End else Alignment.Start
    val darkColor by animateColorAsState(
        targetValue = style.color.copy(
            alpha = if (active) style.activeDarkMaskAlpha else style.inactiveMaskAlpha,
        ),
        animationSpec = tween(durationMillis = if (active) 300 else 450),
        label = "amll-line-dark-mask",
    )
    val brightColor by animateColorAsState(
        targetValue = style.color.copy(
            alpha = if (active) style.activeMaskAlpha else style.inactiveMaskAlpha,
        ),
        animationSpec = tween(durationMillis = if (active) 300 else 450),
        label = "amll-line-bright-mask",
    )
    Column(horizontalAlignment = alignment) {
        BalancedLyricWords(
            words = line.words,
            currentTimeMs = currentTimeMs,
            active = active,
            duet = line.isDuet,
            dynamic = dynamic,
            style = style,
            fontFamily = fontFamily,
            darkColor = darkColor,
            brightColor = brightColor,
        )
        val subStyle = remember(style, fontFamily, line.isDuet) {
            TextStyle(
                color = style.color.copy(alpha = 0.3f),
                fontSize = maxTextUnit(style.fontSize * 0.5f, 10.sp),
                lineHeight = maxTextUnit(style.fontSize * 0.75f, 15.sp),
                fontWeight = FontWeight.SemiBold,
                fontFamily = fontFamily,
                textAlign = if (line.isDuet) TextAlign.End else TextAlign.Start,
            )
        }
        if (showTranslation && line.translatedLyric.isNotBlank()) BasicText(
            text = line.translatedLyric,
            modifier = Modifier.fillMaxWidth(),
            style = subStyle,
        )
        if (showRomanization && line.romanLyric.isNotBlank()) BasicText(
            text = line.romanLyric,
            modifier = Modifier.fillMaxWidth(),
            style = subStyle,
        )
    }
}

@Composable
internal fun LyricChunkView(
    words: List<LyricWord>,
    currentTimeMs: Long,
    emphasisDurationMs: Long,
    emphasisCharacterOffsets: IntArray,
    emphasisCharacterCount: Int,
    emphasize: Boolean,
    active: Boolean,
    style: AppleMusicLyricPlayerStyle,
    fontFamily: FontFamily,
    darkColor: Color,
    brightColor: Color,
    hasRubyLine: Boolean,
    hasRomanLine: Boolean,
) {
    val characterWeights = remember(words) { lyricChunkCharacterWeights(words) }
    val highlightProgress = calculateLyricChunkProgress(words, currentTimeMs, characterWeights)
    val emphasisAmount = remember(emphasisDurationMs) {
        var amount = max(1L, emphasisDurationMs) / 2000f
        amount = if (amount > 1) sqrt(amount) else amount.pow(3)
        min(1.2f, amount * 0.6f)
    }
    val floatY = if (active) -style.fontSize.value * 0.05f * highlightProgress else 0f
    val mainStyle = TextStyle(
        color = darkColor,
        fontSize = style.fontSize,
        lineHeight = style.fontSize * 1.2f,
        fontWeight = FontWeight.Bold,
        fontFamily = fontFamily,
    )

    Box(
        modifier = Modifier.graphicsLayer {
            translationY = floatY
        },
    ) {
        val highlightStyle = mainStyle.copy(color = brightColor)
        if (active && highlightProgress >= 0.995f) {
            LyricChunkTextContent(
                words = words,
                mainStyle = highlightStyle,
                fontSize = style.fontSize,
                hasRubyLine = hasRubyLine,
                hasRomanLine = hasRomanLine,
                emphasize = emphasize && active,
                emphasisProgress = highlightProgress,
                emphasisAmount = emphasisAmount,
                emphasisCharacterOffsets = emphasisCharacterOffsets,
                emphasisCharacterCount = emphasisCharacterCount,
                glowColor = style.color,
            )
        } else {
            LyricChunkTextContent(
                words = words,
                mainStyle = mainStyle,
                fontSize = style.fontSize,
                hasRubyLine = hasRubyLine,
                hasRomanLine = hasRomanLine,
                emphasize = emphasize && active,
                emphasisProgress = highlightProgress,
                emphasisAmount = emphasisAmount,
                emphasisCharacterOffsets = emphasisCharacterOffsets,
                emphasisCharacterCount = emphasisCharacterCount,
                glowColor = style.color,
            )
            if (active && highlightProgress > 0f) {
                LyricChunkTextContent(
                    words = words,
                    mainStyle = highlightStyle,
                    fontSize = style.fontSize,
                    hasRubyLine = hasRubyLine,
                    hasRomanLine = hasRomanLine,
                    emphasize = emphasize && active,
                    emphasisProgress = highlightProgress,
                    emphasisAmount = emphasisAmount,
                    emphasisCharacterOffsets = emphasisCharacterOffsets,
                    emphasisCharacterCount = emphasisCharacterCount,
                    glowColor = style.color,
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithCache {
                            val fadeWidth = (size.height * style.wordFadeWidth).coerceAtLeast(0.01f)
                            val halfFadeWidth = fadeWidth * 0.5f
                            val head = highlightProgress * (size.width + fadeWidth) - halfFadeWidth
                            val mask = Brush.horizontalGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                startX = head - halfFadeWidth,
                                endX = head + halfFadeWidth,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(mask, blendMode = BlendMode.DstIn)
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun LyricChunkTextContent(
    words: List<LyricWord>,
    mainStyle: TextStyle,
    fontSize: TextUnit,
    hasRubyLine: Boolean,
    hasRomanLine: Boolean,
    emphasize: Boolean,
    emphasisProgress: Float,
    emphasisAmount: Float,
    emphasisCharacterOffsets: IntArray,
    emphasisCharacterCount: Int,
    glowColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        words.forEachIndexed { index, word ->
            val displayWord = remember(word) { maskObscene(word) }
            val rubyText = remember(word.ruby) { word.ruby.joinToString("") { it.word } }
            key(index, word.startTime, word.endTime, word.word) {
                WordTextContent(
                    displayWord = displayWord,
                    rubyText = rubyText,
                    romanWord = word.romanWord,
                    mainStyle = mainStyle,
                    fontSize = fontSize,
                    hasRubyLine = hasRubyLine,
                    hasRomanLine = hasRomanLine,
                    emphasize = emphasize,
                    emphasisProgress = emphasisProgress,
                    emphasisAmount = emphasisAmount,
                    emphasisCharacterOffset = emphasisCharacterOffsets[index],
                    emphasisCharacterCount = emphasisCharacterCount,
                    glowColor = glowColor,
                )
            }
        }
    }
}

/**
 * Maps independently timed syllables to one monotonic visual progress value.
 * Each syllable keeps its own timing, while the mask travels only once across
 * the complete unbroken word instead of restarting at every syllable boundary.
 */
internal fun calculateLyricChunkProgress(
    words: List<LyricWord>,
    timeMs: Long,
    characterWeights: IntArray = lyricChunkCharacterWeights(words),
): Float {
    if (words.isEmpty()) return 0f
    var totalWeight = 0
    var completedWeight = 0f
    words.forEachIndexed { index, word ->
        val weight = characterWeights.getOrElse(index) { 1 }.coerceAtLeast(1)
        totalWeight += weight
        completedWeight += weight * wordProgress(word, timeMs)
    }
    return (completedWeight / totalWeight.coerceAtLeast(1)).coerceIn(0f, 1f)
}

internal fun lyricChunkCharacterWeights(words: List<LyricWord>): IntArray =
    IntArray(words.size) { index ->
        splitGraphemeClusters(maskObscene(words[index])).size.coerceAtLeast(1)
    }

@Composable
private fun WordTextContent(
    displayWord: String,
    rubyText: String,
    romanWord: String,
    mainStyle: TextStyle,
    fontSize: TextUnit,
    hasRubyLine: Boolean,
    hasRomanLine: Boolean,
    emphasize: Boolean,
    emphasisProgress: Float,
    emphasisAmount: Float,
    emphasisCharacterOffset: Int,
    emphasisCharacterCount: Int,
    glowColor: Color,
    modifier: Modifier = Modifier,
) {
    val annotationStyle = mainStyle.copy(
        fontSize = fontSize * 0.5f,
        lineHeight = fontSize * 0.5f,
        shadow = null,
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (hasRubyLine) BasicText(text = rubyText.ifEmpty { "\u00a0" }, style = annotationStyle)
        if (emphasize) {
            EmphasizedWordText(
                text = displayWord,
                style = mainStyle,
                progress = emphasisProgress,
                amount = emphasisAmount,
                characterOffset = emphasisCharacterOffset,
                characterCount = emphasisCharacterCount,
                glowColor = glowColor,
            )
        } else {
            BasicText(text = displayWord, style = mainStyle)
        }
        if (hasRomanLine) BasicText(text = romanWord.ifBlank { "\u00a0" }, style = annotationStyle)
    }
}

@Composable
private fun EmphasizedWordText(
    text: String,
    style: TextStyle,
    progress: Float,
    amount: Float,
    characterOffset: Int,
    characterCount: Int,
    glowColor: Color,
) {
    val characters = remember(text) { splitGraphemeClusters(text) }
    val fontSizePx = with(LocalDensity.current) { style.fontSize.toPx() }
    Row(verticalAlignment = Alignment.Bottom) {
        characters.forEachIndexed { localIndex, character ->
            val characterIndex = characterOffset + localIndex
            val motion = calculateEmphasisCharacterMotion(
                progress = progress,
                characterIndex = characterIndex,
                characterCount = characterCount,
                amount = amount,
            )
            key(localIndex, character) {
                BasicText(
                    text = character,
                    style = style.copy(
                        shadow = if (motion.wave > 0f) {
                            Shadow(
                                color = glowColor.copy(alpha = motion.wave * 0.5f),
                                blurRadius = motion.wave * fontSizePx * 0.25f,
                            )
                        } else null,
                    ),
                    modifier = Modifier.graphicsLayer {
                        scaleX = motion.scale
                        scaleY = motion.scale
                        translationX = motion.translationXEm * fontSizePx
                        translationY = motion.translationYEm * fontSizePx
                    },
                )
            }
        }
    }
}

internal data class EmphasisCharacterMotion(
    val wave: Float,
    val scale: Float,
    val translationXEm: Float,
    val translationYEm: Float,
)

/** Creates a left-to-right travelling arch across a highlighted lyric word. */
internal fun calculateEmphasisCharacterMotion(
    progress: Float,
    characterIndex: Int,
    characterCount: Int,
    amount: Float,
): EmphasisCharacterMotion {
    val safeCount = characterCount.coerceAtLeast(1)
    val indexRatio = if (safeCount == 1) 0f else {
        characterIndex.coerceIn(0, safeCount - 1).toFloat() / (safeCount - 1)
    }
    val staggerWindow = 0.4f
    val localProgress = ((progress.coerceIn(0f, 1f) - indexRatio * staggerWindow) /
        (1f - staggerWindow)).coerceIn(0f, 1f)
    val wave = sin(localProgress * PI).toFloat().coerceAtLeast(0f)
    if (wave <= 0f) {
        return EmphasisCharacterMotion(
            wave = 0f,
            scale = 1f,
            translationXEm = 0f,
            translationYEm = 0f,
        )
    }
    val strength = amount.coerceAtLeast(0f)
    val centerOffset = (safeCount - 1) * 0.5f - characterIndex
    return EmphasisCharacterMotion(
        wave = wave,
        scale = 1f + wave * 0.1f * strength,
        translationXEm = -wave * 0.015f * strength * centerOffset,
        translationYEm = -wave * 0.05f * strength,
    )
}

@Composable
private fun InterludeDots(animation: InterludeDotsAnimation, color: Color, duet: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .graphicsLayer {
                scaleX = animation.scale
                scaleY = animation.scale
                transformOrigin = if (duet) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)
            },
        horizontalArrangement = if (duet) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            Canvas(Modifier.padding(2.dp).size(8.dp).graphicsLayer { alpha = animation.opacities[index] }) {
                drawCircle(color)
            }
        }
    }
}

private data class LyricGroup(val main: LyricLine, val background: LyricLine? = null) {
    private val bounds = lyricGroupRenderBounds(main, background)
    val renderStartTime: Long = bounds.startTime
    val renderEndTime: Long = bounds.endTime
}

internal data class LyricRenderBounds(val startTime: Long, val endTime: Long)

internal fun lyricGroupRenderBounds(main: LyricLine, background: LyricLine?): LyricRenderBounds {
    var earliest = min(main.startTime, main.endTime)
    var latest = max(main.startTime, main.endTime)
    fun include(startTime: Long, endTime: Long) {
        earliest = min(earliest, min(startTime, endTime))
        latest = max(latest, max(startTime, endTime))
    }
    main.words.forEach { include(it.startTime, it.endTime) }
    background?.let {
        include(it.startTime, it.endTime)
        it.words.forEach { word -> include(word.startTime, word.endTime) }
    }
    return LyricRenderBounds(startTime = earliest, endTime = latest)
}

internal data class InterludeDotsAnimation(
    val scale: Float,
    val opacities: List<Float>,
)

/** Time-anchored port of AMLL's breathing and staggered interlude dots. */
internal fun calculateInterludeDotsAnimation(elapsedMs: Long, durationMs: Long): InterludeDotsAnimation {
    if (durationMs <= 0L || elapsedMs !in 0..durationMs) {
        return InterludeDotsAnimation(0f, listOf(0f, 0f, 0f))
    }
    val current = elapsedMs.toDouble()
    val duration = durationMs.toDouble()
    val breatheDuration = duration / ceil(duration / 1_500.0).coerceAtLeast(1.0)
    var scale = sin(1.5 * PI - current / breatheDuration * 2.0) / 20.0 + 1.0
    var opacity = when {
        current < 500.0 -> 0.0
        current < 1_000.0 -> (current - 500.0) / 500.0
        else -> 1.0
    }
    if (current < 2_000.0) scale *= easeOutExpo(current / 2_000.0)

    val remaining = duration - current
    if (remaining < 750.0) {
        scale *= 1.0 - easeInOutBack((750.0 - remaining) / 1_500.0)
    }
    if (remaining < 375.0) opacity *= (remaining / 375.0).coerceIn(0.0, 1.0)

    val dotsDuration = (duration - 750.0).coerceAtLeast(1.0)
    val opacities = List(3) { index ->
        val delayed = current - dotsDuration / 3.0 * index
        val local = ((delayed * 3.0 / dotsDuration) * 0.75).coerceIn(0.25, 1.0)
        (opacity * local).coerceIn(0.0, 1.0).toFloat()
    }
    return InterludeDotsAnimation(
        scale = (scale.coerceAtLeast(0.0) * 0.7).toFloat(),
        opacities = opacities,
    )
}

private fun easeOutExpo(value: Double): Double =
    if (value >= 1.0) 1.0 else 1.0 - 2.0.pow(-10.0 * value.coerceAtLeast(0.0))

private fun easeInOutBack(value: Double): Double {
    val x = value.coerceIn(0.0, 1.0)
    val c1 = 1.70158
    val c2 = c1 * 1.525
    return if (x < 0.5) {
        (2.0 * x).pow(2) * ((c2 + 1.0) * 2.0 * x - c2) / 2.0
    } else {
        ((2.0 * x - 2.0).pow(2) * ((c2 + 1.0) * (x * 2.0 - 2.0) + c2) + 2.0) / 2.0
    }
}

private fun groupLyrics(lines: List<LyricLine>): List<LyricGroup> {
    val groups = mutableListOf<LyricGroup>()
    lines.forEach { line ->
        if (line.isBG && groups.isNotEmpty() && groups.last().background == null) {
            groups[groups.lastIndex] = groups.last().copy(background = line)
        } else {
            groups += LyricGroup(line)
        }
    }
    return groups
}

fun findActiveLyricIndex(lines: List<LyricLine>, timeMs: Long): Int {
    if (lines.isEmpty() || timeMs < lines.first().startTime) return -1
    return lines.indexOfLast { timeMs >= it.startTime }.coerceAtLeast(0)
}

fun findPlayingLyricIndices(lines: List<LyricLine>, timeMs: Long): Set<Int> =
    lines.indices.filterTo(linkedSetOf()) { index ->
        timeMs >= lines[index].startTime && timeMs < lines[index].endTime
    }

fun wordProgress(word: LyricWord, timeMs: Long): Float {
    if (word.endTime <= word.startTime) return if (timeMs >= word.startTime) 1f else 0f
    return ((timeMs - word.startTime).toFloat() / (word.endTime - word.startTime)).coerceIn(0f, 1f)
}

fun shouldEmphasize(word: LyricWord): Boolean {
    val text = word.word.trim()
    val duration = word.endTime - word.startTime
    val hasCjk = isCjkText(text)
    return if (hasCjk) duration >= 1_000 else duration >= 1_000 && text.length in 2..7
}

internal fun maskObscene(word: LyricWord): String = if (!word.obscene) word.word else {
    val text = word.word
    when {
        text.length <= 2 -> "*".repeat(text.length)
        else -> text.first() + "*".repeat(text.length - 2) + text.last()
    }
}

private fun maxTextUnit(a: TextUnit, b: TextUnit): TextUnit = if (a.value >= b.value) a else b
