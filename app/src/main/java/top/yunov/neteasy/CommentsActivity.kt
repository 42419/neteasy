package top.yunov.neteasy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.yunov.neteasy.ui.player.PlayerAwareContent
import top.yunov.neteasy.ui.screens.CommentsScreen
import top.yunov.neteasy.ui.theme.NeteasyThemedScreen

/**
 * 评论区独立 Activity（歌曲/歌单/专辑共用）。被评论对象的 id/类型/标题/封面通过
 * Intent extra 传入（跨 Activity 没法传 lambda/引用）。用 [intent] 工厂方法构造，
 * 调用方不用关心 extra 的 key。
 */
class CommentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NeteasyApp
        val resourceId = intent.getLongExtra(EXTRA_RESOURCE_ID, -1L)
        val resourceType = intent.getIntExtra(EXTRA_RESOURCE_TYPE, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val coverUrl = intent.getStringExtra(EXTRA_COVER_URL) ?: ""
        setContent {
            NeteasyThemedScreen {
                PlayerAwareContent(player = app.playerController, likeRepository = app.likeRepository) {
                    if (resourceId != -1L && resourceType != -1) {
                        CommentsScreen(
                            resourceId = resourceId,
                            resourceType = resourceType,
                            title = title,
                            coverUrl = coverUrl,
                            repository = app.repository,
                            cookieStore = app.apiClient.cookieStore,
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_RESOURCE_ID = "resourceId"
        const val EXTRA_RESOURCE_TYPE = "resourceType"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COVER_URL = "coverUrl"

        /** 评论区入口统一走这里构造 Intent（type 见 [top.yunov.neteasy.data.model.CommentType]） */
        fun intent(context: Context, id: Long, type: Int, title: String, coverUrl: String): Intent =
            Intent(context, CommentsActivity::class.java).apply {
                putExtra(EXTRA_RESOURCE_ID, id)
                putExtra(EXTRA_RESOURCE_TYPE, type)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_COVER_URL, coverUrl)
            }
    }
}
