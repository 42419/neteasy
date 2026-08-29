package top.yunov.neteasy.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yunov.neteasy.Screen

/** 导航状态（由 NeteasyApp 提供） */
data class NavState(val screen: Screen, val onNavigate: (Screen) -> Unit)

val LocalNavState =
    staticCompositionLocalOf<NavState> {
        error("LocalNavState not provided")
    }

/**
 * 首页/我的两个 Tab 的底部导航（仅 MainActivity 用）。容器背景设为透明——
 * 不要那条实色背景条，图标/文字直接浮在页面背景色上，视觉上跟上方悬浮的
 * [PlayerMinibar] 融为一体，只有那张悬浮卡片本身带一点浅色底和阴影。
 */
@Composable
fun MainNavigationBar(navState: NavState) {
    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = navState.screen == Screen.HOME,
            onClick = { navState.onNavigate(Screen.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "首页") },
            label = { Text("首页") }
        )
        NavigationBarItem(
            selected = navState.screen == Screen.PROFILE,
            onClick = { navState.onNavigate(Screen.PROFILE) },
            icon = { Icon(Icons.Filled.Person, contentDescription = "我的") },
            label = { Text("我的") }
        )
    }
}
