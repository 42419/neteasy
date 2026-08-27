package top.yunov.neteasy

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.data.ThemeMode
import top.yunov.neteasy.data.UpdateChecker
import top.yunov.neteasy.data.UpdateUiState
import top.yunov.neteasy.data.isNewerVersion
import top.yunov.neteasy.ui.SettingsScreen
import top.yunov.neteasy.ui.theme.NeteasyTheme

/**
 * 设置页独立 Activity——从 MainActivity 拆出来，好让系统自己接管这个页面的
 * 进入/退出转场（而不是在 Compose 里手写动画模拟）。
 * 主题状态在这里自己管理（改了立即生效），MainActivity 那边靠 onResume 重新读一次
 * SettingsStore 来同步，不需要跨 Activity 传回调。
 *
 * 存储空间管理拆到了 StorageActivity（参考网易云官方存储空间页样式），这里只负责跳转。
 *
 * 检查更新：对比 GitHub Releases 最新 tag 和本地 versionName（两者本来就是同一套
 * git tag 生成的，见 build.gradle.kts），有更新则弹窗展示 changelog；下载前先并发测速
 * 直连 GitHub 和几个国内镜像反代，挑最快的节点；下载用系统 DownloadManager 存到
 * 公共 Downloads 目录（scoped storage 下唯一不需要额外权限就能写公共目录的方式）；
 * 安装完成后 UpdateReceiver（监听 ACTION_MY_PACKAGE_REPLACED）自动清掉安装包。
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsStore(this)
        val app = application as NeteasyApp
        val updateChecker = UpdateChecker()
        val currentVersion =
            try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "未知"
            } catch (e: Exception) {
                "未知"
            }

        setContent {
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }
            var defaultQuality by remember { mutableStateOf(settings.preferredAudioQuality) }
            var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
            // 下载完成后记下本地文件路径 + DownloadManager 的下载 id，点「安装」时要用
            var pendingApkPath by remember { mutableStateOf<String?>(null) }
            var pendingDownloadId by remember { mutableStateOf<Long?>(null) }
            val scope = rememberCoroutineScope()

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
                    onOpenStorage = { startActivity(Intent(this, StorageActivity::class.java)) },
                    currentVersion = currentVersion,
                    defaultQuality = defaultQuality,
                    onDefaultQualityChange = {
                        settings.preferredAudioQuality = it
                        defaultQuality = it
                        // 同步给播放器：作为之后“新开始播放歌曲”的首选音质
                        app.playerController.setDefaultQuality(it)
                    },
                    updateState = updateState,
                    onCheckUpdate = {
                        updateState = UpdateUiState.Checking
                        scope.launch {
                            val release = updateChecker.fetchLatestRelease()
                            updateState =
                                when {
                                    release == null -> UpdateUiState.Error("检查更新失败，请检查网络连接后重试")
                                    isNewerVersion(release.versionName, currentVersion) -> UpdateUiState.Available(release)
                                    else -> UpdateUiState.UpToDate
                                }
                        }
                    },
                    onStartDownload = {
                        val release = (updateState as? UpdateUiState.Available)?.release ?: return@SettingsScreen
                        updateState = UpdateUiState.PickingMirror
                        scope.launch {
                            val mirror = updateChecker.pickFastestMirror(release.apkDownloadUrl)
                            val downloadUrl = mirror.buildUrl(release.apkDownloadUrl)
                            val downloadFile =
                                File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    release.apkFileName
                                )
                            pendingApkPath = downloadFile.absolutePath

                            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                            val request =
                                DownloadManager
                                    .Request(Uri.parse(downloadUrl))
                                    .setTitle("Neteasy 更新")
                                    .setDescription("正在通过 ${mirror.label} 下载新版本")
                                    .setDestinationInExternalPublicDir(
                                        Environment.DIRECTORY_DOWNLOADS,
                                        release.apkFileName
                                    ).setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                    ).setAllowedOverMetered(true)
                                    .setMimeType("application/vnd.android.package-archive")
                            // 同名旧文件先删掉，避免 DownloadManager 因为目标文件已存在而拒绝下载
                            if (downloadFile.exists()) downloadFile.delete()
                            val id = dm.enqueue(request)
                            pendingDownloadId = id
                            updateState = UpdateUiState.Downloading(0f, mirror.label)

                            // 轮询下载进度，DownloadManager 没有 Compose 友好的 Flow API，
                            // 用 300ms 轮询足够跟手，也不会太费电
                            var finished = false
                            while (!finished) {
                                delay(300)
                                val cursor =
                                    withContext(Dispatchers.IO) {
                                        dm.query(DownloadManager.Query().setFilterById(id))
                                    }
                                cursor?.use {
                                    if (it.moveToFirst()) {
                                        val status =
                                            it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                        when (status) {
                                            DownloadManager.STATUS_SUCCESSFUL -> {
                                                finished = true
                                                updateState = UpdateUiState.ReadyToInstall
                                            }
                                            DownloadManager.STATUS_FAILED -> {
                                                finished = true
                                                updateState = UpdateUiState.Error("下载失败，请检查网络后重试")
                                            }
                                            else -> {
                                                val downloaded =
                                                    it.getLong(
                                                        it.getColumnIndexOrThrow(
                                                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                                        )
                                                    )
                                                val total =
                                                    it.getLong(
                                                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                                    )
                                                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                                                updateState = UpdateUiState.Downloading(progress, mirror.label)
                                            }
                                        }
                                    } else {
                                        // 查不到记录了（用户从系统通知栏手动取消下载等极端情况）
                                        finished = true
                                        updateState = UpdateUiState.Error("下载被中断，请重试")
                                    }
                                }
                            }
                        }
                    },
                    onInstallUpdate = {
                        val id = pendingDownloadId ?: return@SettingsScreen
                        val path = pendingApkPath
                        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                        val uri =
                            try {
                                dm.getUriForDownloadedFile(id)
                            } catch (e: Exception) {
                                null
                            }
                        if (uri == null) {
                            updateState = UpdateUiState.Error("安装包已丢失，请重新下载")
                            return@SettingsScreen
                        }
                        // 先记下路径，安装成功后（ACTION_MY_PACKAGE_REPLACED）UpdateReceiver 才知道删哪个文件
                        if (path != null) UpdateReceiver.rememberPendingApk(this, path)
                        val installIntent =
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        startActivity(installIntent)
                        updateState = UpdateUiState.Idle
                    },
                    onDismissUpdateDialog = { updateState = UpdateUiState.Idle },
                    onBack = { finish() }
                )
            }
        }
    }
}
