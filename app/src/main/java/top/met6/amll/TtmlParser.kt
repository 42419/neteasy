package top.met6.amll

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Pure Kotlin port of AMLL's TTML parser and AMLL converter. */
object TtmlParser {
    fun parse(source: String): TtmlResult {
        require(source.isNotBlank()) { "TTMLParser: input must be a valid XML string" }
        val root = XmlTreeParser(source).parse()
        require(root.localName == "tt") { "TTMLParser: root element must be <tt>" }

        val sidecar = mutableMapOf<String, Sidecar>()
        val metadata = parseHead(root, sidecar)
        val lines = parseBody(root, sidecar)
        val inferredTiming = metadata.timingMode ?: if (lines.any {
                it.content.words.size > 1 || (it.content.backgroundVocal?.words?.size ?: 0) > 1
            }) "Word" else "Line"

        return TtmlResult(
            metadata = metadata.copy(
                language = root.attr("lang") ?: metadata.language,
                timingMode = root.attr("timing")?.takeIf { it == "Word" || it == "Line" }
                    ?: inferredTiming,
            ),
            lines = lines,
        )
    }

    fun parseAmll(
        source: String,
        translationLanguage: String? = null,
        romanizationLanguage: String? = null,
    ): AmllLyricResult = toAmllLyrics(parse(source), translationLanguage, romanizationLanguage)

    fun parseTime(value: String?): Long {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return 0
        if (text.endsWith('s')) return (text.dropLast(1).toDoubleOrNull()?.times(1000))?.roundToLong() ?: 0
        val parts = text.split(':')
        if (parts.isEmpty() || parts.size > 3) return 0
        val seconds = parts.last().toDoubleOrNull() ?: return 0
        val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0
        val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0
        return ((hours * 3600 + minutes * 60 + seconds) * 1000).roundToLong()
    }

    private fun parseHead(root: XmlElement, sidecar: MutableMap<String, Sidecar>): TtmlMetadata {
        val head = root.firstDescendant("head") ?: return TtmlMetadata()
        val title = mutableListOf<String>()
        val artist = mutableListOf<String>()
        val album = mutableListOf<String>()
        val isrc = mutableListOf<String>()
        val authorIds = mutableListOf<String>()
        val authorNames = mutableListOf<String>()
        val songwriters = mutableListOf<String>()
        val agents = linkedMapOf<String, TtmlAgent>()
        val platformIds = linkedMapOf<String, MutableList<String>>()
        val rawProperties = linkedMapOf<String, MutableList<String>>()

        head.descendants("title").firstOrNull()?.textContent?.trim()?.takeIf(String::isNotEmpty)?.let(title::add)
        head.descendants("agent").forEach { element ->
            val id = element.attr("id") ?: return@forEach
            agents[id] = TtmlAgent(
                id = id,
                type = element.attr("type"),
                name = element.descendants("name").firstOrNull()?.textContent?.trim()?.takeIf(String::isNotEmpty),
            )
        }

        head.descendants("meta").forEach { element ->
            val key = element.attr("key") ?: return@forEach
            val value = element.attr("value")?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
            when (key) {
                "musicName" -> title += value
                "artists" -> artist += value
                "album" -> album += value
                "isrc" -> isrc += value
                "ttmlAuthorGithub" -> authorIds += value
                "ttmlAuthorGithubLogin" -> authorNames += value
                "ncmMusicId", "qqMusicId", "spotifyId", "appleMusicId" ->
                    platformIds.getOrPut(key, ::mutableListOf) += value
                else -> rawProperties.getOrPut(key, ::mutableListOf) += value
            }
        }

        head.descendants("songwriter").mapNotNull { it.textContent.trim().takeIf(String::isNotEmpty) }
            .forEach(songwriters::add)

        head.descendants("iTunesMetadata").forEach { meta ->
            parseSidecarContainer(meta, "translations", "translation", true, sidecar)
            parseSidecarContainer(meta, "transliterations", "transliteration", false, sidecar)
        }

        fun List<String>.dedupe() = distinct()
        return TtmlMetadata(
            title = title.dedupe(), artist = artist.dedupe(), album = album.dedupe(),
            isrc = isrc.dedupe(), authorIds = authorIds.dedupe(), authorNames = authorNames.dedupe(),
            songwriters = songwriters.dedupe(), agents = agents,
            platformIds = platformIds.mapValues { it.value.distinct() },
            rawProperties = rawProperties.mapValues { it.value.distinct() },
        )
    }

