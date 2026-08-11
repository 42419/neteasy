package top.yunov.neteasy

import android.util.Log

/**
 * nodejs-mobile 运行时入口。
 *
 * native 实现位于 native-lib.cpp（JNI 函数名与类名/方法名绑定）：
 *   Java_top_yunov_neteasy_NodeJS_startNodeWithArguments
 */
object NodeJS {

    private const val TAG = "NodeJS"

    init {
        // 先加载 node，再加载桥接库（native-lib 依赖 libnode）
        System.loadLibrary("node")
        System.loadLibrary("native-lib")
    }

    @Volatile
    private var started = false

    /**
     * 由 native-lib.cpp 实现。会阻塞直到 Node 退出，必须在工作线程调用。
     */
    @JvmStatic
    external fun startNodeWithArguments(arguments: Array<String>): Int

    /**
     * 在后台线程启动 Node，运行 projectDir 下的入口脚本。重复调用仅首次生效。
     *
     * @param projectDir nodejs-project 目录（filesDir/nodejs-project）
     * @param entry 入口文件名，默认 main.js；M2 起为 mobile-entry.js
     */
    fun start(projectDir: String, entry: String = "main.js") {
        synchronized(this) {
            if (started) return
            started = true
        }
        Thread({
            try {
                Log.i(TAG, "Starting node with project: $projectDir, entry: $entry")
                startNodeWithArguments(arrayOf("node", "$projectDir/$entry"))
                Log.i(TAG, "Node exited")
            } catch (t: Throwable) {
                Log.e(TAG, "Node failed to start", t)
            }
        }, "node-main").start()
    }
}
