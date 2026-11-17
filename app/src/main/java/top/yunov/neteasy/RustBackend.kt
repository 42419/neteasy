package top.yunov.neteasy

/**
 * 内嵌 Rust 后端（neteasy-backend，api-enhanced 的 Rust 移植）的 JNI 入口。
 *
 * 类名和包名不能改：native 库里导出的符号叫 `Java_top_yunov_neteasy_RustBackend_start`，
 * 符号名里编码了这里的包名和类名。
 */
object RustBackend {
    init {
        System.loadLibrary("neteasy_backend")
    }

    /**
     * 启动本地 HTTP 后端（只监听 127.0.0.1）。
     *
     * 端口绑定成功后才返回 `true`；已经在运行、参数无效或绑定失败（比如端口被占用）时返回 `false`。
     * 设备 ID、xeapi 公钥、游客 token 缓存在 [dataDir]，热启动不需要再联网初始化。
     */
    @JvmStatic
    external fun start(dataDir: String, port: Int): Boolean

    /**
     * 停止后端并等待它的线程退出；之后可以再次 [start]（同一个端口可以立刻重新监听）。
     * 确实停掉了一个正在运行的后端返回 `true`，本来就没在运行返回 `false`。
     */
    @JvmStatic
    external fun stop(): Boolean

    /** 后端当前是否在运行 */
    @JvmStatic
    external fun isRunning(): Boolean
}
