package top.yunov.neteasy

import android.app.Application
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import top.yunov.neteasy.data.ApiClient
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.player.PlayerController

/**
 * 应用入口：冷启动时拉起 Node 后端前台服务。
 *
 * 网络层（[ApiClient]/[NcmRepository]）和播放器（[PlayerController]）提升为 App 级单例，
 * 不再跟着 Activity 的 remember{} 走——这样切后台、Activity 重建都不会打断播放，
 * 新增的 [top.yunov.neteasy.player.PlaybackService]（播放前台服务/通知栏控制）
 * 也能拿到跟 UI 完全同一份播放状态。
 */
class NeteasyApp : Application() {
    /** App 生命周期协程作用域：不随 Activity 销毁而取消，播放才能在切后台时继续跑 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val apiClient: ApiClient by lazy { ApiClient(this) }
    val repository: NcmRepository by lazy { NcmRepository(apiClient) }
    val playerController: PlayerController by lazy {
        PlayerController(
            scope = appScope,
            repository = repository,
            appContext = this,
            initialQuality = SettingsStore(this).preferredAudioQuality
        )
    }

    override fun onCreate() {
        super.onCreate()
        val intent = Intent(this, NodeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
