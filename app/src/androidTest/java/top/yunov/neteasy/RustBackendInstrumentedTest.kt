package top.yunov.neteasy

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 内嵌 Rust 后端的端到端测试（在模拟器/真机上跑）：
 * 真正加载 libneteasy_backend.so，经 JNI 启动，再用 HTTP 访问 127.0.0.1:19800。
 * 只测不依赖外网的接口，CI 环境不能访问网易云也不影响结果。
 */
@RunWith(AndroidJUnit4::class)
class RustBackendInstrumentedTest {
    private val base = "http://127.0.0.1:19800"

    private fun get(path: String): Pair<Int, String> {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 3_000
        conn.readTimeout = 10_000
        return try {
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            code to stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 轮询直到后端可连接，返回等待了多少毫秒；超时直接判失败 */
    private fun awaitReady(timeoutMs: Long = 10_000): Long {
        val begin = System.nanoTime()
        while (true) {
            val waited = (System.nanoTime() - begin) / 1_000_000
            try {
                if (get("/").first == 200) return waited
            } catch (_: Exception) {
                // 还没 listen，继续等
            }
            if (waited > timeoutMs) fail("后端在 ${timeoutMs}ms 内没有就绪")
            Thread.sleep(50)
        }
    }

    @Test
    fun backendStartsViaJniAndServesLocalRoutes() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dataDir = File(ctx.filesDir, "neteasy-backend").apply { mkdirs() }

        val startedAt = System.nanoTime()
        // 返回 false 说明应用启动时 BackendService 已经把后端拉起来了，同样可以继续测
        val started = RustBackend.start(dataDir.absolutePath, 19800)
        val startMs = (System.nanoTime() - startedAt) / 1_000_000
        val readyMs = awaitReady()
        Log.i(TAG, "RustBackend.start -> $started（${startMs}ms），之后再等 ${readyMs}ms 可连接")
        // 原来内嵌 Node 冷启动要 2~5 秒；Rust 后端应当在很短时间内就绪
        assertTrue("冷启动过慢：start ${startMs}ms + 等待 ${readyMs}ms", startMs + readyMs < 3_000)

        val (code, body) = get("/")
        assertEquals(200, code)
        assertEquals("neteasy-backend", JSONObject(body).getString("msg"))

        // 二维码生成是纯本地计算，不访问外网
        val (qrCode, qrBody) = get("/login/qr/create?key=abc123&qrimg=true")
        assertEquals(200, qrCode)
        val data = JSONObject(qrBody).getJSONObject("data")
        assertEquals("https://music.163.com/login?codekey=abc123", data.getString("qrurl"))
        assertTrue(data.getString("qrimg").startsWith("data:image/png;base64,"))

        // 未知路由：和上游一致返回 404 + JSON
        val (notFound, notFoundBody) = get("/no/such/route")
        assertEquals(404, notFound)
        assertEquals("Not Found", JSONObject(notFoundBody).getString("msg"))
    }

    @Test
    fun startingTwiceDoesNotRestartTheServer() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dataDir = File(ctx.filesDir, "neteasy-backend").apply { mkdirs() }
        RustBackend.start(dataDir.absolutePath, 19800)
        awaitReady()
        // 已经在运行：第二次调用应当返回 false，且服务不受影响
        assertEquals(false, RustBackend.start(dataDir.absolutePath, 19800))
        assertEquals(200, get("/").first)
    }

    private companion object {
        const val TAG = "RustBackendTest"
    }
}
