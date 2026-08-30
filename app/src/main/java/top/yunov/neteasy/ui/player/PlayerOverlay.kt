package top.yunov.neteasy.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import top.met6.amll.AppleMusicLyricPlayerStyle
import top.yunov.neteasy.data.SettingsStore
import top.yunov.neteasy.data.filteredForDisplay
import top.yunov.neteasy.player.PlayerController
import top.yunov.neteasy.ui.components.PlayerMinibar
import top.yunov.neteasy.ui.components.QueueSheet

/**
 * Minibar ↔ 展开播放页的“逻辑目标状态”，跟连续的拖动进度分开管：拖动过程中进度是
 * 连续值（0..1，可能停在任意中间态），但返回键、收起按钮这些离散操作得知道
 * “现在算展开还是收起”，不能看瞬时进度（拖到一半松手前它俩语义不一样）。
 */
private enum class SheetTarget { COLLAPSED, EXPANDED }

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
 *
 * Minibar 展开成全屏播放页支持两种触发方式：
 * - 点击：弹簧动画一步到位（[expandSheet]/[collapseSheet]，见函数体内）
 * - 手动拖动：在 Minibar 上下拖动实时跟手展开/收起，松手按拖动距离/甩动速度/当前进度
 *   三级判断该展开还是收起，再弹簧归位；收起瞬间 Minibar 有一下轻微压扁再弹回的
 *   “落地感”。手感思路参考自 PixelPlayerHQ/PixelPlayer 的拖动展开手势（具体见
 *   PlayerSheetMotion.kt 顶部注释）——那边是把 Minibar/全屏播放页做成同一个连续
 *   变形的 Box（宽高圆角都跟着进度插值），这里两者布局差异太大没照抄那套“形状
 *   连续变形”的实现，改用透明度+位移交叉过渡来表现同一套拖动手感（跟手、甩动
 *   判定、回弹参数是照抄的，视觉呈现做了简化，下面每一步都有注释说明）。
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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var showQueue by remember { mutableStateOf(false) }

    // 歌词渲染细节设置（对齐/模糊/弹簧手感/翻译音译显隐……）是在独立的 LyricSettingsActivity 里改的，
    // 跟主题设置一样，onResume 时重新从 SharedPreferences 读一次即可保持同步，不需要跨 Activity 回调。
    var lyricAlignPosition by remember { mutableStateOf(settings.lyricAlignPosition) }
    var lyricWordFadeWidth by remember { mutableStateOf(settings.lyricWordFadeWidth) }
    var lyricEnableBlur by remember { mutableStateOf(settings.lyricEnableBlur) }
    var lyricEnableScale by remember { mutableStateOf(settings.lyricEnableScale) }
    var lyricInactiveAlpha by remember { mutableStateOf(settings.lyricInactiveAlpha) }
    var lyricShowTranslation by remember { mutableStateOf(settings.lyricShowTranslation) }
    var lyricShowLineRomanization by remember { mutableStateOf(settings.lyricShowLineRomanization) }
    var lyricShowWordRomanization by remember { mutableStateOf(settings.lyricShowWordRomanization) }
    var lyricSpringPreset by remember { mutableStateOf(settings.lyricSpringPreset) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    lyricAlignPosition = settings.lyricAlignPosition
                    lyricWordFadeWidth = settings.lyricWordFadeWidth
                    lyricEnableBlur = settings.lyricEnableBlur
                    lyricEnableScale = settings.lyricEnableScale
                    lyricInactiveAlpha = settings.lyricInactiveAlpha
                    lyricShowTranslation = settings.lyricShowTranslation
                    lyricShowLineRomanization = settings.lyricShowLineRomanization
                    lyricShowWordRomanization = settings.lyricShowWordRomanization
                    lyricSpringPreset = settings.lyricSpringPreset
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val lyricStyle =
        AppleMusicLyricPlayerStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            alignPosition = lyricAlignPosition,
            wordFadeWidth = lyricWordFadeWidth,
            inactiveMaskAlpha = lyricInactiveAlpha,
            backgroundLineScale = 0.75f,
            enableBlur = lyricEnableBlur,
            enableScale = lyricEnableScale,
            scrollSpringOverride = lyricSpringPreset.toSpringParams()
        )
    val filteredLyricLines =
        remember(playerState.lyricLines, lyricShowTranslation, lyricShowLineRomanization, lyricShowWordRomanization) {
            playerState.lyricLines.map {
                it.filteredForDisplay(lyricShowTranslation, lyricShowLineRomanization, lyricShowWordRomanization)
            }
        }

    // ---- Minibar ↔ 展开播放页：手动拖动展开/收起 ----
    var sheetTarget by remember { mutableStateOf(SheetTarget.COLLAPSED) }
    // 连续展开进度：0=完全收起（只看得到 Minibar），1=完全展开（只看得到全屏播放页）。
    // 拖动过程中直接 snapTo 跟手；松手/点击后用弹簧 animateTo 归位。
    val expansionFraction = remember { Animatable(0f) }
    // Minibar 收起落地时的压扁-弹回效果，跟 expansionFraction 分开一条动画轨道，
    // 不然“归位”和“压扁回弹”这两条曲线互相干扰会显得很怪。
    val minibarSquash = remember { Animatable(1f) }
    // 拖满这么多像素视为“完全展开”——手指拖动距离到展开进度的换算尺度，
    // 同时也是 NowPlayingScreen 从底部升起的滑动距离，两者共用同一个值，
    // 保证“拖了多少”和“画面挪了多少”视觉上是匹配的，不会看着不跟手。
    val dragExtentPx = with(density) { 320.dp.toPx() }
    val minibarSlidePx = with(density) { 28.dp.toPx() }

    fun expandSheet(initialVelocity: Float = 0f) {
        sheetTarget = SheetTarget.EXPANDED
        scope.launch {
            expansionFraction.animateTo(
                targetValue = 1f,
                initialVelocity = initialVelocity,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            )
            // 弹簧衰减到目标值附近就算“完成”，不保证精确等于 1f；这里强制吸附一下，
            // 纯粹是为了跟 collapseSheet() 那边的 snapTo(0f) 对称，实际上 1f 这一侧
            // 没有依赖精确相等的判断逻辑，加上只是保险。
            expansionFraction.snapTo(1f)
        }
    }

    fun collapseSheet(initialVelocity: Float = 0f) {
        val currentFraction = expansionFraction.value
        sheetTarget = SheetTarget.COLLAPSED
        scope.launch {
            launch {
                minibarSquash.snapTo(collapseInitialSquash(currentFraction))
                minibarSquash.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessVeryLow)
                )
            }
            expansionFraction.animateTo(
                targetValue = 0f,
                initialVelocity = initialVelocity,
                animationSpec = spring(dampingRatio = collapseSpringDamping(currentFraction), stiffness = Spring.StiffnessLow)
            )
            // 关键：弹簧衰减是渐近的，理论上可能永远到不了精确的 0f（残留极小的浮点误差）。
            // 下面渲染 NowPlayingScreen 靠的是 expansionFraction.value > 0f 这个判断，
            // 一旦卡在比如 0.0000003f 这种值上，NowPlayingScreen 会一直挂载在组合树里
            // 不被移除——它内部歌词渲染器的 withFrameNanos 循环会跟着永远跑下去，
            // 每一帧都占用主线程，不管当前显示的是哪个页面，表现就是全局卡顿。
            // 强制吸附到精确 0f，保证这个条件最终一定会变成 false，NowPlayingScreen
            // 真正从组合树里卸载，循环才会真正停掉。
            expansionFraction.snapTo(0f)
        }
    }

    // 展开播放页仍然是 Compose 内覆盖层，不是独立 Activity——它是“同一播放器展开/收起”，
    // 不是“跳转到新地方”；返回键走跟点收起按钮一样的弹簧动画收起它，不是硬切消失。
    BackHandler(enabled = sheetTarget == SheetTarget.EXPANDED) { collapseSheet() }

    // Minibar 上的拖动手势：作为 modifier 参数传入 PlayerMinibar，会排在 Surface 自带的
    // onClick（内部用 Modifier.clickable 实现）之前生效——拖动一旦越过触摸阈值就会
    // consume 掉这次手势，不会再触发点击；没拖动过的纯点击不受影响，照常展开。
    val minibarDragModifier =
        Modifier.pointerInput(dragExtentPx) {
            val velocityTracker = VelocityTracker()
            var dragStartFraction = 0f
            var dragStartTarget = SheetTarget.COLLAPSED
            var accumulatedDragPx = 0f
            detectVerticalDragGestures(
                onDragStart = {
                    velocityTracker.resetTracking()
                    dragStartFraction = expansionFraction.value
                    dragStartTarget = sheetTarget
                    accumulatedDragPx = 0f
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    accumulatedDragPx += dragAmount
                    val next = computeDragFraction(dragStartFraction, accumulatedDragPx, dragExtentPx)
                    scope.launch { expansionFraction.snapTo(next) }
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                },
                onDragEnd = {
                    val verticalVelocity = velocityTracker.calculateVelocity().y
                    // px/s 甩动速度换算成 fraction/s，喂给收尾的弹簧动画当初速度，
                    // 这样松手瞬间的动画不会“断档”，是这一下甩动的自然延续。
                    val fractionVelocity = -verticalVelocity / dragExtentPx
                    val expand =
                        resolveDragTarget(
                            accumulatedDragPx = accumulatedDragPx,
                            minDragThresholdPx = with(density) { 24.dp.toPx() },
                            verticalVelocity = verticalVelocity,
                            velocityThresholdPxPerSec = 600f,
                            currentFraction = expansionFraction.value
                        )
                    if (expand) expandSheet(fractionVelocity) else collapseSheet(fractionVelocity)
                },
                onDragCancel = {
                    // 手势被打断（比如被其他手势抢走）：撤销这次拖动，回到拖动开始前的状态
                    if (dragStartTarget == SheetTarget.EXPANDED) expandSheet() else collapseSheet()
                }
            )
        }

    // 悬浮卡片背景是跟随全局动态取色的纯色块（见 PlayerMinibar），不需要再从这层
    // 内容捕获背景做真实模糊了，content() 就是普通内容，不用额外包一层
    Box(modifier = modifier.fillMaxSize()) {
        content()

        // 悬浮卡片贴在内容最下方，四周留白 + 阴影，压在系统导航栏（手势条/三大金刚键）
        // 之上——不用它把内容顶上去（不占布局空间），是真正“浮”在页面上的覆盖层。
        if (playerState.song != null) {
            PlayerMinibar(
                state = playerState,
                onToggle = { player.toggle() },
                onOpenQueue = { showQueue = true },
                onExpand = { expandSheet() },
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = minibarBottomPadding)
                    .graphicsLayer {
                        // 读 Animatable.value 放在 graphicsLayer 里（draw 阶段），
                        // 拖动/动画每一帧只触发重绘不触发重新布局，滑起来更顺。
                        val fraction = expansionFraction.value
                        // alpha 用 1.6 倍速度提前淡出——展开到一半左右 Minibar 基本就
                        // 看不见了，避免它跟正在升起的全屏播放页长时间叠在一起显得脏。
                        alpha = (1f - fraction * 1.6f).coerceIn(0f, 1f)
                        translationY = fraction * minibarSlidePx
                        val squash = minibarSquash.value
                        scaleX = squash
                        scaleY = squash
                    }
                    .then(minibarDragModifier)
            )
        }

        // 展开播放页：跟 Minibar 共用同一条 expansionFraction 驱动交叉淡入淡出 + 从底部
        // 升起，不是固定时长的转场——进度由拖动或点击触发的弹簧动画连续给出，手指拖到哪
        // 屏幕就跟到哪。fraction 完全为 0 且逻辑状态也是收起时才跳过渲染，省一次合成。
        if (expansionFraction.value > 0.001f || sheetTarget == SheetTarget.EXPANDED) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = expansionFraction.value
                        alpha = fraction.coerceIn(0f, 1f)
                        translationY = (1f - fraction) * dragExtentPx
                    }
            ) {
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
                    onCollapse = { collapseSheet() },
                    lyricStyle = lyricStyle,
                    lyricLines = filteredLyricLines
                )
            }
        }
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