    private fun parseSidecarContainer(
        metadata: XmlElement,
        containerName: String,
        itemName: String,
        translation: Boolean,
        sidecar: MutableMap<String, Sidecar>,
    ) {
        metadata.descendants(containerName).firstOrNull()?.descendants(itemName)?.forEach { item ->
            val language = item.attr("lang")
            item.descendants("text").forEach { textNode ->
                val lineId = textNode.attr("for") ?: return@forEach
                val parsed = parseCommonContent(textNode)
                val target = sidecar.getOrPut(lineId, ::Sidecar)
                extractSubContent(parsed, language).let { (main, background) ->
                    if (translation) {
                        main?.let(target.translations::add)
                        background?.let(target.bgTranslations::add)
                    } else {
                        main?.let(target.romanizations::add)
                        background?.let(target.bgRomanizations::add)
                    }
                }
            }
        }
    }

    private fun parseBody(root: XmlElement, sidecar: Map<String, Sidecar>): List<TtmlLyricLine> {
        val body = root.firstDescendant("body") ?: return emptyList()
        val result = mutableListOf<TtmlLyricLine>()
        var blockIndex = 0
        body.elementChildren.forEach { child ->
            when (child.localName) {
                "div" -> {
                    blockIndex++
                    val songPart = child.attr("song-part") ?: child.attr("songPart")
                    child.descendants("p").forEach { processLine(it, sidecar, songPart, blockIndex)?.let(result::add) }
                }
                "p" -> {
                    blockIndex++
                    processLine(child, sidecar, null, blockIndex)?.let(result::add)
                }
            }
        }
        return result
    }

    private fun processLine(
        element: XmlElement,
        sidecar: Map<String, Sidecar>,
        songPart: String?,
        blockIndex: Int,
    ): TtmlLyricLine? {
        val id = element.attr("key") ?: element.attr("id") ?: return null
        var content = parseCommonContent(element)
        sidecar[id]?.let { extra ->
            content = content.copy(
                translations = content.translations + extra.translations,
                romanizations = content.romanizations + extra.romanizations,
                backgroundVocal = content.backgroundVocal?.let { bg ->
                    bg.copy(
                        translations = bg.translations + extra.bgTranslations,
                        romanizations = bg.romanizations + extra.bgRomanizations,
                    )
                },
            )
        }
        return TtmlLyricLine(
            id = id,
            agentId = element.attr("agent"),
            songPart = songPart,
            blockIndex = blockIndex,
            content = content,
        )
    }

    private fun parseCommonContent(element: XmlElement): TtmlLyricBase {
        val originalStart = parseTime(element.attr("begin"))
        val originalEnd = parseTime(element.attr("end"))
        val hasTime = element.attr("begin") != null || element.attr("end") != null
        val state = ParsedState()

        element.children.forEach { node ->
            when (node) {
                is XmlText -> processTextNode(state, node.value)
                is XmlElement -> processElementNode(state, node)
            }
        }

        state.words.firstOrNull()?.let { state.words[0] = it.copy(text = it.text.trimStart()) }
        state.words.lastOrNull()?.let { state.words[state.words.lastIndex] = it.copy(text = it.text.trimEnd(), endsWithSpace = false) }

        var start = originalStart
        var end = originalEnd
        val timed = state.words.map { it.startTime to it.endTime }.toMutableList()
        state.backgroundVocal?.let { timed += it.startTime to it.endTime }
        if (timed.isNotEmpty()) {
            val minStart = timed.minOf { it.first }
            val maxEnd = timed.maxOf { it.second }
            if (start == 0L || (minStart > 0 && minStart < start)) start = minStart
            if (end == 0L || maxEnd > end) end = maxEnd
        }

        val cleanText = normalizeText(state.fullText)
        if (state.words.isEmpty() && cleanText.isNotEmpty() && hasTime) {
            state.words += TtmlSyllable(cleanText, originalStart.takeIf { it > 0 } ?: start, originalEnd.takeIf { it > 0 } ?: end)
        }

        var background = state.backgroundVocal
        if (background != null) {
            background = background.copy(
                translations = background.translations + state.bgTranslations,
                romanizations = background.romanizations + state.bgRomanizations,
            )
        }
        return TtmlLyricBase(
            text = cleanText,
            startTime = start,
            endTime = end,
            words = state.words,
            translations = state.translations,
            romanizations = state.romanizations,
            backgroundVocal = background,
        )
    }

