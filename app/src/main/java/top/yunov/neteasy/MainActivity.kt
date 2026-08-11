package top.yunov.neteasy

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yunov.neteasy.ui.theme.NeteasyTheme
import java.net.HttpURLConnection
import java.net.URL

/**
 * M2 验证页：确认内嵌的真实 Node 后端在 127.0.0.1:19800 正常响应。
 * 轮询真实接口 /banner（无需登录），HTTP 200 且响应含 banners 即视为就绪。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeteasyTheme {
                NodeCheckScreen()
            }
        }
    }
}

private const val NODE_BASE_URL = "http://127.0.0.1:19800"

@Composable
private fun NodeCheckScreen() {
    var status by remember { mutableStateOf("Node 启动中…") }
    var detail by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Android 13+：请求通知权限，让前台服务通知可见（服务本身不依赖该权限）
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    suspend fun runCheck() {
        status = "检测中…"
        val result = withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL("$NODE_BASE_URL/banner?type=2").openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 8000
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (code == 200 && body.contains("banners")) {
                    "OK → $body".take(600)
                } else {
                    "HTTP $code → ${body.take(200)}"
                }
            } catch (e: Exception) {
                "FAIL → ${e.message}"
            } finally {
                conn?.disconnect()
            }
        }
        status = if (result.startsWith("OK →")) "Node 运行中 ✓" else "Node 未就绪"
        detail = result
    }

    fun check() {
        scope.launch { runCheck() }
    }

    // 首次启动要复制 41MB 资产 + 启动 Node，可能超过 10 秒，轮询 20 次 × 2 秒
    LaunchedEffect(Unit) {
        for (i in 1..20) {
            runCheck()
            if (status.startsWith("Node 运行中")) break
            delay(2000)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("neteasy · Node 内嵌验证", style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.titleMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 300.dp),
            )
            Button(onClick = { check() }) { Text("重新检测") }
        }
    }
}
