package top.yunov.neteasy

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import top.yunov.neteasy.data.ApiClient
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.ui.HomeScreen
import top.yunov.neteasy.ui.LoginScreen
import top.yunov.neteasy.ui.Minibar
import top.yunov.neteasy.ui.NavState
import top.yunov.neteasy.ui.PlaylistScreen
import top.yunov.neteasy.ui.ProfileScreen
import top.yunov.neteasy.ui.SearchScreen
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
            NeteasyTheme {
                NcmApp()
            }
        }
    }
}

/** 底部导航页 */
enum class Screen { HOME, SEARCH, PROFILE }

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
        animationSpec = tween(200),
        label = "tabAlpha"
    )
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .zIndex(if (visible) 2f else 1f)
            .alpha(alpha)
            .then(if (visible) Modifier else Modifier.semantics { invisibleToUser() })
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
private fun NcmApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 网络层 + 播放器（组合内单例）
    val apiClient = remember { ApiClient(context) }
    val repository = remember { NcmRepository(apiClient) }
    val cookieStore = remember { apiClient.cookieStore }
    val player = remember { PlayerController(scope) }

    // 组合销毁时释放 MediaPlayer，防 Activity 重建（旋转）泄漏音频资源
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // 导航状态
    var screen by remember { mutableStateOf(Screen.HOME) }
    var currentPlaylistId by remember { mutableStateOf<Long?>(null) }
    var showLogin by remember { mutableStateOf(false) }
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
                navState = NavState(screen, { screen = it })
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // 三个页面 keep-alive：切换 Tab 只改变可见性与层级，不重新加载
            TabHost(visible = screen == Screen.HOME) {
                HomeScreen(
                    repository = repository,
                    onOpenPlaylist = { id -> currentPlaylistId = id },
                    modifier = Modifier.fillMaxSize()
                )
            }
            TabHost(visible = screen == Screen.SEARCH) {
                SearchScreen(
                    repository = repository,
                    player = player,
                    modifier = Modifier.fillMaxSize()
                )
            }
            TabHost(visible = screen == Screen.PROFILE) {
                ProfileScreen(
                    repository = repository,
                    cookieStore = cookieStore,
                    refreshKey = profileRefreshKey,
                    onLoginClick = { showLogin = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // 登录页覆盖层（登录成功后刷新“我的”页登录态）
    if (showLogin) {
        LoginScreen(
            repository = repository,
            onBack = { showLogin = false },
            onLoggedIn = {
                showLogin = false
                profileRefreshKey++
            }
        )
    }

    // 歌单详情作为覆盖层
    val playlistId = currentPlaylistId
    if (playlistId != null) {
        PlaylistScreen(
            playlistId = playlistId,
            repository = repository,
            player = player,
            onBack = { currentPlaylistId = null }
        )
    }
}
