package top.yunov.neteasy

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import top.yunov.neteasy.data.ApiClient
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.ui.HomeScreen
import top.yunov.neteasy.ui.Minibar
import top.yunov.neteasy.ui.NavState
import top.yunov.neteasy.ui.PlaylistScreen
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
enum class Screen { HOME, SEARCH }

@Composable
private fun NcmApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 网络层 + 播放器（组合内单例）
    val apiClient = remember { ApiClient(context) }
    val repository = remember { NcmRepository(apiClient) }
    val player = remember { PlayerController(scope) }

    // 组合销毁时释放 MediaPlayer，防 Activity 重建（旋转）泄漏音频资源
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // 导航状态
    var screen by remember { mutableStateOf(Screen.HOME) }
    var currentPlaylistId by remember { mutableStateOf<Long?>(null) }

    // Android 13+ 通知权限（前台服务通知可见）
    val notifPermLauncher = rememberLauncherForActivityResult(
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
                navState = NavState(screen, { screen = it }),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    repository = repository,
                    onOpenPlaylist = { id -> currentPlaylistId = id },
                )
                Screen.SEARCH -> SearchScreen(repository = repository, player = player)
            }
        }
    }

    // 歌单详情作为覆盖层
    val playlistId = currentPlaylistId
    if (playlistId != null) {
        PlaylistScreen(
            playlistId = playlistId,
            repository = repository,
            player = player,
            onBack = { currentPlaylistId = null },
        )
    }
}
