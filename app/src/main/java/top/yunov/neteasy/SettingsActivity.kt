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
import java.io.File
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
 * 缓存管理：把 App 实际占用的磁盘空间完整摊开展示，不只挑几项——
 * - 图片缓存：Coil 磁盘缓存 + 内存缓存的真实字节数
 * - 歌单数据缓存：NcmRepository（App 级单例）里缓存了多少个歌单
 * - 临时文件：filesDir/tmp（内嵌 Node 后端接口请求过程中产生的临时数据，
 *   比如上传类接口的临时文件，实际用不到但代码路径存在就有可能写进来）
 * - 其他缓存：cacheDir 里除 Coil 自己那份之外剩下的全部，兜底项，
 *   防止有什么没被专门枚举到的缓存源被漏掉
 * - 后端程序文件：filesDir/nodejs-project，App 运行必需，只读展示不提供清除
 *   （清了下次启动 Node 后端会直接起不来，除非发生了一次 APK 更新触发重新复制）
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
            var tempFilesBytes by remember { mutableLongStateOf(0L) }
            var otherCacheBytes by remember { mutableLongStateOf(0L) }
            var backendFilesBytes by remember { mutableLongStateOf(0L) }
            val scope = rememberCoroutineScope()

            suspend fun refreshStorageSizes() {
                withContext(Dispatchers.IO) {
                    val loader = applicationContext.imageLoader
                    val coilDiskBytes = loader.diskCache?.size ?: 0L
                    val coilMemBytes = loader.memoryCache?.size?.toLong() ?: 0L
                    val cacheDirTotal = cacheDir.sizeRecursively()
                    imageCacheBytes = coilDiskBytes + coilMemBytes
                    tempFilesBytes = File(filesDir, "tmp").sizeRecursively()
                    backendFilesBytes = File(filesDir, "nodejs-project").sizeRecursively()
                    // cacheDir 里除了 Coil 自己那份之外剩下的全部，兜底不遗漏
                    otherCacheBytes = (cacheDirTotal - coilDiskBytes).coerceAtLeast(0L)
                }
            }
            LaunchedEffect(Unit) { refreshStorageSizes() }

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
                    tempFilesBytes = tempFilesBytes,
                    otherCacheBytes = otherCacheBytes,
                    backendFilesBytes = backendFilesBytes,
                    onClearImageCache = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val loader = applicationContext.imageLoader
                                loader.memoryCache?.clear()
                                loader.diskCache?.clear()
                            }
                            refreshStorageSizes()
                        }
                    },
                    onClearPlaylistCache = {
                        app.repository.clearPlaylistCache()
                        playlistCacheCount = 0
                    },
                    onClearTempFiles = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                File(filesDir, "tmp").listFiles()?.forEach { it.deleteRecursively() }
                            }
                            refreshStorageSizes()
                        }
                    },
                    onClearOtherCache = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // 只清 cacheDir 里不属于 Coil 的部分，Coil 自己那份交给它自己的
                                // clear() 管（上面「图片缓存」那一项），不手动碰它的目录，
                                // 避免绕过 Coil 内部索引直接删文件导致状态不一致
                                val coilDirName = applicationContext.imageLoader.diskCache?.directory?.name
                                cacheDir.listFiles()?.forEach { f ->
                                    if (f.name != coilDirName) {
                                        f.deleteRecursively()
                                    }
                                }
                            }
                            refreshStorageSizes()
                        }
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

/** 递归计算文件/目录占用的字节数；目录不存在时返回 0 */
private fun File.sizeRecursively(): Long {
    if (!exists()) return 0L
    return if (isFile) length() else (listFiles()?.sumOf { it.sizeRecursively() } ?: 0L)
}