    private fun processTextNode(state: ParsedState, rawText: String) {
        val formatting = '\n' in rawText
        if (formatting && rawText.isBlank()) return
        val normalized = normalizeText(rawText, trim = false)
        state.fullText += normalized
        if (!formatting && normalized.isNotEmpty() && normalized.isBlank() && state.words.isNotEmpty()) {
            val last = state.words.last()
            state.words[state.words.lastIndex] = last.copy(endsWithSpace = true)
        }
    }

    private fun processElementNode(state: ParsedState, element: XmlElement) {
        if (element.attr("ruby") == "container") {
            processRubyElement(state, element)
            return
        }
        when (element.attr("role")) {
            "x-bg" -> state.backgroundVocal = parseBackgroundVocal(element)
            "x-translation" -> extractSubContent(parseCommonContent(element), element.attr("lang"), ignoreWords = true).let {
                it.first?.let(state.translations::add); it.second?.let(state.bgTranslations::add)
            }
            "x-roman" -> extractSubContent(parseCommonContent(element), element.attr("lang"), ignoreWords = true).let {
                it.first?.let(state.romanizations::add); it.second?.let(state.bgRomanizations::add)
            }
            else -> processWordElement(state, element)
        }
    }

    private fun processWordElement(state: ParsedState, element: XmlElement) {
        val begin = element.attr("begin") ?: return
        val end = element.attr("end") ?: return
        val rawText = element.textContent
        val normalized = normalizeText(rawText, trim = false)
        state.fullText += normalized
        val formatting = '\n' in rawText
        val startsWithSpace = !formatting && normalized.firstOrNull()?.isWhitespace() == true
        val endsWithSpace = !formatting && normalized.lastOrNull()?.isWhitespace() == true
        if (startsWithSpace && state.words.isNotEmpty()) {
            val last = state.words.last()
            state.words[state.words.lastIndex] = last.copy(endsWithSpace = true)
        }
        normalized.trim().takeIf(String::isNotEmpty)?.let { text ->
            state.words += TtmlSyllable(
                text = text,
                startTime = parseTime(begin),
                endTime = parseTime(end),
                endsWithSpace = endsWithSpace,
                obscene = element.attr("obscene") == "true",
                emptyBeat = element.attr("empty-beat")?.toIntOrNull(),
            )
        }
    }

    private fun processRubyElement(state: ParsedState, container: XmlElement) {
        val base = container.elementChildren.firstOrNull { it.attr("ruby") == "base" }
            ?.textContent?.let { normalizeText(it, trim = false) } ?: return
        val ruby = container.elementChildren.firstOrNull { it.attr("ruby") == "textContainer" }
            ?.elementChildren?.filter { it.attr("ruby") == "text" }
            ?.mapNotNull { node ->
                val begin = node.attr("begin") ?: return@mapNotNull null
                val end = node.attr("end") ?: return@mapNotNull null
                normalizeText(node.textContent).takeIf(String::isNotEmpty)?.let {
                    LyricWordBase(parseTime(begin), parseTime(end), it)
                }
            }.orEmpty()
        state.fullText += base
        if (base.firstOrNull()?.isWhitespace() == true && state.words.isNotEmpty()) {
            val last = state.words.last()
            state.words[state.words.lastIndex] = last.copy(endsWithSpace = true)
        }
        val clean = base.trim()
        if (clean.isNotEmpty()) {
            state.words += TtmlSyllable(
                text = clean,
                startTime = ruby.minOfOrNull { it.startTime } ?: 0,
                endTime = ruby.maxOfOrNull { it.endTime } ?: 0,
                endsWithSpace = base.lastOrNull()?.isWhitespace() == true,
                ruby = ruby,
                obscene = container.attr("obscene") == "true",
                emptyBeat = container.attr("empty-beat")?.toIntOrNull(),
            )
        }
    }

