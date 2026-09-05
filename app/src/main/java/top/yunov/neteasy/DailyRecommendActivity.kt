package top.yunov.neteasy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.yunov.neteasy.ui.player.PlayerAwareContent
import top.yunov.neteasy.ui.screens.DailyRecommendScreen
import top.yunov.neteasy.ui.theme.NeteasyThemedScreen

/**
 * 每日推荐独立 Activity，跟 PlaylistActivity 同一个模式。
 */
class DailyRecommendActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NeteasyApp
        setContent {
            NeteasyThemedScreen {
                PlayerAwareContent(player = app.playerController, likeRepository = app.likeRepository) {
                    DailyRecommendScreen(
                        repository = app.repository,
                        player = app.playerController,
                        likeRepository = app.likeRepository,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
