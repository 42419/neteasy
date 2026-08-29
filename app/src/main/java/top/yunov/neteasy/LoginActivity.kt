package top.yunov.neteasy

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.yunov.neteasy.ui.screens.LoginScreen
import top.yunov.neteasy.ui.theme.NeteasyThemedScreen

/**
 * 登录页独立 Activity。登录成功后 setResult(RESULT_OK) + finish()，
 * MainActivity 用 ActivityResultLauncher 接住这个结果去刷新「我的」页登录态。
 */
class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NeteasyApp
        setContent {
            NeteasyThemedScreen {
                LoginScreen(
                    repository = app.repository,
                    onBack = { finish() },
                    onLoggedIn = {
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }
}