    private fun parseBackgroundVocal(element: XmlElement): TtmlLyricBase {
        val parsed = parseCommonContent(element)
        val words = parsed.words.toMutableList()
        if (words.isNotEmpty()) {
            words[0] = words[0].copy(text = words[0].text.replace(Regex("^[（(]+"), "").trimStart())
            words[words.lastIndex] = words.last().copy(text = words.last().text.replace(Regex("[）)]+$"), "").trimEnd())
        }
        return parsed.copy(
            text = parsed.text.replace(Regex("^[（(]+"), "").replace(Regex("[）)]+$"), ""),
            words = words,
            backgroundVocal = null,
        )
    }

    private fun extractSubContent(
        source: TtmlLyricBase,
        language: String?,
        ignoreWords: Boolean = false,
    ): Pair<SubLyricContent?, SubLyricContent?> {
        fun content(base: TtmlLyricBase): SubLyricContent? {
            val words = if (ignoreWords || (base.words.size == 1 && base.words[0].startTime == 0L && base.words[0].endTime == 0L)) emptyList() else base.words
            val text = normalizeText(base.text)
            return if (text.isNotEmpty() || words.isNotEmpty()) SubLyricContent(text, language, words) else null
        }
        return content(source) to source.backgroundVocal?.let(::content)
    }

    private fun toAmllLyrics(
        result: TtmlResult,
        translationLanguage: String?,
        romanizationLanguage: String?,
    ): AmllLyricResult {
        val lines = mutableListOf<LyricLine>()
        var lastPersonAgent: String? = null
        var lastPersonDuet = false

        result.lines.forEach { line ->
            val agentId = line.agentId ?: "v1"
            val agent = result.metadata.agents[agentId]
            val duet = when (agent?.type) {
                "group" -> false
                else -> when {
                    lastPersonAgent == null -> (agent?.type == "other").also {
                        lastPersonAgent = agentId; lastPersonDuet = it
                    }
                    lastPersonAgent == agentId -> lastPersonDuet
                    else -> (!lastPersonDuet).also {
                        lastPersonAgent = agentId; lastPersonDuet = it
                    }
                }
            }
            lines += convertLine(line.content, isBackground = false, duet, translationLanguage, romanizationLanguage)
            line.content.backgroundVocal?.let {
                lines += convertLine(it, isBackground = true, duet, translationLanguage, romanizationLanguage)
            }
        }

        val metadata = buildList {
            fun addValues(key: String, values: List<String>) { if (values.isNotEmpty()) add(key to values) }
            addValues("musicName", result.metadata.title)
            addValues("artists", result.metadata.artist)
            addValues("album", result.metadata.album)
            addValues("isrc", result.metadata.isrc)
            addValues("ttmlAuthorGithub", result.metadata.authorIds)
            addValues("ttmlAuthorGithubLogin", result.metadata.authorNames)
            result.metadata.language?.let { add("language" to listOf(it)) }
            result.metadata.timingMode?.let { add("timingMode" to listOf(it)) }
            addValues("songwriters", result.metadata.songwriters)
            result.metadata.platformIds.forEach { (key, values) -> addValues(key, values) }
            result.metadata.rawProperties.forEach { (key, values) -> addValues(key, values) }
        }
        return AmllLyricResult(lines, metadata)
    }

