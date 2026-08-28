package top.yunov.neteasy.data

import android.content.Context

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

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_FOLLOW_COVER_COLOR = "follow_cover_color"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AUDIO_QUALITY = "audio_quality"
    }
}
