package top.yunov.neteasy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.yunov.neteasy.ui.player.PlayerAwareContent
import top.yunov.neteasy.ui.screens.PlaylistScreen
import top.yunov.neteasy.ui.theme.NeteasyThemedScreen

/**
 * 歌单详情独立 Activity。playlistId 通过 Intent extra 传入（跨 Activity 没法传 lambda/引用）。
 * 用 [PlayerAwareContent] 包一层，歌单详情也要显示悬浮 Minibar。
 */
class PlaylistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NeteasyApp
        val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
        setContent {
            NeteasyThemedScreen {
                PlayerAwareContent(player = app.playerController, likeRepository = app.likeRepository) {
                    if (playlistId != -1L) {
                        PlaylistScreen(
                            playlistId = playlistId,
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

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlistId"
    }
}
