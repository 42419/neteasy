package top.yunov.neteasy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.rememberHazeState
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.player.PlayerController

/**
 * 悬浮 Minibar 的统一挂载点：把 [PlayerMinibar]（悬浮卡片）+ 展开播放页
 * （[NowPlayingScreen]，Compose 覆盖层）+ 播放队列（[QueueSheet]）一起包在
 * [content] 之上，谁调用谁就有悬浮播放条，不用每个 Activity 各写一遍。
 *
 * 目前接入的页面：首页/我的（MainActivity）、搜索、歌单详情。
 * 不接入：设置页及其子页（存储空间等）、登录页——这几个页面语义上跟“正在听歌”
 * 无关，保持干净。
 *
 * 播放器/设置都是 App 级单例状态，这里只是订阅展示，不持有跨 Activity 状态。
 */
@Composable
fun PlayerAwareContent(
    player: PlayerController,
    modifier: Modifier = Modifier,
    /**
     * 悬浮卡片额外再往上抬多少：给「卡片下面还有别的东西」的场景用（比如 MainActivity
     * 首页/我的下面还有一条底部导航栏），不然悬浮卡片会直接贴底压在导航栏上，把导航栏
     * 点击区域全部挡住，切不了 Tab。没有底部导航的页面（搜索/歌单详情）用默认 0，
     * 贴底浮着就行。
     */
    minibarBottomPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val playerState by player.state.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    var showNowPlaying by remember { mutableStateOf(false) }

    // Haze 捕获悬浮卡片下面那层内容（content()），供卡片做磨砂玻璃背景用——
    // 卡片本身不知道自己下面具体是什么（列表/歌单封面/搜索结果……），只需要
    // 知道“该往哪捕获”，所以捕获点和使用点分开：这里标记源，PlayerMinibar 里取用
    val hazeState = rememberHazeState()

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().haze(state = hazeState)) {
            content()
        }

        // 悬浮卡片贴在内容最下方，四周留白 + 阴影，压在系统导航栏（手势条/三大金刚键）
        // 之上——不用它把内容顶上去（不占布局空间），是真正“浮”在页面上的覆盖层。
        if (playerState.song != null) {
            PlayerMinibar(
                state = playerState,
                onToggle = { player.toggle() },
                onOpenQueue = { showQueue = true },
                onExpand = { showNowPlaying = true },
                hazeState = hazeState,
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = minibarBottomPadding)
            )
        }
    }

    // 展开播放页仍然是 Compose 内覆盖层，不是独立 Activity——它是“同一播放器展开/收起”，
    // 不是“跳转到新地方”，返回键收起它而不是退出/跳转别的页面
    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }

    // 展开播放页转场：从底部滑起 / 收回，跟“从 Minibar 展开”的方向直觉一致
    val expandEnter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 }
    val expandExit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 6 }

    AnimatedVisibility(visible = showNowPlaying, enter = expandEnter, exit = expandExit) {
        NowPlayingScreen(
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
            onCycleRepeat = { player.cycleRepeatMode() },
            onCollapse = { showNowPlaying = false }
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
}
