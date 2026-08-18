package top.yunov.neteasy

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.data.ThemeMode
import top.yunov.neteasy.ui.SettingsScreen
import top.yunov.neteasy.ui.theme.NeteasyTheme

/**
 * 设置页独立 Activity——从 MainActivity 拆出来，好让系统自己接管这个页面的
 * 进入/退出转场（而不是在 Compose 里手写动画模拟）。
 * 主题状态在这里自己管理（改了立即生效），MainActivity 那边靠 onResume 重新读一次
 * SettingsStore 来同步，不需要跨 Activity 传回调。
 *
 * 缓存管理：图片缓存读的是 Coil 的磁盘缓存 + 内存缓存大小（真实字节数，来自
 * applicationContext.imageLoader）；歌单数据缓存读的是 NcmRepository（App 级单例）
 * 里缓存了多少个歌单，两者都能各自单独清除。
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsStore(this)
        val app = application as NeteasyApp
        setContent {
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }
            var imageCacheBytes by remember { mutableLongStateOf(0L) }
            var playlistCacheCount by remember { mutableIntStateOf(app.repository.playlistCacheCount) }
            val scope = rememberCoroutineScope()

            suspend fun refreshImageCacheSize() {
                imageCacheBytes =
                    withContext(Dispatchers.IO) {
                        val loader = applicationContext.imageLoader
                        (loader.diskCache?.size ?: 0L) + (loader.memoryCache?.size?.toLong() ?: 0L)
                    }
            }
            LaunchedEffect(Unit) { refreshImageCacheSize() }

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
                    imageCacheBytes = imageCacheBytes,
                    playlistCacheCount = playlistCacheCount,
                    onClearImageCache = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val loader = applicationContext.imageLoader
                                loader.memoryCache?.clear()
                                loader.diskCache?.clear()
                            }
                            refreshImageCacheSize()
                        }
                    },
                    onClearPlaylistCache = {
                        app.repository.clearPlaylistCache()
                        playlistCacheCount = 0
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
