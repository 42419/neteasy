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
 * - 数据缓存 = cacheDir（含 Coil 图片缓存，音乐缓存单独摘出去不算在内）+ filesDir 除
 *   nodejs-project 外的全部（不挑着算，见下方注释）
 * - 音乐缓存 = 播放过的歌曲本地缓存（AudioCacheManager），容量上限/有效期在
 *   「设置 - 播放」里调，这里只管展示占用 + 手动清空
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
            var musicCacheBytes by remember { mutableLongStateOf(0L) }
            var essentialFilesBytes by remember { mutableLongStateOf(0L) }
            val scope = rememberCoroutineScope()

            suspend fun refresh() {
                withContext(Dispatchers.IO) {
                    val musicCache = app.playerController.audioCache.currentSizeBytes()
                    // cacheDir 里现在混了两种东西：Coil 图片缓存 + 咱们自己的歌曲音频缓存
                    // （AudioCacheManager，见 audio_cache 子目录）。歌曲缓存单独算一栏「音乐缓存」，
                    // 不能被算进「数据缓存」，不然总占用没错但两个数字对不上、清数据缓存那个按钮
                    // 也会把还没过期的歌曲缓存一起清掉
                    val cacheDirBytes = cacheDir.sizeRecursively() - musicCache
                    val nodeProjectBytes = File(filesDir, "nodejs-project").sizeRecursively()
                    val filesDirTotal = filesDir.sizeRecursively()
                    // filesDir 除 nodejs-project 之外的都算「数据缓存」——之前这里只挑了 tmp 和
                    // lyric_cache 两个「已知」的子目录来算，跟清理按钮那边维护的是两份分开的名单，
                    // 只要有一个漏加（或者以后又多了新的缓存目录）这个数字就会跟实际能清理的对不上，
                    // 显示的和真正删的不是同一个范围，导致清理后总有一部分数字清不掉。现在直接
                    // 「除 nodejs-project 外全部」，清理那边也改成同样口径的一刀切，两边天然一致
                    val filesDirClearableBytes = (filesDirTotal - nodeProjectBytes).coerceAtLeast(0L)
                    val apkBytes =
                        try {
                            File(applicationInfo.sourceDir).length()
                        } catch (e: Exception) {
                            0L
                        }

                    dataCacheBytes = cacheDirBytes + filesDirClearableBytes
                    musicCacheBytes = musicCache
                    essentialFilesBytes = nodeProjectBytes + apkBytes
                    appTotalBytes = dataCacheBytes + musicCacheBytes + essentialFilesBytes

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
                    musicCacheBytes = musicCacheBytes,
                    essentialFilesBytes = essentialFilesBytes,
                    onClearDataCache = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val loader = applicationContext.imageLoader
                                loader.memoryCache?.clear()
                                // Coil 的 diskCache.clear() 只是把缓存条目清空，索引用的 journal 文件
                                // 不会立刻收缩到 0（DiskLruCache 这类实现的 journal 记录了增删改历史，
                                // 只在达到阈值时才整理重写，clear() 不会主动触发）——之前这里特意把
                                // Coil 的目录从下面的删除范围里摘出去，只用它自己的 API 清，结果就是
                                // 这个 journal 文件一直留在那儿，「数据缓存」永远清不到 0（用 adb 导出
                                // 目录清单实测确认过，cacheDir 下就剩这一个 journal 文件）。
                                // 直接把整个目录也删掉，Coil 会在下次要写缓存时自己重新建，没有副作用。
                                loader.diskCache?.clear()
                                cacheDir.listFiles()?.forEach { f ->
                                    // audio_cache 是歌曲缓存（AudioCacheManager 自己的目录），单独有
                                    // 「音乐缓存」那张卡片和自己的清空按钮，这里不连带清掉
                                    if (f.name != "audio_cache") f.deleteRecursively()
                                }
                                // 跟上面 refresh() 的 filesDirClearableBytes 口径保持一致：filesDir
                                // 除 nodejs-project 外全部删掉（覆盖 tmp、LyricRepository 的
                                // lyric_cache，以及以后任何新加的缓存目录），不用再挨个记名单——
                                // 之前显示的「数据缓存」数字和这里真正删除的范围是两份分开维护的
                                // 名单，只要漏加一个就会导致清理后总剩几 KB 清不掉，现在两边由
                                // 同一个规则（除 nodejs-project 外全删）决定，不可能再对不上
                                filesDir.listFiles()?.forEach { f ->
                                    if (f.name != "nodejs-project") f.deleteRecursively()
                                }
                                app.repository.clearPlaylistCache()
                            }
                            refresh()
                        }
                    },
                    onClearMusicCache = {
                        scope.launch {
                            withContext(Dispatchers.IO) { app.playerController.audioCache.clearAll() }
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
