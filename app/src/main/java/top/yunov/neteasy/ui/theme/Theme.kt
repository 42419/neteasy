package top.yunov.neteasy.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme
import top.yunov.neteasy.NeteasyApp
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.data.ThemeMode

private val LightColorScheme =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        secondary = LightSecondary,
        onSecondary = LightOnSecondary,
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        tertiary = LightTertiary,
        onTertiary = LightOnTertiary,
        tertiaryContainer = LightTertiaryContainer,
        onTertiaryContainer = LightOnTertiaryContainer,
        error = LightError,
        onError = LightOnError,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        background = LightBackground,
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceContainerLowest = LightSurfaceContainerLowest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiary = DarkTertiary,
        onTertiary = DarkOnTertiary,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceContainerLowest = DarkSurfaceContainerLowest,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant
    )

/**
 * MD3 Expressive 主题入口：
 * 使用官方 [MaterialExpressiveTheme] + 官方 expressive [MotionScheme]，
 * 让 21 个内置 Material 组件（按钮、导航栏、卡片等）自动获得 spring 物理动效，
 * 与自定义组件的 [ExpressiveMotion] token 风格一致。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeteasyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    /**
     * 「封面取色」种子色：非空时优先级最高，覆盖 [dynamicColor]/静态品牌配色——
     * 跟着当前播放歌曲的封面变，见 [CoverThemeController]。
     */
    coverSeedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme =
        if (coverSeedColor != null) {
            // 种子色本身先做一次动画插值，再喂给 HCT 配色算法：换歌时不是配色瞬间跳变，
            // 而是从旧种子色平滑过渡到新种子色，插值过程中的每一帧都基于同一套色彩空间
            // 重新生成一整套自洽的配色，不会出现「某个颜色跳变了、其它颜色没跟上」的割裂感
            val animatedSeed by animateColorAsState(
                targetValue = coverSeedColor,
                animationSpec = tween(600),
                label = "coverSeedColor"
            )
            rememberDynamicColorScheme(seedColor = animatedSeed, isDark = darkTheme)
        } else {
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
        }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

/**
 * 独立 Activity（设置/登录/搜索/歌单详情，拆出来是为了让系统接管这几个页面之间的
 * 原生转场动画）套主题用的便捷封装：读一次已持久化的设置直接应用，本身不提供
 * 修改入口——真要改深色模式/动态取色去设置页改，改完存到 SharedPreferences，
 * 这几个页面下次单独启动时自然读到最新值，不需要跨 Activity 实时同步。
 *
 * 「封面取色」是例外：它不是「进这个页面时读一次」的静态设置，而是要跟着播放器
 * 实时变的（听歌听一半切到搜索页，颜色不能停在切页面那一刻），所以单独从
 * App 级单例 [top.yunov.neteasy.NeteasyApp.coverThemeController] 订阅，
 * 不受「不实时同步」这条注释的约束。
 */
@Composable
fun NeteasyThemedScreen(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val darkTheme =
        when (settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val coverSeedColor =
        if (settings.followCoverColor) {
            val app = context.applicationContext as NeteasyApp
            val seed by app.coverThemeController.seedColor.collectAsState()
            seed
        } else {
            null
        }
    NeteasyTheme(
        darkTheme = darkTheme,
        dynamicColor = settings.dynamicColor,
        coverSeedColor = coverSeedColor
    ) {
        content()
    }
}
