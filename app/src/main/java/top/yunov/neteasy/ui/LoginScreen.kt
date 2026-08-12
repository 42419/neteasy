package top.yunov.neteasy.ui

import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.NcmRepository
import top.yunov.neteasy.ui.theme.ButtonShape

/** 登录页二维码来源：base64 data url 由后端 /login/qr/create?qrimg=true 生成 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(repository: NcmRepository, onBack: () -> Unit, onLoggedIn: () -> Unit) {
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var status by remember { mutableStateOf("正在生成二维码…") }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        loading = true
        status = "正在生成二维码…"
        qrBitmap = null
        // 网络请求必须在 IO 线程（ApiClient 是同步执行，主线程会抛 NetworkOnMainThreadException）
        val key = withContext(Dispatchers.IO) { repository.qrKey() }
        if (key.isEmpty()) {
            status = "获取二维码失败，请稍后重试"
            loading = false
            return@LaunchedEffect
        }
        val qrimg = withContext(Dispatchers.IO) { repository.qrCreate(key).second }
        qrBitmap = decodeBase64Png(qrimg)
        loading = false
        if (qrBitmap == null) {
            status = "二维码生成失败"
            return@LaunchedEffect
        }
        status = "请使用网易云音乐 App 扫码登录"

        // 轮询扫码状态：800 等待 / 801 过期 / 802 已扫码待确认 / 803 成功
        var failCount = 0
        while (!finished) {
            kotlinx.coroutines.delay(2000)
            val code =
                try {
                    withContext(Dispatchers.IO) { repository.qrCheck(key) }
                } catch (e: Exception) {
                    // 网络抖动：最多连续失败 3 次，之后提示用户手动刷新
                    failCount++
                    if (failCount >= 3) {
                        status = "网络异常，请点击下方按钮刷新"
                        break
                    }
                    status = "网络不稳定，重试中…"
                    continue
                }
            failCount = 0
            when (code) {
                // 状态码语义与官方 qrlogin.html 一致：
                // 801 等待扫码（正常轮询态，继续等）
                // 800 二维码过期 / 802 已扫码待确认 / 803 登录成功
                801 -> status = "请使用网易云音乐 App 扫码登录"
                802 -> status = "已扫码，请在手机上确认"
                800 -> {
                    status = "二维码已过期，点击下方按钮刷新"
                    break
                }
                803 -> {
                    finished = true
                    status = "登录成功"
                    onLoggedIn()
                    break
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部返回栏
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 官方 FilledTonalIconButton：自带容器 + Expressive spring 动效
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "扫码登录",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 二维码卡片
                Box(
                    modifier =
                    Modifier
                        .size(250.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // 二维码生成是短等待 → 用官方形状 morph 加载指示器
                        loading -> LoadingIndicator()
                        qrBitmap != null ->
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "登录二维码",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        else -> Text("加载失败", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                FilledTonalButton(
                    onClick = { refreshKey++ },
                    shape = ButtonShape
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("刷新二维码", modifier = Modifier.padding(start = 6.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "二维码 5 分钟内有效\n请勿将二维码分享给他人",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 解码后端返回的 data:image/png;base64,xxx */
private fun decodeBase64Png(dataUrl: String): android.graphics.Bitmap? = try {
    val b64 = dataUrl.substringAfter("base64,")
    val bytes = Base64.decode(b64, Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (e: Exception) {
    null
}
