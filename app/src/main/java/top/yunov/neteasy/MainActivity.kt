package top.yunov.neteasy

import android.app.Activity
import android.content.Intent
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.data.ThemeMode
import top.yunov.neteasy.ui.HomeScreen
import top.yunov.neteasy.ui.MainNavigationBar
import top.yunov.neteasy.ui.NavState
import top.yunov.neteasy.ui.PlayerAwareContent
import top.yunov.neteasy.ui.ProfileScreen
import top.yunov.neteasy.ui.theme.ExpressiveMotion
import top.yunov.neteasy.ui.theme.NeteasyTheme

/**
 * M3 极简播放器：
 * - 首页（banner + 推荐歌单）/ 我的
 * - 设置 / 登录 / 搜索 / 歌单详情拆成独立 Activity（见各自文件），交给系统接管
 *   页面间的原生转场动画，这里只负责 startActivity 跳转
 * - 展开播放页 / 播放队列面板仍在 Compose 内实现（不是“跳转到新地方”，是同一播放器的
 *   展开/收起，语义上不适合拆成 Activity）
 * - 底部迷你播放栏：封面 + 歌名 + 播放/暂停
 * - 所有网络请求走本地 Node 后端（127.0.0.1:19800）
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 设置状态提升到主题之上：切换深色模式/动态取色立即生效（SharedPreferences 持久化）
            val settings = remember { SettingsStore(applicationContext) }
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            var dynamicColor by remember { mutableStateOf(settings.dynamicColor) }

            // 设置页现在是独立 Activity，改完主题这边收不到直接回调，
            // 每次从别的 Activity 回到前台（onResume）都重新读一次持久化设置，保持同步
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer =
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            themeMode = settings.themeMode
                            dynamicColor = settings.dynamicColor
                        }
                    }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            NeteasyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                NcmApp(settings = settings)
            }
        }
    }
}

/** 底部导航页（搜索已移到首页顶部搜索框入口，不再是单独的底部 Tab） */
enum class Screen { HOME, PROFILE }

/**
 * Tab 宿主：页面常驻组合（keep-alive），切换 Tab 时页面不销毁、不重建，
 * 已加载的数据与滚动位置直接保留（不再重复请求）。
 * - 可见页：置顶 + 全不透明；
 * - 隐藏页：沉底 + 淡出 + 对屏幕阅读器隐藏 + 铺一层透明“挡板”吃掉所有指针事件（防止误触下层页面）。
 */
@Composable
private fun TabHost(visible: Boolean, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        // 透明度属于 effects 弹簧：无过冲（MD3 Expressive 规范）
        animationSpec = ExpressiveMotion.EffectsFast,
        label = "tabAlpha"
    )
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .zIndex(if (visible) 2f else 1f)
            .alpha(alpha)
            .then(if (visible) Modifier else Modifier.semantics { hideFromAccessibility() })
    ) {
        content()
        // 隐藏时铺一层透明“挡板”，吃掉所有指针事件，避免误触下层页面
        if (!visible) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun NcmApp(settings: SettingsStore) {
    val context = LocalContext.current
    val app = context.applicationContext as NeteasyApp

    // 网络层 + 播放器是 App 级单例（NeteasyApp 持有），独立 Activity 也是从这里拿，
    // 保证设置页/登录页/搜索页/歌单详情跟主界面用的是完全同一份状态。
    val repository = app.repository
    val cookieStore = app.apiClient.cookieStore
    val player = app.playerController

    var screen by remember { mutableStateOf(Screen.HOME) }
    var profileRefreshKey by remember { mutableIntStateOf(0) }

    // Android 13+ 通知权限（前台服务通知可见）
    val notifPermLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 登录页是独立 Activity，登录成功后 setResult(RESULT_OK) 带回来，这边接住刷新「我的」页
    val loginLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                profileRefreshKey++
            }
        }

    // 悬浮 Minibar / 展开播放页 / 播放队列统一由 PlayerAwareContent 挂载在最外层，
    // 跟搜索页/歌单详情页共用同一套逻辑；这里只负责首页/我的自己的 Scaffold + 底部 Tab。
    // minibarBottomPadding = 标准 NavigationBar 高度（M3 规范 80dp）：悬浮卡片得再往上
    // 抬这么多，不然会直接盖在下面透明的导航栏上，把「首页/我的」的点击区域全挡住，
    // 导致切不了 Tab（这块被反馈过一次，之前漏了导航栏本身也要占的这段高度）。
    PlayerAwareContent(
        player = player,
        modifier = Modifier.fillMaxSize(),
        minibarBottomPadding = 80.dp
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { MainNavigationBar(navState = NavState(screen, { screen = it })) }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                // 两个主 Tab keep-alive：切换 Tab 只改变可见性与层级，不重新加载
                TabHost(visible = screen == Screen.HOME) {
                    HomeScreen(
                        repository = repository,
                        onOpenPlaylist = { id ->
                            context.startActivity(
                                Intent(context, PlaylistActivity::class.java)
                                    .putExtra(PlaylistActivity.EXTRA_PLAYLIST_ID, id)
                            )
                        },
                        onOpenSearch = { context.startActivity(Intent(context, SearchActivity::class.java)) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                TabHost(visible = screen == Screen.PROFILE) {
                    ProfileScreen(
                        repository = repository,
                        cookieStore = cookieStore,
                        refreshKey = profileRefreshKey,
                        onLoginClick = { loginLauncher.launch(Intent(context, LoginActivity::class.java)) },
                        onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                        onOpenPlaylist = { id ->
                            context.startActivity(
                                Intent(context, PlaylistActivity::class.java)
                                    .putExtra(PlaylistActivity.EXTRA_PLAYLIST_ID, id)
                            )
                        },
                        onOpenUserDetail = { uid ->
                            context.startActivity(
                                Intent(context, UserDetailActivity::class.java)
                                    .putExtra(UserDetailActivity.EXTRA_UID, uid)
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
