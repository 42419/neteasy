<div align="center">

# 🎵 neteasy

**Android 原生网易云音乐客户端** — Kotlin + Jetpack Compose 构建，内嵌 Node.js 后端，**无需任何外部服务器**。

</div>

<div align="center">

![Platform - Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white&style=flat)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white&style=flat)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.02.01-4285F4?logo=jetpackcompose&logoColor=white&style=flat)
![Material 3 Expressive](https://img.shields.io/badge/Material3%20Expressive-1.5.0--alpha25-6750A4?logo=materialdesign&logoColor=white&style=flat)
![API Level](https://img.shields.io/badge/API-29%2B-00897B?style=flat)

![Node.js](https://img.shields.io/badge/Node.js-18.20.4%20via%20nodejs--mobile-339933?logo=nodedotjs&logoColor=white&style=flat)
[![Backend - api-enhanced](<https://img.shields.io/badge/Backend-api--enhanced%20(436%20API)-4F5D95?logo=github&logoColor=white&style=flat>)](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced)

</div>

---

## ✨ 项目亮点

App 启动时通过 [nodejs-mobile](https://github.com/nodejs-mobile/nodejs-mobile) 在设备本地拉起
[**api-enhanced**](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced)（网易云音乐 Node.js API 后端，436 个接口），
前端只访问 `http://127.0.0.1:19800`。**不需要部署任何服务器，登录 cookie 不出设备**。

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
│  NodeService（前台服务 · dataSync）          │
│  └─ nodejs-mobile（JNI 桥 native-lib.cpp）   │
│     └─ nodejs-project/（assets → filesDir）  │
│        ├─ mobile-entry.js      ← 移动端入口   │
│        ├─ server.js / generateConfig.js      │
│        ├─ module/（436 接口） util/ data/     │
│        └─ node_modules/（pnpm 平铺，prod）    │
└─────────────────────────────────────────────┘
```

**数据流**：UI 请求 → OkHttp → 本地 Node 后端 → 网易云服务器；图片（Coil）与歌曲流（MediaPlayer）直连网易云 CDN。

## 📚 技术栈

| 领域      | 选型                                                                                                                                                                  |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 语言 / UI | [Kotlin 2.2.10](https://kotlinlang.org/) + [Jetpack Compose](https://developer.android.com/jetpack/compose)（BOM 2026.02.01）                                         |
| 设计系统  | [Material3 **1.5.0-alpha25**](https://m3.material.io/)（官方 Expressive 组件）                                                                                        |
| 网络      | [OkHttp 4.12.0](https://square.github.io/okhttp/) + [Coil 2.7.0](https://coil-kt.github.io/coil/)                                                                     |
| 播放      | MediaPlayer + MediaSessionCompat + 音频焦点（androidx.media 1.7.0）                                                                                                   |
| 内嵌后端  | [nodejs-mobile](https://github.com/nodejs-mobile/nodejs-mobile) v18.20.4（Node.js 18） + [api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) |
| 构建      | AGP 9.2.1 / Gradle 9.4.1（Kotlin DSL） / CMake 3.22.1 / NDK 28.2.13676358                                                                                             |

## 🚀 快速开始

### 环境要求

| 依赖               | 版本                   | 说明                        |
| ------------------ | ---------------------- | --------------------------- |
| Android SDK        | compileSdk 37          | 需要 platform 37 与构建工具 |
| JDK                | 21                     | Gradle toolchain            |
| NDK / CMake        | 28.2.13676358 / 3.22.1 | 编译 JNI 桥                 |
| pnpm               | ≥ 8                    | 安装 Node 依赖              |
| nodejs-mobile 产物 | v18.20.4               | 预编译 `libnode.so`，见下文 |
| api-enhanced 仓库  | 最新                   | 后端源码，构建时同步        |

### 1. 准备 nodejs-mobile

从 [nodejs-mobile releases](https://github.com/nodejs-mobile/nodejs-mobile/releases) 下载
`nodejs-mobile-v18.20.4-android.zip`，解压后将其 `bin/` 目录放到 `app/libnode/bin/`
（`libnode.so` 与 `libc++_shared.so` 需一起打包，否则运行时 `dlopen` 失败）。

### 2. 配置 api-enhanced 路径

构建时从本地 [api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) 仓库同步后端源码，二选一（路径用正斜杠）：

```bash
# 方式一：环境变量（推荐）
export NETEASY_NODE_PROJECT_DIR=D:/path/to/api-enhanced
```

```properties
# 方式二：gradle.properties
neteasy.nodeProjectDir=D:/path/to/api-enhanced
```

### 3. 构建

```bash
./gradlew :app:assembleDebug
```

构建会自动执行三个 Gradle 任务，把后端源码 + 依赖完整打进 APK：

| 任务                     | 作用                                                                                                                                       |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `syncNodeProject`        | 从 api-enhanced 同步源码到 `assets/nodejs-project`（裁剪测试 / 文档 / 配置文件），并入本项目维护的 `mobile-entry.js` / `.npmrc` / 补丁脚本 |
| `installNodeProjectDeps` | `pnpm install --prod --ignore-scripts`（必须平铺：symlink 式 node_modules 在 APK 解压后会损坏）                                            |
| `patchNodeProjectDeps`   | 给 path-to-regexp 打 nodejs-mobile 兼容补丁（其 V8 不支持 `\p{ID_Start}` Unicode 正则，不补直接 SyntaxError）                              |

**产物**：按 ABI 拆分三个 APK（`arm64-v8a` / `armeabi-v7a` / `x86_64`），位于 `app/build/outputs/apk/debug/`。

### 4. 运行

用 Android Studio 直接 Run，或 `adb install` 安装对应 ABI 的 APK。

首次启动会拉起本地 Node 后端（约 2~5 秒），`logcat` 出现 `Server started successfully` 即可正常使用。

### 5. 使用 GitHub Actions 打包（可选）

仓库已内置 CI（`.github/workflows/build-apk.yml`），无需本地环境即可出包：

- **手动触发**：Actions 页面 → *Build APK* → Run workflow，可选 `debug` / `release`
- **打 tag 触发**：推送 `v*` tag（如 `v1.0.0`）自动构建 `release` 并发布 GitHub Release（APK 挂到 Release 资产）

CI 会自动完成：安装 platform 37 / NDK / CMake → 下载 nodejs-mobile 预编译 `libnode.so` → 从 NDK 补齐 `libc++_shared.so` → clone api-enhanced 后端 → 构建 → 上传 APK 产物。

## 📁 项目结构

```
app/src/main/
├── AndroidManifest.xml          # 权限、Activity、NodeService / PlaybackService 声明
├── assets/                      # nodejs-project/ 由 Gradle 同步任务生成（gitignore）
├── cpp/native-lib.cpp           # JNI 桥：node::Start + stdout/stderr 重定向 logcat
├── java/top/yunov/neteasy/
│   ├── NeteasyApp.kt            # Application：拉起 NodeService；App 级单例持有处
│   ├── NodeService.kt           # 前台服务：assets 复制到 filesDir + 启动 Node 线程
│   ├── NodeJS.kt                # nodejs-mobile 入口（加载 libnode + native-lib）
│   ├── MainActivity.kt          # 主界面：首页/我的 Tab + Minibar + 展开播放页 + 队列面板
│   ├── LoginActivity.kt         # 登录页独立 Activity（系统原生转场）
│   ├── SearchActivity.kt        # 搜索页独立 Activity
│   ├── SettingsActivity.kt      # 设置页独立 Activity
│   ├── PlaylistActivity.kt      # 歌单详情独立 Activity
│   ├── data/                    # ApiClient / CookieStore / NcmRepository / Models / SettingsStore
│   ├── player/                  # PlayerController / PlaybackService / PlayerActions
│   └── ui/                      # 各屏幕 + theme（Expressive 主题 / 形状 / 动效 token）
├── libnode/                     # nodejs-mobile 预编译产物 bin/<ABI>/libnode.so（不入库）
├── keepRules/rules.keep         # R8 keep 规则
└── res/xml/network_security_config.xml  # 放行 127.0.0.1 明文 HTTP + 网易云 CDN

nodejs-project/                  # 本项目维护的移动端补丁与入口
├── mobile-entry.js              # TMPDIR 重定向 + xeapi 预热 + 起 HTTP 服务
├── .npmrc                       # node-linker=hoisted（pnpm 平铺）
└── patch-path-to-regexp.js      # nodejs-mobile 兼容补丁脚本
```

## ⚙️ 工作原理

1. **启动** — `NeteasyApp.onCreate` 拉起 `NodeService`（前台服务，`dataSync` 类型）
2. **部署** — 仅当 APK 更新（`lastUpdateTime` 变化）时把 `assets/nodejs-project` 复制到 `filesDir`，避免每次启动变慢
3. **运行** — JNI 桥在独立线程调用 `node::Start`，执行 `mobile-entry.js`：
    - 将 `os.tmpdir()` monkey-patch 到应用私有目录（Android 沙箱 `/tmp` 不可写，`anonymous_token` / xeapi 公钥 / 上传临时文件全靠它）
    - 预热 xeapi 公钥（上游 generateConfig 首次自举会失败）
    - `serveNcmApi({ checkVersion: false, port: 19800, host: '127.0.0.1' })` —— `checkVersion: false` 绕开 `child_process.exec`（nodejs-mobile 不支持子进程）；只绑本机，不暴露局域网
4. **容错** — Node 未就绪时请求会遇 `ConnectException`，`ApiClient` 仅对**连接建立失败**这一种错误轮询重试（最多 30 次 × 400ms），其余错误直接抛出——避免冷启动时误判「未登录」而丢失请求携带的 cookie

## 💡 关键设计

- **App 级单例**：`ApiClient` / `NcmRepository` / `PlayerController` 由 `NeteasyApp` 持有，独立 Activity 共用同一份状态，播放不因 Activity 重建 / 切后台而中断
- **播放器状态机**：`PlayerController` 持有 `MutableStateFlow<PlayerUiState>`，UI、通知服务、MediaSession 订阅同一份状态；异步 URL 解析带代际号（`loadGeneration`）防止旧回调乱序覆盖
- **音质判定**：歌曲实际存在的音质档位取自 `/song/detail` 的 `l/h/sq/hr` 字段（存在才列出），切音质重新解析 URL 并原进度续播，暂停态切音质不会自动播放
- **锁屏封面**：系统控制中心读 MediaSession 不会自己联网下图片，必须把解码好的**软件位图**（`allowHardware(false)`，硬件位图跨进程 Binder 传不过去）塞进 metadata
- **通知按钮**：PendingIntent 直接指向本 Service 并带自定义 action，在 `onStartCommand` 分发——不依赖 `buildMediaButtonPendingIntent()` 的反查机制（查不到返回 null，曾导致放歌闪退）
- **导航**：设置 / 登录 / 搜索 / 歌单详情拆成独立 Activity，交给系统接管原生转场；展开播放页与队列面板留在 Compose 内（是同一播放器的展开 / 收起，不是跳转新页面）
- **Tab keep-alive**：首页两个 Tab 常驻组合，切换只改可见性与层级（隐藏页铺透明挡板吃指针事件），数据与滚动位置不重建

## ⚠️ 已知限制

- 内嵌 Node 为 18.20.4（nodejs-mobile 最新版），Node 18 已 EOL，功能不受影响
- 无损 / Hi-Res 通常需黑胶 VIP 权限，权限不足时服务端自动降级；无版权 / VIP 歌曲 URL 为空，无法播放
- release 构建暂用 debug 签名且未开 R8 优化，keep 规则见 `app/src/main/keepRules/rules.keep`
- `NodeService` 前台服务类型为 `dataSync`，Android 15+ 有 6 小时上限；播放服务（`mediaPlayback`）不受此限制

## 🤝 贡献

欢迎提交 Issue 与 PR。涉及 Node 后端行为的改动，请同步验证 [api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) 上游行为。

## 🙏 致谢

- [NeteaseCloudMusicApiEnhanced/api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) — 内嵌的网易云音乐 API 后端
- [nodejs-mobile](https://github.com/nodejs-mobile/nodejs-mobile) — Android 上的 Node.js 运行时
- Material Design 3 Expressive 设计体系