    private fun convertLine(
        source: TtmlLyricBase,
        isBackground: Boolean,
        isDuet: Boolean,
        translationLanguage: String?,
        romanizationLanguage: String?,
    ): LyricLine {
        val words = if (source.words.isNotEmpty()) source.words.map {
            LyricWord(
                startTime = it.startTime,
                endTime = it.endTime,
                word = it.text + if (it.endsWithSpace) " " else "",
                obscene = it.obscene,
                emptyBeat = it.emptyBeat,
                ruby = it.ruby,
            )
        }.toMutableList() else mutableListOf(LyricWord(source.startTime, source.endTime, source.text))

        val translation = preferred(source.translations, translationLanguage)?.text.orEmpty()
        val roman = preferred(source.romanizations, romanizationLanguage)
        val romanText = if (roman?.words.isNullOrEmpty()) roman?.text.orEmpty() else ""
        roman?.words?.takeIf(List<TtmlSyllable>::isNotEmpty)?.let { alignRomanization(words, it) }

        return LyricLine(words, translation, romanText, source.startTime, source.endTime, isBackground, isDuet)
    }

    private fun preferred(values: List<SubLyricContent>, language: String?): SubLyricContent? =
        language?.let { target -> values.firstOrNull { it.language == target } } ?: values.firstOrNull()

    private fun alignRomanization(mainWords: MutableList<LyricWord>, romanWords: List<TtmlSyllable>) {
        var searchStart = 0
        mainWords.indices.forEach { i ->
            val main = mainWords[i]
            var best = -1
            var bestIou = 0.0
            var fast = false
            for (j in searchStart until romanWords.size) {
                val sub = romanWords[j]
                if (abs(main.startTime - sub.startTime) <= 2) {
                    mainWords[i] = main.copy(romanWord = sub.text)
                    searchStart = j + 1
                    fast = true
                    break
                }
                val intersection = max(0, min(main.endTime, sub.endTime) - max(main.startTime, sub.startTime))
                if (intersection > 0) {
                    val union = max(1, max(main.endTime, sub.endTime) - min(main.startTime, sub.startTime))
                    val iou = intersection.toDouble() / union
                    if (iou > bestIou) { bestIou = iou; best = j }
                }
                if (sub.startTime >= main.endTime) break
            }
            if (!fast && best >= 0 && bestIou >= 0.1) {
                mainWords[i] = main.copy(romanWord = romanWords[best].text)
                searchStart = best + 1
            }
        }
    }

    private fun normalizeText(value: String?, trim: Boolean = true): String {
        val normalized = value.orEmpty().replace(Regex("\\s+"), " ")
        return if (trim) normalized.trim() else normalized
    }

    private data class Sidecar(
        val translations: MutableList<SubLyricContent> = mutableListOf(),
        val romanizations: MutableList<SubLyricContent> = mutableListOf(),
        val bgTranslations: MutableList<SubLyricContent> = mutableListOf(),
        val bgRomanizations: MutableList<SubLyricContent> = mutableListOf(),
    )

    private data class ParsedState(
        var fullText: String = "",
        val words: MutableList<TtmlSyllable> = mutableListOf(),
        val translations: MutableList<SubLyricContent> = mutableListOf(),
        val romanizations: MutableList<SubLyricContent> = mutableListOf(),
        val bgTranslations: MutableList<SubLyricContent> = mutableListOf(),
        val bgRomanizations: MutableList<SubLyricContent> = mutableListOf(),
        var backgroundVocal: TtmlLyricBase? = null,
    )
}

private sealed interface XmlNode
private data class XmlText(val value: String) : XmlNode
private data class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: MutableList<XmlNode> = mutableListOf(),
) : XmlNode {
    val localName: String get() = name.substringAfterLast(':')
    val elementChildren: List<XmlElement> get() = children.filterIsInstance<XmlElement>()
    val textContent: String get() = buildString {
        children.forEach { append(if (it is XmlText) it.value else (it as XmlElement).textContent) }
    }
    fun attr(localName: String): String? = attributes.entries.firstOrNull {
        it.key.substringAfterLast(':') == localName
    }?.value
    fun descendants(localName: String): List<XmlElement> = buildList {
        elementChildren.forEach { child ->
            if (child.localName == localName) add(child)
            addAll(child.descendants(localName))
        }
    }
    fun firstDescendant(localName: String): XmlElement? = descendants(localName).firstOrNull()
}

