package top.yunov.neteasy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * 安装更新完成后的清理器：Android 在“本 App 被新版本替换安装完成”时会广播
 * ACTION_MY_PACKAGE_REPLACED（只有自己能收到，不需要额外权限）。这里借这个时机
 * 把之前下载到 Downloads 目录的安装包删掉，不用用户自己手动清理。
 *
 * 下载路径存在 SharedPreferences 里而不是内存变量——因为触发安装后系统可能先把
 * 旧进程杀掉，新版本重新拉起进程时这个广播才到，内存状态早就没了，必须落盘才能跨进程重启存活。
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PENDING_APK_PATH, null) ?: return
        try {
            File(path).takeIf { it.exists() }?.delete()
        } catch (e: Exception) {
            // 删不掉就算了，不是关键路径，不影响 App 正常使用
        }
        prefs.edit().remove(KEY_PENDING_APK_PATH).apply()
    }

    companion object {
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_PENDING_APK_PATH = "pending_apk_path"

        /** 开始下载安装包前调用：记下路径，安装完成后 UpdateReceiver 才知道删哪个文件 */
        fun rememberPendingApk(context: Context, absolutePath: String) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_APK_PATH, absolutePath)
                .apply()
        }
    }
}
