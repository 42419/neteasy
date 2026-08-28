package top.yunov.neteasy.data

import android.content.Context
import top.met6.amll.AmllSpringParams

/** 深色模式选项：跟随系统 / 强制浅色 / 强制深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 音质选项：对应 /song/url/v1 的 level 参数（严格按 api-enhanced 实际类型定义 SoundQualityType 取值）。
 * 注：该枚举里没有「较高」（higher）——新版 song/url/v1 已不支持这一档，
 * 与网易云新版客户端界面保持一致（只有 标准/极高/无损/Hi-Res）。
 * jyeffect（高清环绕声）/ sky（沉浸环绕声）/ jymaster（超清母带）三档虽然 level 合法，
 * 但 /song/detail 没有对应字段能判断某首歌是否存在该音质，为避免瞎列不存在的选项，这里不收录。
 * lossless / hires 通常需要黑胶 VIP 权限，账号权限不足时服务端会自动降级返回可播放的最高音质。
 */
enum class AudioQuality(val level: String, val label: String) {
    STANDARD("standard", "标准"),
    EXHIGH("exhigh", "极高"),
    LOSSLESS("lossless", "无损"),
    HIRES("hires", "Hi-Res")
}

/**
 * 歌词滚动的“手感”预设——覆盖 AMLL 原本按行间隔动态计算的弹簧参数（[top.met6.amll.lyricScrollSpringPolicy]）。
 * ADAPTIVE 即不覆盖，沿用原版按歌词密度自适应的效果；其余几档给一个固定观感，供不想深究物理参数的人直接选。
 */
enum class LyricSpringPreset(val label: String) {
    ADAPTIVE("默认（自适应）"),
    SMOOTH("柔和"),
    RESPONSIVE("跟手"),
    JELLO("弹性"),
    HEAVY("沉稳"),
    NO_BOUNCE("无回弹");

    /** 对应的固定弹簧参数；ADAPTIVE 返回 null，表示交回 [top.met6.amll.lyricScrollSpringPolicy] 动态计算。 */
    fun toSpringParams(): AmllSpringParams? =
        when (this) {
            ADAPTIVE -> null
            SMOOTH -> AmllSpringParams(mass = 0.9, stiffness = 140.0, damping = 22.4)
            RESPONSIVE -> AmllSpringParams(mass = 0.9, stiffness = 260.0, damping = 26.0)
            JELLO -> AmllSpringParams(mass = 1.0, stiffness = 180.0, damping = 12.0)
            HEAVY -> AmllSpringParams(mass = 1.6, stiffness = 90.0, damping = 25.2)
            NO_BOUNCE -> AmllSpringParams(mass = 0.9, stiffness = 200.0, damping = 26.8, soft = true)
        }
}

