package top.met6.amll

import androidx.compose.runtime.Immutable

@Immutable
data class LyricWordBase(
    val startTime: Long,
    val endTime: Long,
    val word: String,
)

@Immutable
data class LyricWord(
    val startTime: Long,
    val endTime: Long,
    val word: String,
    val romanWord: String = "",
    val obscene: Boolean = false,
    val emptyBeat: Int? = null,
    val ruby: List<LyricWordBase> = emptyList(),
)

@Immutable
data class LyricLine(
    val words: List<LyricWord>,
    val translatedLyric: String = "",
    val romanLyric: String = "",
    val startTime: Long,
    val endTime: Long,
    val isBG: Boolean = false,
    val isDuet: Boolean = false,
) {
    val text: String get() = words.joinToString(separator = "") { it.word }
}

@Immutable
data class AmllLyricResult(
    val lines: List<LyricLine>,
    val metadata: List<Pair<String, List<String>>> = emptyList(),
)

@Immutable
data class TtmlSyllable(
    val text: String,
    val startTime: Long,
    val endTime: Long,
    val endsWithSpace: Boolean = false,
    val ruby: List<LyricWordBase> = emptyList(),
    val obscene: Boolean = false,
    val emptyBeat: Int? = null,
)

@Immutable
data class SubLyricContent(
    val text: String,
    val language: String? = null,
    val words: List<TtmlSyllable> = emptyList(),
)

@Immutable
data class TtmlLyricBase(
    val text: String,
    val startTime: Long,
    val endTime: Long,
    val words: List<TtmlSyllable> = emptyList(),
    val translations: List<SubLyricContent> = emptyList(),
    val romanizations: List<SubLyricContent> = emptyList(),
    val backgroundVocal: TtmlLyricBase? = null,
)

@Immutable
data class TtmlLyricLine(
    val id: String? = null,
    val agentId: String? = null,
    val songPart: String? = null,
    val blockIndex: Int? = null,
    val content: TtmlLyricBase,
)

@Immutable
data class TtmlAgent(
    val id: String,
    val name: String? = null,
    val type: String? = null,
)

@Immutable
data class TtmlMetadata(
    val language: String? = null,
    val timingMode: String? = null,
    val songwriters: List<String> = emptyList(),
    val title: List<String> = emptyList(),
    val artist: List<String> = emptyList(),
    val album: List<String> = emptyList(),
    val isrc: List<String> = emptyList(),
    val authorIds: List<String> = emptyList(),
    val authorNames: List<String> = emptyList(),
    val agents: Map<String, TtmlAgent> = emptyMap(),
    val platformIds: Map<String, List<String>> = emptyMap(),
    val rawProperties: Map<String, List<String>> = emptyMap(),
)

@Immutable
data class TtmlResult(
    val metadata: TtmlMetadata,
    val lines: List<TtmlLyricLine>,
)