/** Small namespace-tolerant XML tree reader; TTML needs no DTD processing. */
private class XmlTreeParser(private val source: String) {
    private var index = 0

    fun parse(): XmlElement {
        skipMisc()
        return parseElement()
    }

    private fun parseElement(): XmlElement {
        expect('<')
        val name = readName()
        val attrs = linkedMapOf<String, String>()
        while (true) {
            skipWhitespace()
            when {
                consume("/>") -> return XmlElement(name, attrs)
                consume(">") -> break
                else -> {
                    val attrName = readName()
                    skipWhitespace(); expect('='); skipWhitespace()
                    attrs[attrName] = decodeEntities(readQuoted())
                }
            }
        }
        val element = XmlElement(name, attrs)
        while (index < source.length) {
            when {
                source.startsWith("</", index) -> {
                    index += 2
                    val closing = readName()
                    require(closing == name) { "XML closing tag </$closing> does not match <$name>" }
                    skipWhitespace(); expect('>')
                    return element
                }
                source.startsWith("<!--", index) -> skipUntil("-->", 4)
                source.startsWith("<![CDATA[", index) -> {
                    index += 9
                    val end = source.indexOf("]]>", index).takeIf { it >= 0 } ?: error("Unclosed CDATA")
                    element.children += XmlText(source.substring(index, end)); index = end + 3
                }
                source.startsWith("<?", index) -> skipUntil("?>", 2)
                source[index] == '<' -> element.children += parseElement()
                else -> {
                    val end = source.indexOf('<', index).takeIf { it >= 0 } ?: source.length
                    element.children += XmlText(decodeEntities(source.substring(index, end)))
                    index = end
                }
            }
        }
        error("Unclosed element <$name>")
    }

    private fun skipMisc() {
        while (true) {
            skipWhitespace()
            when {
                source.startsWith("<?", index) -> skipUntil("?>", 2)
                source.startsWith("<!--", index) -> skipUntil("-->", 4)
                source.startsWith("<!DOCTYPE", index, ignoreCase = true) -> skipUntil(">", 9)
                else -> return
            }
        }
    }

    private fun skipUntil(marker: String, prefix: Int) {
        index += prefix
        val end = source.indexOf(marker, index).takeIf { it >= 0 } ?: error("Unclosed XML section")
        index = end + marker.length
    }

    private fun readName(): String {
        val start = index
        while (index < source.length && (source[index].isLetterOrDigit() || source[index] in ":_-.")) index++
        require(index > start) { "Expected XML name at $index" }
        return source.substring(start, index)
    }

    private fun readQuoted(): String {
        val quote = source.getOrNull(index)
        require(quote == '\'' || quote == '"') { "Expected quoted XML attribute at $index" }
        index++
        val end = source.indexOf(quote, index).takeIf { it >= 0 } ?: error("Unclosed XML attribute")
        return source.substring(index, end).also { index = end + 1 }
    }

    private fun decodeEntities(text: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos);")
        .replace(text) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val codePoint = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16)
                    else entity.drop(1).toIntOrNull()
                    codePoint?.let(::codePointString) ?: match.value
                }
            }
        }

    private fun codePointString(codePoint: Int): String = when {
        codePoint !in 0..0x10ffff -> ""
        codePoint <= 0xffff -> codePoint.toChar().toString()
        else -> {
            val value = codePoint - 0x10000
            "${(0xd800 + (value shr 10)).toChar()}${(0xdc00 + (value and 0x3ff)).toChar()}"
        }
    }

    private fun skipWhitespace() { while (index < source.length && source[index].isWhitespace()) index++ }
    private fun consume(value: String): Boolean = source.startsWith(value, index).also { if (it) index += value.length }
    private fun expect(char: Char) { require(source.getOrNull(index) == char) { "Expected '$char' at $index" }; index++ }
}