/**
 * 用户设置（SharedPreferences 持久化）：
 * - themeMode：深色模式（跟随系统 / 浅色 / 深色）。默认跟随系统。
 * - dynamicColor：是否跟随系统动态取色（Material You）。默认开启；
 *   关闭时回退到网易云品牌红配色。
 * - followCoverColor：是否跟随当前播放歌曲封面取色（覆盖 dynamicColor）。默认关闭
 *   （opt-in——这是个视觉冲击比较大的效果，不默认打开，怕有人不喜欢颜色跟着换歌跳来跳去）。
 * - preferredAudioQuality：音质偏好，仅作为「新开始播放一首歌时优先尝试的音质」，
 *   实际每首歌可选的音质列表以该歌曲 /song/detail 返回的 l/h/sq/hr 字段为准
 *   （在 Minibar 的音质按钮里现查现列，不在这里写死）。默认「极高」。
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ncm_settings", Context.MODE_PRIVATE)

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    var followCoverColor: Boolean
        get() = prefs.getBoolean(KEY_FOLLOW_COVER_COLOR, false)
        set(value) = prefs.edit().putBoolean(KEY_FOLLOW_COVER_COLOR, value).apply()

    var themeMode: ThemeMode
        get() =
            when (prefs.getString(KEY_THEME_MODE, null)) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name.lowercase()).apply()

    var preferredAudioQuality: AudioQuality
        get() {
            val level = prefs.getString(KEY_AUDIO_QUALITY, null)
            return AudioQuality.entries.firstOrNull { it.level == level } ?: AudioQuality.EXHIGH
        }
        set(value) = prefs.edit().putString(KEY_AUDIO_QUALITY, value.level).apply()

    // ---- 歌词渲染细节（AppleMusicLyricPlayerStyle 的可调部分）----
    // 数值型默认值均与目前 NowPlayingScreen 里写死的效果保持一致，
    // 加这批设置不改变任何人升级后的默认观感，只是把它们从写死变成可调。

    /** 当前播放行在歌词视口中的垂直锚点（0=贴顶，1=贴底）。 */
    var lyricAlignPosition: Float
        get() = prefs.getFloat(KEY_LYRIC_ALIGN_POSITION, 0.2f)
        set(value) = prefs.edit().putFloat(KEY_LYRIC_ALIGN_POSITION, value.coerceIn(0.05f, 0.9f)).apply()

    /** 逐字高亮遮罩的渐变过渡宽度，越大过渡越柔和。 */
    var lyricWordFadeWidth: Float
        get() = prefs.getFloat(KEY_LYRIC_WORD_FADE_WIDTH, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_LYRIC_WORD_FADE_WIDTH, value.coerceIn(0.05f, 1f)).apply()

    /** 非当前行是否启用模糊效果。 */
    var lyricEnableBlur: Boolean
        get() = prefs.getBoolean(KEY_LYRIC_ENABLE_BLUR, true)
        set(value) = prefs.edit().putBoolean(KEY_LYRIC_ENABLE_BLUR, value).apply()

    /** 播放中非当前行是否做轻微缩放呼吸效果。 */
    var lyricEnableScale: Boolean
        get() = prefs.getBoolean(KEY_LYRIC_ENABLE_SCALE, true)
        set(value) = prefs.edit().putBoolean(KEY_LYRIC_ENABLE_SCALE, value).apply()

    /** 非当前行的不透明度（0=几乎看不见，1=和当前行一样亮）。 */
    var lyricInactiveAlpha: Float
        get() = prefs.getFloat(KEY_LYRIC_INACTIVE_ALPHA, 0.3f)
        set(value) = prefs.edit().putFloat(KEY_LYRIC_INACTIVE_ALPHA, value.coerceIn(0f, 1f)).apply()

    /** 是否显示翻译行。 */
    var lyricShowTranslation: Boolean
        get() = prefs.getBoolean(KEY_LYRIC_SHOW_TRANSLATION, true)
        set(value) = prefs.edit().putBoolean(KEY_LYRIC_SHOW_TRANSLATION, value).apply()

    /** 是否显示逐行音译（如日语罗马音整行版）。 */
    var lyricShowLineRomanization: Boolean
        get() = prefs.getBoolean(KEY_LYRIC_SHOW_LINE_ROMANIZATION, true)
        set(value) = prefs.edit().putBoolean(KEY_LYRIC_SHOW_LINE_ROMANIZATION, value).apply()

    /** 是否显示逐词音译（TTML ruby/roman word）。 */
    var lyricShowWordRomanization: Boolean
        get() = prefs.getBoolean(KEY_LYRIC_SHOW_WORD_ROMANIZATION, true)
        set(value) = prefs.edit().putBoolean(KEY_LYRIC_SHOW_WORD_ROMANIZATION, value).apply()

    /** 歌词滚动的弹簧手感预设。 */
    var lyricSpringPreset: LyricSpringPreset
        get() {
            val name = prefs.getString(KEY_LYRIC_SPRING_PRESET, null)
            return LyricSpringPreset.entries.firstOrNull { it.name == name } ?: LyricSpringPreset.ADAPTIVE
        }
        set(value) = prefs.edit().putString(KEY_LYRIC_SPRING_PRESET, value.name).apply()

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_FOLLOW_COVER_COLOR = "follow_cover_color"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AUDIO_QUALITY = "audio_quality"
        const val KEY_LYRIC_ALIGN_POSITION = "lyric_align_position"
        const val KEY_LYRIC_WORD_FADE_WIDTH = "lyric_word_fade_width"
        const val KEY_LYRIC_ENABLE_BLUR = "lyric_enable_blur"
        const val KEY_LYRIC_ENABLE_SCALE = "lyric_enable_scale"
        const val KEY_LYRIC_INACTIVE_ALPHA = "lyric_inactive_alpha"
        const val KEY_LYRIC_SHOW_TRANSLATION = "lyric_show_translation"
        const val KEY_LYRIC_SHOW_LINE_ROMANIZATION = "lyric_show_line_romanization"
        const val KEY_LYRIC_SHOW_WORD_ROMANIZATION = "lyric_show_word_romanization"
        const val KEY_LYRIC_SPRING_PRESET = "lyric_spring_preset"
    }
}

/** 按显示设置裁剪一行歌词——把不想显示的翻译/音译字段清空，不改动渲染器本身。 */
fun top.met6.amll.LyricLine.filteredForDisplay(
    showTranslation: Boolean,
    showLineRomanization: Boolean,
    showWordRomanization: Boolean,
): top.met6.amll.LyricLine {
    if (showTranslation && showLineRomanization && showWordRomanization) return this
    return copy(
        translatedLyric = if (showTranslation) translatedLyric else "",
        romanLyric = if (showLineRomanization) romanLyric else "",
        words = if (showWordRomanization) words else words.map { it.copy(romanWord = "") },
    )
}
