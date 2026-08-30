package top.yunov.neteasy.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.io.FileOutputStream
import top.yunov.neteasy.NodeJS
import top.yunov.neteasy.R

/**
 * 前台服务：运行内嵌的 Node.js 后端（nodejs-mobile + api-enhanced）。
 *
 * - APK 更新时（packageInfo.lastUpdateTime 变化）把 assets/nodejs-project 复制到 filesDir
 * - 在独立线程启动 Node（native 桥内部再起线程），HTTP 服务监听 http://127.0.0.1:19800
 */
class NodeService : Service() {
    companion object {
        private const val TAG = "NodeService"
        private const val CHANNEL_ID = "node_service"
        private const val NOTIF_ID = 19800
        private const val PORT = 19800
        private const val PREFS = "nodejs_mobile_prefs"
        private const val KEY_LAST_APK_UPDATE = "last_apk_update_time"
        private const val NODE_ENTRY = "mobile-entry.js"
    }

    private val nodeDir: File
        get() = File(filesDir, "nodejs-project")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NodeService onCreate")
        startAsForeground()
        Thread {
            try {
                if (wasApkUpdated()) {
                    nodeDir.deleteRecursively()
                    copyAssetFolder("nodejs-project", nodeDir)
                    saveApkUpdateTime()
                    Log.i(TAG, "nodejs-project 已复制到 ${nodeDir.absolutePath}")
                } else {
                    Log.i(TAG, "APK 未更新，跳过复制 nodejs-project")
                }
                NodeJS.start(nodeDir.absolutePath, NODE_ENTRY)
            } catch (t: Throwable) {
                Log.e(TAG, "启动 Node 失败", t)
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        Log.i(TAG, "NodeService onDestroy（注意：Node 线程不受服务生命周期管理，进程存活时仍会运行）")
        super.onDestroy()
    }

    // ---------- 前台服务 ----------

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "neteasy 后端服务", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "运行本地 Node.js 后端（localhost:$PORT）"
                }
            )
        }
        val notification: Notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setContentTitle("neteasy 后端服务")
                .setContentText("Node.js 运行中（http://127.0.0.1:$PORT）")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build()
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
        } catch (e: RuntimeException) {
            // Android 12+ 或国产 ROM 可能限制后台启动前台服务。
            // 降级处理：Node 线程照常启动，仅无前台通知。
            Log.w(TAG, "startForeground 失败（降级：Node 仍将启动）", e)
        }
    }

    // ---------- 仅 APK 更新时复制 assets → filesDir ----------

    private fun wasApkUpdated(): Boolean {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getLong(KEY_LAST_APK_UPDATE, 0L)
        return lastApkUpdateTime() != previous
    }

    private fun saveApkUpdateTime() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_APK_UPDATE, lastApkUpdateTime())
            .apply()
    }

    private fun lastApkUpdateTime(): Long = try {
        packageManager.getPackageInfo(packageName, 0).lastUpdateTime
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(TAG, "getPackageInfo failed", e)
        1L
    }

    private fun copyAssetFolder(fromPath: String, target: File) {
        val children = assets.list(fromPath)
        if (children.isNullOrEmpty()) {
            // 单文件
            copyAsset(fromPath, target)
        } else {
            target.mkdirs()
            for (child in children) {
                copyAssetFolder("$fromPath/$child", File(target, child))
            }
        }
    }

    private fun copyAsset(fromPath: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        assets.open(fromPath).use { input ->
            FileOutputStream(targetFile).use { output -> input.copyTo(output) }
        }
    }
}
