package top.yunov.neteasy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.yunov.neteasy.ui.UserDetailScreen
import top.yunov.neteasy.ui.theme.NeteasyThemedScreen

/**
 * 用户信息详情页（独立 Activity）：从「我的」页点击头像进入，展示账户等级 / VIP / 听歌数等。
 * 与设置/登录等页一样，独立 Activity 便于系统接管原生转场；repository 从 App 级单例取。
 */
class UserDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NeteasyApp
        val uid = intent.getLongExtra(EXTRA_UID, -1L)
        setContent {
            NeteasyThemedScreen {
                if (uid != -1L) {
                    UserDetailScreen(repository = app.repository, uid = uid, onBack = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_UID = "uid"
    }
}
