package top.yunov.neteasy

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import top.yunov.neteasy.data.ApiClient
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.data.ThemeMode
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.ui.HomeScreen
import top.yunov.neteasy.ui.LoginScreen
import top.yunov.neteasy.ui.Minibar
import top.yunov.neteasy.ui.NavState
import top.yunov.neteasy.ui.PlaylistScreen
import top.yunov.neteasy.ui.ProfileScreen
import top.yunov.neteasy.ui.QueueSheet
import top.yunov.neteasy.ui.SearchScreen
import top.yunov.neteasy.ui.SettingsScreen
import top.yunov.neteasy.ui.theme.ExpressiveMotion
import top.yunov.neteasy.ui.theme.NeteasyTheme

/**
 * M3 极简播放器：
 * - 首页（banner + 推荐歌单）/ 搜索 / 歌单详情
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
            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            NeteasyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                NcmApp(
                    settings = settings,
                    themeMode = themeMode,
                    onThemeModeChange = {
                        settings.themeMode = it
                        themeMode = it
                    },
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = {
                        settings.dynamicColor = it
                        dynamicColor = it
                    }
                )
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
private fun NcmApp(
    settings: SettingsStore,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean = true,
    onDynamicColorChange: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 网络层 + 播放器（组合内单例）
    val apiClient = remember { ApiClient(context) }
    val repository = remember { NcmRepository(apiClient) }
    val cookieStore = remember { apiClient.cookieStore }
    val player = remember { PlayerController(scope, repository, initialQuality = settings.preferredAudioQuality) }

    // 组合销毁时释放 MediaPlayer，防 Activity 重建（旋转）泄漏音频资源
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // 导航状态
    // 动态取色仅 Android 12+ 可用（系统壁纸色提取）
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var screen by remember { mutableStateOf(Screen.HOME) }
    var currentPlaylistId by remember { mutableStateOf<Long?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
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

    val playerState by player.state.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Minibar(
                state = playerState,
                onToggle = { player.toggle() },
                onSeek = { player.seekTo(it) },
                onPrevious = { player.previous() },
                onNext = { player.next() },
                onOpenQueue = { showQueue = true },
                onQualityChange = { quality ->
                    settings.preferredAudioQuality = quality
                    player.setQuality(quality)
                },
                navState = NavState(screen, { screen = it })
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // 两个主 Tab keep-alive：切换 Tab 只改变可见性与层级，不重新加载
            TabHost(visible = screen == Screen.HOME) {
                HomeScreen(
                    repository = repository,
                    onOpenPlaylist = { id -> currentPlaylistId = id },
                    onOpenSearch = { showSearch = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            TabHost(visible = screen == Screen.PROFILE) {
                ProfileScreen(
                    repository = repository,
                    cookieStore = cookieStore,
                    refreshKey = profileRefreshKey,
                    onLoginClick = { showLogin = true },
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // 系统返回键/手势：优先关闭最上层的覆盖层，而不是直接退出 App
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showLogin) { showLogin = false }
    BackHandler(enabled = showSearch) { showSearch = false }
    BackHandler(enabled = currentPlaylistId != null) { currentPlaylistId = null }

    // Material/Android 默认页面转场：标准 300ms + FastOutSlowInEasing（tween 默认曲线），
    // 无弹簧过冲——右侧滑入淡入进入，原路滑出淡出退出，就是系统级最朴素的 push/pop 效果。
    val defaultEnter =
        fadeIn(tween(300)) +
            slideInHorizontally(tween(300)) { it / 3 }
    val defaultExit =
        fadeOut(tween(300)) +
            slideOutHorizontally(tween(300)) { it / 3 }

    // 设置页覆盖层
    AnimatedVisibility(visible = showSettings, enter = defaultEnter, exit = defaultExit) {
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            dynamicColor = dynamicColor,
            dynamicColorSupported = dynamicColorSupported,
            onDynamicColorChange = onDynamicColorChange,
            onBack = { showSettings = false }
        )
    }

    // 登录页覆盖层
    AnimatedVisibility(visible = showLogin, enter = defaultEnter, exit = defaultExit) {
        LoginScreen(
            repository = repository,
            onBack = { showLogin = false },
            onLoggedIn = {
                showLogin = false
                profileRefreshKey++
            }
        )
    }

    // 搜索页覆盖层：从首页顶部搜索框点进来，跟设置/登录一样的默认转场
    AnimatedVisibility(visible = showSearch, enter = defaultEnter, exit = defaultExit) {
        SearchScreen(
            repository = repository,
            player = player,
            onBack = { showSearch = false },
            modifier = Modifier.fillMaxSize()
        )
    }

    // 播放队列面板（底部弹出，自带手势下拉关闭 + 返回键关闭，不用套 AnimatedVisibility/BackHandler）
    if (showQueue) {
        QueueSheet(
            queue = playerState.queue,
            currentIndex = playerState.queueIndex,
            onSelect = { index -> player.playAt(index) },
            onDismiss = { showQueue = false }
        )
    }

    // 歌单详情作为覆盖层：关闭时 currentPlaylistId 会先变 null，用 displayedPlaylistId 记住
    // 最后一次的 id，让退出动画播放期间 PlaylistScreen 仍能拿到有效 id（不会中途白屏）。
    var displayedPlaylistId by remember { mutableStateOf<Long?>(null) }
    currentPlaylistId?.let { displayedPlaylistId = it }
    AnimatedVisibility(visible = currentPlaylistId != null, enter = defaultEnter, exit = defaultExit) {
        displayedPlaylistId?.let { id ->
            PlaylistScreen(
                playlistId = id,
                repository = repository,
                player = player,
                onBack = { currentPlaylistId = null }
            )
        }
    }
}
