<div align="center">

# 🎵 neteasy

**Android 原生网易云音乐客户端** — Kotlin + Jetpack Compose 构建，内嵌 Rust 后端，**无需任何外部服务器**。

</div>

<div align="center">

![Platform - Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white&style=flat)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white&style=flat)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.02.01-4285F4?logo=jetpackcompose&logoColor=white&style=flat)
![Material 3 Expressive](https://img.shields.io/badge/Material3%20Expressive-1.5.0--alpha25-6750A4?logo=materialdesign&logoColor=white&style=flat)
![API Level](https://img.shields.io/badge/API-29%2B-00897B?style=flat)

![Rust](https://img.shields.io/badge/Backend-Rust%20(neteasy--backend)-DEA584?logo=rust&logoColor=white&style=flat)
[![Backend - neteasy-backend](<https://img.shields.io/badge/Backend-neteasy--backend-4F5D95?logo=github&logoColor=white&style=flat>)](https://github.com/42419/neteasy-backend)

</div>

---

## ✨ 项目亮点

App 启动时通过 JNI 在设备本地拉起 [**neteasy-backend**](https://github.com/42419/neteasy-backend)
（[api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) 中本应用实际用到的接口的 Rust 移植版，
单个动态库约 1.8~3.5 MB），前端只访问 `http://127.0.0.1:19800`。
**不需要部署任何服务器，登录 cookie 不出设备**；冷启动在毫秒级完成，不再有 Node 运行时的几秒启动开销。

## 🧩 功能特性

- 🏠 **发现音乐** — Banner 轮播 + 推荐歌单，顶部胶囊搜索框入口
- 🔍 **搜索** — 500ms 防抖，结果即点即播（整批结果入队自动连播）
- 📱 **扫码登录** — 二维码实时轮询（等待 / 已扫码 / 过期 / 成功），登录态 cookie 持久化
- 👤 **我的** — 登录态卡片、喜欢的音乐、自建与收藏歌单
- 📃 **歌单详情** — 大封面头部 + 播放全部 + 歌曲列表
- ▶️ **完整播放器**
    - 队列播放：上一首 / 下一首 / 自动连播 / 队列面板点播
    - 循环模式：顺序播放 / 列表循环 / 单曲循环
    - 音质切换：标准 / 极高 / 无损 / Hi-Res（仅列出该曲实际存在的档位，切换后原进度续播）
    - 系统集成：音频焦点、拔耳机自动暂停、锁屏 / 通知栏 / 蓝牙耳机 / 车机物理键控制
- 🎨 **M3 Expressive 设计** — 官方 spring 物理动效、可拖动波浪进度条、10 级大圆角、强调排版
- 🌗 **设置** — 深色模式三选一（跟随系统 / 浅色 / 深色）、Material You 动态取色（可回退网易云品牌红）

## 🏗️ 架构

```
┌─────────────────────────────────────────────┐
│  Compose UI（Kotlin）                        │
│  首页 · 我的 · 搜索 · 歌单 · 播放页 · 设置    │
└──────────────────────┬──────────────────────┘
                       │  HTTP 127.0.0.1:19800（OkHttp）
┌──────────────────────▼──────────────────────┐
│  BackendService（前台服务 · dataSync）       │
│  └─ RustBackend.start()（JNI）               │
│     └─ libneteasy_backend.so（Rust）         │
│        ├─ axum HTTP 服务（127.0.0.1:19800）  │
│        ├─ module/（按路由一文件）            │
│        └─ util/（weapi / eapi / xeapi 加密）  │
└─────────────────────────────────────────────┘
```

**数据流**：UI 请求 → OkHttp → 本地 Rust 后端 → 网易云服务器；图片（Coil）与歌曲流（MediaPlayer）直连网易云 CDN。

## 📚 技术栈

| 领域      | 选型                                                                                                                                                                  |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 语言 / UI | [Kotlin 2.2.10](https://kotlinlang.org/) + [Jetpack Compose](https://developer.android.com/jetpack/compose)（BOM 2026.02.01）                                         |
| 设计系统  | [Material3 **1.5.0-alpha25**](https://m3.material.io/)（官方 Expressive 组件）                                                                                        |
| 网络      | [OkHttp 4.12.0](https://square.github.io/okhttp/) + [Coil 2.7.0](https://coil-kt.github.io/coil/)                                                                     |
| 播放      | MediaPlayer + MediaSessionCompat + 音频焦点（androidx.media 1.7.0）                                                                                                   |
| 内嵌后端  | [neteasy-backend](https://github.com/42419/neteasy-backend)（Rust，api-enhanced 的移植版，经 JNI 加载）                                                          |
| 构建      | AGP 9.2.1 / Gradle 9.4.1（Kotlin DSL）                                                                                             |

## 🚀 快速开始

### 环境要求

| 依赖        | 版本          | 说明                                                            |
| ----------- | ------------- | --------------------------------------------------------------- |
| Android SDK | compileSdk 37 | 需要 platform 37 与构建工具                                     |
| JDK         | 21            | Gradle toolchain                                                |
| 网络        | —             | 构建时从 neteasy-backend 的 GitHub Release 下载预编译后端动态库 |

不再需要 NDK / CMake / Node.js / pnpm / nodejs-mobile / api-enhanced 源码。

### 1. 构建

```bash
./gradlew :app:assembleDebug
```

构建会先执行 `fetchBackendLibs` 任务：从
[neteasy-backend 的 Release](https://github.com/42419/neteasy-backend/releases) 下载
`libneteasy_backend-<abi>.so`（三个 ABI），**逐个校验 SHA-256** 后放进
`app/build/backend-jniLibs/<abi>/libneteasy_backend.so` 并打进 APK。版本号和哈希写在 `gradle.properties`
（`neteasy.backendVersion` / `neteasy.backendSha256.*`），升级后端时一起改。

**产物**：按 ABI 拆分三个 APK（`arm64-v8a` / `armeabi-v7a` / `x86_64`），位于 `app/build/outputs/apk/debug/`。

### 2. 本地调试后端（可选）

想用本机编译的后端代替下载的版本：在 neteasy-backend 仓库里执行

```bash
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o jniLibs build --release --lib
```

然后把环境变量 `NETEASY_BACKEND_JNILIBS_DIR`（或 `gradle.properties` 里的 `neteasy.backendJniLibsDir`）
指向该 `jniLibs` 目录，构建时就会跳过下载。

### 3. 运行

用 Android Studio 直接 Run，或 `adb install` 安装对应 ABI 的 APK。

后端在毫秒级就绪，`logcat` 里能看到 `BackendService: RustBackend.start -> true（耗时 …ms）`，
以及 tag 为 `neteasy-backend` 的后端日志。

### 4. 使用 GitHub Actions

- **`ci.yml`**（每次推送 / PR 自动跑）：编译 + 单元测试；并在 API 34 / x86_64 模拟器上跑
  `RustBackendInstrumentedTest`——真正经 JNI 加载后端、用 HTTP 访问本地接口、校验冷启动耗时
- **`build-apk.yml`**（手动 / 打 tag）：出 `debug` / `release` APK；推送 `v*` tag（如 `v1.0.0`）自动构建 `release`
  并发布 GitHub Release（APK 挂到 Release 资产）

## 📁 项目结构

```
app/src/main/
├── AndroidManifest.xml          # 权限、Activity、BackendService / PlaybackService 声明
├── java/top/yunov/neteasy/
│   ├── NeteasyApp.kt            # Application：拉起 BackendService；App 级单例持有处
│   ├── RustBackend.kt           # Rust 后端 JNI 入口（类名/包名不能改，符号名里编码了它）
│   ├── bridge/BackendService.kt # 前台服务：调用 RustBackend.start，清理旧版 Node 残留
│   ├── MainActivity.kt          # 主界面：首页/我的 Tab + Minibar + 展开播放页 + 队列面板
│   ├── LoginActivity.kt         # 登录页独立 Activity（系统原生转场）
│   ├── SearchActivity.kt        # 搜索页独立 Activity
│   ├── SettingsActivity.kt      # 设置页独立 Activity
│   ├── PlaylistActivity.kt      # 歌单详情独立 Activity
│   ├── data/                    # ApiClient / CookieStore / NcmRepository / Models / SettingsStore
│   ├── player/                  # PlayerController / PlaybackService / PlayerActions
│   └── ui/                      # 各屏幕 + theme（Expressive 主题 / 形状 / 动效 token）
├── keepRules/rules.keep         # R8 keep 规则
└── res/xml/network_security_config.xml  # 放行 127.0.0.1 明文 HTTP + 网易云 CDN

app/src/androidTest/              # RustBackendInstrumentedTest：模拟器上的后端端到端测试
.github/workflows/               # ci.yml（编译 + 测试）/ build-apk.yml（出包）
```

## ⚙️ 工作原理

1. **启动** — `NeteasyApp.onCreate` 拉起 `BackendService`（前台服务，`dataSync` 类型）
2. **加载** — 服务里调用 `RustBackend.start(filesDir/neteasy-backend, 19800)`（JNI）；该调用在端口绑定成功后才返回，
   绑定失败（如端口被占用）返回 `false`。不再复制 assets，也没有 Node 线程和模块加载开销
3. **引导** — 后端先绑定 `127.0.0.1:19800`（只绑本机，不暴露局域网），再在后台获取 xeapi 公钥和游客 token；
   设备 ID / 公钥 / token 缓存在 `filesDir/neteasy-backend`，热启动不用再联网初始化，首次冷启动期间的请求会等待引导完成（最多 20 秒）
4. **容错** — `ApiClient` 仍对**连接建立失败**这一种错误做短暂轮询重试（最多 30 次 × 400ms）作为兜底，其余错误直接抛出
5. **升级清理** — 从旧版（内嵌 Node）升级后，首次启动会自动删除遗留的 `filesDir/nodejs-project`

## 💡 关键设计

- **App 级单例**：`ApiClient` / `NcmRepository` / `PlayerController` 由 `NeteasyApp` 持有，独立 Activity 共用同一份状态，播放不因 Activity 重建 / 切后台而中断
- **播放器状态机**：`PlayerController` 持有 `MutableStateFlow<PlayerUiState>`，UI、通知服务、MediaSession 订阅同一份状态；异步 URL 解析带代际号（`loadGeneration`）防止旧回调乱序覆盖
- **音质判定**：歌曲实际存在的音质档位取自 `/song/detail` 的 `l/h/sq/hr` 字段（存在才列出），切音质重新解析 URL 并原进度续播，暂停态切音质不会自动播放
- **锁屏封面**：系统控制中心读 MediaSession 不会自己联网下图片，必须把解码好的**软件位图**（`allowHardware(false)`，硬件位图跨进程 Binder 传不过去）塞进 metadata
- **通知按钮**：PendingIntent 直接指向本 Service 并带自定义 action，在 `onStartCommand` 分发——不依赖 `buildMediaButtonPendingIntent()` 的反查机制（查不到返回 null，曾导致放歌闪退）
- **导航**：设置 / 登录 / 搜索 / 歌单详情拆成独立 Activity，交给系统接管原生转场；展开播放页与队列面板留在 Compose 内（是同一播放器的展开 / 收起，不是跳转新页面）
- **Tab keep-alive**：首页两个 Tab 常驻组合，切换只改可见性与层级（隐藏页铺透明挡板吃指针事件），数据与滚动位置不重建

## ⚠️ 已知限制

- 评论区只读：可以浏览、翻页、排序、点赞，不提供发评论（后端不实现 `/comment/add`）
- 无损 / Hi-Res 通常需黑胶 VIP 权限，权限不足时服务端自动降级；无版权 / VIP 歌曲 URL 为空，无法播放
- release 构建暂用 debug 签名且未开 R8 优化，keep 规则见 `app/src/main/keepRules/rules.keep`
- `BackendService` 前台服务类型为 `dataSync`，Android 15+ 有 6 小时上限；播放服务（`mediaPlayback`）不受此限制

## 🤝 贡献

欢迎提交 Issue 与 PR。涉及后端行为的改动，请到 [neteasy-backend](https://github.com/42419/neteasy-backend) 仓库处理，并对照 [api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) 上游行为验证。

## 🙏 致谢

- [NeteaseCloudMusicApiEnhanced/api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) — 网易云音乐 API 后端；本项目的 Rust 后端（[neteasy-backend](https://github.com/42419/neteasy-backend)）移植自它
- Material Design 3 Expressive 设计体系
