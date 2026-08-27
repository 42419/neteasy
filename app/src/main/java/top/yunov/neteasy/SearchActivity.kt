package top.yunov.neteasy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import top.yunov.neteasy.ui.PlayerAwareContent
import top.yunov.neteasy.ui.SearchScreen
import top.yunov.neteasy.ui.theme.NeteasyThemedScreen

/**
 * 搜索页独立 Activity。repository/player 都是 App 级单例，直接从 Application 拿。
 * 用 [PlayerAwareContent] 包一层，跟首页/我的共用同一套悬浮 Minibar + 展开播放页逻辑。
 */
class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NeteasyApp
        setContent {
            NeteasyThemedScreen {
                PlayerAwareContent(player = app.playerController) {
                    SearchScreen(
                        repository = app.repository,
                        player = app.playerController,
                        onBack = { finish() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
