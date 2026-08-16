package top.yunov.neteasy

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.data.ThemeMode
import top.yunov.neteasy.ui.SettingsScreen
import top.yunov.neteasy.ui.theme.NeteasyTheme

/**
 * 设置页独立 Activity——从 MainActivity 拆出来，好让系统自己接管这个页面的
 * 进入/退出转场（而不是在 Compose 里手写动画模拟）。
 * 主题状态在这里自己管理（改了立即生效），MainActivity 那边靠 onResume 重新读一次
 * SettingsStore 来同步，不需要跨 Activity 传回调。
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsStore(this)
        setContent {
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }
            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            NeteasyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = {
                        settings.themeMode = it
                        themeMode = it
                    },
                    dynamicColor = dynamicColor,
                    dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onDynamicColorChange = {
                        settings.dynamicColor = it
                        dynamicColor = it
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
