package top.yunov.neteasy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.ui.screens.LyricSettingsScreen
import top.yunov.neteasy.ui.theme.NeteasyThemedScreen

/**
 * 歌词渲染细节设置——从 SettingsActivity 跳转进来的独立页面。
 * 每一项都直接读写 SettingsStore，改完当场持久化；PlayerOverlay 在 onResume 时
 * 重新读一次，回到播放页/首页就能看到最新效果，不需要跨 Activity 回调。
 */
class LyricSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsStore(this)
        setContent {
            var showTranslation by remember { mutableStateOf(settings.lyricShowTranslation) }
            var showLineRomanization by remember { mutableStateOf(settings.lyricShowLineRomanization) }
            var showWordRomanization by remember { mutableStateOf(settings.lyricShowWordRomanization) }
            var alignPosition by remember { mutableStateOf(settings.lyricAlignPosition) }
            var wordFadeWidth by remember { mutableStateOf(settings.lyricWordFadeWidth) }
            var inactiveAlpha by remember { mutableStateOf(settings.lyricInactiveAlpha) }
            var enableBlur by remember { mutableStateOf(settings.lyricEnableBlur) }
            var enableScale by remember { mutableStateOf(settings.lyricEnableScale) }
            var springPreset by remember { mutableStateOf(settings.lyricSpringPreset) }

            NeteasyThemedScreen {
                LyricSettingsScreen(
                    showTranslation = showTranslation,
                    onShowTranslationChange = {
                        settings.lyricShowTranslation = it
                        showTranslation = it
                    },
                    showLineRomanization = showLineRomanization,
                    onShowLineRomanizationChange = {
                        settings.lyricShowLineRomanization = it
                        showLineRomanization = it
                    },
                    showWordRomanization = showWordRomanization,
                    onShowWordRomanizationChange = {
                        settings.lyricShowWordRomanization = it
                        showWordRomanization = it
                    },
                    alignPosition = alignPosition,
                    onAlignPositionChange = {
                        settings.lyricAlignPosition = it
                        alignPosition = it
                    },
                    wordFadeWidth = wordFadeWidth,
                    onWordFadeWidthChange = {
                        settings.lyricWordFadeWidth = it
                        wordFadeWidth = it
                    },
                    inactiveAlpha = inactiveAlpha,
                    onInactiveAlphaChange = {
                        settings.lyricInactiveAlpha = it
                        inactiveAlpha = it
                    },
                    enableBlur = enableBlur,
                    onEnableBlurChange = {
                        settings.lyricEnableBlur = it
                        enableBlur = it
                    },
                    enableScale = enableScale,
                    onEnableScaleChange = {
                        settings.lyricEnableScale = it
                        enableScale = it
                    },
                    springPreset = springPreset,
                    onSpringPresetChange = {
                        settings.lyricSpringPreset = it
                        springPreset = it
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
