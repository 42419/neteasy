package top.yunov.neteasy

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import top.yunov.neteasy.bridge.NodeService
import top.yunov.neteasy.data.ApiClient
import top.yunov.neteasy.data.LikeRepository
import top.yunov.neteasy.data.LyricRepository
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.ui.theme.CoverThemeController
import top.yunov.neteasy.util.RefreshRate

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
    val lyricRepository: LyricRepository by lazy { LyricRepository(this, repository) }
    val likeRepository: LikeRepository by lazy { LikeRepository(appScope, apiClient, repository) }
    val playerController: PlayerController by lazy {
        PlayerController(
            scope = appScope,
            repository = repository,
            appContext = this,
            lyricRepository = lyricRepository,
            initialQuality = SettingsStore(this).preferredAudioQuality
        )
    }
    /** 封面动态取色：跟着 playerController 当前歌曲变，见类注释 */
    val coverThemeController: CoverThemeController by lazy {
        CoverThemeController(scope = appScope, appContext = this, player = playerController)
    }

    override fun onCreate() {
        super.onCreate()
        // minSdk 29，startForegroundService 恒可用，不用再判断 Build.VERSION
        startForegroundService(Intent(this, NodeService::class.java))

        // 高刷新率：所有 Activity（含独立 Activity）创建时统一向系统申请
        // 当前分辨率下的最高显示模式，避免系统省电策略把滚动/动画降回 60Hz
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                RefreshRate.applyHighest(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
