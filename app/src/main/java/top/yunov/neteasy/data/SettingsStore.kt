package top.yunov.neteasy.data

import android.content.Context

/** 深色模式选项：跟随系统 / 强制浅色 / 强制深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 用户设置（SharedPreferences 持久化）：
 * - themeMode：深色模式（跟随系统 / 浅色 / 深色）。默认跟随系统。
 * - dynamicColor：是否跟随系统动态取色（Material You）。默认开启；
 *   关闭时回退到网易云品牌红配色。
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ncm_settings", Context.MODE_PRIVATE)

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    var themeMode: ThemeMode
        get() =
            when (prefs.getString(KEY_THEME_MODE, null)) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name.lowercase()).apply()

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
