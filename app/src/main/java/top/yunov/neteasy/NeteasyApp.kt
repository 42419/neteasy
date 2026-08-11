package top.yunov.neteasy

import android.app.Application
import android.content.Intent
import android.os.Build

/**
 * 应用入口：冷启动时拉起 Node 后端前台服务。
 */
class NeteasyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val intent = Intent(this, NodeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
