package top.yunov.neteasy

import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil.imageLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.ui.screens.StorageScreen
import top.yunov.neteasy.ui.theme.NeteasyThemedScreen

/**
 * 存储空间独立 Activity——参考网易云音乐官方「存储空间」页样式重做，
 * 把 App 实际占用的磁盘空间摊开成三块：
 * - 数据缓存 = cacheDir 全部（含 Coil 图片缓存）+ filesDir/tmp（后端接口临时数据）+
 *   filesDir 里除 nodejs-project 外的其余杂项，全部可安全清除
 * - 音乐缓存 = 恒为 0（本 App 不落盘缓存歌曲，纯流式播放），仅作说明
 * - 必要文件 = filesDir/nodejs-project（内嵌后端程序）+ APK 安装包本身，运行必需不可清除
 *
 * 顶部占用条对比的是整机存储（StatFs），不是单纯 App 内部数字，跟系统「设置」里
 * 看到的口径一致。
 */
class StorageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NeteasyApp
        setContent {
            var appTotalBytes by remember { mutableLongStateOf(0L) }
            var deviceTotalBytes by remember { mutableLongStateOf(0L) }
            var deviceFreeBytes by remember { mutableLongStateOf(0L) }
            var dataCacheBytes by remember { mutableLongStateOf(0L) }
            var essentialFilesBytes by remember { mutableLongStateOf(0L) }
            val scope = rememberCoroutineScope()

            suspend fun refresh() {
                withContext(Dispatchers.IO) {
                    val cacheDirBytes = cacheDir.sizeRecursively()
                    val tempBytes = File(filesDir, "tmp").sizeRecursively()
                    val nodeProjectBytes = File(filesDir, "nodejs-project").sizeRecursively()
                    val filesDirTotal = filesDir.sizeRecursively()
                    // filesDir 里除了 nodejs-project、tmp 之外剩下的杂项，归进数据缓存兜底
                    val otherFilesDirBytes = (filesDirTotal - nodeProjectBytes - tempBytes).coerceAtLeast(0L)
                    val apkBytes =
                        try {
                            File(applicationInfo.sourceDir).length()
                        } catch (e: Exception) {
                            0L
                        }

                    dataCacheBytes = cacheDirBytes + tempBytes + otherFilesDirBytes
                    essentialFilesBytes = nodeProjectBytes + apkBytes
                    appTotalBytes = dataCacheBytes + essentialFilesBytes

                    val statFs = StatFs(Environment.getDataDirectory().path)
                    deviceTotalBytes = statFs.totalBytes
                    deviceFreeBytes = statFs.availableBytes
                }
            }
            LaunchedEffect(Unit) { refresh() }

            NeteasyThemedScreen {
                StorageScreen(
                    appTotalBytes = appTotalBytes,
                    deviceTotalBytes = deviceTotalBytes,
                    deviceUsedByOthersBytes = (deviceTotalBytes - deviceFreeBytes - appTotalBytes).coerceAtLeast(0L),
                    deviceFreeBytes = deviceFreeBytes,
                    dataCacheBytes = dataCacheBytes,
                    musicCacheBytes = 0L,
                    essentialFilesBytes = essentialFilesBytes,
                    onClearDataCache = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val loader = applicationContext.imageLoader
                                // Coil 自己的磁盘/内存缓存交给它自己的 API 清，不绕过内部索引直接删文件
                                loader.memoryCache?.clear()
                                val coilDirName = loader.diskCache?.directory?.name
                                loader.diskCache?.clear()
                                cacheDir.listFiles()?.forEach { f ->
                                    if (f.name != coilDirName) f.deleteRecursively()
                                }
                                File(filesDir, "tmp").listFiles()?.forEach { it.deleteRecursively() }
                                app.repository.clearPlaylistCache()
                            }
                            refresh()
                        }
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

/** 递归计算文件/目录占用的字节数；不存在时返回 0 */
private fun File.sizeRecursively(): Long {
    if (!exists()) return 0L
    return if (isFile) length() else (listFiles()?.sumOf { it.sizeRecursively() } ?: 0L)
}
